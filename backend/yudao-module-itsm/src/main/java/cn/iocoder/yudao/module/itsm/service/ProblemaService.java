package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.InteracaoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ItilAtorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ItilHistoricoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ItilInteracaoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ItilVinculoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ProblemaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ProblemaMapper;
import cn.iocoder.yudao.module.itsm.dominio.CicloChamado;
import cn.iocoder.yudao.module.itsm.dominio.CicloItil;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.dominio.Prioridade;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Problemas (GLPI: Problem): a causa comum de incidentes. Só a equipe técnica vê; o escopo de unidade do perfil
 * vale sobre {@code dept_id}, como nos chamados.
 */
@Slf4j
@Service
public class ProblemaService {

    public static final String P_QUERY = "itsm:problema:query";
    public static final String P_UPDATE = "itsm:problema:update";
    public static final String P_DELETE = "itsm:problema:delete";
    private static final String OBJ = ItilVOs.PROBLEMA;

    @Resource
    private ProblemaMapper problemaMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ItilApoio apoio;
    @Resource
    private CadastroService cadastros;
    @Resource
    private ChamadoEscopo escopo;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private ItsmProperties props;

    // ------------------------------------------------------------------ consulta

    public PageResult<ItilVOs.Resp> pagina(ItilVOs.PaginaReq req, Long userId) {
        PageResult<ProblemaDO> p = problemaMapper.selectPage(req, userId, cadastros.equipesDoUsuario(userId));
        return new PageResult<>(montar(p.getList(), null), p.getTotal());
    }

    public ItilVOs.Resp obter(Long id, Long userId) {
        return montar(List.of(visivel(id)), userId).get(0);
    }

    public List<ChamadoVOs.Interacao> linhaDoTempo(Long id) {
        visivel(id);
        return apoio.linhaDoTempo(OBJ, id);
    }

    public List<ItilHistoricoDO> historico(Long id) {
        visivel(id);
        return apoio.historico(OBJ, id);
    }

    public List<ItilVOs.Vinculo> vinculos(Long id) {
        visivel(id);
        return apoio.vinculos(OBJ, id);
    }

    // ------------------------------------------------------------------ abertura e edição

    @Transactional(rollbackFor = Exception.class)
    public Long criar(ItilVOs.SalvarReq req, Long userId) {
        validarCampos(req);
        if (req.getChamadoId() != null && !escopo.visivel(req.getChamadoId())) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        ProblemaDO p = ProblemaDO.builder()
                .titulo(req.getTitulo().trim())
                .descricao(HtmlSeguro.limpar(req.getDescricao()))
                .urgencia(req.getUrgencia()).impacto(req.getImpacto())
                .prioridade(req.getPrioridade() != null ? req.getPrioridade() : prioridade(req.getUrgencia(), req.getImpacto()))
                .categoriaId(req.getCategoriaId())
                .deptId(req.getDeptId() != null ? req.getDeptId() : apoio.deptDe(userId))
                .prazo(req.getPrazo())
                .impactos(HtmlSeguro.limpar(req.getImpactos()))
                .causas(HtmlSeguro.limpar(req.getCausas()))
                .sintomas(HtmlSeguro.limpar(req.getSintomas()))
                .registradoPor(userId)
                .build();
        List<Long> tecnicos = req.getTecnicoIds().stream().filter(Objects::nonNull).distinct().toList();
        CicloItil.abrir(p, LocalDateTime.now(), !tecnicos.isEmpty());
        problemaMapper.insert(p);
        for (Long t : tecnicos) {
            ItilVOs.AtorReq a = new ItilVOs.AtorReq();
            a.setObjetoId(p.getId());
            a.setTipo(AtorTipo.TECNICO.getCodigo());
            a.setUserId(t);
            apoio.incluirAtor(OBJ, p.getId(), a);
        }
        apoio.registrar(OBJ, p.getId(), userId, "criado", null, p.getTitulo());
        if (req.getChamadoId() != null) {
            apoio.vincular(OBJ, p.getId(), ItilVOs.CHAMADO, req.getChamadoId());
            apoio.registrar(OBJ, p.getId(), userId, "chamado+", null, "#" + req.getChamadoId());
            chamadoService.registrarSistema(req.getChamadoId(), userId, "problema+", null, "#" + p.getId() + " " + p.getTitulo());
        }
        return p.getId();
    }

    /** Problema a partir de um chamado: copia o resumo, a descrição, a categoria e as escalas e já vincula. */
    @Transactional(rollbackFor = Exception.class)
    public Long criarDoChamado(Long chamadoId, Long userId) {
        if (!escopo.visivel(chamadoId)) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        ChamadoDO c = chamadoMapper.selectById(chamadoId);
        ItilVOs.SalvarReq r = new ItilVOs.SalvarReq();
        r.setTitulo(c.getTitulo());
        r.setDescricao(c.getDescricao());
        r.setUrgencia(c.getUrgencia());
        r.setImpacto(c.getImpacto());
        r.setCategoriaId(c.getCategoriaId());
        r.setDeptId(c.getDeptId());
        r.setChamadoId(chamadoId);
        r.setTecnicoIds(List.of(userId));
        return criar(r, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizar(ItilVOs.SalvarReq req, Long userId) {
        validarCampos(req);
        visivel(req.getId());
        ProblemaDO p = travar(req.getId());
        exigirNaoFechado(p);
        ProblemaDO antes = ProblemaDO.builder().titulo(p.getTitulo()).urgencia(p.getUrgencia()).impacto(p.getImpacto())
                .prioridade(p.getPrioridade()).categoriaId(p.getCategoriaId()).deptId(p.getDeptId()).prazo(p.getPrazo())
                .descricao(p.getDescricao()).impactos(p.getImpactos()).causas(p.getCausas()).sintomas(p.getSintomas()).build();
        p.setTitulo(req.getTitulo().trim());
        p.setDescricao(manter(req.getDescricao(), p.getDescricao()));
        p.setUrgencia(req.getUrgencia());
        p.setImpacto(req.getImpacto());
        p.setPrioridade(req.getPrioridade() != null ? req.getPrioridade() : prioridade(req.getUrgencia(), req.getImpacto()));
        p.setCategoriaId(req.getCategoriaId());
        if (req.getDeptId() != null) {
            p.setDeptId(req.getDeptId());
        }
        p.setPrazo(req.getPrazo());
        p.setImpactos(manter(req.getImpactos(), p.getImpactos()));
        p.setCausas(manter(req.getCausas(), p.getCausas()));
        p.setSintomas(manter(req.getSintomas(), p.getSintomas()));
        gravar(p);
        Long id = p.getId();
        apoio.diferenca(OBJ, id, userId, "titulo", antes.getTitulo(), p.getTitulo());
        apoio.diferenca(OBJ, id, userId, "urgencia", Prioridade.rotulo(antes.getUrgencia()), Prioridade.rotulo(p.getUrgencia()));
        apoio.diferenca(OBJ, id, userId, "impacto", Prioridade.rotulo(antes.getImpacto()), Prioridade.rotulo(p.getImpacto()));
        apoio.diferenca(OBJ, id, userId, "prioridade", Prioridade.rotulo(antes.getPrioridade()), Prioridade.rotulo(p.getPrioridade()));
        apoio.diferenca(OBJ, id, userId, "categoria", apoio.nomeCategoria(antes.getCategoriaId()), apoio.nomeCategoria(p.getCategoriaId()));
        apoio.diferenca(OBJ, id, userId, "unidade", apoio.nomeDept(antes.getDeptId()), apoio.nomeDept(p.getDeptId()));
        apoio.diferenca(OBJ, id, userId, "prazo", antes.getPrazo(), p.getPrazo());
        textoAlterado(id, userId, "descricao", antes.getDescricao(), p.getDescricao());
        textoAlterado(id, userId, "impactos", antes.getImpactos(), p.getImpactos());
        textoAlterado(id, userId, "causas", antes.getCausas(), p.getCausas());
        textoAlterado(id, userId, "sintomas", antes.getSintomas(), p.getSintomas());
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        visivel(id);
        apoio.apagarTudo(OBJ, id);
        problemaMapper.deleteById(id);
        log.info("[problema] #{} excluído por {}", id, userId);
    }

    // ------------------------------------------------------------------ participantes

    @Transactional(rollbackFor = Exception.class)
    public void adicionarAtor(ItilVOs.AtorReq req, Long userId) {
        visivel(req.getObjetoId());
        ProblemaDO p = travar(req.getObjetoId());
        exigirNaoFechado(p);
        String nome = apoio.incluirAtor(OBJ, p.getId(), req);
        AtorTipo tipo = AtorTipo.de(req.getTipo());
        if (tipo == AtorTipo.TECNICO) {
            mudarStatus(p, userId, () -> CicloItil.tecnicoAtribuido(p));
        }
        apoio.registrar(OBJ, p.getId(), userId, tipo.name().toLowerCase() + "+", null, nome);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removerAtor(Long atorId, Long userId) {
        ItilAtorDO a = apoio.ator(OBJ, atorId);
        visivel(a.getObjetoId());
        ProblemaDO p = travar(a.getObjetoId());
        exigirNaoFechado(p);
        String nome = apoio.removerAtor(a);
        apoio.registrar(OBJ, p.getId(), userId, AtorTipo.de(a.getTipo()).name().toLowerCase() + "-", nome, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assumir(Long id, Long userId) {
        ItilVOs.AtorReq r = new ItilVOs.AtorReq();
        r.setObjetoId(id);
        r.setTipo(AtorTipo.TECNICO.getCodigo());
        r.setUserId(userId);
        adicionarAtor(r, userId);
    }

    // ------------------------------------------------------------------ linha do tempo e ciclo de vida

    @Transactional(rollbackFor = Exception.class)
    public Long adicionarInteracao(ItilVOs.InteracaoReq req, Long userId) {
        boolean solucao = InteracaoDO.SOLUCAO.equals(req.getTipo());
        if (Boolean.TRUE.equals(req.getSolucionarChamados()) && !solucao) {
            throw exception(ITIL_SEM_SOLUCAO_CHAMADOS);
        }
        visivel(req.getObjetoId());
        ProblemaDO p = travar(req.getObjetoId());
        exigirNaoFechado(p);
        ItilInteracaoDO i = apoio.inserirInteracao(OBJ, p.getId(), req, userId);
        LocalDateTime agora = LocalDateTime.now();
        if (InteracaoDO.TAREFA.equals(req.getTipo())) {
            p.setTempoGastoSeg(p.getTempoGastoSeg() + i.getTempoGastoSeg());
            gravar(p);
        } else if (solucao) {
            mudarStatus(p, userId, () -> CicloItil.solucionar(p, agora));
            if (Boolean.TRUE.equals(req.getSolucionarChamados())) {
                solucionarChamados(p, userId);
            }
        }
        return i.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void mudarStatus(ItilVOs.StatusReq req, Long userId) {
        visivel(req.getId());
        ProblemaDO p = travar(req.getId());
        mudarStatus(p, userId, () -> CicloItil.mudarStatus(p, req.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void fechar(Long id, Long userId) {
        visivel(id);
        ProblemaDO p = travar(id);
        mudarStatus(p, userId, () -> CicloItil.fechar(p, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reabrir(Long id, Long userId) {
        visivel(id);
        ProblemaDO p = travar(id);
        boolean temTecnico = apoio.temTecnico(OBJ, id);
        mudarStatus(p, userId, () -> CicloItil.reabrir(p, temTecnico));
    }

    // ------------------------------------------------------------------ vínculos

    @Transactional(rollbackFor = Exception.class)
    public void vincular(ItilVOs.VinculoReq req, Long userId) {
        visivel(req.getObjetoId());
        apoio.vincular(OBJ, req.getObjetoId(), req.getAlvo(), req.getAlvoId());
        apoio.registrar(OBJ, req.getObjetoId(), userId, req.getAlvo() + "+", null, "#" + req.getAlvoId());
        if (ItilVOs.CHAMADO.equals(req.getAlvo())) {
            ProblemaDO p = problemaMapper.selectById(req.getObjetoId());
            chamadoService.registrarSistema(req.getAlvoId(), userId, "problema+", null, "#" + p.getId() + " " + p.getTitulo());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void desvincular(Long vinculoId, Long userId) {
        ItilVinculoDO v = apoio.vinculo(vinculoId);
        if (!OBJ.equals(v.getObjeto()) && !OBJ.equals(v.getAlvo())) {
            throw exception(ITIL_VINCULO_NAO_EXISTE);
        }
        boolean direto = OBJ.equals(v.getObjeto());
        Long id = direto ? v.getObjetoId() : v.getAlvoId();
        visivel(id);
        apoio.desvincular(v);
        apoio.registrar(OBJ, id, userId, (direto ? v.getAlvo() : v.getObjeto()) + "-",
                "#" + (direto ? v.getAlvoId() : v.getObjetoId()), null);
    }

    // ------------------------------------------------------------------ internos

    /** Leva a solução do problema para os chamados vinculados que ainda estão abertos. */
    private void solucionarChamados(ProblemaDO p, Long userId) {
        String texto = "Solucionado pelo problema #" + p.getId() + " (" + p.getTitulo() + "): " + apoio.textoUltimaSolucao(OBJ, p.getId());
        int n = 0;
        for (Long chamadoId : apoio.chamadosVinculados(OBJ, p.getId())) {
            ChamadoDO c = chamadoMapper.selectById(chamadoId);
            if (c != null && ChamadoStatus.de(c.getStatus()).aberto()) {
                chamadoService.solucionarSistema(chamadoId, texto);
                n++;
            }
        }
        apoio.registrar(OBJ, p.getId(), userId, "chamados-solucionados", null, String.valueOf(n));
    }

    private void mudarStatus(ProblemaDO p, Long userId, Runnable transicao) {
        Integer antes = p.getStatus();
        try {
            transicao.run();
        } catch (CicloChamado.TransicaoInvalidaException e) {
            throw exception(ITIL_TRANSICAO_INVALIDA, e.getMessage());
        }
        gravar(p);
        if (!Objects.equals(antes, p.getStatus())) {
            apoio.registrar(OBJ, p.getId(), userId, "status", ProblemaStatus.de(antes).getRotulo(), ProblemaStatus.de(p.getStatus()).getRotulo());
        }
    }

    /** Grava tudo; os campos que podem voltar a vazio vão explícitos (o updateById ignora nulos). */
    private void gravar(ProblemaDO p) {
        problemaMapper.updateById(p);
        problemaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProblemaDO>()
                .eq(ProblemaDO::getId, p.getId())
                .set(ProblemaDO::getCategoriaId, p.getCategoriaId())
                .set(ProblemaDO::getPrazo, p.getPrazo())
                .set(ProblemaDO::getSolucionadoEm, p.getSolucionadoEm())
                .set(ProblemaDO::getFechadoEm, p.getFechadoEm()));
    }

    /** Texto formatado na edição: ausente (null) mantém o atual; vazio apaga. */
    private static String manter(String novo, String atual) {
        return novo == null ? atual : HtmlSeguro.limpar(novo);
    }

    private void textoAlterado(Long id, Long userId, String campo, String de, String para) {
        if (!Objects.equals(de == null ? "" : de, para == null ? "" : para)) {
            apoio.registrar(OBJ, id, userId, campo, null, "(texto alterado)");
        }
    }

    private void validarCampos(ItilVOs.SalvarReq req) {
        if (req.getCategoriaId() != null) {
            cadastros.obterCategoria(req.getCategoriaId());
        }
    }

    private int prioridade(int urgencia, int impacto) {
        return Prioridade.calcular(urgencia, impacto, props.getMatrizPrioridade().isEmpty() ? null : props.getMatrizPrioridade());
    }

    private ProblemaDO visivel(Long id) {
        // selectById passa pelo filtro de escopo de unidade: fora do escopo, "não encontrado"
        ProblemaDO p = id == null ? null : problemaMapper.selectById(id);
        if (p == null) {
            throw exception(PROBLEMA_NAO_EXISTE);
        }
        return p;
    }

    private ProblemaDO travar(Long id) {
        ProblemaDO p = problemaMapper.selectByIdParaAtualizar(id);
        if (p == null) {
            throw exception(PROBLEMA_NAO_EXISTE);
        }
        return p;
    }

    private static void exigirNaoFechado(ProblemaDO p) {
        if (p.getStatus() == ProblemaStatus.FECHADO.getCodigo()) {
            throw exception(ITIL_ENCERRADO);
        }
    }

    private List<ItilVOs.Resp> montar(List<ProblemaDO> lista, Long userId) {
        List<ItilVOs.Resp> out = new ArrayList<>();
        for (ProblemaDO p : lista) {
            ItilVOs.Resp r = new ItilVOs.Resp();
            r.setId(p.getId());
            r.setTitulo(p.getTitulo());
            r.setStatus(p.getStatus());
            r.setStatusRotulo(ProblemaStatus.de(p.getStatus()).getRotulo());
            r.setUrgencia(p.getUrgencia());
            r.setImpacto(p.getImpacto());
            r.setPrioridade(p.getPrioridade());
            r.setPrioridadeRotulo(Prioridade.rotulo(p.getPrioridade()));
            r.setCategoriaId(p.getCategoriaId());
            r.setDeptId(p.getDeptId());
            r.setAbertoEm(p.getAbertoEm());
            r.setPrazo(p.getPrazo());
            r.setSolucionadoEm(p.getSolucionadoEm());
            r.setFechadoEm(p.getFechadoEm());
            r.setTempoGastoSeg(p.getTempoGastoSeg());
            r.setRegistradoPor(p.getRegistradoPor());
            if (userId != null) {   // detalhe
                r.setDescricao(p.getDescricao());
                r.setImpactos(p.getImpactos());
                r.setCausas(p.getCausas());
                r.setSintomas(p.getSintomas());
            }
            out.add(r);
        }
        apoio.montarComuns(OBJ, out);
        if (userId != null) {
            out.forEach(r -> r.setAcoes(acoes(r, userId)));
        }
        return out;
    }

    private List<String> acoes(ItilVOs.Resp r, Long userId) {
        List<String> a = new ArrayList<>();
        ProblemaStatus s = ProblemaStatus.de(r.getStatus());
        if (permissionApi.hasAnyPermissions(userId, P_UPDATE)) {
            if (s != ProblemaStatus.FECHADO) {
                a.addAll(List.of("editar", "atribuir", "acompanhar", "tarefa", "solucionar", "vincular", "status"));
                if (r.getTecnicos().stream().noneMatch(t -> userId.equals(t.getUserId()))) {
                    a.add("assumir");
                }
            }
            if (s == ProblemaStatus.SOLUCIONADO || s == ProblemaStatus.EM_OBSERVACAO) {
                a.add("fechar");
            }
            if (!s.aberto() || s == ProblemaStatus.EM_OBSERVACAO) {
                a.add("reabrir");
            }
        }
        if (permissionApi.hasAnyPermissions(userId, P_DELETE)) {
            a.add("excluir");
        }
        return a;
    }

}
