package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.MudancaAprovacaoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.MudancaMapper;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.dominio.CicloChamado;
import cn.iocoder.yudao.module.itsm.dominio.CicloItil;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.dominio.Prioridade;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaTipo;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Mudanças (GLPI: Change), com o tipo ITIL (padrão, normal, emergencial), janela de execução, planos de
 * implantação e retorno e aprovação por um comitê (todos aprovam; uma recusa recusa).
 *
 * <p>A equipe técnica vê pelo escopo de unidade ({@code dept_id}). O aprovador responde em "Minhas aprovações"
 * mesmo sem ver mudanças no menu.
 */
@Slf4j
@Service
public class MudancaService {

    public static final String P_QUERY = "itsm:mudanca:query";
    public static final String P_UPDATE = "itsm:mudanca:update";
    public static final String P_DELETE = "itsm:mudanca:delete";
    private static final String OBJ = ItilVOs.MUDANCA;

    @Resource
    private MudancaMapper mudancaMapper;
    @Resource
    private MudancaAprovacaoMapper mudancaAprovacaoMapper;
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
    private AdminUserApi adminUserApi;
    @Resource
    private ItsmProperties props;

    // ------------------------------------------------------------------ consulta

    public PageResult<ItilVOs.Resp> pagina(ItilVOs.PaginaReq req, Long userId) {
        PageResult<MudancaDO> p = mudancaMapper.selectPage(req, userId, cadastros.equipesDoUsuario(userId));
        return new PageResult<>(montar(p.getList(), null), p.getTotal());
    }

    public ItilVOs.Resp obter(Long id, Long userId) {
        MudancaDO m = visivel(id);
        ItilVOs.Resp r = montar(List.of(m), userId).get(0);
        r.setConflitos(conflitos(m));
        return r;
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
        MudancaDO m = MudancaDO.builder()
                .titulo(req.getTitulo().trim())
                .descricao(HtmlSeguro.limpar(req.getDescricao()))
                .tipo(req.getTipo() != null ? req.getTipo() : MudancaTipo.NORMAL.getCodigo())
                .urgencia(req.getUrgencia()).impacto(req.getImpacto())
                .prioridade(req.getPrioridade() != null ? req.getPrioridade() : prioridade(req.getUrgencia(), req.getImpacto()))
                .categoriaId(req.getCategoriaId())
                .deptId(req.getDeptId() != null ? req.getDeptId() : apoio.deptDe(userId))
                .prazo(req.getPrazo())
                .inicioPrevisto(req.getInicioPrevisto()).fimPrevisto(req.getFimPrevisto())
                .analiseImpacto(HtmlSeguro.limpar(req.getAnaliseImpacto()))
                .planoControle(HtmlSeguro.limpar(req.getPlanoControle()))
                .planoImplantacao(HtmlSeguro.limpar(req.getPlanoImplantacao()))
                .planoRetorno(HtmlSeguro.limpar(req.getPlanoRetorno()))
                .checklist(HtmlSeguro.limpar(req.getChecklist()))
                .registradoPor(userId)
                .build();
        List<Long> tecnicos = req.getTecnicoIds().stream().filter(Objects::nonNull).distinct().toList();
        CicloItil.abrir(m, LocalDateTime.now(), !tecnicos.isEmpty());
        mudancaMapper.insert(m);
        for (Long t : tecnicos) {
            ItilVOs.AtorReq a = new ItilVOs.AtorReq();
            a.setObjetoId(m.getId());
            a.setTipo(AtorTipo.TECNICO.getCodigo());
            a.setUserId(t);
            apoio.incluirAtor(OBJ, m.getId(), a);
        }
        apoio.registrar(OBJ, m.getId(), userId, "criado", null, m.getTitulo());
        if (req.getChamadoId() != null) {
            apoio.vincular(OBJ, m.getId(), ItilVOs.CHAMADO, req.getChamadoId());
            apoio.registrar(OBJ, m.getId(), userId, "chamado+", null, "#" + req.getChamadoId());
            chamadoService.registrarSistema(req.getChamadoId(), userId, "mudanca+", null, "#" + m.getId() + " " + m.getTitulo());
        }
        return m.getId();
    }

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
        MudancaDO m = travar(req.getId());
        exigirNaoEncerrada(m);
        MudancaDO antes = MudancaDO.builder().titulo(m.getTitulo()).tipo(m.getTipo()).urgencia(m.getUrgencia())
                .impacto(m.getImpacto()).prioridade(m.getPrioridade()).categoriaId(m.getCategoriaId()).deptId(m.getDeptId())
                .prazo(m.getPrazo()).inicioPrevisto(m.getInicioPrevisto()).fimPrevisto(m.getFimPrevisto())
                .descricao(m.getDescricao()).analiseImpacto(m.getAnaliseImpacto()).planoControle(m.getPlanoControle())
                .planoImplantacao(m.getPlanoImplantacao()).planoRetorno(m.getPlanoRetorno()).checklist(m.getChecklist()).build();
        m.setTitulo(req.getTitulo().trim());
        m.setDescricao(manter(req.getDescricao(), m.getDescricao()));
        if (req.getTipo() != null) {
            m.setTipo(req.getTipo());
        }
        m.setUrgencia(req.getUrgencia());
        m.setImpacto(req.getImpacto());
        m.setPrioridade(req.getPrioridade() != null ? req.getPrioridade() : prioridade(req.getUrgencia(), req.getImpacto()));
        m.setCategoriaId(req.getCategoriaId());
        if (req.getDeptId() != null) {
            m.setDeptId(req.getDeptId());
        }
        m.setPrazo(req.getPrazo());
        m.setInicioPrevisto(req.getInicioPrevisto());
        m.setFimPrevisto(req.getFimPrevisto());
        m.setAnaliseImpacto(manter(req.getAnaliseImpacto(), m.getAnaliseImpacto()));
        m.setPlanoControle(manter(req.getPlanoControle(), m.getPlanoControle()));
        m.setPlanoImplantacao(manter(req.getPlanoImplantacao(), m.getPlanoImplantacao()));
        m.setPlanoRetorno(manter(req.getPlanoRetorno(), m.getPlanoRetorno()));
        m.setChecklist(manter(req.getChecklist(), m.getChecklist()));
        // virar "normal" depois de aceita sem aprovação não pode deixar a mudança andando sem aprovação
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        if (!CicloItil.liberada(m) && (s == MudancaStatus.ACEITA || s == MudancaStatus.EM_TESTE
                || s == MudancaStatus.EM_QUALIFICACAO)) {
            throw exception(ITIL_TRANSICAO_INVALIDA, "uma mudança normal precisa ser aprovada antes; peça aprovação ou volte para avaliação");
        }
        gravar(m);
        Long id = m.getId();
        apoio.diferenca(OBJ, id, userId, "titulo", antes.getTitulo(), m.getTitulo());
        apoio.diferenca(OBJ, id, userId, "tipo", MudancaTipo.de(antes.getTipo()).getRotulo(), MudancaTipo.de(m.getTipo()).getRotulo());
        apoio.diferenca(OBJ, id, userId, "urgencia", Prioridade.rotulo(antes.getUrgencia()), Prioridade.rotulo(m.getUrgencia()));
        apoio.diferenca(OBJ, id, userId, "impacto", Prioridade.rotulo(antes.getImpacto()), Prioridade.rotulo(m.getImpacto()));
        apoio.diferenca(OBJ, id, userId, "prioridade", Prioridade.rotulo(antes.getPrioridade()), Prioridade.rotulo(m.getPrioridade()));
        apoio.diferenca(OBJ, id, userId, "categoria", apoio.nomeCategoria(antes.getCategoriaId()), apoio.nomeCategoria(m.getCategoriaId()));
        apoio.diferenca(OBJ, id, userId, "unidade", apoio.nomeDept(antes.getDeptId()), apoio.nomeDept(m.getDeptId()));
        apoio.diferenca(OBJ, id, userId, "prazo", antes.getPrazo(), m.getPrazo());
        apoio.diferenca(OBJ, id, userId, "inicio", antes.getInicioPrevisto(), m.getInicioPrevisto());
        apoio.diferenca(OBJ, id, userId, "fim", antes.getFimPrevisto(), m.getFimPrevisto());
        textoAlterado(id, userId, "descricao", antes.getDescricao(), m.getDescricao());
        textoAlterado(id, userId, "analise", antes.getAnaliseImpacto(), m.getAnaliseImpacto());
        textoAlterado(id, userId, "controle", antes.getPlanoControle(), m.getPlanoControle());
        textoAlterado(id, userId, "implantacao", antes.getPlanoImplantacao(), m.getPlanoImplantacao());
        textoAlterado(id, userId, "retorno", antes.getPlanoRetorno(), m.getPlanoRetorno());
        textoAlterado(id, userId, "checklist", antes.getChecklist(), m.getChecklist());
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        visivel(id);
        apoio.apagarTudo(OBJ, id);
        mudancaAprovacaoMapper.delete(MudancaAprovacaoDO::getMudancaId, id);
        mudancaMapper.deleteById(id);
        log.info("[mudanca] #{} excluída por {}", id, userId);
    }

    // ------------------------------------------------------------------ participantes

    @Transactional(rollbackFor = Exception.class)
    public void adicionarAtor(ItilVOs.AtorReq req, Long userId) {
        visivel(req.getObjetoId());
        MudancaDO m = travar(req.getObjetoId());
        exigirNaoEncerrada(m);
        String nome = apoio.incluirAtor(OBJ, m.getId(), req);
        AtorTipo tipo = AtorTipo.de(req.getTipo());
        if (tipo == AtorTipo.TECNICO) {
            mudarStatus(m, userId, () -> CicloItil.tecnicoAtribuido(m));
        }
        apoio.registrar(OBJ, m.getId(), userId, tipo.name().toLowerCase() + "+", null, nome);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removerAtor(Long atorId, Long userId) {
        ItilAtorDO a = apoio.ator(OBJ, atorId);
        visivel(a.getObjetoId());
        MudancaDO m = travar(a.getObjetoId());
        exigirNaoEncerrada(m);
        String nome = apoio.removerAtor(a);
        apoio.registrar(OBJ, m.getId(), userId, AtorTipo.de(a.getTipo()).name().toLowerCase() + "-", nome, null);
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

    /** Acompanhamento, tarefa ou solução; a solução registra a aplicação da mudança. */
    @Transactional(rollbackFor = Exception.class)
    public Long adicionarInteracao(ItilVOs.InteracaoReq req, Long userId) {
        boolean solucao = InteracaoDO.SOLUCAO.equals(req.getTipo());
        if (Boolean.TRUE.equals(req.getSolucionarChamados()) && !solucao) {
            throw exception(ITIL_SEM_SOLUCAO_CHAMADOS);
        }
        visivel(req.getObjetoId());
        MudancaDO m = travar(req.getObjetoId());
        exigirNaoEncerrada(m);
        LocalDateTime agora = LocalDateTime.now();
        if (solucao) {
            // valida antes de gravar o texto: mudança normal sem aprovação não pode ser aplicada
            mudarStatus(m, userId, () -> CicloItil.aplicar(m, agora));
        }
        ItilInteracaoDO i = apoio.inserirInteracao(OBJ, m.getId(), req, userId);
        if (InteracaoDO.TAREFA.equals(req.getTipo())) {
            m.setTempoGastoSeg(m.getTempoGastoSeg() + i.getTempoGastoSeg());
            gravar(m);
        }
        if (solucao && Boolean.TRUE.equals(req.getSolucionarChamados())) {
            solucionarChamados(m, userId);
        }
        return i.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void mudarStatus(ItilVOs.StatusReq req, Long userId) {
        visivel(req.getId());
        MudancaDO m = travar(req.getId());
        mudarStatus(m, userId, () -> CicloItil.mudarStatus(m, req.getStatus()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelar(Long id, String motivo, Long userId) {
        visivel(id);
        MudancaDO m = travar(id);
        mudarStatus(m, userId, () -> CicloItil.cancelar(m, LocalDateTime.now()));
        cancelarAprovacoesPendentes(m.getId(), userId);
        if (motivo != null && !motivo.isBlank()) {
            ItilVOs.InteracaoReq r = new ItilVOs.InteracaoReq();
            r.setTipo(InteracaoDO.ACOMPANHAMENTO);
            r.setConteudo("<p>Cancelada: " + org.springframework.web.util.HtmlUtils.htmlEscape(motivo.trim()) + "</p>");
            apoio.inserirInteracao(OBJ, m.getId(), r, userId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void fechar(Long id, Long userId) {
        visivel(id);
        MudancaDO m = travar(id);
        mudarStatus(m, userId, () -> CicloItil.fechar(m, LocalDateTime.now()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reabrir(Long id, Long userId) {
        visivel(id);
        MudancaDO m = travar(id);
        mudarStatus(m, userId, () -> CicloItil.reabrir(m));
    }

    // ------------------------------------------------------------------ aprovação

    @Transactional(rollbackFor = Exception.class)
    public void pedirAprovacao(ItilVOs.PedirAprovacaoReq req, Long userId) {
        visivel(req.getMudancaId());
        MudancaDO m = travar(req.getMudancaId());
        Set<Long> aprovadores = new LinkedHashSet<>(req.getAprovadorIds());
        aprovadores.remove(null);
        adminUserApi.validateUserList(aprovadores);
        // rodada nova quando não há aprovação em curso (primeira vez, ou reaberta depois de recusada)
        int ultima = rodadaAtual(m.getId());
        int rodada = m.getValidacao() == Aprovacoes.NENHUMA ? ultima + 1 : Math.max(ultima, 1);
        Set<Long> jaPendentes = mudancaAprovacaoMapper.selectList(new LambdaQueryWrapperX<MudancaAprovacaoDO>()
                        .eq(MudancaAprovacaoDO::getMudancaId, m.getId()).eq(MudancaAprovacaoDO::getStatus, Aprovacoes.AGUARDANDO))
                .stream().map(MudancaAprovacaoDO::getAprovadorId).collect(Collectors.toSet());
        LocalDateTime agora = LocalDateTime.now();
        List<String> nomes = new ArrayList<>();
        for (Long a : aprovadores) {
            if (jaPendentes.contains(a)) {
                continue;
            }
            mudancaAprovacaoMapper.insert(MudancaAprovacaoDO.builder().mudancaId(m.getId()).aprovadorId(a).solicitadoPor(userId)
                    .rodada(rodada).status(Aprovacoes.AGUARDANDO)
                    .comentarioPedido(req.getComentario() == null || req.getComentario().isBlank() ? null : req.getComentario().trim())
                    .pedidoEm(agora).build());
            nomes.add(apoio.nomePessoa(a));
        }
        if (nomes.isEmpty()) {
            return;
        }
        mudarStatus(m, userId, () -> CicloItil.aguardarAprovacao(m));
        apoio.registrar(OBJ, m.getId(), userId, "aprovacao+", null, String.join(", ", nomes));
    }

    /** O aprovador responde; a situação global da rodada decide se a mudança foi aceita ou recusada. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void responderAprovacao(ItilVOs.ResponderAprovacaoReq req, Long userId) {
        MudancaAprovacaoDO a = mudancaAprovacaoMapper.selectById(req.getAprovacaoId());
        if (a == null) {
            throw exception(MUDANCA_APROVACAO_NAO_EXISTE);
        }
        if (!a.getAprovadorId().equals(userId)) {
            throw exception(MUDANCA_APROVACAO_DE_OUTRO);
        }
        MudancaDO m = travar(a.getMudancaId());
        if (a.getStatus() != Aprovacoes.AGUARDANDO) {
            throw exception(MUDANCA_APROVACAO_JA_RESPONDIDA);
        }
        boolean aprovar = Boolean.TRUE.equals(req.getAprovar());
        String comentario = req.getComentario() == null || req.getComentario().isBlank() ? null : req.getComentario().trim();
        a.setStatus(aprovar ? Aprovacoes.APROVADA : Aprovacoes.RECUSADA);
        a.setComentarioResposta(comentario);
        a.setRespondidoEm(LocalDateTime.now());
        mudancaAprovacaoMapper.updateById(a);
        apoio.registrar(OBJ, m.getId(), userId, "aprovacao", "Aguardando", (aprovar ? "Aprovado" : "Recusado")
                + (comentario == null ? "" : ": " + comentario));
        int global = Aprovacoes.global(mudancaAprovacaoMapper.selectList(new LambdaQueryWrapperX<MudancaAprovacaoDO>()
                        .eq(MudancaAprovacaoDO::getMudancaId, m.getId()).eq(MudancaAprovacaoDO::getRodada, a.getRodada()))
                .stream().map(MudancaAprovacaoDO::getStatus).toList());
        LocalDateTime agora = LocalDateTime.now();
        mudarStatus(m, userId, () -> CicloItil.resultadoAprovacao(m, global, agora));
        if (global == Aprovacoes.RECUSADA) {
            cancelarAprovacoesPendentes(m.getId(), null);   // uma recusa decide: as outras não precisam mais responder
        }
    }

    public List<ItilVOs.Aprovacao> aprovacoes(Long mudancaId) {
        MudancaDO m = visivel(mudancaId);
        return converterAprovacoes(mudancaAprovacaoMapper.selectList(new LambdaQueryWrapperX<MudancaAprovacaoDO>()
                .eq(MudancaAprovacaoDO::getMudancaId, m.getId()).orderByAsc(MudancaAprovacaoDO::getId)), Map.of(m.getId(), m));
    }

    /** "Minhas aprovações": o aprovador lê o pedido e os planos sem precisar ver a mudança no menu. */
    @DataPermission(enable = false)
    public List<ItilVOs.Aprovacao> minhas(Long userId, boolean somentePendentes) {
        List<MudancaAprovacaoDO> lista = mudancaAprovacaoMapper.selectList(new LambdaQueryWrapperX<MudancaAprovacaoDO>()
                .eq(MudancaAprovacaoDO::getAprovadorId, userId)
                .eqIfPresent(MudancaAprovacaoDO::getStatus, somentePendentes ? Aprovacoes.AGUARDANDO : null)
                .orderByDesc(MudancaAprovacaoDO::getId).last("LIMIT 200"));
        Set<Long> ids = lista.stream().map(MudancaAprovacaoDO::getMudancaId).collect(Collectors.toSet());
        Map<Long, MudancaDO> mudancas = ids.isEmpty() ? Map.of() : mudancaMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MudancaDO::getId, Function.identity()));
        return converterAprovacoes(lista, mudancas);
    }

    // ------------------------------------------------------------------ vínculos

    @Transactional(rollbackFor = Exception.class)
    public void vincular(ItilVOs.VinculoReq req, Long userId) {
        visivel(req.getObjetoId());
        apoio.vincular(OBJ, req.getObjetoId(), req.getAlvo(), req.getAlvoId());
        apoio.registrar(OBJ, req.getObjetoId(), userId, req.getAlvo() + "+", null, "#" + req.getAlvoId());
        if (ItilVOs.CHAMADO.equals(req.getAlvo())) {
            MudancaDO m = mudancaMapper.selectById(req.getObjetoId());
            chamadoService.registrarSistema(req.getAlvoId(), userId, "mudanca+", null, "#" + m.getId() + " " + m.getTitulo());
        } else {
            apoio.registrar(ItilVOs.PROBLEMA, req.getAlvoId(), userId, "mudanca+", null, "#" + req.getObjetoId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void desvincular(Long vinculoId, Long userId) {
        ItilVinculoDO v = apoio.vinculo(vinculoId);
        if (!OBJ.equals(v.getObjeto())) {
            throw exception(ITIL_VINCULO_NAO_EXISTE);
        }
        visivel(v.getObjetoId());
        apoio.desvincular(v);
        apoio.registrar(OBJ, v.getObjetoId(), userId, v.getAlvo() + "-", "#" + v.getAlvoId(), null);
    }

    // ------------------------------------------------------------------ internos

    private void solucionarChamados(MudancaDO m, Long userId) {
        String texto = "Resolvido pela mudança #" + m.getId() + " (" + m.getTitulo() + "): " + apoio.textoUltimaSolucao(OBJ, m.getId());
        int n = 0;
        for (Long chamadoId : apoio.chamadosVinculados(OBJ, m.getId())) {
            ChamadoDO c = chamadoMapper.selectById(chamadoId);
            if (c != null && ChamadoStatus.de(c.getStatus()).aberto()) {
                chamadoService.solucionarSistema(chamadoId, texto);
                n++;
            }
        }
        apoio.registrar(OBJ, m.getId(), userId, "chamados-solucionados", null, String.valueOf(n));
    }

    private void cancelarAprovacoesPendentes(Long mudancaId, Long userId) {
        List<MudancaAprovacaoDO> pendentes = mudancaAprovacaoMapper.selectList(new LambdaQueryWrapperX<MudancaAprovacaoDO>()
                .eq(MudancaAprovacaoDO::getMudancaId, mudancaId).eq(MudancaAprovacaoDO::getStatus, Aprovacoes.AGUARDANDO));
        // pedidos sem resposta deixam de existir (não viram recusa nem aprovação de ninguém)
        pendentes.forEach(p -> mudancaAprovacaoMapper.deleteById(p.getId()));
        if (!pendentes.isEmpty() && userId != null) {
            apoio.registrar(OBJ, mudancaId, userId, "aprovacao-", pendentes.size() + " pedido(s)", null);
        }
    }

    private int rodadaAtual(Long mudancaId) {
        return mudancaAprovacaoMapper.selectList(MudancaAprovacaoDO::getMudancaId, mudancaId).stream()
                .mapToInt(MudancaAprovacaoDO::getRodada).max().orElse(0);
    }

    private List<ItilVOs.Vinculo> conflitos(MudancaDO m) {
        if (m.getInicioPrevisto() == null || m.getFimPrevisto() == null || MudancaStatus.de(m.getStatus()).encerrada()) {
            return List.of();
        }
        return mudancaMapper.selectSobrepostas(m.getId(), m.getInicioPrevisto(), m.getFimPrevisto()).stream().map(o -> {
            ItilVOs.Vinculo v = new ItilVOs.Vinculo();
            v.setObjeto(OBJ);
            v.setObjetoId(o.getId());
            v.setTitulo(o.getTitulo());
            v.setStatus(o.getStatus());
            v.setStatusRotulo(MudancaStatus.de(o.getStatus()).getRotulo());
            v.setInicioPrevisto(o.getInicioPrevisto());
            v.setFimPrevisto(o.getFimPrevisto());
            return v;
        }).toList();
    }

    private List<ItilVOs.Aprovacao> converterAprovacoes(List<MudancaAprovacaoDO> lista, Map<Long, MudancaDO> mudancas) {
        Set<Long> pessoas = new HashSet<>();
        lista.forEach(a -> {
            pessoas.add(a.getAprovadorId());
            pessoas.add(a.getSolicitadoPor());
        });
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        return lista.stream().map(a -> {
            ItilVOs.Aprovacao v = new ItilVOs.Aprovacao();
            v.setId(a.getId());
            v.setMudancaId(a.getMudancaId());
            v.setAprovadorId(a.getAprovadorId());
            v.setAprovador(Optional.ofNullable(nomes.get(a.getAprovadorId())).map(AdminUserRespDTO::getNickname).orElse("#" + a.getAprovadorId()));
            v.setSolicitadoPorNome(a.getSolicitadoPor() == null ? "Sistema"
                    : Optional.ofNullable(nomes.get(a.getSolicitadoPor())).map(AdminUserRespDTO::getNickname).orElse("#" + a.getSolicitadoPor()));
            v.setStatus(a.getStatus());
            v.setComentarioPedido(a.getComentarioPedido());
            v.setComentarioResposta(a.getComentarioResposta());
            v.setPedidoEm(a.getPedidoEm());
            v.setRespondidoEm(a.getRespondidoEm());
            MudancaDO m = mudancas.get(a.getMudancaId());
            if (m != null) {
                v.setMudancaTitulo(m.getTitulo());
                v.setTipo(m.getTipo());
                v.setTipoRotulo(MudancaTipo.de(m.getTipo()).getRotulo());
                v.setInicioPrevisto(m.getInicioPrevisto());
                v.setFimPrevisto(m.getFimPrevisto());
                v.setAnaliseImpacto(m.getAnaliseImpacto());
                v.setPlanoRetorno(m.getPlanoRetorno());
            }
            return v;
        }).toList();
    }

    private void mudarStatus(MudancaDO m, Long userId, Runnable transicao) {
        Integer antes = m.getStatus();
        Integer validacaoAntes = m.getValidacao();
        try {
            transicao.run();
        } catch (CicloChamado.TransicaoInvalidaException e) {
            throw exception(ITIL_TRANSICAO_INVALIDA, e.getMessage());
        }
        gravar(m);
        if (!Objects.equals(antes, m.getStatus())) {
            apoio.registrar(OBJ, m.getId(), userId, "status", MudancaStatus.de(antes).getRotulo(), MudancaStatus.de(m.getStatus()).getRotulo());
        }
        if (!Objects.equals(validacaoAntes, m.getValidacao())) {
            apoio.registrar(OBJ, m.getId(), userId, "validacao", Aprovacoes.rotulo(validacaoAntes), Aprovacoes.rotulo(m.getValidacao()));
        }
    }

    private void gravar(MudancaDO m) {
        mudancaMapper.updateById(m);
        mudancaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MudancaDO>()
                .eq(MudancaDO::getId, m.getId())
                .set(MudancaDO::getCategoriaId, m.getCategoriaId())
                .set(MudancaDO::getPrazo, m.getPrazo())
                .set(MudancaDO::getInicioPrevisto, m.getInicioPrevisto())
                .set(MudancaDO::getFimPrevisto, m.getFimPrevisto())
                .set(MudancaDO::getSolucionadoEm, m.getSolucionadoEm())
                .set(MudancaDO::getFechadoEm, m.getFechadoEm()));
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
        if (req.getInicioPrevisto() != null && req.getFimPrevisto() != null && !req.getFimPrevisto().isAfter(req.getInicioPrevisto())) {
            throw exception(MUDANCA_JANELA_INVALIDA);
        }
    }

    private int prioridade(int urgencia, int impacto) {
        return Prioridade.calcular(urgencia, impacto, props.getMatrizPrioridade().isEmpty() ? null : props.getMatrizPrioridade());
    }

    private MudancaDO visivel(Long id) {
        MudancaDO m = id == null ? null : mudancaMapper.selectById(id);
        if (m == null) {
            throw exception(MUDANCA_NAO_EXISTE);
        }
        return m;
    }

    private MudancaDO travar(Long id) {
        MudancaDO m = mudancaMapper.selectByIdParaAtualizar(id);
        if (m == null) {
            throw exception(MUDANCA_NAO_EXISTE);
        }
        return m;
    }

    private static void exigirNaoEncerrada(MudancaDO m) {
        if (MudancaStatus.de(m.getStatus()).encerrada()) {
            throw exception(ITIL_ENCERRADO);
        }
    }

    private List<ItilVOs.Resp> montar(List<MudancaDO> lista, Long userId) {
        List<ItilVOs.Resp> out = new ArrayList<>();
        for (MudancaDO m : lista) {
            ItilVOs.Resp r = new ItilVOs.Resp();
            r.setId(m.getId());
            r.setTitulo(m.getTitulo());
            r.setStatus(m.getStatus());
            r.setStatusRotulo(MudancaStatus.de(m.getStatus()).getRotulo());
            r.setTipo(m.getTipo());
            r.setTipoRotulo(MudancaTipo.de(m.getTipo()).getRotulo());
            r.setUrgencia(m.getUrgencia());
            r.setImpacto(m.getImpacto());
            r.setPrioridade(m.getPrioridade());
            r.setPrioridadeRotulo(Prioridade.rotulo(m.getPrioridade()));
            r.setCategoriaId(m.getCategoriaId());
            r.setDeptId(m.getDeptId());
            r.setAbertoEm(m.getAbertoEm());
            r.setPrazo(m.getPrazo());
            r.setInicioPrevisto(m.getInicioPrevisto());
            r.setFimPrevisto(m.getFimPrevisto());
            r.setSolucionadoEm(m.getSolucionadoEm());
            r.setFechadoEm(m.getFechadoEm());
            r.setTempoGastoSeg(m.getTempoGastoSeg());
            r.setRegistradoPor(m.getRegistradoPor());
            r.setValidacao(m.getValidacao());
            r.setValidacaoRotulo(Aprovacoes.rotulo(m.getValidacao()));
            if (userId != null) {
                r.setDescricao(m.getDescricao());
                r.setAnaliseImpacto(m.getAnaliseImpacto());
                r.setPlanoControle(m.getPlanoControle());
                r.setPlanoImplantacao(m.getPlanoImplantacao());
                r.setPlanoRetorno(m.getPlanoRetorno());
                r.setChecklist(m.getChecklist());
            }
            out.add(r);
        }
        apoio.montarComuns(OBJ, out);
        if (userId != null) {
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setAcoes(acoes(lista.get(i), out.get(i), userId));
            }
        }
        return out;
    }

    private List<String> acoes(MudancaDO m, ItilVOs.Resp r, Long userId) {
        List<String> a = new ArrayList<>();
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        if (permissionApi.hasAnyPermissions(userId, P_UPDATE)) {
            if (!s.encerrada()) {
                a.addAll(List.of("editar", "atribuir", "acompanhar", "tarefa", "vincular", "status", "pedir-aprovacao"));
                if (r.getTecnicos().stream().noneMatch(t -> userId.equals(t.getUserId()))) {
                    a.add("assumir");
                }
                if (s != MudancaStatus.APLICADA && s != MudancaStatus.EM_REVISAO) {
                    a.add("cancelar");
                    if (s != MudancaStatus.EM_APROVACAO && CicloItil.liberada(m)) {
                        a.add("solucionar");
                    }
                }
            }
            if (s == MudancaStatus.APLICADA || s == MudancaStatus.EM_REVISAO) {
                a.add("fechar");
            }
            if (s.encerrada() || s == MudancaStatus.APLICADA || s == MudancaStatus.EM_REVISAO) {
                a.add("reabrir");
            }
        }
        if (permissionApi.hasAnyPermissions(userId, P_DELETE)) {
            a.add("excluir");
        }
        return a;
    }

}
