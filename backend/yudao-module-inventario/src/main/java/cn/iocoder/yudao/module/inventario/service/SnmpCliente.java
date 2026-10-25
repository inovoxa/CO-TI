package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.module.inventario.dominio.SnmpLeitura;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

/**
 * Consulta SNMP (v2c ou v3) a um equipamento: valores avulsos com GET e tabelas com GETNEXT. Uma instância por
 * varredura; feche ao terminar.
 */
public class SnmpCliente implements Closeable {

    /** Linhas lidas por tabela, no máximo (um switch de 48 portas tem ~60 interfaces) */
    static final int MAXIMO_LINHAS = 256;

    /** Credencial já decifrada. */
    public record Credencial(String versao, String comunidade, String usuario, String nivel, String authProtocolo, String authSenha,
                             String privProtocolo, String privSenha, int porta) {
    }

    private final Snmp snmp;
    private final USM usm;
    private final int tempoLimiteMs;
    private final int tentativas;
    private final Set<String> usuariosV3 = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public SnmpCliente(int tempoLimiteMs, int tentativas) throws IOException {
        this.tempoLimiteMs = tempoLimiteMs;
        this.tentativas = tentativas;
        SecurityProtocols protocolos = SecurityProtocols.getInstance();
        protocolos.addDefaultProtocols();
        // o SNMP4J 3 deixa fora dos padrões o SHA-1, o MD5 e o DES (fracos), mas a maioria dos equipamentos ainda
        // usa SHA-1/AES-128: sem eles, o v3 falha com "Unsupported security level"
        protocolos.addAuthenticationProtocol(new AuthSHA());
        protocolos.addAuthenticationProtocol(new AuthMD5());
        protocolos.addPrivacyProtocol(new PrivDES());
        protocolos.addPrivacyProtocol(new PrivAES128());
        protocolos.addPrivacyProtocol(new PrivAES256());
        MessageDispatcher despachante = new MessageDispatcherImpl();
        despachante.addMessageProcessingModel(new MPv1());
        despachante.addMessageProcessingModel(new MPv2c());
        usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
        despachante.addMessageProcessingModel(new MPv3(usm));
        snmp = new Snmp(despachante, new DefaultUdpTransportMapping());
        snmp.listen();
    }

    @Override
    public void close() throws IOException {
        snmp.close();
    }

    /** Lê o equipamento; null = não respondeu com esta credencial. */
    public SnmpLeitura.Dados ler(String ip, Credencial c) throws IOException {
        Target<UdpAddress> alvo = alvo(ip, c);
        Map<String, String> valores = get(alvo, c, SnmpLeitura.CONSULTA_UNICA);
        if (valores == null || valores.get(SnmpLeitura.SYS_DESCR) == null && valores.get(SnmpLeitura.SYS_NAME) == null
                && valores.get(SnmpLeitura.SYS_OBJECT_ID) == null) {
            return null;
        }
        Map<String, Map<String, String>> tabelas = new LinkedHashMap<>();
        for (String coluna : SnmpLeitura.TABELAS) {
            tabelas.put(coluna, coluna(alvo, c, coluna));
        }
        return new SnmpLeitura.Dados(valores, tabelas);
    }

    private Target<UdpAddress> alvo(String ip, Credencial c) {
        UdpAddress endereco = new UdpAddress(ip + "/" + c.porta());
        if ("v3".equals(c.versao())) {
            OctetString usuario = new OctetString(c.usuario());
            String chave = c.usuario() + "|" + c.authProtocolo() + "|" + c.privProtocolo();
            if (usuariosV3.add(chave)) {
                usm.addUser(usuario, new UsmUser(usuario, authOid(c), senha(c.authSenha(), authOid(c)), privOid(c), senha(c.privSenha(), privOid(c))));
            }
            UserTarget<UdpAddress> t = new UserTarget<>();
            t.setAddress(endereco);
            t.setVersion(SnmpConstants.version3);
            t.setSecurityName(usuario);
            t.setSecurityLevel(switch (Objects.toString(c.nivel(), "authPriv")) {
                case "noAuthNoPriv" -> SecurityLevel.NOAUTH_NOPRIV;
                case "authNoPriv" -> SecurityLevel.AUTH_NOPRIV;
                default -> SecurityLevel.AUTH_PRIV;
            });
            t.setTimeout(tempoLimiteMs);
            t.setRetries(tentativas);
            return t;
        }
        CommunityTarget<UdpAddress> t = new CommunityTarget<>();
        t.setAddress(endereco);
        t.setCommunity(new OctetString(c.comunidade()));
        t.setVersion(SnmpConstants.version2c);
        t.setTimeout(tempoLimiteMs);
        t.setRetries(tentativas);
        return t;
    }

    private static OctetString senha(String s, OID protocolo) {
        return protocolo == null || s == null ? null : new OctetString(s);
    }

    private static OID authOid(Credencial c) {
        if ("noAuthNoPriv".equals(c.nivel())) {
            return null;
        }
        return switch (Objects.toString(c.authProtocolo(), "SHA")) {
            case "MD5" -> AuthMD5.ID;
            case "SHA256" -> AuthHMAC192SHA256.ID;
            case "SHA512" -> AuthHMAC384SHA512.ID;
            default -> AuthSHA.ID;
        };
    }

    private static OID privOid(Credencial c) {
        if (!"authPriv".equals(Objects.toString(c.nivel(), "authPriv"))) {
            return null;
        }
        return switch (Objects.toString(c.privProtocolo(), "AES")) {
            case "DES" -> PrivDES.ID;
            case "AES256" -> PrivAES256.ID;
            default -> PrivAES128.ID;
        };
    }

    private PDU pdu(Credencial c) {
        return "v3".equals(c.versao()) ? new ScopedPDU() : new PDU();
    }

    private Map<String, String> get(Target<UdpAddress> alvo, Credencial c, List<String> oids) throws IOException {
        PDU p = pdu(c);
        p.setType(PDU.GET);
        oids.forEach(o -> p.add(new VariableBinding(new OID(o))));
        ResponseEvent<UdpAddress> r = snmp.send(p, alvo);
        if (r == null || r.getResponse() == null || r.getResponse().getErrorStatus() == PDU.authorizationError) {
            return null;
        }
        PDU resp = r.getResponse();
        if (resp.getErrorStatus() != PDU.noError) {
            // v1/agentes antigos recusam o pacote todo se um OID não existe: tenta um por um
            Map<String, String> m = new LinkedHashMap<>();
            for (String o : oids) {
                PDU um = pdu(c);
                um.setType(PDU.GET);
                um.add(new VariableBinding(new OID(o)));
                ResponseEvent<UdpAddress> x = snmp.send(um, alvo);
                if (x != null && x.getResponse() != null && x.getResponse().getErrorStatus() == PDU.noError) {
                    VariableBinding vb = x.getResponse().get(0);
                    if (!vb.getVariable().isException()) {
                        m.put(o, texto(vb.getVariable()));
                    }
                }
            }
            return m;
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (VariableBinding vb : resp.getVariableBindings()) {
            if (!vb.getVariable().isException()) {
                m.put(vb.getOid().toDottedString(), texto(vb.getVariable()));
            }
        }
        return m;
    }

    /** Coluna de tabela com GETNEXT: índice (o que vem depois do OID da coluna) → valor. */
    private Map<String, String> coluna(Target<UdpAddress> alvo, Credencial c, String colunaOid) throws IOException {
        OID raiz = new OID(colunaOid);
        OID atual = raiz;
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < MAXIMO_LINHAS; i++) {
            PDU p = pdu(c);
            p.setType(PDU.GETNEXT);
            p.add(new VariableBinding(atual));
            ResponseEvent<UdpAddress> r = snmp.send(p, alvo);
            if (r == null || r.getResponse() == null || r.getResponse().getErrorStatus() != PDU.noError) {
                break;
            }
            VariableBinding vb = r.getResponse().get(0);
            if (vb.getVariable().isException() || !vb.getOid().startsWith(raiz) || vb.getOid().compareTo(atual) <= 0) {
                break;
            }
            m.put(vb.getOid().toDottedString().substring(raiz.toDottedString().length() + 1), texto(vb.getVariable()));
            atual = vb.getOid();
        }
        return m;
    }

    /** UTF-8 quando for válido; senão Latin-1 (equipamentos antigos). Sem os NUL do fim que alguns mandam. */
    static String textoDeBytes(byte[] b) {
        int n = b.length;
        while (n > 0 && b[n - 1] == 0) {
            n--;
        }
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(b, 0, n)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(b, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    private static String texto(Variable v) {
        if (v instanceof OctetString o) {
            // MAC (6 bytes) vira aa:bb:..; o resto é texto (o SNMP4J só acha "imprimível" o ASCII: acentos viram hex)
            return o.length() == 6 && !o.isPrintable() ? o.toHexString(':') : textoDeBytes(o.getValue());
        }
        if (v instanceof TimeTicks t) {
            return String.valueOf(t.getValue());
        }
        return v.toString();
    }

}
