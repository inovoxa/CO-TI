package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.notify.NotifySendService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Sino do portal: avisa quem está envolvido no chamado. Independe do e-mail e usa os mesmos eventos dele. Andamento privado
 * só vai para os técnicos, e quem agiu não recebe o próprio aviso.
 */
@Slf4j
@Service
public class SinoChamadoService {

    public static final String TECNICO = "tecnico";
    public static final String USUARIO = "usuario";
    public static final String APROVADOR = "aprovador";

    /** Ação do chamado → modelo de aviso. Outras ações não avisam. */
    private static final Map<String, String> MODELOS = Map.ofEntries(
            Map.entry("criado", "criado"), Map.entry("ator", "atribuido"),
            Map.entry(InteracaoDO.ACOMPANHAMENTO, "acompanhamento"),
            Map.entry(InteracaoDO.SOLUCAO, "solucao"), Map.entry("solucionado", "solucao"),
            Map.entry("fechado", "fechado"), Map.entry("solucao-aprovada", "fechado"),
            Map.entry("reaberto", "reaberto"), Map.entry("solucao-recusada", "reaberto"),
            Map.entry("pendente", "pendente"), Map.entry("aprovacao-solicitada", "aprovacao"),
            Map.entry("aprovado", "aprovado"), Map.entry("recusado", "recusado"), Map.entry("escalonado", "escalonado"));

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private EquipeMembroMapper equipeMembroMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private AprovacaoMapper aprovacaoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private NotifySendService notifySendService;
    @Resource
    private WebSocketSenderApi webSocketSenderApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void aoMudarChamado(ChamadoEvento e) {
        String modelo = MODELOS.get(e.acao());
        if (modelo == null) {
            return;
        }
        try {
            avisar(e, modelo);
        } catch (Exception ex) {
            // aviso não pode desfazer nada no chamado; fica no log
            log.error("[aoMudarChamado][chamado {} ação {}]", e.chamadoId(), e.acao(), ex);
        }
    }

    private void avisar(ChamadoEvento e, String modelo) {
        ChamadoDO c = chamadoMapper.selectById(e.chamadoId());
        if (c == null) {
            return;
        }
        Map<Long, String> destinos = destinos(c, e, modelo);
        if (e.userId() != null) {
            destinos.remove(e.userId());
        }
        enviar(c, modelo, destinos, Map.of());
    }

    private void enviar(ChamadoDO c, String modelo, Map<Long, String> destinos, Map<String, Object> extras) {
        if (destinos.isEmpty()) {
            return;
        }
        Set<Long> ativos = new HashSet<>();
        adminUserMapper.selectByIds(destinos.keySet()).stream().filter(u -> CommonStatusEnum.isEnable(u.getStatus()))
                .map(AdminUserDO::getId).forEach(ativos::add);
        String titulo = c.getTitulo().length() > 90 ? c.getTitulo().substring(0, 89) + "…" : c.getTitulo();
        destinos.forEach((id, papel) -> {
            if (!ativos.contains(id)) {
                return;
            }
            Map<String, Object> params = new HashMap<>();
            params.put("chamado", c.getId());
            params.put("titulo", titulo);
            params.put("papel", papel);
            params.putAll(extras);
            notifySendService.sendSingleNotifyToAdmin(id, "coti_chamado_" + modelo, params);
            try {
                webSocketSenderApi.sendObject(UserTypeEnum.ADMIN.getValue(), id, "sino", Map.of("chamado", c.getId()));
            } catch (Exception ex) {
                // o sino também consulta de tempos em tempos
                log.warn("[avisar][tempo real para {}: {}]", id, ex.getMessage());
            }
        });
    }

    /** Prazo perto de vencer: para os técnicos e as equipes do chamado. */
    public void avisarSla(ChamadoDO c, String modelo, LocalDateTime prazo) {
        Map<Long, String> d = new LinkedHashMap<>();
        tecnicos(chamadoAtorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId()), d);
        enviar(c, modelo, d, Map.of("hora", prazo.format(HORA)));
    }

    /** Pessoa → papel, que define para onde o aviso leva. Técnico prevalece, para abrir a visão completa. */
    private Map<Long, String> destinos(ChamadoDO c, ChamadoEvento e, String modelo) {
        List<ChamadoAtorDO> atores = chamadoAtorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId());
        Map<Long, String> d = new LinkedHashMap<>();
        switch (modelo) {
            case "atribuido" -> {
                // só quem acabou de entrar como técnico
                ChamadoAtorDO novo = e.refId() == null ? null : chamadoAtorMapper.selectById(e.refId());
                if (novo != null && novo.getTipo() == AtorTipo.TECNICO.getCodigo()) {
                    pessoas(novo).forEach(p -> d.put(p, TECNICO));
                }
            }
            case "criado", "escalonado" -> tecnicos(atores, d);
            case "aprovacao" -> aprovacaoMapper.selectList(new LambdaQueryWrapperX<AprovacaoDO>().eq(AprovacaoDO::getChamadoId, c.getId())
                    .eq(AprovacaoDO::getStatus, Aprovacoes.AGUARDANDO)).forEach(a -> d.put(a.getAprovadorId(), APROVADOR));
            case "acompanhamento" -> {
                InteracaoDO i = e.refId() == null ? null : interacaoMapper.selectById(e.refId());
                if (i == null || !Boolean.TRUE.equals(i.getPrivado())) {
                    usuarios(atores, d);
                }
                tecnicos(atores, d);
            }
            default -> {
                usuarios(atores, d);
                tecnicos(atores, d);
            }
        }
        return d;
    }

    private void tecnicos(List<ChamadoAtorDO> atores, Map<Long, String> d) {
        atores.stream().filter(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo()).forEach(a -> pessoas(a).forEach(p -> d.put(p, TECNICO)));
    }

    private static void usuarios(List<ChamadoAtorDO> atores, Map<Long, String> d) {
        atores.stream().filter(a -> a.getTipo() == AtorTipo.SOLICITANTE.getCodigo() || a.getTipo() == AtorTipo.OBSERVADOR.getCodigo())
                .filter(a -> a.getUserId() != null && Boolean.TRUE.equals(a.getNotificar()))
                .forEach(a -> d.put(a.getUserId(), USUARIO));
    }

    private List<Long> pessoas(ChamadoAtorDO a) {
        if (a.getUserId() != null) {
            return List.of(a.getUserId());
        }
        if (a.getEquipeId() != null) {
            return equipeMembroMapper.selectList(EquipeMembroDO::getEquipeId, a.getEquipeId()).stream().map(EquipeMembroDO::getUserId).toList();
        }
        return List.of();
    }

}
