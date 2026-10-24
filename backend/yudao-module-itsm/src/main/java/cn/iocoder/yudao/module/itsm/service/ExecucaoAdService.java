package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.ad.ExecutorAd;
import cn.iocoder.yudao.module.itsm.ad.ExecutorAdSimulado;
import cn.iocoder.yudao.module.itsm.ad.ExecutorAdSsh;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ExecucaoAdDO;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ExecucaoAdMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Operações no AD pedidas por formulário: fila, execução, resultado no chamado e a senha provisória.
 *
 * <p>Ciclo: aguardando_aprovacao → (aprovado) pronta → executando → sucesso | erro; recusada; cancelada.
 * Falha de infraestrutura (rede/SSH) volta para "pronta" com nova tentativa em 1, 5 e 15 minutos.
 * Resposta {sucesso:false} do script (ex.: usuário não existe) vira "erro" direto: repetir não adianta.
 *
 * <p>A senha provisória nunca vai para a linha do tempo nem para o log: fica cifrada (chave-mestra) e o
 * solicitante a vê uma única vez no portal; depois disso é apagada.
 */
@Slf4j
@Service
public class ExecucaoAdService {

    public static final Set<String> ACOES_COM_SENHA = Set.of("Criar", "Reset", "Reativar", "Transferir");
    private static final int[] ESPERA_MIN = {1, 5, 15};

    @Resource
    private ExecucaoAdMapper execucaoMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ItsmProperties props;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    @Lazy
    private ChamadoService chamadoService;

    private ExecutorAd executor;

    @PostConstruct
    void iniciar() {
        try {
            executor = switch (props.getAd().getModo()) {
                case "simulado" -> new ExecutorAdSimulado();
                case "ssh" -> new ExecutorAdSsh(props.getAd().getSsh());
                default -> null;
            };
        } catch (IllegalStateException e) {
            // configuração incompleta não derruba o portal: a automação fica desligada e o motivo vai para o log
            log.error("[ExecucaoAd][automação no AD desligada: {}]", e.getMessage());
            executor = null;
        }
        log.info("[ExecucaoAd][modo de execução no AD: {}]", executor == null ? "desligado" : executor.modo());
    }

    /** Usado nos testes e na tela para mostrar o modo atual. */
    public String modo() {
        return executor == null ? "desligado" : executor.modo();
    }

    // ------------------------------------------------------------------ ciclo

    public void criar(Long chamadoId, String acao, Map<String, String> dados, boolean aguardaAprovacao) {
        execucaoMapper.insert(ExecucaoAdDO.builder().chamadoId(chamadoId).acao(acao).dados(JsonUtils.toJsonString(dados))
                .status(aguardaAprovacao ? "aguardando_aprovacao" : "pronta").tentativas(0).criadoEm(LocalDateTime.now()).build());
    }

    public void liberar(Long chamadoId) {
        ExecucaoAdDO e = execucaoMapper.selectByChamadoId(chamadoId);
        if (e != null && "aguardando_aprovacao".equals(e.getStatus())) {
            execucaoMapper.updateById(ExecucaoAdDO.builder().id(e.getId()).status("pronta").build());
        }
    }

    public void cancelarPorRecusa(Long chamadoId) {
        ExecucaoAdDO e = execucaoMapper.selectByChamadoId(chamadoId);
        if (e != null && "aguardando_aprovacao".equals(e.getStatus())) {
            execucaoMapper.updateById(ExecucaoAdDO.builder().id(e.getId()).status("recusada")
                    .mensagem("Pedido recusado na aprovação").build());
        }
    }

    /** Roda o que estiver pronto (chamado pela tarefa agendada). @return quantas foram processadas */
    @DataPermission(enable = false)
    public int processarFila() {
        if (executor == null) {
            return 0;
        }
        int presas = execucaoMapper.marcarInterrompidas(Math.max(300, props.getAd().getSsh().getTempoLimiteSeg() * 3));
        if (presas > 0) {
            log.warn("[processarFila][{} execuções interrompidas marcadas como erro]", presas);
        }
        int n = 0;
        for (Long id : execucaoMapper.selectIdsProntas(20)) {
            if (execucaoMapper.reivindicar(id) == 1) {
                executar(execucaoMapper.selectById(id));
                n++;
            }
        }
        return n;
    }

    private void executar(ExecucaoAdDO e) {
        if (ACOES_COM_SENHA.contains(e.getAcao()) && !cifrador.configurado()) {
            // sem chave-mestra a senha provisória não teria onde ficar com segurança: não executa
            terminar(e, "erro", "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela a senha provisória não pode ser guardada", null, null);
            chamadoService.acompanhamentoSistema(e.getChamadoId(), null,
                    "Automação no AD não executada: o servidor não tem a chave-mestra configurada.", true);
            return;
        }
        String saida;
        try {
            saida = executor.executar(e.getAcao(), e.getDados());
        } catch (Exception ex) {
            falhaDeInfraestrutura(e, ex);
            return;
        }
        Map<String, Object> r;
        try {
            r = JsonUtils.parseObject(saida, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            r = null;
        }
        if (r == null) {
            falhaDeInfraestrutura(e, new IllegalStateException("Saída inválida do script"));
            return;
        }
        if (!Boolean.TRUE.equals(r.get("sucesso"))) {
            String erro = String.valueOf(Optional.ofNullable(r.get("erro")).orElse("Falha sem mensagem"));
            terminar(e, "erro", erro, JsonUtils.toJsonString(r), null);
            chamadoService.acompanhamentoSistema(e.getChamadoId(), null, "Automação no AD falhou: " + erro
                    + " — um técnico precisa verificar.", true);
            return;
        }
        Object senha = r.remove("senha");
        String cifrada = null;
        if (senha != null && !String.valueOf(senha).isBlank()) {
            cifrada = cifrador.cifrar(String.valueOf(senha), contexto(e.getId()));
        }
        String mensagem = String.valueOf(Optional.ofNullable(r.get("mensagem")).orElse("Operação concluída"));
        terminar(e, "sucesso", mensagem, JsonUtils.toJsonString(r), cifrada);
        chamadoService.acompanhamentoSistema(e.getChamadoId(), null, "Resultado da automação no AD: "
                + JsonUtils.toJsonString(r), true);
        chamadoService.solucionarSistema(e.getChamadoId(), mensagem + (cifrada == null ? ""
                : ". A senha provisória está disponível UMA vez em \"Meus chamados\" (botão \"Ver senha provisória\"); "
                + "no primeiro acesso será pedida a troca."));
    }

    private void falhaDeInfraestrutura(ExecucaoAdDO e, Exception ex) {
        int tentativas = e.getTentativas() == null ? 1 : e.getTentativas();
        log.warn("[executar][execução {} tentativa {}: {}]", e.getId(), tentativas, ex.getMessage());
        if (tentativas >= props.getAd().getTentativas()) {
            terminar(e, "erro", "Falha de comunicação com o AD: " + ex.getMessage(), null, null);
            chamadoService.acompanhamentoSistema(e.getChamadoId(), null, "Automação no AD falhou depois de "
                    + tentativas + " tentativas (" + ex.getMessage() + ").", true);
        } else {
            int espera = ESPERA_MIN[Math.min(tentativas - 1, ESPERA_MIN.length - 1)];
            execucaoMapper.updateById(ExecucaoAdDO.builder().id(e.getId()).status("pronta")
                    .proximaTentativa(LocalDateTime.now().plusMinutes(espera))
                    .mensagem("Tentativa " + tentativas + " falhou: " + ex.getMessage()).build());
        }
    }

    private void terminar(ExecucaoAdDO e, String status, String mensagem, String resultado, String senhaCifrada) {
        execucaoMapper.updateById(ExecucaoAdDO.builder().id(e.getId()).status(status)
                .mensagem(mensagem.length() > 2000 ? mensagem.substring(0, 2000) : mensagem)
                .resultado(resultado).senhaCifrada(senhaCifrada).executadoEm(LocalDateTime.now()).build());
    }

    // ------------------------------------------------------------------ ações manuais

    @Transactional(rollbackFor = Exception.class)
    public void repetir(Long id) {
        ExecucaoAdDO e = obter(id);
        if (!"erro".equals(e.getStatus()) && !"cancelada".equals(e.getStatus())) {
            throw exception(EXECUCAO_ESTADO_INVALIDO);
        }
        execucaoMapper.updateById(ExecucaoAdDO.builder().id(id).status("pronta").tentativas(0).build());
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancelar(Long id, String motivo) {
        ExecucaoAdDO e = obter(id);
        if ("sucesso".equals(e.getStatus()) || "executando".equals(e.getStatus())) {
            throw exception(EXECUCAO_ESTADO_INVALIDO);
        }
        execucaoMapper.updateById(ExecucaoAdDO.builder().id(id).status("cancelada")
                .mensagem(motivo == null || motivo.isBlank() ? "Cancelada por um técnico" : motivo).build());
    }

    /**
     * O solicitante vê a senha provisória uma única vez; depois ela é apagada do banco.
     * Quem pode ver é conferido pelo ChamadoService (só o solicitante).
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public String revelarSenha(Long chamadoId, Long userId) {
        ExecucaoAdDO e = execucaoMapper.selectByChamadoId(chamadoId);
        if (e == null || e.getSenhaCifrada() == null) {
            throw exception(EXECUCAO_SENHA_INDISPONIVEL);
        }
        String senha = cifrador.decifrar(e.getSenhaCifrada(), contexto(e.getId()));
        execucaoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExecucaoAdDO>()
                .eq(ExecucaoAdDO::getId, e.getId()).set(ExecucaoAdDO::getSenhaCifrada, null)
                .set(ExecucaoAdDO::getSenhaVistaEm, LocalDateTime.now()));
        auditoria.registrar("ad.senha_provisoria_vista", userId, null, Map.of("chamado", chamadoId, "acao", e.getAcao()));
        return senha;
    }

    // ------------------------------------------------------------------ consulta

    public ExecucaoAdDO doChamado(Long chamadoId) {
        return execucaoMapper.selectByChamadoId(chamadoId);
    }

    public Map<Long, ExecucaoAdDO> dosChamados(Collection<Long> ids) {
        Map<Long, ExecucaoAdDO> m = new HashMap<>();
        if (!ids.isEmpty()) {
            execucaoMapper.selectList(ExecucaoAdDO::getChamadoId, ids).forEach(e -> m.put(e.getChamadoId(), e));
        }
        return m;
    }

    /** Fila para a equipe técnica. A senha cifrada nunca sai daqui. */
    @DataPermission(enable = false)
    public PageResult<CatalogoVOs.ExecucaoResp> pagina(PageParam p, String status) {
        PageResult<ExecucaoAdDO> r = execucaoMapper.selectPage(p, new LambdaQueryWrapperX<ExecucaoAdDO>()
                .eqIfPresent(ExecucaoAdDO::getStatus, status).orderByDesc(ExecucaoAdDO::getId));
        Map<Long, String> titulos = new HashMap<>();
        if (!r.getList().isEmpty()) {
            chamadoMapper.selectBatchIds(r.getList().stream().map(ExecucaoAdDO::getChamadoId).toList())
                    .forEach(c -> titulos.put(c.getId(), c.getTitulo()));
        }
        return new PageResult<>(r.getList().stream().map(e -> {
            CatalogoVOs.ExecucaoResp v = new CatalogoVOs.ExecucaoResp();
            v.setId(e.getId());
            v.setChamadoId(e.getChamadoId());
            v.setChamadoTitulo(titulos.getOrDefault(e.getChamadoId(), "(excluído)"));
            v.setAcao(e.getAcao());
            v.setStatus(e.getStatus());
            v.setTentativas(e.getTentativas());
            v.setProximaTentativa(e.getProximaTentativa());
            v.setMensagem(e.getMensagem());
            v.setResultado(e.getResultado());
            v.setDados(e.getDados());
            v.setSenhaGuardada(e.getSenhaCifrada() != null);
            v.setSenhaVistaEm(e.getSenhaVistaEm());
            v.setCriadoEm(e.getCriadoEm());
            v.setExecutadoEm(e.getExecutadoEm());
            return v;
        }).toList(), r.getTotal());
    }

    private ExecucaoAdDO obter(Long id) {
        ExecucaoAdDO e = id == null ? null : execucaoMapper.selectById(id);
        if (e == null) {
            throw exception(EXECUCAO_NAO_EXISTE);
        }
        return e;
    }

    private static String contexto(Long execucaoId) {
        return "execucao-ad:" + execucaoId;
    }

}
