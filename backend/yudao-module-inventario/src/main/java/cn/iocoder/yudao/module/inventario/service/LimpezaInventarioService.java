package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.LimpezaVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.HistoricoAtivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbHistoricoMapper;
import cn.iocoder.yudao.module.inventario.dominio.Normalizacao;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.LIMPEZA_INVALIDA;

/**
 * Limpeza do inventário. Máquina formatada com o mesmo hardware já volta no mesmo ativo (UUID, série ou MAC + nome;
 * ver {@link InventarioAgenteService}). Sobram dois casos:
 * <ul>
 *   <li><b>repetidas:</b> placa-mãe trocada, máquina clonada, cadastro manual antigo, nome reaproveitado num
 *   equipamento novo. A equipe mescla (é a mesma máquina), dá baixa no antigo (o nome foi para outra máquina) ou
 *   marca "são diferentes";</li>
 *   <li><b>paradas:</b> sem contato há muito tempo. A equipe dá baixa; opcionalmente, a baixa é automática
 *   ({@code coti.inventario.baixa-automatica-dias}).</li>
 * </ul>
 * Nada é apagado de verdade: o ativo mesclado sai das listas, e o histórico dele aparece no que ficou.
 */
@Service
public class LimpezaInventarioService {

    static final Set<String> COMPUTADORES = Set.of("desktop", "notebook", "servidor", "virtual");
    /** Um valor repetido em mais máquinas que isto é genérico (série "Default string", nome "PC"...) e não agrupa */
    static final int MAXIMO_POR_CHAVE = 30;
    private static final int MAXIMO_GRUPOS = 500;

    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbHistoricoMapper cmdbHistoricoMapper;
    @Resource
    private AtivoService ativoService;
    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AuditoriaService auditoriaService;

    // ======================================================================================= repetidas

    /** Grupos de ativos (fora os baixados) que parecem ser a mesma máquina. */
    public List<LimpezaVOs.Grupo> repetidas() {
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado"));
        Set<String> distintos = new HashSet<>();
        jdbcTemplate.query("SELECT ativo_a, ativo_b FROM cmdb.ativo_distinto",
                (RowCallbackHandler) rs -> distintos.add(rs.getLong(1) + "|" + rs.getLong(2)));
        List<List<AtivoDO>> grupos = agrupar(ativos, distintos);
        Map<Long, Long> chamados = new HashMap<>();
        Set<Long> ids = grupos.stream().flatMap(List::stream).map(AtivoDO::getId).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            jdbcTemplate.query("SELECT ativo_id, count(*) FROM cmdb.chamado_ativo WHERE deleted = 0 AND ativo_id = ANY(?::int8[]) GROUP BY ativo_id",
                    (RowCallbackHandler) rs -> chamados.put(rs.getLong(1), rs.getLong(2)), arrayPg(ids));
        }
        List<LimpezaVOs.Grupo> res = new ArrayList<>();
        for (List<AtivoDO> g : grupos) {
            LimpezaVOs.Grupo vo = new LimpezaVOs.Grupo();
            vo.setMotivos(motivos(g));
            vo.setAtivos(ativoService.converter(g));
            g.forEach(a -> vo.getChamados().add(chamados.getOrDefault(a.getId(), 0L)));
            res.add(vo);
        }
        return res;
    }

    /**
     * Junta os ativos que têm o mesmo nome (só computadores), a mesma série com o mesmo fabricante, o mesmo UUID ou o
     * mesmo MAC, menos os pares marcados como diferentes. Cada grupo vem do contato mais recente para o mais antigo.
     */
    static List<List<AtivoDO>> agrupar(List<AtivoDO> ativos, Set<String> distintos) {
        Map<String, List<AtivoDO>> porChave = new LinkedHashMap<>();
        for (AtivoDO a : ativos) {
            for (String k : chaves(a)) {
                porChave.computeIfAbsent(k, x -> new ArrayList<>()).add(a);
            }
        }
        Map<Long, Long> pai = new HashMap<>();
        for (List<AtivoDO> l : porChave.values()) {
            if (l.size() < 2 || l.size() > MAXIMO_POR_CHAVE) {
                continue;
            }
            for (int i = 0; i < l.size(); i++) {
                for (int j = i + 1; j < l.size(); j++) {
                    long x = l.get(i).getId(), y = l.get(j).getId();
                    if (x != y && !distintos.contains(Math.min(x, y) + "|" + Math.max(x, y))) {
                        unir(pai, x, y);
                    }
                }
            }
        }
        Map<Long, List<AtivoDO>> grupos = new LinkedHashMap<>();
        for (AtivoDO a : ativos) {
            if (pai.containsKey(a.getId())) {
                grupos.computeIfAbsent(raiz(pai, a.getId()), k -> new ArrayList<>()).add(a);
            }
        }
        Comparator<AtivoDO> recente = Comparator.comparing(AtivoDO::getUltimoContato, Comparator.nullsLast(Comparator.reverseOrder()));
        return grupos.values().stream().filter(g -> g.size() > 1)
                .map(g -> g.stream().sorted(recente.thenComparing(AtivoDO::getId)).toList())
                .sorted(Comparator.comparing(g -> Objects.toString(g.get(0).getNome(), "").toLowerCase(Locale.ROOT)))
                .limit(MAXIMO_GRUPOS).toList();
    }

    static List<String> chaves(AtivoDO a) {
        List<String> k = new ArrayList<>();
        if (a.getNome() != null && !a.getNome().isBlank() && COMPUTADORES.contains(a.getTipo())) {
            k.add("nome:" + a.getNome().strip().toLowerCase(Locale.ROOT));
        }
        if (!Normalizacao.generico(a.getNumeroSerie())) {
            k.add("serie:" + Objects.toString(a.getFabricante(), "").strip().toLowerCase(Locale.ROOT) + "|"
                    + a.getNumeroSerie().strip().toLowerCase(Locale.ROOT));
        }
        if (!Normalizacao.generico(a.getUuid())) {
            k.add("uuid:" + a.getUuid().strip().toLowerCase(Locale.ROOT));
        }
        String mac = a.getMac() == null ? null : a.getMac().strip().toUpperCase(Locale.ROOT).replace('-', ':');
        if (mac != null && !mac.isEmpty() && !mac.matches("^[0:]+$") && !mac.matches("^[F:]+$")) {
            k.add("mac:" + mac);
        }
        return k;
    }

    private static List<String> motivos(List<AtivoDO> g) {
        Map<String, Integer> conta = new HashMap<>();
        for (AtivoDO a : g) {
            for (String k : chaves(a)) {
                conta.merge(k, 1, Integer::sum);
            }
        }
        Map<String, String> rotulos = Map.of("nome", "mesmo nome", "serie", "mesma série", "uuid", "mesmo UUID", "mac", "mesmo MAC");
        return conta.entrySet().stream().filter(e -> e.getValue() > 1).map(e -> rotulos.get(e.getKey().substring(0, e.getKey().indexOf(':'))))
                .distinct().sorted().toList();
    }

    private static void unir(Map<Long, Long> pai, long a, long b) {
        pai.putIfAbsent(a, a);
        pai.putIfAbsent(b, b);
        long ra = raiz(pai, a), rb = raiz(pai, b);
        if (ra != rb) {
            pai.put(Math.max(ra, rb), Math.min(ra, rb));
        }
    }

    private static long raiz(Map<Long, Long> pai, long a) {
        long r = a;
        while (pai.get(r) != r) {
            r = pai.get(r);
        }
        pai.put(a, r);
        return r;
    }

    /** "São máquinas diferentes": nenhum par destes volta a aparecer como repetido. */
    public void distintos(List<Long> ids, Long userId) {
        List<Long> l = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (l.size() < 2 || l.size() > 50) {
            throw exception(LIMPEZA_INVALIDA, "escolha de 2 a 50 ativos");
        }
        l.forEach(ativoService::existente);
        for (int i = 0; i < l.size(); i++) {
            for (int j = i + 1; j < l.size(); j++) {
                jdbcTemplate.update("INSERT INTO cmdb.ativo_distinto (ativo_a, ativo_b, user_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        l.get(i), l.get(j), userId);
            }
        }
    }

    // ======================================================================================= mesclar

    /**
     * Junta os ativos {@code ids} no {@code manterId}. Chamados, equipamentos de rede vistos e alvos SNMP passam para o
     * que fica; o histórico continua de cada um e aparece junto no que fica. Se o agente está mandando por um dos que
     * somem, o DEVICEID e os dados do agente (hardware, sistema, softwares) passam para o que fica, para o próximo
     * inventário cair nele. Patrimônio, observação e unidade escolhida à mão completam o que fica quando ele não tem.
     */
    @Transactional(rollbackFor = Exception.class)
    public void mesclar(LimpezaVOs.MesclarReq req, Long userId) {
        AtivoDO alvo = ativoService.existente(req.getManterId());
        List<AtivoDO> saem = req.getIds().stream().filter(Objects::nonNull).filter(id -> !id.equals(alvo.getId())).distinct()
                .map(ativoService::existente).toList();
        if (saem.isEmpty()) {
            throw exception(LIMPEZA_INVALIDA, "escolha pelo menos um ativo para juntar ao que fica");
        }
        LocalDateTime agora = LocalDateTime.now();
        AtivoDO m = AtivoDO.builder().id(alvo.getId()).build();

        // 1. o agente que está mandando agora (contato mais recente com DEVICEID)
        List<AtivoDO> todos = new ArrayList<>(saem);
        todos.add(alvo);
        AtivoDO vivo = todos.stream().filter(a -> a.getDeviceId() != null && !"manual".equals(a.getOrigem()))
                .max(Comparator.comparing(AtivoDO::getUltimoContato, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
        if (vivo != null && vivo != alvo) {
            jdbcTemplate.update("UPDATE cmdb.ativo SET device_id = NULL WHERE id = ?", vivo.getId());
            copiarDoAgente(vivo, m);
            if (alvo.getPrimeiroInventario() != null && vivo.getPrimeiroInventario() != null
                    && alvo.getPrimeiroInventario().isBefore(vivo.getPrimeiroInventario())) {
                m.setPrimeiroInventario(alvo.getPrimeiroInventario());
            }
            // peças e softwares: valem os do agente atual
            for (String t : List.of("cmdb.ativo_componente", "cmdb.ativo_software")) {
                jdbcTemplate.update("UPDATE " + t + " SET deleted = 1, update_time = now() WHERE ativo_id = ? AND deleted = 0", alvo.getId());
                jdbcTemplate.update("UPDATE " + t + " SET ativo_id = ?, update_time = now() WHERE ativo_id = ? AND deleted = 0", alvo.getId(), vivo.getId());
            }
            historico(alvo.getId(), userId, agora, "dados_agente", vivo.getNome() + " (#" + vivo.getId() + ")",
                    "O agente deste registro passa a atualizar " + alvo.getNome());
            if ("baixado".equals(alvo.getSituacao())) {
                m.setSituacao("em_uso");
            }
        }

        // 2. o que o que fica não tem, vem dos que saem
        if (vazio(alvo.getPatrimonio())) {
            saem.stream().map(AtivoDO::getPatrimonio).filter(p -> !vazio(p)).findFirst().ifPresent(m::setPatrimonio);
        }
        String obs = saem.stream().filter(a -> !vazio(a.getObservacao()))
                .map(a -> "[de " + a.getNome() + " #" + a.getId() + "] " + a.getObservacao().strip()).collect(Collectors.joining("\n"));
        if (!obs.isEmpty()) {
            m.setObservacao(vazio(alvo.getObservacao()) ? obs : alvo.getObservacao().strip() + "\n" + obs);
        }
        if (!Boolean.TRUE.equals(alvo.getDeptManual())) {
            saem.stream().filter(a -> Boolean.TRUE.equals(a.getDeptManual()) && a.getDeptId() != null).findFirst().ifPresent(a -> {
                m.setDeptId(a.getDeptId());
                m.setDeptManual(true);
            });
        }
        cmdbAtivoMapper.updateById(m);

        // 3. o que aponta para os que saem passa para o que fica
        for (AtivoDO a : saem) {
            jdbcTemplate.update("""
                    UPDATE cmdb.chamado_ativo SET ativo_id = ?, update_time = now() WHERE ativo_id = ? AND deleted = 0
                    AND chamado_id NOT IN (SELECT chamado_id FROM cmdb.chamado_ativo WHERE ativo_id = ? AND deleted = 0)
                    """, alvo.getId(), a.getId(), alvo.getId());
            jdbcTemplate.update("UPDATE cmdb.chamado_ativo SET deleted = 1, update_time = now() WHERE ativo_id = ? AND deleted = 0", a.getId());
            jdbcTemplate.update("UPDATE cmdb.rede_dispositivo SET ativo_id = ?, update_time = now() WHERE ativo_id = ?", alvo.getId(), a.getId());
            jdbcTemplate.update("UPDATE cmdb.rede_varredor SET deleted = 1, update_time = now() WHERE ativo_id = ? AND deleted = 0", a.getId());
            jdbcTemplate.update("UPDATE cmdb.snmp_alvo SET ativo_id = ?, update_time = now() WHERE ativo_id = ?", alvo.getId(), a.getId());
            jdbcTemplate.update("UPDATE cmdb.alerta_ativo SET fim = now(), update_time = now() WHERE objeto = 'ativo' AND objeto_id = ? AND fim IS NULL",
                    a.getId());
            for (String t : List.of("cmdb.ativo_componente", "cmdb.ativo_software")) {
                jdbcTemplate.update("UPDATE " + t + " SET deleted = 1, update_time = now() WHERE ativo_id = ? AND deleted = 0", a.getId());
            }
            jdbcTemplate.update("UPDATE cmdb.ativo SET mesclado_em = ?, device_id = NULL, deleted = 1, update_time = now() WHERE id = ?",
                    alvo.getId(), a.getId());
            historico(a.getId(), userId, agora, "mesclado", a.getNome(), "Juntado em " + alvo.getNome() + " (#" + alvo.getId() + ")");
            historico(alvo.getId(), userId, agora, "mesclado", a.getNome() + " (#" + a.getId() + ")", alvo.getNome());
        }
        auditoriaService.registrar("inventario.ativos_mesclados", userId, null, Map.of("manter", alvo.getId(), "nome", alvo.getNome(),
                "mesclados", saem.stream().map(AtivoDO::getId).toList()));
    }

    private static void copiarDoAgente(AtivoDO de, AtivoDO para) {
        para.setDeviceId(de.getDeviceId());
        para.setOrigem(de.getOrigem());
        para.setAgente(de.getAgente());
        para.setTipo(de.getTipo());
        para.setNome(de.getNome());
        para.setFabricante(de.getFabricante());
        para.setModelo(de.getModelo());
        para.setNumeroSerie(de.getNumeroSerie());
        para.setUuid(de.getUuid());
        para.setSoNome(de.getSoNome());
        para.setSoVersao(de.getSoVersao());
        para.setSoArquitetura(de.getSoArquitetura());
        para.setSoSemSuporte(de.getSoSemSuporte());
        para.setCpu(de.getCpu());
        para.setCpuNucleos(de.getCpuNucleos());
        para.setMemoriaMb(de.getMemoriaMb());
        para.setDiscoTotalMb(de.getDiscoTotalMb());
        para.setDiscoLivreMb(de.getDiscoLivreMb());
        para.setDiscoTipo(de.getDiscoTipo());
        para.setIp(de.getIp());
        para.setMac(de.getMac());
        para.setDominio(de.getDominio());
        para.setUsuarioLogin(de.getUsuarioLogin());
        para.setUsuarioId(de.getUsuarioId());
        para.setAntivirus(de.getAntivirus());
        para.setPrimeiroInventario(de.getPrimeiroInventario());
        para.setUltimoInventario(de.getUltimoInventario());
        para.setUltimoContato(de.getUltimoContato());
    }

    // ======================================================================================= paradas

    /** Máquinas de agente ou SNMP (fora as baixadas) sem contato há mais de {@code dias}. */
    public List<AtivoVOs.Resp> paradas(int dias) {
        return ativoService.converter(cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>()
                .ne(AtivoDO::getOrigem, "manual").ne(AtivoDO::getSituacao, "baixado")
                .lt(AtivoDO::getUltimoContato, LocalDateTime.now().minusDays(dias))
                .orderByAsc(AtivoDO::getUltimoContato).last("LIMIT 2000")));
    }

    /** Dá baixa (situação "baixado"). Se a máquina voltar a mandar inventário, volta para "em uso" sozinha. */
    @Transactional(rollbackFor = Exception.class)
    public int baixar(List<Long> ids, Long userId) {
        LocalDateTime agora = LocalDateTime.now();
        int n = 0;
        for (Long id : new LinkedHashSet<>(ids)) {
            AtivoDO a = ativoService.existente(id);
            if ("baixado".equals(a.getSituacao())) {
                continue;
            }
            cmdbAtivoMapper.updateById(AtivoDO.builder().id(a.getId()).situacao("baixado").build());
            historico(a.getId(), userId, agora, "situacao", a.getSituacao(), "baixado");
            n++;
        }
        if (n > 0) {
            auditoriaService.registrar("inventario.ativos_baixados", userId, null, Map.of("quantidade", n));
        }
        return n;
    }

    /** Baixa automática das máquinas paradas ({@code coti.inventario.baixa-automatica-dias}; 0 = desligada). */
    @DataPermission(enable = false)
    @Transactional(rollbackFor = Exception.class)
    public int baixaAutomatica() {
        int dias = inventarioProperties.getBaixaAutomaticaDias();
        if (dias <= 0) {
            return 0;
        }
        LocalDateTime agora = LocalDateTime.now();
        List<AtivoDO> l = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getOrigem, "manual")
                .ne(AtivoDO::getSituacao, "baixado").lt(AtivoDO::getUltimoContato, agora.minusDays(dias)));
        for (AtivoDO a : l) {
            cmdbAtivoMapper.updateById(AtivoDO.builder().id(a.getId()).situacao("baixado").build());
            cmdbHistoricoMapper.insert(HistoricoAtivoDO.builder().ativoId(a.getId()).quando(agora).tipo("limpeza").campo("situacao")
                    .antes(a.getSituacao()).depois("baixado (sem contato há mais de " + dias + " dias)").build());
        }
        if (!l.isEmpty()) {
            auditoriaService.registrar("inventario.baixa_automatica", null, "sistema", Map.of("quantidade", l.size(), "dias", dias));
        }
        return l.size();
    }

    // ======================================================================================= apoio

    private void historico(Long ativoId, Long userId, LocalDateTime quando, String campo, String antes, String depois) {
        cmdbHistoricoMapper.insert(HistoricoAtivoDO.builder().ativoId(ativoId).quando(quando).tipo("limpeza").campo(campo)
                .antes(antes).depois(depois).userId(userId).build());
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    private static String arrayPg(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

}
