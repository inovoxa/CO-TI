package cn.iocoder.yudao.module.inventario.dominio;

import java.util.*;

/**
 * O que uma consulta SNMP trouxe de um equipamento, e como isso vira ativo do CMDB. Sem dependência de rede: o
 * cliente ({@code SnmpCliente}) preenche os valores; aqui ficam as regras (tipo, fabricante, suprimentos), testáveis.
 */
public final class SnmpLeitura {

    // MIB-II system
    public static final String SYS_DESCR = "1.3.6.1.2.1.1.1.0";
    public static final String SYS_OBJECT_ID = "1.3.6.1.2.1.1.2.0";
    public static final String SYS_UPTIME = "1.3.6.1.2.1.1.3.0";
    public static final String SYS_CONTACT = "1.3.6.1.2.1.1.4.0";
    public static final String SYS_NAME = "1.3.6.1.2.1.1.5.0";
    public static final String SYS_LOCATION = "1.3.6.1.2.1.1.6.0";
    public static final String IF_NUMBER = "1.3.6.1.2.1.2.1.0";
    // ENTITY-MIB (chassi = índice 1 na maioria)
    public static final String ENT_SERIAL = "1.3.6.1.2.1.47.1.1.1.1.11.1";
    public static final String ENT_MFG = "1.3.6.1.2.1.47.1.1.1.1.12.1";
    public static final String ENT_MODELO = "1.3.6.1.2.1.47.1.1.1.1.13.1";
    // HOST-RESOURCES (descrição do primeiro dispositivo: o modelo da impressora)
    public static final String HR_DEVICE_DESCR = "1.3.6.1.2.1.25.3.2.1.3.1";
    // Printer-MIB
    public static final String PRT_SERIAL = "1.3.6.1.2.1.43.5.1.1.17.1";
    public static final String PRT_PAGINAS = "1.3.6.1.2.1.43.10.2.1.4.1.1";
    public static final String PRT_SUPRIMENTO_DESCR = "1.3.6.1.2.1.43.11.1.1.6.1";
    public static final String PRT_SUPRIMENTO_MAX = "1.3.6.1.2.1.43.11.1.1.8.1";
    public static final String PRT_SUPRIMENTO_NIVEL = "1.3.6.1.2.1.43.11.1.1.9.1";
    // IF-MIB
    public static final String IF_DESCR = "1.3.6.1.2.1.2.2.1.2";
    public static final String IF_MAC = "1.3.6.1.2.1.2.2.1.6";
    public static final String IF_STATUS = "1.3.6.1.2.1.2.2.1.8";
    public static final String IF_VELOCIDADE = "1.3.6.1.2.1.2.2.1.5";

    public static final List<String> CONSULTA_UNICA = List.of(SYS_DESCR, SYS_OBJECT_ID, SYS_UPTIME, SYS_CONTACT, SYS_NAME, SYS_LOCATION,
            IF_NUMBER, ENT_SERIAL, ENT_MFG, ENT_MODELO, HR_DEVICE_DESCR, PRT_SERIAL, PRT_PAGINAS);
    public static final List<String> TABELAS = List.of(PRT_SUPRIMENTO_DESCR, PRT_SUPRIMENTO_MAX, PRT_SUPRIMENTO_NIVEL,
            IF_DESCR, IF_MAC, IF_STATUS, IF_VELOCIDADE);

    /** Número de empresa IANA (1.3.6.1.4.1.N) → fabricante */
    private static final Map<Integer, String> EMPRESAS = Map.ofEntries(
            Map.entry(9, "Cisco"), Map.entry(11, "HP"), Map.entry(43, "3Com"), Map.entry(171, "D-Link"), Map.entry(253, "Xerox"),
            Map.entry(311, "Microsoft"), Map.entry(367, "Ricoh"), Map.entry(641, "Lexmark"), Map.entry(674, "Dell"),
            Map.entry(1248, "Epson"), Map.entry(1347, "Kyocera"), Map.entry(1588, "Brocade"), Map.entry(1602, "Canon"),
            Map.entry(1916, "Extreme Networks"), Map.entry(2011, "Huawei"), Map.entry(2435, "Brother"), Map.entry(2636, "Juniper"),
            Map.entry(3375, "F5"), Map.entry(4526, "Netgear"), Map.entry(6876, "VMware"), Map.entry(8072, "Net-SNMP"),
            Map.entry(11863, "TP-Link"), Map.entry(12356, "Fortinet"), Map.entry(14988, "MikroTik"), Map.entry(18334, "Konica Minolta"),
            Map.entry(25506, "H3C"), Map.entry(25461, "Palo Alto"), Map.entry(30065, "Arista"), Map.entry(41112, "Ubiquiti"),
            Map.entry(47196, "Intelbras"), Map.entry(10418, "Avocent"), Map.entry(318, "APC"), Map.entry(534, "Eaton"),
            Map.entry(2620, "Check Point"), Map.entry(8741, "SonicWall"), Map.entry(6889, "Avaya"), Map.entry(5596, "Tandberg"),
            Map.entry(1991, "Brocade (Foundry)"), Map.entry(45, "Nortel"), Map.entry(3076, "Alteon"), Map.entry(236, "Samsung"));

    private SnmpLeitura() {
    }

    /** Valores lidos: OID → texto. Tabelas: OID da coluna → (índice → valor). */
    public record Dados(Map<String, String> valores, Map<String, Map<String, String>> tabelas) {

        public String v(String oid) {
            String x = valores.get(oid);
            return x == null || x.isBlank() || x.equals("noSuchObject") || x.equals("noSuchInstance") ? null : x.strip();
        }

        public Map<String, String> tabela(String oid) {
            return tabelas.getOrDefault(oid, Map.of());
        }
    }

    /** Suprimento de impressora (toner, cilindro, kit), com o nível em % quando o equipamento informa. */
    public record Suprimento(String indice, String descricao, Integer nivel, Integer maximo, Integer percentual) {
    }

    public record Interface(String indice, String descricao, String mac, String estado, Long velocidade) {
    }

    /** Resultado pronto para gravar: o resumo (como o dos agentes), os suprimentos e as interfaces. */
    public record Resultado(Normalizacao.Resumo resumo, Long paginas, List<Suprimento> suprimentos, List<Interface> interfaces,
                            String sysObjectId, String localizacao, String contato, String descricao) {
    }

    public static Resultado interpretar(String ip, Dados d) {
        List<Suprimento> suprimentos = suprimentos(d);
        List<Interface> interfaces = interfaces(d);
        Long paginas = numero(d.v(PRT_PAGINAS));
        boolean impressora = paginas != null || !suprimentos.isEmpty() || d.v(PRT_SERIAL) != null;
        String descr = d.v(SYS_DESCR);
        String fabricante = Optional.ofNullable(d.v(ENT_MFG)).orElse(fabricante(d.v(SYS_OBJECT_ID)));
        String modelo = Optional.ofNullable(d.v(ENT_MODELO)).orElse(impressora ? d.v(HR_DEVICE_DESCR) : null);
        if (modelo == null && descr != null) {
            modelo = descr.length() > 120 ? descr.substring(0, 120) : descr;
        }
        String serie = Optional.ofNullable(d.v(PRT_SERIAL)).orElse(d.v(ENT_SERIAL));
        if (serie != null && Normalizacao.generico(serie)) {
            serie = null;
        }
        String tipo = impressora ? "impressora" : tipoRede(d, interfaces, descr);
        String mac = interfaces.stream().map(Interface::mac).filter(Objects::nonNull).findFirst().orElse(null);
        String nome = Optional.ofNullable(d.v(SYS_NAME)).orElse(ip);
        Normalizacao.Resumo r = new Normalizacao.Resumo(tipo, nome, fabricante, modelo, serie, null, null, null, null, null, false,
                null, null, null, null, null, ip, mac, null, null, "desconhecido", null);
        return new Resultado(r, paginas, suprimentos, interfaces, d.v(SYS_OBJECT_ID), d.v(SYS_LOCATION), d.v(SYS_CONTACT), descr);
    }

    /** Switch/roteador/AP têm várias interfaces; o resto (nobreak, câmera, telefone) fica "outro". */
    static String tipoRede(Dados d, List<Interface> interfaces, String descr) {
        String t = descr == null ? "" : descr.toLowerCase(Locale.ROOT);
        if (Normalizacao.Termos.contem(t, "linux", "windows") && !Normalizacao.Termos.contem(t, "switch", "router", "routeros", "firewall")) {
            return "servidor";
        }
        Long n = numero(d.v(IF_NUMBER));
        int qtd = Math.max(interfaces.size(), n == null ? 0 : n.intValue());
        if (qtd >= 4 || Normalizacao.Termos.contem(t, "switch", "router", "routeros", "firewall", "access point", "fortigate", "ios ")) {
            return "rede";
        }
        if (Normalizacao.Termos.contem(t, "phone", "sip", "voip", "telefone")) {
            return "telefone";
        }
        return "outro";
    }

    static String fabricante(String sysObjectId) {
        if (sysObjectId == null) {
            return null;
        }
        String s = sysObjectId.startsWith(".") ? sysObjectId.substring(1) : sysObjectId;
        if (!s.startsWith("1.3.6.1.4.1.")) {
            return null;
        }
        String[] p = s.substring("1.3.6.1.4.1.".length()).split("\\.");
        try {
            return EMPRESAS.get(Integer.parseInt(p[0]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<Suprimento> suprimentos(Dados d) {
        Map<String, String> descr = d.tabela(PRT_SUPRIMENTO_DESCR), max = d.tabela(PRT_SUPRIMENTO_MAX), nivel = d.tabela(PRT_SUPRIMENTO_NIVEL);
        List<Suprimento> l = new ArrayList<>();
        for (Map.Entry<String, String> e : descr.entrySet()) {
            Integer mx = inteiro(max.get(e.getKey())), nv = inteiro(nivel.get(e.getKey()));
            // Printer-MIB: -1 = outro, -2 = desconhecido, -3 = "tem algum"; só calcula % com máximo e nível reais
            Integer pct = mx != null && mx > 0 && nv != null && nv >= 0 ? (int) Math.round(100.0 * Math.min(nv, mx) / mx) : null;
            l.add(new Suprimento(e.getKey(), e.getValue().strip(), nv, mx, pct));
        }
        return l;
    }

    static List<Interface> interfaces(Dados d) {
        Map<String, String> descr = d.tabela(IF_DESCR), mac = d.tabela(IF_MAC), st = d.tabela(IF_STATUS), vel = d.tabela(IF_VELOCIDADE);
        List<Interface> l = new ArrayList<>();
        for (Map.Entry<String, String> e : descr.entrySet()) {
            String m = mac(mac.get(e.getKey()));
            String estado = switch (Objects.toString(st.get(e.getKey()), "")) {
                case "1" -> "up";
                case "2" -> "down";
                case "" -> null;
                default -> "outro";
            };
            l.add(new Interface(e.getKey(), e.getValue().strip(), m, estado, numero(vel.get(e.getKey()))));
        }
        return l;
    }

    /** MAC como o SNMP4J entrega (aa:bb:..) ou em hexadecimal corrido; zerado = sem MAC. */
    static String mac(String bruto) {
        if (bruto == null) {
            return null;
        }
        String h = bruto.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (h.length() != 12 || h.equals("000000000000")) {
            return null;
        }
        return String.join(":", h.substring(0, 2), h.substring(2, 4), h.substring(4, 6), h.substring(6, 8), h.substring(8, 10), h.substring(10));
    }

    static Long numero(String s) {
        try {
            return s == null ? null : Long.parseLong(s.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Integer inteiro(String s) {
        Long n = numero(s);
        return n == null ? null : n.intValue();
    }

}
