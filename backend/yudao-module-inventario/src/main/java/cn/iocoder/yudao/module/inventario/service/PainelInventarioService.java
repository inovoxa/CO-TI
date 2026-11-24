package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AlertaAtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AlertaRegraDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.RedeDispositivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAlertaAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAlertaRegraMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbRedeDispositivoMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Painel do inventário: totais e distribuições do parque. As contagens de ativos passam pelo escopo de unidade do
 * perfil (mapper com regra de dados); a série de inventários por dia vem do registro de envios dos agentes.
 */
@Service
public class PainelInventarioService {

    private static final String COMPUTADORES = " AND a.tipo IN ('desktop', 'notebook', 'servidor', 'virtual')";
    private static final String COM_AGENTE = COMPUTADORES + " AND a.origem IN ('ocs', 'glpi_agent')";

    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbAlertaAtivoMapper cmdbAlertaAtivoMapper;
    @Resource
    private CmdbAlertaRegraMapper cmdbAlertaRegraMapper;
    @Resource
    private CmdbRedeDispositivoMapper cmdbRedeDispositivoMapper;
    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private JdbcTemplate jdbcTemplate;

    public record Fatia(String valor, long total) {
    }

    @Data
    public static class Painel {
        private Map<String, Long> totais = new LinkedHashMap<>();
        private Map<String, List<Fatia>> series = new LinkedHashMap<>();
        private int semContatoDias;
    }

    public Painel painel() {
        Painel p = new Painel();
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limiteContato = agora.minusDays(inventarioProperties.getSemContatoDias());
        p.setSemContatoDias(inventarioProperties.getSemContatoDias());

        Map<String, Long> t = p.getTotais();
        t.put("ativos", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")));
        t.put("computadores", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .in(AtivoDO::getTipo, LimpezaInventarioService.COMPUTADORES)));
        t.put("impressoras", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .eq(AtivoDO::getTipo, "impressora")));
        t.put("rede", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .eq(AtivoDO::getTipo, "rede")));
        t.put("contato24h", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .ge(AtivoDO::getUltimoContato, agora.minusHours(24))));
        t.put("semContato", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .ne(AtivoDO::getOrigem, "manual").lt(AtivoDO::getUltimoContato, limiteContato)));
        t.put("semSuporte", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .eq(AtivoDO::getSoSemSuporte, true)));
        t.put("antivirus", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .ne(AtivoDO::getOrigem, "manual").in(AtivoDO::getAntivirus, "desatualizado", "ausente")));
        t.put("ssd", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado")
                .in(AtivoDO::getDiscoTipo, "ssd", "misto")));
        t.put("baixados", cmdbAtivoMapper.selectCount(AtivoDO::getSituacao, "baixado"));
        t.put("softwares", cmdbAtivoMapper.contarSoftwares(null));
        t.put("redeDesconhecidos", cmdbRedeDispositivoMapper.selectCount(new LambdaQueryWrapperX<RedeDispositivoDO>()
                .isNull(RedeDispositivoDO::getAtivoId).eq(RedeDispositivoDO::getIgnorado, false)));

        Map<String, List<Fatia>> s = p.getSeries();
        s.put("tipo", fatias(cmdbAtivoMapper.contarPor("a.tipo", "", 20)));
        s.put("situacao", fatias(cmdbAtivoMapper.porSituacao()));
        s.put("origem", fatias(cmdbAtivoMapper.contarPor("a.origem", "", 10)));
        s.put("sistema", fatias(cmdbAtivoMapper.contarPor("coalesce(a.so_nome, 'Não informado')", COMPUTADORES, 12)));
        s.put("semSuporte", fatias(cmdbAtivoMapper.contarPor("coalesce(a.so_nome, 'Não informado')", COMPUTADORES
                + " AND a.so_sem_suporte = true", 10)));
        s.put("unidade", fatias(cmdbAtivoMapper.contarPor(
                "coalesce((SELECT d.name FROM system_dept d WHERE d.id = a.dept_id), 'Sem unidade')", "", 15)));
        s.put("fabricante", fatias(cmdbAtivoMapper.contarPor("coalesce(a.fabricante, 'Não informado')", COMPUTADORES, 10)));
        s.put("modelo", fatias(cmdbAtivoMapper.contarPor("coalesce(a.modelo, 'Não informado')", COMPUTADORES, 10)));
        s.put("memoria", ordenar(fatias(cmdbAtivoMapper.contarPor("""
                CASE WHEN a.memoria_mb IS NULL THEN 'Não informado' WHEN a.memoria_mb < 3584 THEN 'Até 3 GB'
                WHEN a.memoria_mb < 7680 THEN '4 a 7 GB' WHEN a.memoria_mb < 15360 THEN '8 a 15 GB'
                WHEN a.memoria_mb < 31744 THEN '16 a 31 GB' ELSE '32 GB ou mais' END""", COMPUTADORES, 10)),
                List.of("Até 3 GB", "4 a 7 GB", "8 a 15 GB", "16 a 31 GB", "32 GB ou mais", "Não informado")));
        s.put("disco", fatias(cmdbAtivoMapper.contarPor("coalesce(a.disco_tipo, 'sem_info')", COMPUTADORES, 10)));
        s.put("antivirus", fatias(cmdbAtivoMapper.contarPor("a.antivirus", COM_AGENTE, 10)));
        s.put("arquitetura", fatias(cmdbAtivoMapper.contarPor("coalesce(a.so_arquitetura, 'Não informado')", COMPUTADORES, 5)));
        s.put("contato", ordenar(fatias(cmdbAtivoMapper.contarPor("""
                CASE WHEN a.ultimo_contato IS NULL THEN 'Nunca' WHEN a.ultimo_contato >= now() - interval '1 day' THEN 'Últimas 24 h'
                WHEN a.ultimo_contato >= now() - interval '7 days' THEN '1 a 7 dias'
                WHEN a.ultimo_contato >= now() - interval '30 days' THEN '8 a 30 dias'
                WHEN a.ultimo_contato >= now() - interval '90 days' THEN '31 a 90 dias' ELSE 'Mais de 90 dias' END""",
                " AND a.origem <> 'manual'", 10)),
                List.of("Últimas 24 h", "1 a 7 dias", "8 a 30 dias", "31 a 90 dias", "Mais de 90 dias", "Nunca")));
        s.put("tag", fatias(cmdbAtivoMapper.contarPor("coalesce(a.tag, 'Sem TAG')", "", 15)));
        s.put("agente", fatias(cmdbAtivoMapper.contarPor("coalesce(a.agente, '?')", " AND a.origem IN ('ocs', 'glpi_agent')", 10)));
        s.put("softwares", cmdbAtivoMapper.softwares(null, 10, 0).stream()
                .map(m -> new Fatia(String.valueOf(m.get("nome")), ((Number) m.get("instalacoes")).longValue())).toList());
        s.put("novosPorMes", meses(cmdbAtivoMapper.novosPorMes(LocalDate.now().withDayOfMonth(1).minusMonths(11).atStartOfDay())));
        s.put("inventariosPorDia", inventariosPorDia());

        // alertas abertos por regra
        Map<String, String> nomes = cmdbAlertaRegraMapper.selectList().stream()
                .collect(Collectors.toMap(AlertaRegraDO::getCodigo, AlertaRegraDO::getNome, (a, b) -> a));
        Map<String, Long> porRegra = new LinkedHashMap<>();
        for (AlertaAtivoDO a : cmdbAlertaAtivoMapper.selectList(new LambdaQueryWrapperX<AlertaAtivoDO>().isNull(AlertaAtivoDO::getFim))) {
            porRegra.merge(nomes.getOrDefault(a.getRegra(), a.getRegra()), 1L, Long::sum);
        }
        t.put("alertas", porRegra.values().stream().mapToLong(Long::longValue).sum());
        s.put("alertas", porRegra.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Fatia(e.getKey(), e.getValue())).toList());
        return p;
    }

    /** Máquinas distintas com inventário gravado em cada um dos últimos 30 dias (dias sem envio aparecem com zero). */
    private List<Fatia> inventariosPorDia() {
        LocalDate hoje = LocalDate.now();
        Map<String, Long> m = new HashMap<>();
        jdbcTemplate.query("""
                SELECT to_char(quando::date, 'YYYY-MM-DD') AS dia, count(DISTINCT device_id) AS n FROM cmdb.agente_envio
                WHERE resultado = 'gravado' AND quando >= ? GROUP BY 1
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> m.put(rs.getString("dia"), rs.getLong("n")),
                Timestamp.valueOf(hoje.minusDays(29).atStartOfDay()));
        List<Fatia> l = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            String d = hoje.minusDays(i).toString();
            l.add(new Fatia(d, m.getOrDefault(d, 0L)));
        }
        return l;
    }

    private static List<Fatia> meses(List<Map<String, Object>> linhas) {
        Map<String, Long> m = new HashMap<>();
        fatias(linhas).forEach(f -> m.put(f.valor(), f.total()));
        List<Fatia> l = new ArrayList<>();
        LocalDate primeiro = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String k = primeiro.plusMonths(i).toString().substring(0, 7);
            l.add(new Fatia(k, m.getOrDefault(k, 0L)));
        }
        return l;
    }

    static List<Fatia> fatias(List<Map<String, Object>> linhas) {
        return linhas.stream().map(m -> new Fatia(String.valueOf(m.get("valor")), ((Number) m.get("total")).longValue())).toList();
    }

    static List<Fatia> ordenar(List<Fatia> l, List<String> ordem) {
        return l.stream().sorted(Comparator.comparingInt(f -> {
            int i = ordem.indexOf(f.valor());
            return i < 0 ? ordem.size() : i;
        })).toList();
    }

}
