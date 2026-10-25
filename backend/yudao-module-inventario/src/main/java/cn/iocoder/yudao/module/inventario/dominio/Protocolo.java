package cn.iocoder.yudao.module.inventario.dominio;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * Protocolo dos agentes de inventário, sem dependência de servidor (ver docs/specs/inventario-agentes.md):
 * <ul>
 *     <li><b>OCS Inventory</b> (agentes Windows 2.x e Unix): POST de XML comprimido com zlib; {@code QUERY} é PROLOG
 *     (o agente pergunta se deve mandar o inventário) ou INVENTORY; a resposta é um {@code REPLY} XML, também
 *     comprimido. Conferido no servidor OCS 2.12.6 (Apache/Ocsinventory.pm, Server/Communication.pm).</li>
 *     <li><b>GLPI Agent</b>: JSON ({@code action} = contact ou inventory), comprimido com zlib ou puro.</li>
 * </ul>
 */
public final class Protocolo {

    private Protocolo() {
    }

    /** Limite do conteúdo já descomprimido (uma bomba de compressão não derruba o servidor). */
    public static final int MAXIMO_DESCOMPRIMIDO = 50 * 1024 * 1024;

    /** O que o agente mandou: tipo da consulta, identificador e as seções do inventário. */
    public record Pedido(String formato, String consulta, String deviceId, Map<String, List<Map<String, String>>> secoes) {

        /** Primeiro (ou único) item de uma seção, ou vazio. */
        public Map<String, String> unico(String secao) {
            List<Map<String, String>> l = secoes.get(secao);
            return l == null || l.isEmpty() ? Map.of() : l.get(0);
        }

        public List<Map<String, String>> lista(String secao) {
            return secoes.getOrDefault(secao, List.of());
        }
    }

    public static class ProtocoloInvalido extends RuntimeException {
        public ProtocoloInvalido(String mensagem) {
            super(mensagem);
        }
    }

    // ======================================================================================= descompressão

    /** zlib (o normal), gzip, deflate puro ou texto sem compressão. */
    public static byte[] descomprimir(byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            throw new ProtocoloInvalido("corpo vazio");
        }
        int primeiro = corpo[0] & 0xff;
        if (primeiro == '<' || primeiro == '{' || primeiro == 0xEF /* BOM UTF-8 */) {
            return corpo;
        }
        if (primeiro == 0x1f && corpo.length > 1 && (corpo[1] & 0xff) == 0x8b) {
            try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(corpo))) {
                return lerComLimite(in);
            } catch (IOException e) {
                throw new ProtocoloInvalido("gzip inválido");
            }
        }
        for (boolean semCabecalho : new boolean[]{false, true}) {
            Inflater inf = new Inflater(semCabecalho);
            try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(corpo), inf)) {
                byte[] r = lerComLimite(in);
                if (r.length > 0) {
                    return r;
                }
            } catch (IOException e) {
                // tenta o próximo formato
            } finally {
                inf.end();
            }
        }
        throw new ProtocoloInvalido("compressão não reconhecida");
    }

    private static byte[] lerComLimite(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAXIMO_DESCOMPRIMIDO) {
                throw new ProtocoloInvalido("inventário maior que " + (MAXIMO_DESCOMPRIMIDO / 1024 / 1024) + " MB");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** Comprime a resposta como o servidor OCS (Compress::Zlib::compress = formato zlib). */
    public static byte[] comprimir(byte[] dados) {
        Deflater d = new Deflater();
        try {
            d.setInput(dados);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!d.finished()) {
                out.write(buf, 0, d.deflate(buf));
            }
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    // ======================================================================================= leitura

    public static Pedido ler(byte[] corpo) {
        byte[] dados = descomprimir(corpo);
        String texto = new String(dados, StandardCharsets.UTF_8).strip();
        if (texto.startsWith("﻿")) {
            texto = texto.substring(1);
        }
        return texto.startsWith("{") ? lerJson(texto) : lerXml(dados);
    }

    /** XML do agente OCS: {@code <REQUEST><QUERY>..</QUERY><DEVICEID>..</DEVICEID><CONTENT>...}. */
    static Pedido lerXml(byte[] dados) {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            // sem DTD nem entidades externas (XXE)
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            doc = b.parse(new ByteArrayInputStream(codificacaoSegura(dados)));
        } catch (Exception e) {
            throw new ProtocoloInvalido("XML inválido: " + e.getMessage());
        }
        Element raiz = doc.getDocumentElement();
        if (!"REQUEST".equalsIgnoreCase(raiz.getTagName())) {
            throw new ProtocoloInvalido("raiz do XML não é REQUEST");
        }
        // o agente Unix escreve com XML::Simple, que põe os campos como atributos: <REQUEST QUERY="DOWNLOAD" ID=".."/>
        Map<String, String> raizCampos = new LinkedHashMap<>();
        for (int i = 0; i < raiz.getAttributes().getLength(); i++) {
            raizCampos.put(raiz.getAttributes().item(i).getNodeName().toUpperCase(Locale.ROOT), raiz.getAttributes().item(i).getNodeValue().strip());
        }
        NodeList daRaiz = raiz.getChildNodes();
        for (int i = 0; i < daRaiz.getLength(); i++) {
            if (daRaiz.item(i) instanceof Element e && !"CONTENT".equalsIgnoreCase(e.getTagName())
                    && e.getElementsByTagName("*").getLength() == 0) {
                raizCampos.putIfAbsent(e.getTagName().toUpperCase(Locale.ROOT), e.getTextContent().strip());
            }
        }
        String consulta = raizCampos.get("QUERY");
        String device = raizCampos.get("DEVICEID");
        Map<String, List<Map<String, String>>> secoes = new LinkedHashMap<>();
        secoes.put("REQUEST", new ArrayList<>(List.of(raizCampos)));
        Element conteudo = filho(raiz, "CONTENT");
        if (conteudo != null) {
            if (device == null) {
                device = textoFilho(conteudo, "DEVICEID");
            }
            NodeList filhos = conteudo.getChildNodes();
            for (int i = 0; i < filhos.getLength(); i++) {
                if (filhos.item(i) instanceof Element secao && "IPDISCOVER".equalsIgnoreCase(secao.getTagName())) {
                    // <IPDISCOVER><H><I>ip</I><M>mac</M><N>nome</N></H>...: um item por H
                    List<Map<String, String>> hosts = secoes.computeIfAbsent("IPDISCOVER", k -> new ArrayList<>());
                    NodeList hs = secao.getChildNodes();
                    for (int j = 0; j < hs.getLength(); j++) {
                        if (hs.item(j) instanceof Element h) {
                            Map<String, String> campos = new LinkedHashMap<>();
                            NodeList cs = h.getChildNodes();
                            for (int k = 0; k < cs.getLength(); k++) {
                                if (cs.item(k) instanceof Element c) {
                                    campos.put(c.getTagName().toUpperCase(Locale.ROOT), c.getTextContent().strip());
                                }
                            }
                            hosts.add(campos);
                        }
                    }
                } else if (filhos.item(i) instanceof Element secao) {
                    Map<String, String> campos = new LinkedHashMap<>();
                    NodeList cs = secao.getChildNodes();
                    boolean temFilho = false;
                    for (int j = 0; j < cs.getLength(); j++) {
                        if (cs.item(j) instanceof Element c) {
                            temFilho = true;
                            campos.put(c.getTagName().toUpperCase(Locale.ROOT), c.getTextContent().strip());
                        }
                    }
                    if (!temFilho) {
                        campos.put("VALOR", secao.getTextContent().strip());
                    }
                    secoes.computeIfAbsent(secao.getTagName().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(campos);
                }
            }
        }
        if (consulta == null || consulta.isBlank()) {
            throw new ProtocoloInvalido("QUERY ausente");
        }
        return new Pedido("ocs", consulta.strip().toUpperCase(Locale.ROOT), device, secoes);
    }

    /**
     * O agente Windows antigo declara ISO-8859-1 / Windows-1252 no cabeçalho e manda nessa codificação: o parser do
     * JDK respeita a declaração, então só garante que um "encoding" desconhecido não derrube a leitura.
     */
    private static byte[] codificacaoSegura(byte[] dados) {
        String inicio = new String(dados, 0, Math.min(dados.length, 200), StandardCharsets.ISO_8859_1);
        if (inicio.matches("(?s)^<\\?xml[^>]*encoding=['\"](?i)(utf-8|iso-8859-1|windows-1252|us-ascii|utf-16)['\"].*") || !inicio.startsWith("<?xml")) {
            return dados;
        }
        // declaração com codificação estranha: trata como UTF-8
        String s = new String(dados, StandardCharsets.UTF_8).replaceFirst("encoding=['\"][^'\"]*['\"]", "encoding=\"UTF-8\"");
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static Element filho(Element pai, String nome) {
        NodeList l = pai.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) {
            Node n = l.item(i);
            if (n instanceof Element e && e.getTagName().equalsIgnoreCase(nome)) {
                return e;
            }
        }
        return null;
    }

    private static String textoFilho(Element pai, String nome) {
        Element e = filho(pai, nome);
        return e == null ? null : e.getTextContent().strip();
    }

    /**
     * JSON do GLPI Agent: {@code {"action": "inventory", "deviceid": "...", "content": {"hardware": {...}, "cpus": [...]}}}.
     * As chaves viram maiúsculas, como no XML do OCS; alguns nomes do GLPI Agent são traduzidos para os do OCS.
     */
    @SuppressWarnings("unchecked")
    static Pedido lerJson(String texto) {
        Map<String, Object> j;
        try {
            j = JsonUtils.parseObject(texto, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new ProtocoloInvalido("JSON inválido");
        }
        if (j == null || j.get("action") == null) {
            throw new ProtocoloInvalido("JSON sem action");
        }
        Map<String, List<Map<String, String>>> secoes = new LinkedHashMap<>();
        if (j.get("content") instanceof Map<?, ?> conteudo) {
            for (Map.Entry<?, ?> e : conteudo.entrySet()) {
                String nome = String.valueOf(e.getKey()).toUpperCase(Locale.ROOT);
                List<Map<String, String>> itens = new ArrayList<>();
                if (e.getValue() instanceof List<?> l) {
                    for (Object o : l) {
                        if (o instanceof Map<?, ?> m) {
                            itens.add(plano((Map<String, Object>) m));
                        }
                    }
                } else if (e.getValue() instanceof Map<?, ?> m) {
                    itens.add(plano((Map<String, Object>) m));
                } else if (e.getValue() != null) {
                    itens.add(Map.of("VALOR", String.valueOf(e.getValue())));
                }
                secoes.put(nome, itens);
            }
        }
        traduzirGlpiAgent(secoes);
        return new Pedido("glpi", String.valueOf(j.get("action")).toUpperCase(Locale.ROOT),
                j.get("deviceid") == null ? null : j.get("deviceid").toString(), secoes);
    }

    private static Map<String, String> plano(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (v != null && !(v instanceof Map) && !(v instanceof List)) {
                out.put(k.toUpperCase(Locale.ROOT), v.toString());
            }
        });
        return out;
    }

    /** Nomes do GLPI Agent que diferem dos do OCS. */
    private static void traduzirGlpiAgent(Map<String, List<Map<String, String>>> s) {
        List<Map<String, String>> sistemas = s.getOrDefault("OPERATINGSYSTEM", List.of());
        Map<String, String> so = sistemas.isEmpty() ? Map.of() : sistemas.get(0);
        if (!so.isEmpty()) {
            Map<String, String> hw = s.computeIfAbsent("HARDWARE", k -> new ArrayList<>(List.of(new LinkedHashMap<>()))).get(0);
            putSeVazio(hw, "OSNAME", so.getOrDefault("FULL_NAME", so.get("NAME")));
            putSeVazio(hw, "OSVERSION", so.get("VERSION"));
            putSeVazio(hw, "ARCH", so.get("ARCH"));
        }
        // usuário logado: o GLPI Agent manda numa seção própria (users: [{login, domain}])
        List<Map<String, String>> usuarios = s.getOrDefault("USERS", List.of());
        if (!usuarios.isEmpty() && usuarios.get(0).get("LOGIN") != null) {
            Map<String, String> hw = s.computeIfAbsent("HARDWARE", k -> new ArrayList<>(List.of(new LinkedHashMap<>()))).get(0);
            putSeVazio(hw, "USERID", usuarios.get(0).get("LOGIN"));
        }
        for (Map<String, String> cpu : s.getOrDefault("CPUS", List.of())) {
            putSeVazio(cpu, "TYPE", cpu.get("NAME"));
            putSeVazio(cpu, "CORES", cpu.get("CORE"));
            putSeVazio(cpu, "LOGICAL_CPUS", cpu.get("THREAD"));
        }
        for (Map<String, String> sw : s.getOrDefault("SOFTWARES", List.of())) {
            putSeVazio(sw, "INSTALLDATE", sw.get("INSTALL_DATE"));
            putSeVazio(sw, "ARCHITECTURE", sw.get("ARCH"));
        }
    }

    private static void putSeVazio(Map<String, String> m, String chave, String valor) {
        if (valor != null && !valor.isBlank() && (m.get(chave) == null || m.get(chave).isBlank())) {
            m.put(chave, valor);
        }
    }

    // ======================================================================================= respostas

    /** Elemento com atributos e texto, ex.: {@code <PARAM IPDISC_LAT="100">10.20.0.0</PARAM>}. */
    public record ComAtributos(Map<String, Object> atributos, String texto) {
    }

    /** {@code <REPLY>} do OCS: pares nome/valor na ordem, com listas (ex.: ACCOUNTINFO). */
    public static byte[] replyOcs(Map<String, Object> campos) {
        StringBuilder s = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?>\n<REPLY>\n");
        campos.forEach((k, v) -> escrever(s, k, v, "  "));
        s.append("</REPLY>\n");
        return comprimir(s.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static void escrever(StringBuilder s, String nome, Object valor, String recuo) {
        if (valor instanceof ComAtributos ca) {
            s.append(recuo).append('<').append(nome);
            ca.atributos().forEach((k, v) -> s.append(' ').append(k).append("=\"").append(escaparXml(String.valueOf(v))).append('"'));
            s.append('>').append(ca.texto() == null ? "" : escaparXml(ca.texto())).append("</").append(nome).append(">\n");
        } else if (valor instanceof List<?> l) {
            l.forEach(x -> escrever(s, nome, x, recuo));
        } else if (valor instanceof Map<?, ?> m) {
            s.append(recuo).append('<').append(nome).append(">\n");
            ((Map<String, Object>) m).forEach((k, v) -> escrever(s, k, v, recuo + "  "));
            s.append(recuo).append("</").append(nome).append(">\n");
        } else {
            s.append(recuo).append('<').append(nome).append('>').append(escaparXml(String.valueOf(valor))).append("</").append(nome).append(">\n");
        }
    }

    public static String escaparXml(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

}
