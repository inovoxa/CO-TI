package cn.iocoder.yudao.module.inventario.dominio;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Do inventário do agente para o ativo do CMDB, sem dependência de servidor: resumo (tipo, sistema, CPU, memória,
 * disco, tipo de disco, rede, usuário), componentes com a chave que identifica cada peça e os softwares instalados.
 */
public final class Normalizacao {

    private Normalizacao() {
    }

    public record Resumo(String tipo, String nome, String fabricante, String modelo, String numeroSerie, String patrimonio, String uuid,
                         String soNome, String soVersao, String soArquitetura, boolean soSemSuporte, String cpu, Integer cpuNucleos,
                         Integer memoriaMb, Long discoTotalMb, Long discoLivreMb, String ip, String mac, String dominio,
                         String usuarioLogin, String antivirus, String discoTipo) {
    }

    public record Componente(String tipo, String chave, String descricao, Map<String, String> dados) {
    }

    public record Software(String nome, String editora, String versao, LocalDate instaladoEm, String arquitetura, String pasta) {
    }

    public record Resultado(Resumo resumo, List<Componente> componentes, List<Software> softwares) {
    }

    /** Seção do agente → tipo de componente no CO-TI e os campos que identificam a peça. */
    private static final Map<String, String[]> SECOES = new LinkedHashMap<>();

    static {
        SECOES.put("CPUS", new String[]{"processador", "SERIALNUMBER", "TYPE", "SOCKET"});
        SECOES.put("MEMORIES", new String[]{"memoria", "SERIALNUMBER", "NUMSLOTS", "CAPACITY"});
        SECOES.put("STORAGES", new String[]{"disco", "SERIALNUMBER", "MODEL", "NAME", "DISKSIZE"});
        SECOES.put("DRIVES", new String[]{"volume", "VOLUMN", "LETTER", "TYPE"});
        SECOES.put("NETWORKS", new String[]{"rede", "MACADDR", "DESCRIPTION"});
        SECOES.put("VIDEOS", new String[]{"video", "NAME", "CHIPSET"});
        SECOES.put("MONITORS", new String[]{"monitor", "SERIAL", "CAPTION", "DESCRIPTION"});
        SECOES.put("PRINTERS", new String[]{"impressora", "NAME", "PORT"});
        SECOES.put("CONTROLLERS", new String[]{"controlador", "NAME", "PCIID", "TYPE"});
        SECOES.put("USBDEVICES", new String[]{"usb", "SERIAL", "DESCRIPTION"});
        SECOES.put("BATTERIES", new String[]{"bateria", "SERIAL", "SERIALNUMBER", "NAME"});
        SECOES.put("SOUNDS", new String[]{"som", "NAME", "MANUFACTURER"});
        SECOES.put("INPUTS", new String[]{"entrada", "DESCRIPTION", "CAPTION"});
        SECOES.put("SLOTS", new String[]{"slot", "NAME", "DESIGNATION"});
    }

    /** Valores de fábrica que não identificam nada */
    private static final Set<String> GENERICOS = Set.of("", "0", "none", "null", "n/a", "na", "default string", "to be filled by o.e.m.",
            "system serial number", "chassis serial number", "not specified", "not applicable", "123456789", "0123456789",
            "00000000", "ffffffff", "03000200-0400-0500-0006-000700080009", "00000000-0000-0000-0000-000000000000",
            "ffffffff-ffff-ffff-ffff-ffffffffffff", "o.e.m.", "unknown", "desconhecido");

    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");

    public static Resultado normalizar(Protocolo.Pedido p, LocalDate hoje) {
        Map<String, String> hw = p.unico("HARDWARE");
        Map<String, String> bios = p.unico("BIOS");
        List<Componente> componentes = componentes(p);
        String soNome = vazio(hw.get("OSNAME"));
        String nome = vazio(hw.get("NAME"));
        if (nome == null) {
            nome = p.deviceId() == null ? "sem-nome" : p.deviceId().replaceFirst("-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}$", "");
        }
        String cpu = vazio(hw.get("PROCESSORT"));
        Integer nucleos = inteiro(hw.get("PROCESSORN"));
        List<Map<String, String>> cpus = p.lista("CPUS");
        if (!cpus.isEmpty()) {
            cpu = Optional.ofNullable(vazio(cpus.get(0).get("TYPE"))).orElse(cpu);
            int soma = cpus.stream().mapToInt(c -> Optional.ofNullable(inteiro(c.get("CORES"))).orElse(0)).sum();
            if (soma > 0) {
                nucleos = soma;
            }
        }
        long total = 0;
        long livre = 0;
        for (Map<String, String> d : p.lista("DRIVES")) {
            if (discoLocal(d)) {
                total += Optional.ofNullable(longo(d.get("TOTAL"))).orElse(0L);
                livre += Optional.ofNullable(longo(d.get("FREE"))).orElse(0L);
            }
        }
        String ip = vazio(hw.get("IPADDR"));
        String mac = null;
        for (Map<String, String> n : p.lista("NETWORKS")) {
            String m = vazio(n.get("MACADDR"));
            String i = vazio(n.get("IPADDRESS"));
            boolean virtual = "1".equals(n.get("VIRTUALDEV")) || Termos.contem(n.get("DESCRIPTION"), "virtual", "loopback", "vpn", "hyper-v");
            if (m != null && !virtual && mac == null) {
                mac = m.toUpperCase(Locale.ROOT);
                if (ip == null && i != null) {
                    ip = i;
                }
            }
        }
        String login = usuario(Optional.ofNullable(vazio(hw.get("LASTLOGGEDUSER"))).orElse(vazio(hw.get("USERID"))));
        Resumo r = new Resumo(tipo(bios.get("TYPE"), soNome, hw.get("VMSYSTEM"), bios.get("SMANUFACTURER"), bios.get("SMODEL")),
                cortar(nome, 255), cortar(semGenerico(bios.get("SMANUFACTURER")), 255), cortar(semGenerico(bios.get("SMODEL")), 255),
                cortar(semGenerico(bios.get("SSN")), 100), cortar(semGenerico(bios.get("ASSETTAG")), 100), cortar(semGenerico(hw.get("UUID")), 64),
                cortar(soNome, 255), cortar(vazio(hw.get("OSVERSION")), 100), cortar(arquitetura(hw.get("ARCH"), soNome), 30),
                semSuporte(soNome, hw.get("OSVERSION"), hoje), cortar(cpu, 255), nucleos, inteiro(hw.get("MEMORY")),
                total > 0 ? total : null, total > 0 ? livre : null, cortar(ip, 64), mac,
                cortar(Optional.ofNullable(vazio(hw.get("WORKGROUP"))).orElse(vazio(hw.get("USERDOMAIN"))), 255), cortar(login, 255),
                antivirus(p, soNome), discoTipo(p.lista("STORAGES")));
        return new Resultado(r, componentes, softwares(p));
    }

    // ================================================================================== componentes

    static List<Componente> componentes(Protocolo.Pedido p) {
        List<Componente> out = new ArrayList<>();
        for (Map.Entry<String, String[]> s : SECOES.entrySet()) {
            String tipo = s.getValue()[0];
            Set<String> vistas = new HashSet<>();
            for (Map<String, String> item : p.lista(s.getKey())) {
                StringBuilder chave = new StringBuilder();
                for (int i = 1; i < s.getValue().length; i++) {
                    String v = semGenerico(item.get(s.getValue()[i]));
                    if (v != null) {
                        chave.append(chave.isEmpty() ? "" : "|").append(v);
                    }
                }
                String k = chave.isEmpty() ? String.valueOf(item.hashCode()) : chave.toString();
                // duas peças iguais (dois pentes sem série): a segunda ganha um número
                String unica = k;
                for (int n = 2; !vistas.add(unica); n++) {
                    unica = k + "#" + n;
                }
                out.add(new Componente(tipo, cortar(unica, 500), cortar(descricao(tipo, item), 500), item));
            }
        }
        return out;
    }

    private static String descricao(String tipo, Map<String, String> d) {
        return switch (tipo) {
            case "processador" -> juntar(d.get("TYPE"), d.get("CORES") == null ? null : d.get("CORES") + " núcleos");
            case "memoria" -> juntar(d.get("CAPACITY") == null ? null : d.get("CAPACITY") + " MB", d.get("TYPE"), d.get("SPEED") == null ? null : d.get("SPEED") + " MHz");
            case "disco" -> juntar(d.get("MODEL"), d.get("NAME"), d.get("DISKSIZE") == null ? null : mbLegivel(longo(d.get("DISKSIZE"))));
            case "volume" -> juntar(Optional.ofNullable(vazio(d.get("LETTER"))).orElse(d.get("VOLUMN")), d.get("FILESYSTEM"),
                    d.get("TOTAL") == null ? null : mbLegivel(longo(d.get("FREE"))) + " livres de " + mbLegivel(longo(d.get("TOTAL"))));
            case "rede" -> juntar(d.get("DESCRIPTION"), d.get("MACADDR"), d.get("IPADDRESS"));
            case "monitor" -> juntar(d.get("MANUFACTURER"), d.get("CAPTION"), d.get("SERIAL"));
            default -> juntar(Optional.ofNullable(vazio(d.get("NAME"))).orElse(d.get("DESCRIPTION")), d.get("MANUFACTURER"));
        };
    }

    // ================================================================================== softwares

    static List<Software> softwares(Protocolo.Pedido p) {
        Map<String, Software> unicos = new LinkedHashMap<>();
        for (Map<String, String> s : p.lista("SOFTWARES")) {
            String nome = vazio(s.get("NAME"));
            if (nome == null) {
                continue;
            }
            String arq = vazio(s.get("ARCHITECTURE"));
            if (arq == null && s.get("BITSWIDTH") != null) {
                arq = s.get("BITSWIDTH") + " bits";
            }
            Software sw = new Software(cortar(nome, 255), cortar(vazio(s.get("PUBLISHER")), 255), cortar(Optional.ofNullable(vazio(s.get("VERSION"))).orElse(""), 100),
                    data(s.get("INSTALLDATE")), cortar(arq, 20), cortar(vazio(s.get("FOLDER")), 500));
            unicos.putIfAbsent(chaveSoftware(sw.nome(), sw.editora()) + "|" + sw.versao(), sw);
        }
        return new ArrayList<>(unicos.values());
    }

    /** Nome e editora normalizados: o mesmo software vindo de máquinas diferentes vira um item do catálogo. */
    public static String chaveSoftware(String nome, String editora) {
        return cortar(simples(nome) + "|" + simples(editora), 520);
    }

    // ================================================================================== regras

    /** Tipo do equipamento pelo chassi da BIOS, pelo sistema e pela virtualização. */
    static String tipo(String chassi, String so, String vm, String fabricante, String modelo) {
        String v = simples(vm);
        String fm = simples(fabricante) + " " + simples(modelo);
        if ((!v.isEmpty() && !v.equals("physical") && !v.equals("fisico")) || Termos.contem(fm, "vmware", "virtualbox", "kvm", "qemu", "xen", "hyper-v", "virtual machine")) {
            return "virtual";
        }
        String c = simples(chassi);
        if (Termos.contem(c, "notebook", "laptop", "portable", "sub notebook", "tablet", "convertible", "detachable", "hand held")) {
            return "notebook";
        }
        if (Termos.contem(simples(so), "server") || Termos.contem(c, "rack", "server", "blade")) {
            return "servidor";
        }
        return "desktop";
    }

    /** Sistemas fora do suporte do fabricante (sem atualização de segurança) na data informada. */
    static boolean semSuporte(String so, String versao, LocalDate hoje) {
        String s = simples(so);
        if (s.isEmpty()) {
            return false;
        }
        if (s.contains("windows")) {
            if (Termos.contem(s, "xp", "vista", "windows 7", "windows 8", "2000", "2003", "2008")) {
                return true;
            }
            if (s.contains("2012")) {
                return hoje.isAfter(LocalDate.of(2023, 10, 10));
            }
            if (s.contains("2016")) {
                return hoje.isAfter(LocalDate.of(2027, 1, 12));
            }
            if (s.contains("windows 10") && !s.contains("ltsc") && !s.contains("ltsb")) {
                return hoje.isAfter(LocalDate.of(2025, 10, 14));
            }
        }
        if (Termos.contem(s, "centos linux 7", "centos linux release 7", "ubuntu 16.04", "ubuntu 18.04", "debian gnu/linux 9", "debian gnu/linux 10")) {
            return true;
        }
        return false;
    }

    /** Antivírus pela seção ANTIVIRUS (GLPI Agent, plugins do OCS) ou, sem ela, pelo Windows (Defender embutido) e softwares. */
    static String antivirus(Protocolo.Pedido p, String so) {
        List<Map<String, String>> av = p.lista("ANTIVIRUS");
        if (!av.isEmpty()) {
            boolean ativo = av.stream().anyMatch(a -> verdadeiro(a.get("ENABLED")));
            boolean atualizado = av.stream().anyMatch(a -> verdadeiro(a.get("ENABLED")) && !"0".equals(a.get("UPTODATE")) && !"false".equalsIgnoreCase(a.get("UPTODATE")));
            return !ativo ? "ausente" : atualizado ? "ativo" : "desatualizado";
        }
        boolean conhecido = p.lista("SOFTWARES").stream().anyMatch(s -> Termos.contem(simples(s.get("NAME")), "defender", "kaspersky",
                "eset", "sophos", "avast", "avg ", "bitdefender", "mcafee", "symantec", "trend micro", "crowdstrike", "sentinelone", "norton", "trellix"));
        String s = simples(so);
        if (conhecido || Termos.contem(s, "windows 10", "windows 11", "windows server 2016", "windows server 2019", "windows server 2022", "windows server 2025")) {
            return "ativo";
        }
        return s.contains("windows") ? "ausente" : "desconhecido";
    }

    /** "PREFEITURA\\maria.silva" e "maria.silva@prefeitura" → "maria.silva". */
    public static String usuario(String bruto) {
        String u = vazio(bruto);
        if (u == null) {
            return null;
        }
        int barra = u.lastIndexOf('\\');
        if (barra >= 0) {
            u = u.substring(barra + 1);
        }
        int arroba = u.indexOf('@');
        if (arroba > 0) {
            u = u.substring(0, arroba);
        }
        u = u.strip();
        return u.isEmpty() || simples(u).equals("system") ? null : u;
    }

    /** Modelos de SSD que não trazem "SSD" no nome (OEM Samsung, Kingston A400/UV400/NV, WD Green/Blue SSD, Micron, Kioxia, SK hynix). */
    private static final Pattern MODELO_SSD = Pattern.compile(
            "\\b(mz[0-9a-z]{5,}|sa400\\w*|suv[0-9]\\w*|sv300\\w*|skc[0-9]\\w*|snv[0-9s]\\w*|sedc\\w*|wds[0-9]\\w*|mtfd\\w*|thnsn\\w*|kbg[0-9]\\w*|hf[sm][0-9]\\w*|pc sn[0-9]\\w*|ct[0-9]+(mx|bx|p[0-9])\\w*)");
    /** Modelos de HD (disco giratório) dos fabricantes comuns. */
    private static final Pattern MODELO_HD = Pattern.compile(
            "\\b(wd[0-9]{2,}[a-z]\\w*|st[0-9]{3,}[a-z]{2}\\w*|hts[0-9]\\w*|hgst\\w*|hitachi\\w*|mq0[0-9]\\w*|dt01\\w*|hdw[a-z]\\w*|hd[0-9]{3}[a-z]{2}\\w*|hn-m\\w*)");

    /**
     * Tipo de disco da máquina: {@code ssd}, {@code hdd}, {@code misto} (os dois) ou {@code desconhecido}; null sem
     * discos informados. O agente OCS do Windows informa "Fixed hard disk media" para os dois, então a classificação vai
     * pelo modelo e pela interface; disco que não se identifica não muda o resultado dos que se identificam.
     */
    public static String discoTipo(List<Map<String, String>> discos) {
        boolean ssd = false;
        boolean hdd = false;
        boolean algum = false;
        for (Map<String, String> d : discos) {
            String c = classeDisco(d);
            if (c == null) {
                continue;
            }
            algum = true;
            ssd |= c.equals("ssd");
            hdd |= c.equals("hdd");
        }
        if (!algum) {
            return null;
        }
        return ssd && hdd ? "misto" : ssd ? "ssd" : hdd ? "hdd" : "desconhecido";
    }

    /** ssd | hdd | desconhecido; null para o que não é disco interno (pen drive, leitor de cartão, CD). */
    static String classeDisco(Map<String, String> d) {
        String texto = simples(String.join(" ", Objects.toString(d.get("MODEL"), ""), Objects.toString(d.get("NAME"), ""),
                Objects.toString(d.get("DESCRIPTION"), ""), Objects.toString(d.get("TYPE"), ""), Objects.toString(d.get("INTERFACE"), ""),
                Objects.toString(d.get("MANUFACTURER"), "")));
        if (Termos.contem(texto, "usb", "removable", "removivel", "cd-rom", "cdrom", "dvd", "card reader", "sd card", "floppy", "flash drive")) {
            return null;
        }
        if (Termos.contem(texto, "ssd", "nvme", "solid state", "m.2") || MODELO_SSD.matcher(texto).find()) {
            return "ssd";
        }
        if (Termos.contem(texto, "hdd", "rpm") || MODELO_HD.matcher(texto).find()) {
            return "hdd";
        }
        return "desconhecido";
    }

    private static boolean discoLocal(Map<String, String> d) {
        String t = simples(d.get("TYPE"));
        String fs = simples(d.get("FILESYSTEM"));
        if (Termos.contem(t, "network", "rede", "cd-rom", "removable", "removivel", "ram disk")) {
            return false;
        }
        return Termos.contem(t, "hard drive", "fixed", "local", "disco") || Termos.contem(fs, "ntfs", "ext4", "xfs", "btrfs", "apfs", "refs");
    }

    private static String arquitetura(String arch, String so) {
        String a = vazio(arch);
        if (a != null) {
            return a;
        }
        String s = simples(so);
        return s.contains("64") ? "64 bits" : null;
    }

    public static boolean generico(String v) {
        return v == null || GENERICOS.contains(simples(v)) || v.strip().matches("^[0\\-. ]+$");
    }

    private static String semGenerico(String v) {
        return generico(v) ? null : v.strip();
    }

    private static boolean verdadeiro(String v) {
        return v != null && (v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes"));
    }

    static LocalDate data(String v) {
        String s = vazio(v);
        if (s == null) {
            return null;
        }
        for (String f : new String[]{"yyyy/MM/dd", "yyyy-MM-dd", "dd/MM/yyyy", "yyyyMMdd"}) {
            try {
                return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s, DateTimeFormatter.ofPattern(f));
            } catch (Exception ignorado) {
                // próximo formato
            }
        }
        return null;
    }

    static Integer inteiro(String v) {
        Long l = longo(v);
        return l == null || l > Integer.MAX_VALUE ? null : l.intValue();
    }

    static Long longo(String v) {
        if (v == null) {
            return null;
        }
        Matcher m = NUMERO.matcher(v);
        if (!m.find()) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(m.group(1).replace(',', '.')));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String mbLegivel(Long mb) {
        if (mb == null) {
            return "?";
        }
        return mb >= 1024 ? String.format(Locale.ROOT, "%.0f GB", mb / 1024.0) : mb + " MB";
    }

    private static String juntar(String... partes) {
        return String.join(" · ", Arrays.stream(partes).map(Normalizacao::vazio).filter(Objects::nonNull).toList());
    }

    static String vazio(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    static String cortar(String v, int n) {
        return v == null || v.length() <= n ? v : v.substring(0, n);
    }

    public static String simples(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).strip();
    }

    /** Apoio: contém algum dos termos. */
    static final class Termos {
        private Termos() {
        }

        static boolean contem(String texto, String... termos) {
            String t = simples(texto);
            for (String x : termos) {
                if (t.contains(x)) {
                    return true;
                }
            }
            return false;
        }
    }

}
