package cn.iocoder.yudao.module.itsm.fonte;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.CadastroService;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ChamadoNovoReqVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ChamadoVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.ListaVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fonte real da tela "Centro de Operações → Chamados por unidade" e dos chamados de cada unidade no telão: lê o ITSM
 * no lugar dos dados simulados do MVP.
 */
@Primary
@Component
public class ItsmChamadosFonte implements MonitorFontes.Chamados, MonitorFontes.IndicadoresChamados {

    /** Abertos + os solucionados/fechados mais recentes que a tela mostra */
    private static final int RECENTES_ENCERRADOS = 200;
    private static final Long ORIGEM_MONITORAMENTO = 6L;

    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private CadastroService cadastroService;
    @Resource
    private MonitorUnitMapper unitMapper;
    @Resource
    private DeptApi deptApi;

    @Override
    public ListaVO<ChamadoVO> listarChamados(Long unidadeId, String status, String busca, int limite) {
        Map<Long, MonitorUnitDO> unidadePorDept = unidadePorDept();
        Set<Long> deptsDaUnidade = unidadeId == null ? null : unidadePorDept.entrySet().stream()
                .filter(e -> unidadeId.equals(e.getValue().getId())).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (deptsDaUnidade != null && deptsDaUnidade.isEmpty()) {
            return new ListaVO<>();
        }
        List<ChamadoDO> base = new ArrayList<>(chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>()
                .inIfPresent(ChamadoDO::getDeptId, deptsDaUnidade)
                .lt(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo())));
        base.addAll(chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>()
                .inIfPresent(ChamadoDO::getDeptId, deptsDaUnidade)
                .ge(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo())
                .orderByDesc(ChamadoDO::getSolucionadoEm).last("LIMIT " + RECENTES_ENCERRADOS)));

        Map<Long, String> categorias = cadastroService.listarCategorias(false, false).stream()
                .collect(Collectors.toMap(CategoriaDO::getId, CategoriaDO::getNomeCompleto));
        long agora = System.currentTimeMillis();
        List<ChamadoVO> todos = base.stream().map(c -> converter(c, categorias, unidadePorDept)).toList();
        // nomes de solicitante e técnico só para os que vão aparecer
        ListaVO<ChamadoVO> res = new ListaVO<>();
        Map<String, Long> ct = res.getContagens();
        ct.put("abertos", todos.stream().filter(ItsmChamadosFonte::aberto).count());
        for (String s : new String[]{"Novo", "Em atendimento", "Pendente", "Solucionado"}) {
            ct.put(s, todos.stream().filter(c -> s.equals(c.getStatus())).count());
        }
        ct.put("sla", todos.stream().filter(c -> aberto(c) && c.getPrazo() != null && c.getPrazo() < agora).count());
        ct.put("auto", todos.stream().filter(c -> aberto(c) && !c.getOrigem().isEmpty()).count());
        ct.put("todos", (long) todos.size());

        String st = status == null ? "abertos" : status;
        String q = busca == null ? "" : busca.trim().toLowerCase(Locale.ROOT);
        List<ChamadoVO> filtrados = todos.stream()
                .filter(c -> switch (st) {
                    case "todos" -> true;
                    case "abertos" -> aberto(c);
                    case "sla" -> aberto(c) && c.getPrazo() != null && c.getPrazo() < agora;
                    case "auto" -> aberto(c) && !c.getOrigem().isEmpty();
                    default -> st.equals(c.getStatus());
                })
                .filter(c -> q.isEmpty() || (c.getId() + " " + c.getTitulo()).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing((ChamadoVO c) -> !aberto(c))
                        .thenComparing(c -> c.getPrazo() == null ? Long.MAX_VALUE : c.getPrazo()))
                .toList();
        res.setTotal(filtrados.size());
        List<ChamadoVO> pagina = filtrados.stream().limit(limite).toList();
        preencherPessoas(pagina);
        res.setLista(new ArrayList<>(pagina));
        return res;
    }

    @Override
    public ChamadoVO abrirChamado(ChamadoNovoReqVO novo, String solicitante, String origem) {
        Long userId = idDe(solicitante);
        ChamadoVOs.CriarReq req = new ChamadoVOs.CriarReq();
        req.setTitulo(novo.getTitulo());
        int urgencia = switch (Optional.ofNullable(novo.getPrioridade()).orElse("Média")) {
            case "Crítica" -> 5;
            case "Alta" -> 4;
            case "Baixa" -> 2;
            default -> 3;
        };
        req.setUrgencia(urgencia);
        req.setImpacto(urgencia);
        if ("Crítica".equals(novo.getPrioridade())) {
            req.setPrioridade(6);
        }
        if (novo.getCategoria() != null) {
            cadastroService.listarCategorias(true, false).stream()
                    .filter(c -> c.getNome().equalsIgnoreCase(novo.getCategoria().trim())
                            || c.getNomeCompleto().equalsIgnoreCase(novo.getCategoria().trim()))
                    .findFirst().ifPresent(c -> req.setCategoriaId(c.getId()));
        }
        if (novo.getUnidadeId() != null) {
            MonitorUnitDO u = unitMapper.selectById(novo.getUnidadeId());
            if (u != null) {
                req.setDeptId(u.getDeptId());
            }
        }
        boolean automatico = origem != null && !origem.isBlank();
        if (automatico) {
            req.setOrigemId(ORIGEM_MONITORAMENTO);
        }
        Long id = chamadoService.criar(req, userId);
        if (automatico) {
            chamadoMapper.updateById(ChamadoDO.builder().id(id).origemAutomatica(origem).build());
        }
        ChamadoDO c = chamadoMapper.selectById(id);
        Map<Long, String> categorias = cadastroService.listarCategorias(false, false).stream()
                .collect(Collectors.toMap(CategoriaDO::getId, CategoriaDO::getNomeCompleto));
        ChamadoVO vo = converter(c, categorias, unidadePorDept());
        preencherPessoas(List.of(vo));
        return vo;
    }

    // ------------------------------------------------------------------

    private void preencherPessoas(List<ChamadoVO> lista) {
        if (lista.isEmpty()) {
            return;
        }
        List<ChamadoDO> chamados = chamadoMapper.selectByIds(lista.stream().map(ChamadoVO::getId).toList());
        Map<Long, ChamadoVOs.Resp> resumo = chamadoService.converter(chamados).stream()
                .collect(Collectors.toMap(ChamadoVOs.Resp::getId, r -> r));
        for (ChamadoVO vo : lista) {
            ChamadoVOs.Resp r = resumo.get(vo.getId());
            if (r != null) {
                vo.setSolicitante(r.getSolicitantes().stream().map(ChamadoVOs.Ator::getNome).collect(Collectors.joining(", ")));
                vo.setTecnico(r.getTecnicos().stream().map(ChamadoVOs.Ator::getNome).collect(Collectors.joining(", ")));
            }
        }
    }

    private ChamadoVO converter(ChamadoDO c, Map<Long, String> categorias, Map<Long, MonitorUnitDO> unidadePorDept) {
        ChamadoVO vo = new ChamadoVO();
        vo.setId(c.getId());
        vo.setTitulo(c.getTitulo());
        vo.setCategoria(categorias.getOrDefault(c.getCategoriaId(), ""));
        vo.setPrioridade(switch (c.getPrioridade()) {
            case 6 -> "Crítica";
            case 4, 5 -> "Alta";
            case 3 -> "Média";
            default -> "Baixa";
        });
        ChamadoStatus st = ChamadoStatus.de(c.getStatus());
        vo.setStatus(switch (st) {
            case NOVO -> "Novo";
            case ATRIBUIDO, PLANEJADO -> "Em atendimento";
            case PENDENTE -> "Pendente";
            default -> "Solucionado";
        });
        MonitorUnitDO u = unidadePorDept.get(c.getDeptId());
        vo.setUnidadeId(u == null ? null : u.getId());
        vo.setUnidade(u == null ? "" : u.getName());
        vo.setAbertoEm(epoch(c.getAbertoEm()));
        vo.setPrazo(epoch(c.getPrazoSolucao()));
        vo.setSolucionadoEm(epoch(c.getSolucionadoEm()));
        vo.setOrigem(c.getOrigemAutomatica() == null ? "" : c.getOrigemAutomatica());
        return vo;
    }

    /** Cada departamento aponta para a unidade (monitor_unit) cuja raiz é ele ou um ancestral. */
    /** Abertos por unidade; SLA vencido segue a regra da fila (prazo de solução passado e chamado fora de pendente). */
    @Override
    @DataPermission(enable = false)
    public Map<Long, MonitorFontes.ChamadosUnidade> chamadosPorUnidade() {
        Map<Long, MonitorUnitDO> unidadePorDept = unidadePorDept();
        if (unidadePorDept.isEmpty()) {
            return Map.of();
        }
        LocalDateTime agora = LocalDateTime.now();
        Map<Long, int[]> soma = new HashMap<>();
        for (ChamadoDO c : chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>()
                .in(ChamadoDO::getDeptId, unidadePorDept.keySet())
                .lt(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo()))) {
            int[] n = soma.computeIfAbsent(unidadePorDept.get(c.getDeptId()).getId(), k -> new int[2]);
            n[0]++;
            if (!Objects.equals(c.getStatus(), ChamadoStatus.PENDENTE.getCodigo())
                    && c.getPrazoSolucao() != null && c.getPrazoSolucao().isBefore(agora)) {
                n[1]++;
            }
        }
        Map<Long, MonitorFontes.ChamadosUnidade> out = new HashMap<>();
        soma.forEach((unidade, n) -> out.put(unidade, new MonitorFontes.ChamadosUnidade(n[0], n[1])));
        return out;
    }

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

    private static boolean aberto(ChamadoVO c) {
        return !"Solucionado".equals(c.getStatus());
    }

    private static Long epoch(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** O controller do MVP passa "usuario#ID"; sem usuário (alerta automático) o registro fica com o administrador. */
    private static Long idDe(String solicitante) {
        if (solicitante != null && solicitante.startsWith("usuario#")) {
            try {
                return Long.parseLong(solicitante.substring("usuario#".length()));
            } catch (NumberFormatException ignorado) {
                // cai no padrão
            }
        }
        return 1L;
    }

}
