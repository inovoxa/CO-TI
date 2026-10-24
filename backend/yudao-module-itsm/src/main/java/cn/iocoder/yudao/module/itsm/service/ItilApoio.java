package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * O que problemas e mudanças têm em comum: participantes, linha do tempo, histórico e vínculos.
 * Quem chama já conferiu a permissão, o escopo de unidade e travou o objeto.
 */
@Component
public class ItilApoio {

    @Resource
    private ItilAtorMapper itilAtorMapper;
    @Resource
    private ItilInteracaoMapper itilInteracaoMapper;
    @Resource
    private ItilHistoricoMapper itilHistoricoMapper;
    @Resource
    private ItilVinculoMapper itilVinculoMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ProblemaMapper problemaMapper;
    @Resource
    private MudancaMapper mudancaMapper;
    @Resource
    private ChamadoEscopo escopo;
    @Resource
    private CadastroService cadastros;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    // ------------------------------------------------------------------ participantes

    public List<ItilAtorDO> atores(String objeto, Long id) {
        return itilAtorMapper.selectList(new LambdaQueryWrapperX<ItilAtorDO>()
                .eq(ItilAtorDO::getObjeto, objeto).eq(ItilAtorDO::getObjetoId, id).orderByAsc(ItilAtorDO::getId));
    }

    public boolean temTecnico(String objeto, Long id) {
        return atores(objeto, id).stream().anyMatch(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo());
    }

    /** Valida e grava; devolve o nome para o histórico. */
    public String incluirAtor(String objeto, Long id, ItilVOs.AtorReq req) {
        AtorTipo tipo = AtorTipo.de(req.getTipo());
        if ((req.getUserId() == null) == (req.getEquipeId() == null) || (req.getEquipeId() != null && tipo != AtorTipo.TECNICO)) {
            throw exception(ITIL_ATOR_INVALIDO);
        }
        String nome;
        if (req.getUserId() != null) {
            adminUserApi.validateUser(req.getUserId());
            nome = nomePessoa(req.getUserId());
        } else {
            nome = cadastros.obterEquipe(req.getEquipeId()).getNome();
        }
        boolean existe = atores(objeto, id).stream().anyMatch(a -> a.getTipo() == tipo.getCodigo()
                && Objects.equals(a.getUserId(), req.getUserId()) && Objects.equals(a.getEquipeId(), req.getEquipeId()));
        if (existe) {
            throw exception(ITIL_ATOR_DUPLICADO);
        }
        itilAtorMapper.insert(ItilAtorDO.builder().objeto(objeto).objetoId(id).tipo(tipo.getCodigo())
                .userId(req.getUserId()).equipeId(req.getEquipeId()).build());
        return nome;
    }

    public ItilAtorDO ator(String objeto, Long atorId) {
        ItilAtorDO a = atorId == null ? null : itilAtorMapper.selectById(atorId);
        if (a == null || !objeto.equals(a.getObjeto())) {
            throw exception(ITIL_ATOR_NAO_EXISTE);
        }
        return a;
    }

    /** Remove e devolve o nome para o histórico. */
    public String removerAtor(ItilAtorDO a) {
        itilAtorMapper.deleteById(a.getId());
        return a.getUserId() != null ? nomePessoa(a.getUserId()) : cadastros.obterEquipe(a.getEquipeId()).getNome();
    }

    // ------------------------------------------------------------------ linha do tempo

    /** Grava a interação (texto saneado); tarefa soma o tempo gasto no objeto pelo valor devolvido. */
    public ItilInteracaoDO inserirInteracao(String objeto, Long id, ItilVOs.InteracaoReq req, Long userId) {
        ItilInteracaoDO i = ItilInteracaoDO.builder().objeto(objeto).objetoId(id).tipo(req.getTipo()).userId(userId)
                .conteudo(HtmlSeguro.limpar(req.getConteudo())).build();
        if (InteracaoDO.TAREFA.equals(req.getTipo())) {
            if (req.getInicio() != null && req.getFim() != null && !req.getFim().isAfter(req.getInicio())) {
                throw exception(ITIL_TAREFA_DATAS);
            }
            i.setEstadoTarefa(req.getEstadoTarefa() != null ? req.getEstadoTarefa() : InteracaoDO.TAREFA_A_FAZER);
            i.setTecnicoId(req.getTecnicoId() != null ? req.getTecnicoId() : userId);
            i.setInicio(req.getInicio());
            i.setFim(req.getFim());
            i.setTempoGastoSeg(req.getTempoGastoSeg() == null ? 0 : req.getTempoGastoSeg());
        }
        itilInteracaoMapper.insert(i);
        return i;
    }

    public List<ChamadoVOs.Interacao> linhaDoTempo(String objeto, Long id) {
        List<ItilInteracaoDO> itens = itilInteracaoMapper.selectList(new LambdaQueryWrapperX<ItilInteracaoDO>()
                .eq(ItilInteracaoDO::getObjeto, objeto).eq(ItilInteracaoDO::getObjetoId, id)
                .orderByAsc(ItilInteracaoDO::getCreateTime).orderByAsc(ItilInteracaoDO::getId));
        Set<Long> pessoas = new HashSet<>();
        itens.forEach(i -> {
            pessoas.add(i.getUserId());
            pessoas.add(i.getTecnicoId());
        });
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        return itens.stream().map(i -> {
            ChamadoVOs.Interacao v = new ChamadoVOs.Interacao();
            v.setId(i.getId());
            v.setTipo(i.getTipo());
            v.setUserId(i.getUserId());
            v.setAutor(nome(nomes, i.getUserId()));
            v.setConteudo(i.getConteudo());
            v.setPrivado(false);
            v.setCriadoEm(i.getCreateTime());
            v.setEstadoTarefa(i.getEstadoTarefa());
            v.setTecnicoId(i.getTecnicoId());
            v.setTecnico(nome(nomes, i.getTecnicoId()));
            v.setInicio(i.getInicio());
            v.setFim(i.getFim());
            v.setTempoGastoSeg(i.getTempoGastoSeg());
            return v;
        }).toList();
    }

    /** Última solução registrada (texto puro), para levar aos chamados vinculados. */
    public String textoUltimaSolucao(String objeto, Long id) {
        ItilInteracaoDO s = itilInteracaoMapper.selectOne(new LambdaQueryWrapperX<ItilInteracaoDO>()
                .eq(ItilInteracaoDO::getObjeto, objeto).eq(ItilInteracaoDO::getObjetoId, id)
                .eq(ItilInteracaoDO::getTipo, InteracaoDO.SOLUCAO).orderByDesc(ItilInteracaoDO::getId).last("LIMIT 1"));
        return s == null ? "" : HtmlSeguro.texto(s.getConteudo());
    }

    // ------------------------------------------------------------------ histórico

    public void registrar(String objeto, Long id, Long userId, String campo, String de, String para) {
        itilHistoricoMapper.insert(ItilHistoricoDO.builder().objeto(objeto).objetoId(id).momento(LocalDateTime.now())
                .userId(userId).usuario(userId == null ? "Sistema" : nomePessoa(userId)).campo(campo)
                .valorAntigo(limitar(de)).valorNovo(limitar(para)).build());
    }

    public void diferenca(String objeto, Long id, Long userId, String campo, Object de, Object para) {
        String a = de == null ? null : String.valueOf(de);
        String b = para == null ? null : String.valueOf(para);
        if (!Objects.equals(a, b)) {
            registrar(objeto, id, userId, campo, a, b);
        }
    }

    public List<ItilHistoricoDO> historico(String objeto, Long id) {
        return itilHistoricoMapper.selectList(new LambdaQueryWrapperX<ItilHistoricoDO>()
                .eq(ItilHistoricoDO::getObjeto, objeto).eq(ItilHistoricoDO::getObjetoId, id).orderByDesc(ItilHistoricoDO::getId));
    }

    // ------------------------------------------------------------------ vínculos

    /** Confere que o alvo existe e está no escopo de quem vincula; grava. */
    public void vincular(String objeto, Long id, String alvo, Long alvoId) {
        boolean valido = (ItilVOs.PROBLEMA.equals(objeto) && ItilVOs.CHAMADO.equals(alvo))
                || (ItilVOs.MUDANCA.equals(objeto) && (ItilVOs.CHAMADO.equals(alvo) || ItilVOs.PROBLEMA.equals(alvo)));
        if (!valido) {
            throw exception(ITIL_VINCULO_INVALIDO);
        }
        if (ItilVOs.CHAMADO.equals(alvo) && !escopo.visivel(alvoId)) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        if (ItilVOs.PROBLEMA.equals(alvo) && problemaMapper.selectById(alvoId) == null) {
            throw exception(PROBLEMA_NAO_EXISTE);
        }
        if (itilVinculoMapper.selectCount(new LambdaQueryWrapperX<ItilVinculoDO>().eq(ItilVinculoDO::getObjeto, objeto)
                .eq(ItilVinculoDO::getObjetoId, id).eq(ItilVinculoDO::getAlvo, alvo).eq(ItilVinculoDO::getAlvoId, alvoId)) > 0) {
            throw exception(ITIL_VINCULO_DUPLICADO);
        }
        itilVinculoMapper.insert(ItilVinculoDO.builder().objeto(objeto).objetoId(id).alvo(alvo).alvoId(alvoId).build());
    }

    public ItilVinculoDO vinculo(Long vinculoId) {
        ItilVinculoDO v = vinculoId == null ? null : itilVinculoMapper.selectById(vinculoId);
        if (v == null) {
            throw exception(ITIL_VINCULO_NAO_EXISTE);
        }
        return v;
    }

    public void desvincular(ItilVinculoDO v) {
        itilVinculoMapper.deleteById(v.getId());
    }

    /**
     * Tudo ligado a um problema ou mudança, dos dois lados (uma mudança vê seus problemas; um problema vê as
     * mudanças que o tratam). Itens fora do escopo de unidade aparecem só com o número.
     */
    public List<ItilVOs.Vinculo> vinculos(String objeto, Long id) {
        List<ItilVinculoDO> diretos = itilVinculoMapper.selectList(new LambdaQueryWrapperX<ItilVinculoDO>()
                .eq(ItilVinculoDO::getObjeto, objeto).eq(ItilVinculoDO::getObjetoId, id));
        List<ItilVinculoDO> reversos = itilVinculoMapper.selectList(new LambdaQueryWrapperX<ItilVinculoDO>()
                .eq(ItilVinculoDO::getAlvo, objeto).eq(ItilVinculoDO::getAlvoId, id));
        List<ItilVOs.Vinculo> out = new ArrayList<>();
        diretos.forEach(v -> out.add(item(v.getId(), v.getAlvo(), v.getAlvoId())));
        reversos.forEach(v -> out.add(item(v.getId(), v.getObjeto(), v.getObjetoId())));
        preencher(out);
        return out;
    }

    /** Problemas e mudanças ligados a um chamado. */
    public List<ItilVOs.Vinculo> doChamado(Long chamadoId) {
        List<ItilVOs.Vinculo> out = itilVinculoMapper.selectList(new LambdaQueryWrapperX<ItilVinculoDO>()
                        .eq(ItilVinculoDO::getAlvo, ItilVOs.CHAMADO).eq(ItilVinculoDO::getAlvoId, chamadoId)).stream()
                .map(v -> item(v.getId(), v.getObjeto(), v.getObjetoId())).collect(Collectors.toCollection(ArrayList::new));
        preencher(out);
        return out;
    }

    public List<Long> chamadosVinculados(String objeto, Long id) {
        return itilVinculoMapper.selectList(new LambdaQueryWrapperX<ItilVinculoDO>().eq(ItilVinculoDO::getObjeto, objeto)
                .eq(ItilVinculoDO::getObjetoId, id).eq(ItilVinculoDO::getAlvo, ItilVOs.CHAMADO)).stream()
                .map(ItilVinculoDO::getAlvoId).toList();
    }

    public Map<Long, Integer> contarChamados(String objeto, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return itilVinculoMapper.selectList(new LambdaQueryWrapperX<ItilVinculoDO>().eq(ItilVinculoDO::getObjeto, objeto)
                        .in(ItilVinculoDO::getObjetoId, ids).eq(ItilVinculoDO::getAlvo, ItilVOs.CHAMADO)).stream()
                .collect(Collectors.groupingBy(ItilVinculoDO::getObjetoId, Collectors.summingInt(v -> 1)));
    }

    public void apagarTudo(String objeto, Long id) {
        itilAtorMapper.delete(new LambdaQueryWrapperX<ItilAtorDO>().eq(ItilAtorDO::getObjeto, objeto).eq(ItilAtorDO::getObjetoId, id));
        itilVinculoMapper.delete(new LambdaQueryWrapperX<ItilVinculoDO>().eq(ItilVinculoDO::getObjeto, objeto).eq(ItilVinculoDO::getObjetoId, id));
        itilVinculoMapper.delete(new LambdaQueryWrapperX<ItilVinculoDO>().eq(ItilVinculoDO::getAlvo, objeto).eq(ItilVinculoDO::getAlvoId, id));
    }

    private static ItilVOs.Vinculo item(Long vinculoId, String objeto, Long objetoId) {
        ItilVOs.Vinculo v = new ItilVOs.Vinculo();
        v.setId(vinculoId);
        v.setObjeto(objeto);
        v.setObjetoId(objetoId);
        return v;
    }

    /** Títulos e situações em lote; o que o escopo esconde fica sem título. */
    private void preencher(List<ItilVOs.Vinculo> itens) {
        Map<String, Set<Long>> porTipo = itens.stream().collect(Collectors.groupingBy(ItilVOs.Vinculo::getObjeto,
                Collectors.mapping(ItilVOs.Vinculo::getObjetoId, Collectors.toSet())));
        Map<Long, ChamadoDO> chamados = porTipo.containsKey(ItilVOs.CHAMADO)
                ? chamadoMapper.selectBatchIds(porTipo.get(ItilVOs.CHAMADO)).stream().collect(Collectors.toMap(ChamadoDO::getId, Function.identity()))
                : Map.of();
        Map<Long, ProblemaDO> problemas = porTipo.containsKey(ItilVOs.PROBLEMA)
                ? problemaMapper.selectBatchIds(porTipo.get(ItilVOs.PROBLEMA)).stream().collect(Collectors.toMap(ProblemaDO::getId, Function.identity()))
                : Map.of();
        Map<Long, MudancaDO> mudancas = porTipo.containsKey(ItilVOs.MUDANCA)
                ? mudancaMapper.selectBatchIds(porTipo.get(ItilVOs.MUDANCA)).stream().collect(Collectors.toMap(MudancaDO::getId, Function.identity()))
                : Map.of();
        for (ItilVOs.Vinculo v : itens) {
            switch (v.getObjeto()) {
                case ItilVOs.CHAMADO -> Optional.ofNullable(chamados.get(v.getObjetoId())).ifPresent(c -> {
                    v.setTitulo(c.getTitulo());
                    v.setStatus(c.getStatus());
                    v.setStatusRotulo(ChamadoStatus.de(c.getStatus()).getRotulo());
                });
                case ItilVOs.PROBLEMA -> Optional.ofNullable(problemas.get(v.getObjetoId())).ifPresent(p -> {
                    v.setTitulo(p.getTitulo());
                    v.setStatus(p.getStatus());
                    v.setStatusRotulo(ProblemaStatus.de(p.getStatus()).getRotulo());
                });
                default -> Optional.ofNullable(mudancas.get(v.getObjetoId())).ifPresent(m -> {
                    v.setTitulo(m.getTitulo());
                    v.setStatus(m.getStatus());
                    v.setStatusRotulo(MudancaStatus.de(m.getStatus()).getRotulo());
                    v.setInicioPrevisto(m.getInicioPrevisto());
                    v.setFimPrevisto(m.getFimPrevisto());
                });
            }
        }
        itens.sort(Comparator.comparing(ItilVOs.Vinculo::getObjeto).thenComparing(ItilVOs.Vinculo::getObjetoId));
    }

    // ------------------------------------------------------------------ conversão

    /** Preenche nomes de participantes, categoria, unidade e quem registrou, em lote. */
    public void montarComuns(String objeto, List<ItilVOs.Resp> resps) {
        if (resps.isEmpty()) {
            return;
        }
        Set<Long> ids = resps.stream().map(ItilVOs.Resp::getId).collect(Collectors.toSet());
        List<ItilAtorDO> atores = itilAtorMapper.selectList(new LambdaQueryWrapperX<ItilAtorDO>()
                .eq(ItilAtorDO::getObjeto, objeto).in(ItilAtorDO::getObjetoId, ids).orderByAsc(ItilAtorDO::getId));
        Set<Long> pessoas = new HashSet<>();
        atores.forEach(a -> pessoas.add(a.getUserId()));
        resps.forEach(r -> pessoas.add(r.getRegistradoPor()));
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        Map<Long, String> equipes = new HashMap<>();
        cadastros.listarEquipes(false).forEach(e -> equipes.put(e.getId(), e.getNome()));
        Map<Long, String> categorias = new HashMap<>();
        cadastros.listarCategorias(false, false).forEach(c -> categorias.put(c.getId(), c.getNomeCompleto()));
        Map<Long, List<ItilAtorDO>> porObjeto = atores.stream().collect(Collectors.groupingBy(ItilAtorDO::getObjetoId));
        Map<Long, Integer> totalChamados = contarChamados(objeto, ids);
        LocalDateTime agora = LocalDateTime.now();
        for (ItilVOs.Resp r : resps) {
            r.setObjeto(objeto);
            for (ItilAtorDO a : porObjeto.getOrDefault(r.getId(), List.of())) {
                ChamadoVOs.Ator v = new ChamadoVOs.Ator();
                v.setId(a.getId());
                v.setTipo(a.getTipo());
                v.setUserId(a.getUserId());
                v.setEquipeId(a.getEquipeId());
                v.setNome(a.getUserId() != null ? nome(nomes, a.getUserId()) : equipes.getOrDefault(a.getEquipeId(), "#" + a.getEquipeId()));
                switch (AtorTipo.de(a.getTipo())) {
                    case SOLICITANTE -> r.getSolicitantes().add(v);
                    case TECNICO -> r.getTecnicos().add(v);
                    default -> r.getObservadores().add(v);
                }
            }
            r.setCategoria(r.getCategoriaId() == null ? null : categorias.getOrDefault(r.getCategoriaId(), "#" + r.getCategoriaId()));
            r.setDept(nomeDept(r.getDeptId()));
            r.setRegistradoPorNome(nome(nomes, r.getRegistradoPor()));
            r.setTotalChamados(totalChamados.getOrDefault(r.getId(), 0));
            r.setAtrasado(r.getPrazo() != null && r.getSolucionadoEm() == null && r.getFechadoEm() == null && r.getPrazo().isBefore(agora));
        }
    }

    // ------------------------------------------------------------------ nomes

    public String nomePessoa(Long userId) {
        AdminUserRespDTO u = userId == null ? null : adminUserApi.getUser(userId);
        return u == null ? (userId == null ? null : "#" + userId) : u.getNickname();
    }

    public String nomeCategoria(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return cadastros.obterCategoria(id).getNomeCompleto();
        } catch (Exception e) {
            return "#" + id;
        }
    }

    public String nomeDept(Long id) {
        DeptRespDTO d = id == null ? null : deptApi.getDept(id);
        return d == null ? (id == null ? null : "#" + id) : d.getName();
    }

    public Long deptDe(Long userId) {
        AdminUserRespDTO u = adminUserApi.getUser(userId);
        return u == null ? null : u.getDeptId();
    }

    private static String nome(Map<Long, AdminUserRespDTO> m, Long id) {
        if (id == null) {
            return null;
        }
        AdminUserRespDTO u = m.get(id);
        return u == null ? "#" + id : u.getNickname();
    }

    private static String limitar(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "...";
    }

}
