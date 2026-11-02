package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.AprovacaoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoAtorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.AprovacaoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoAtorMapper;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Aprovações de chamado (GLPI: TicketValidation). Todos os aprovadores precisam aprovar; uma recusa recusa.
 *
 * <p>Quem aprova: o gestor do solicitante (atributo manager do AD), uma pessoa achada pelo e-mail ou uma pessoa
 * fixa. O aprovador vê e responde em "Minhas aprovações", mesmo sem ser participante do chamado.
 *
 * <p>Só aprova quem tem a permissão {@link #PERMISSAO_APROVAR} (papel "Aprovador"): sem isso, bastaria o solicitante
 * digitar o e-mail de um colega no formulário para ele aprovar o próprio reset de senha ou acesso.
 */
@Service
public class AprovacaoService {

    public static final String PERMISSAO_APROVAR = "itsm:aprovacao:aprovar";

    @Resource
    private AprovacaoMapper aprovacaoMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private VinculoAdMapper vinculoAdMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    @Lazy
    private ChamadoService chamadoService;
    @Resource
    @Lazy
    private ExecucaoAdService execucaoAdService;

    // ------------------------------------------------------------------ quem aprova

    /** Gestor do usuário pelo AD (manager → conta vinculada no portal), ou vazio. */
    @DataPermission(enable = false)
    public Optional<Long> gestorDe(Long userId) {
        VinculoAdDO v = vinculoAdMapper.selectByUserId(userId);
        if (v == null || v.getGestorDn() == null) {
            return Optional.empty();
        }
        VinculoAdDO gestor = vinculoAdMapper.selectOne(VinculoAdDO::getDn, v.getGestorDn());
        return gestor == null ? Optional.empty() : Optional.of(gestor.getUserId()).filter(this::ativo);
    }

    /** Pessoa ativa com este e-mail, ou vazio. */
    @DataPermission(enable = false)
    public Optional<Long> usuarioPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        AdminUserDO u = adminUserMapper.selectOne(new LambdaQueryWrapperX<AdminUserDO>()
                .apply("lower(email) = lower({0})", email.trim()).last("LIMIT 1"));
        return u == null ? Optional.empty() : Optional.of(u.getId()).filter(this::ativo);
    }

    private boolean ativo(Long userId) {
        AdminUserDO u = adminUserMapper.selectById(userId);
        return u != null && CommonStatusEnum.isEnable(u.getStatus());
    }

    /** Pessoa ativa com o papel Aprovador (ou super administrador). */
    @DataPermission(enable = false)
    public boolean podeAprovar(Long userId) {
        return userId != null && ativo(userId) && permissionApi.hasAnyPermissions(userId, PERMISSAO_APROVAR);
    }

    /** Aprovadores ativos cujo nome ou login contém o trecho, para os campos de escolha (no máximo 30). */
    @DataPermission(enable = false)
    public List<AdminUserDO> buscarAprovadores(String trecho) {
        Set<Long> ids = permissionApi.getUserIdListByPermission(PERMISSAO_APROVAR);
        if (ids.isEmpty()) {
            return List.of();
        }
        String t = trecho == null ? "" : trecho.trim();
        String padrao = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .in(AdminUserDO::getId, ids)
                .eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                .and(w -> w.apply("nickname ILIKE {0}", padrao).or().apply("username ILIKE {0}", padrao))
                .orderByAsc(AdminUserDO::getNickname)
                .last("LIMIT 30"));
    }

    // ------------------------------------------------------------------ pedir e responder

    /**
     * Pede aprovação. Ignora quem já tem pedido pendente neste chamado.
     *
     * @param solicitadoPor null = sistema (regra ou formulário)
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void solicitar(Long chamadoId, Collection<Long> aprovadores, String comentario, Long solicitadoPor) {
        if (aprovadores.isEmpty()) {
            throw exception(APROVACAO_SEM_APROVADOR);
        }
        ChamadoDO c = chamadoMapper.selectByIdParaAtualizar(chamadoId);
        if (c == null) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        LocalDateTime agora = LocalDateTime.now();
        List<AprovacaoDO> existentes = aprovacaoMapper.selectList(AprovacaoDO::getChamadoId, chamadoId);
        for (Long a : new LinkedHashSet<>(aprovadores)) {
            if (!ativo(a)) {
                throw exception(APROVACAO_APROVADOR_INVALIDO);
            }
            if (!podeAprovar(a)) {
                throw exception(APROVACAO_NAO_AUTORIZADO, nome(a));
            }
            boolean pendente = existentes.stream().anyMatch(x -> x.getAprovadorId().equals(a)
                    && x.getStatus() == Aprovacoes.AGUARDANDO);
            if (!pendente) {
                aprovacaoMapper.insert(AprovacaoDO.builder().chamadoId(chamadoId).aprovadorId(a).solicitadoPor(solicitadoPor)
                        .status(Aprovacoes.AGUARDANDO).comentarioPedido(comentario).pedidoEm(agora).build());
                chamadoService.registrarSistema(chamadoId, solicitadoPor, "aprovacao+", null, nome(a));
            }
        }
        atualizarGlobal(c);
        chamadoService.notificar(chamadoId, "aprovacao-solicitada", solicitadoPor);
    }

    /** O aprovador responde. Ao fechar a situação global, libera ou cancela a operação no AD. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void responder(Long aprovacaoId, boolean aprovar, String comentario, Long userId) {
        AprovacaoDO a = aprovacaoMapper.selectById(aprovacaoId);
        if (a == null || !a.getAprovadorId().equals(userId)) {
            throw exception(APROVACAO_NAO_EXISTE);
        }
        ChamadoDO c = chamadoMapper.selectByIdParaAtualizar(a.getChamadoId());
        if (a.getStatus() != Aprovacoes.AGUARDANDO) {
            throw exception(APROVACAO_JA_RESPONDIDA);
        }
        // o papel pode ter sido retirado depois do pedido
        if (!podeAprovar(userId)) {
            throw exception(APROVACAO_SEM_PERMISSAO);
        }
        a.setStatus(aprovar ? Aprovacoes.APROVADA : Aprovacoes.RECUSADA);
        a.setComentarioResposta(comentario == null ? null : comentario.trim());
        a.setRespondidoEm(LocalDateTime.now());
        aprovacaoMapper.updateById(a);
        chamadoService.registrarSistema(c.getId(), userId, "aprovacao", "Aguardando",
                aprovar ? "Aprovado" : "Recusado");
        chamadoService.acompanhamentoSistema(c.getId(), userId, (aprovar ? "Aprovado" : "Recusado")
                + (comentario == null || comentario.isBlank() ? "." : ": " + comentario.trim()), false);
        int global = atualizarGlobal(c);
        if (global == Aprovacoes.APROVADA) {
            execucaoAdService.liberar(c.getId());
        } else if (global == Aprovacoes.RECUSADA) {
            execucaoAdService.cancelarPorRecusa(c.getId());
            chamadoService.encerrarPorRecusa(c.getId(), comentario);
        }
        chamadoService.notificar(c.getId(), aprovar ? "aprovado" : "recusado", userId);
    }

    private int atualizarGlobal(ChamadoDO c) {
        int global = Aprovacoes.global(aprovacaoMapper.selectList(AprovacaoDO::getChamadoId, c.getId()).stream()
                .map(AprovacaoDO::getStatus).toList());
        if (!Objects.equals(c.getValidacao(), global)) {
            chamadoMapper.updateById(ChamadoDO.builder().id(c.getId()).validacao(global).build());
            c.setValidacao(global);
        }
        return global;
    }

    // ------------------------------------------------------------------ consulta

    public List<AprovacaoDO> doChamado(Long chamadoId) {
        return aprovacaoMapper.selectList(new LambdaQueryWrapperX<AprovacaoDO>().eq(AprovacaoDO::getChamadoId, chamadoId)
                .orderByAsc(AprovacaoDO::getId));
    }

    public List<AprovacaoDO> minhas(Long userId, boolean somentePendentes) {
        return aprovacaoMapper.selectList(new LambdaQueryWrapperX<AprovacaoDO>().eq(AprovacaoDO::getAprovadorId, userId)
                .eqIfPresent(AprovacaoDO::getStatus, somentePendentes ? Aprovacoes.AGUARDANDO : null)
                .orderByDesc(AprovacaoDO::getId).last("LIMIT 200"));
    }

    /**
     * Converte para a tela. Com a descrição: lista "Minhas aprovações" (o aprovador precisa ler o pedido, mesmo sem
     * participar do chamado).
     */
    @DataPermission(enable = false)
    public List<CatalogoVOs.AprovacaoResp> converter(List<AprovacaoDO> lista, boolean comDescricao) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Set<Long> chamadoIds = lista.stream().map(AprovacaoDO::getChamadoId).collect(java.util.stream.Collectors.toSet());
        Map<Long, ChamadoDO> chamados = new HashMap<>();
        chamadoMapper.selectBatchIds(chamadoIds).forEach(c -> chamados.put(c.getId(), c));
        Map<Long, Long> solicitantes = new HashMap<>();
        chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().in(ChamadoAtorDO::getChamadoId, chamadoIds)
                        .eq(ChamadoAtorDO::getTipo, AtorTipo.SOLICITANTE.getCodigo()).isNotNull(ChamadoAtorDO::getUserId)
                        .orderByAsc(ChamadoAtorDO::getId))
                .forEach(a -> solicitantes.putIfAbsent(a.getChamadoId(), a.getUserId()));
        Set<Long> pessoas = new HashSet<>(solicitantes.values());
        lista.forEach(a -> {
            pessoas.add(a.getAprovadorId());
            pessoas.add(a.getSolicitadoPor());
        });
        pessoas.remove(null);
        Map<Long, String> nomes = new HashMap<>();
        adminUserMapper.selectBatchIds(pessoas).forEach(u -> nomes.put(u.getId(), u.getNickname()));
        return lista.stream().map(a -> {
            CatalogoVOs.AprovacaoResp r = new CatalogoVOs.AprovacaoResp();
            ChamadoDO c = chamados.get(a.getChamadoId());
            r.setId(a.getId());
            r.setChamadoId(a.getChamadoId());
            r.setChamadoTitulo(c == null ? "(excluído)" : c.getTitulo());
            r.setChamadoDescricao(comDescricao && c != null ? c.getDescricao() : null);
            r.setSolicitante(nomes.get(solicitantes.get(a.getChamadoId())));
            r.setAprovadorId(a.getAprovadorId());
            r.setAprovador(nomes.getOrDefault(a.getAprovadorId(), "#" + a.getAprovadorId()));
            r.setSolicitadoPor(a.getSolicitadoPor() == null ? "Sistema" : nomes.get(a.getSolicitadoPor()));
            r.setStatus(a.getStatus());
            r.setStatusRotulo(switch (a.getStatus()) {
                case Aprovacoes.APROVADA -> "Aprovado";
                case Aprovacoes.RECUSADA -> "Recusado";
                default -> "Aguardando";
            });
            r.setComentarioPedido(a.getComentarioPedido());
            r.setComentarioResposta(a.getComentarioResposta());
            r.setPedidoEm(a.getPedidoEm());
            r.setRespondidoEm(a.getRespondidoEm());
            return r;
        }).toList();
    }

    /** Nome para mensagens; "#id" se a pessoa não existe mais. */
    @DataPermission(enable = false)
    public String nome(Long userId) {
        AdminUserDO u = adminUserMapper.selectById(userId);
        return u == null ? "#" + userId : u.getNickname();
    }

}
