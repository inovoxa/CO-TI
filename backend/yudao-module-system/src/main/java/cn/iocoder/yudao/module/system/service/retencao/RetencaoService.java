package cn.iocoder.yudao.module.system.service.retencao;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Por quanto tempo cada dado fica guardado. O valor salvo na tela vale sobre a variável de ambiente, que continua sendo o
 * padrão de uma instalação nova. Os valores são lidos direto do banco: só as rotinas de limpeza consultam, poucas vezes por dia.
 */
@Service
public class RetencaoService {

    public static final String LOG_OPERACOES = "log-operacoes";
    public static final String LOG_ACESSO = "log-acesso";
    public static final String LOG_ERROS = "log-erros";
    public static final String LOGS_MENSAGENS = "logs-mensagens";
    public static final String LOGS_LIMITE_GB = "logs-limite-gb";
    public static final String MONITOR_BATIDAS = "monitor-batidas";
    public static final String MONITOR_HORAS = "monitor-horas";
    public static final String SINO_LIDAS = "sino-lidas";
    public static final String SINO_NAO_LIDAS = "sino-nao-lidas";
    public static final String EMAIL_ENVIADO = "email-enviado";
    public static final String SLA_AVISO = "sla-aviso";
    public static final String DISCO_LIMITE_GB = "disco-limite-gb";
    public static final String INTEGRACAO_EVENTOS = "integracao-eventos";

    /** @param semLimite se 0 é aceito (guardar para sempre, ou sem limite); o mínimo vale só para os demais valores */
    public record Item(String chave, String rotulo, String ajuda, String unidade, int padrao, int minimo, boolean semLimite, int valor) {
    }

    private record Definicao(String chave, String rotulo, String ajuda, String unidade, int padrao, int minimo, boolean semLimite) {
    }

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${coti.retencao.log-operacoes-dias:730}")
    private int logOperacoes;
    @Value("${coti.retencao.log-acesso-dias:365}")
    private int logAcesso;
    @Value("${coti.retencao.log-erros-dias:90}")
    private int logErros;
    @Value("${coti.logs.retencao-dias:30}")
    private int logsMensagens;
    @Value("${coti.logs.limite-gb:50}")
    private int logsLimiteGb;
    @Value("${coti.monitoramento.retencao-dias:31}")
    private int monitorBatidas;
    @Value("${coti.monitoramento.retencao-horas-dias:400}")
    private int monitorHoras;
    @Value("${coti.retencao.sino-lidas-dias:90}")
    private int sinoLidas;
    @Value("${coti.retencao.sino-nao-lidas-dias:365}")
    private int sinoNaoLidas;
    @Value("${coti.retencao.email-enviado-dias:180}")
    private int emailEnviado;
    @Value("${coti.retencao.sla-aviso-dias:90}")
    private int slaAviso;
    @Value("${coti.retencao.integracao-eventos-dias:90}")
    private int integracaoEventos;
    @Value("${coti.infra.disco-limite-gb:0}")
    private int discoLimiteGb;

    private List<Definicao> definicoes() {
        return List.of(
                new Definicao(LOGS_MENSAGENS, "Logs centralizados", "Mensagens de firewall, servidores e aplicações", "dias", logsMensagens, 1, false),
                new Definicao(LOGS_LIMITE_GB, "Tamanho máximo dos logs", "Acima disso, os dias mais antigos são apagados, preservando 7 dias", "GB", logsLimiteGb, 1, true),
                new Definicao(MONITOR_BATIDAS, "Verificações do monitoramento", "Cada verificação de cada monitor", "dias", monitorBatidas, 1, false),
                new Definicao(MONITOR_HORAS, "Totais por hora do monitoramento", "Base da disponibilidade de 30 dias e de 1 ano", "dias", monitorHoras, 30, false),
                new Definicao(SINO_LIDAS, "Avisos do sino já lidos", "", "dias", sinoLidas, 7, true),
                new Definicao(SINO_NAO_LIDAS, "Avisos do sino não lidos", "", "dias", sinoNaoLidas, 30, true),
                new Definicao(EMAIL_ENVIADO, "E-mails já enviados", "Fila de saída; o andamento continua no chamado", "dias", emailEnviado, 7, true),
                new Definicao(SLA_AVISO, "Controle dos avisos de SLA", "", "dias", slaAviso, 7, true),
                new Definicao(INTEGRACAO_EVENTOS, "Eventos recebidos das integrações", "Podem ter dados pessoais", "dias", integracaoEventos, 7, false),
                new Definicao(LOG_OPERACOES, "Log de operações", "", "dias", logOperacoes, 90, true),
                new Definicao(LOG_ACESSO, "Log de acessos", "Entradas no portal", "dias", logAcesso, 30, true),
                new Definicao(LOG_ERROS, "Erros do sistema", "", "dias", logErros, 7, true),
                new Definicao(DISCO_LIMITE_GB, "Espaço reservado ao CO-TI", "Base da previsão e dos avisos. Use quando o servidor não informa o disco real", "GB", discoLimiteGb, 1, true));
    }

    public List<Item> listar() {
        Map<String, Integer> salvos = salvos();
        List<Item> itens = new ArrayList<>();
        for (Definicao d : definicoes()) {
            itens.add(new Item(d.chave(), d.rotulo(), d.ajuda(), d.unidade(), d.padrao(), d.minimo(), d.semLimite(),
                    salvos.getOrDefault(d.chave(), d.padrao())));
        }
        return itens;
    }

    public int valor(String chave) {
        Definicao d = definicao(chave);
        List<Integer> salvo = jdbcTemplate.queryForList("SELECT valor FROM infra_retencao WHERE chave = ?", Integer.class, chave);
        return salvo.isEmpty() ? d.padrao() : salvo.get(0);
    }

    /**
     * @return o valor anterior
     * @throws IllegalArgumentException se a chave não existir ou o valor estiver abaixo do mínimo
     */
    public int salvar(String chave, int valor, String updater) {
        Definicao d = definicao(chave);
        boolean semLimite = valor == 0 && d.semLimite();
        if (!semLimite && valor < d.minimo()) {
            throw new IllegalArgumentException(d.rotulo() + ": o mínimo é " + d.minimo() + " " + d.unidade());
        }
        int anterior = valor(chave);
        jdbcTemplate.update("INSERT INTO infra_retencao (chave, valor, updater, update_time) VALUES (?, ?, ?, now()) "
                + "ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor, updater = EXCLUDED.updater, update_time = now()",
                chave, valor, updater);
        return anterior;
    }

    private Definicao definicao(String chave) {
        return definicoes().stream().filter(d -> d.chave().equals(chave)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item de retenção desconhecido: " + chave));
    }

    private Map<String, Integer> salvos() {
        Map<String, Integer> m = new HashMap<>();
        jdbcTemplate.query("SELECT chave, valor FROM infra_retencao", rs -> {
            m.put(rs.getString("chave"), rs.getInt("valor"));
        });
        return m;
    }

}
