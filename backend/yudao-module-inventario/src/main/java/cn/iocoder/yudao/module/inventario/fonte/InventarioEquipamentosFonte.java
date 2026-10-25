package cn.iocoder.yudao.module.inventario.fonte;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.EquipamentoVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ListaVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Fonte real da tela "Centro de Operações → Inventário" e do telão: lê o CMDB no lugar dos dados simulados do MVP.
 * Só computadores (desktop, notebook, servidor e máquina virtual) que não foram baixados.
 */
@Primary
@Component
public class InventarioEquipamentosFonte implements MonitorFontes.Inventario {

    /** O MVP chama de "desatualizado" quem não reporta há mais de 7 dias */
    private static final long DIAS_SEM_CONTATO = 7;

    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private MonitorUnitMapper unitMapper;
    @Resource
    private DeptApi deptApi;

    @Override
    public ListaVO<EquipamentoVO> listarEquipamentos(Long unidadeId, String filtro, String busca, int limite) {
        Map<Long, MonitorUnitDO> unidadePorDept = unidadePorDept();
        Set<Long> deptsDaUnidade = unidadeId == null ? null : unidadePorDept.entrySet().stream()
                .filter(e -> unidadeId.equals(e.getValue().getId())).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (deptsDaUnidade != null && deptsDaUnidade.isEmpty()) {
            return new ListaVO<>();
        }
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>()
                .inIfPresent(AtivoDO::getDeptId, deptsDaUnidade)
                .in(AtivoDO::getTipo, "desktop", "notebook", "servidor", "virtual")
                .ne(AtivoDO::getSituacao, "baixado"));
        Set<Long> ids = ativos.stream().map(AtivoDO::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = ids.isEmpty() ? Map.of() : adminUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, AdminUserDO::getNickname));
        List<EquipamentoVO> base = ativos.stream().map(a -> converter(a, unidadePorDept, nomes)).toList();
        Map<Long, Boolean> eol = ativos.stream().collect(Collectors.toMap(AtivoDO::getId, a -> Boolean.TRUE.equals(a.getSoSemSuporte())));

        long agora = System.currentTimeMillis();
        Predicate<EquipamentoVO> velho = e -> e.getUltimaComunicacao() == null || agora - e.getUltimaComunicacao() > DIAS_SEM_CONTATO * 86_400_000L;
        List<Boolean> semSuporte = ativos.stream().map(a -> eol.get(a.getId())).toList();

        ListaVO<EquipamentoVO> res = new ListaVO<>();
        Map<String, Long> ct = res.getContagens();
        ct.put("todos", (long) base.size());
        for (String t : new String[]{"Desktop", "Notebook", "Servidor"}) {
            ct.put(t, base.stream().filter(e -> t.equals(e.getTipo())).count());
        }
        ct.put("eol", semSuporte.stream().filter(b -> b).count());
        ct.put("av", base.stream().filter(e -> !"Atualizado".equals(e.getAntivirus())).count());
        ct.put("old", base.stream().filter(velho).count());
        res.getResumo().put("porSistema", base.stream().collect(Collectors.groupingBy(e -> e.getSo() == null ? "Desconhecido" : e.getSo(),
                TreeMap::new, Collectors.counting())));

        String f = filtro == null ? "todos" : filtro;
        String q = busca == null ? "" : semAcento(busca);
        List<EquipamentoVO> lista = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            EquipamentoVO e = base.get(i);
            boolean passa = switch (f) {
                case "todos" -> true;
                case "eol" -> semSuporte.get(i);
                case "av" -> !"Atualizado".equals(e.getAntivirus());
                case "old" -> velho.test(e);
                default -> f.equals(e.getTipo());
            };
            if (passa && (q.isEmpty() || semAcento(e.getHostname() + " " + e.getSo() + " " + e.getIp() + " " + e.getUsuario()).contains(q))) {
                lista.add(e);
            }
        }
        lista.sort(Comparator.comparing(e -> e.getHostname().toLowerCase(Locale.ROOT)));
        res.setTotal(lista.size());
        res.setLista(new ArrayList<>(lista.stream().limit(limite).toList()));
        return res;
    }

    private static EquipamentoVO converter(AtivoDO a, Map<Long, MonitorUnitDO> unidadePorDept, Map<Long, String> nomes) {
        EquipamentoVO e = new EquipamentoVO();
        e.setHostname(a.getNome());
        e.setTipo(switch (a.getTipo()) {
            case "notebook" -> "Notebook";
            case "servidor", "virtual" -> "Servidor";
            default -> "Desktop";
        });
        MonitorUnitDO u = a.getDeptId() == null ? null : unidadePorDept.get(a.getDeptId());
        e.setUnidadeId(u == null ? null : u.getId());
        e.setUnidade(u == null ? "" : u.getName());
        String usuario = a.getUsuarioId() == null ? null : nomes.get(a.getUsuarioId());
        e.setUsuario(usuario != null ? usuario : Objects.toString(a.getUsuarioLogin(), ""));
        e.setSo(a.getSoNome() == null ? "" : a.getSoNome());
        e.setCpu(a.getCpu());
        e.setRamGb(a.getMemoriaMb() == null ? null : (int) Math.round(a.getMemoriaMb() / 1024.0));
        e.setDiscoGb(a.getDiscoTotalMb() == null ? null : (int) Math.round(a.getDiscoTotalMb() / 1024.0));
        e.setDiscoLivrePct(a.getDiscoTotalMb() == null || a.getDiscoTotalMb() <= 0 || a.getDiscoLivreMb() == null ? null
                : (int) Math.round(100.0 * a.getDiscoLivreMb() / a.getDiscoTotalMb()));
        e.setAntivirus(switch (Objects.toString(a.getAntivirus(), "")) {
            case "ativo" -> "Atualizado";
            case "desatualizado" -> "Desatualizado";
            default -> "Ausente";
        });
        e.setUltimaComunicacao(epoch(a.getUltimoContato()));
        e.setIp(a.getIp());
        return e;
    }

    /** Cada departamento aponta para a unidade (monitor_unit) cuja raiz é ele ou um ancestral. */
    private Map<Long, MonitorUnitDO> unidadePorDept() {
        Map<Long, MonitorUnitDO> mapa = new HashMap<>();
        for (MonitorUnitDO u : unitMapper.selectList()) {
            mapa.putIfAbsent(u.getDeptId(), u);
            for (DeptRespDTO d : deptApi.getChildDeptList(u.getDeptId())) {
                mapa.putIfAbsent(d.getId(), u);
            }
        }
        return mapa;
    }

    private static Long epoch(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

}
