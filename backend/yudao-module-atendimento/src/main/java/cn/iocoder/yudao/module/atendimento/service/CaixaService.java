package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.canal.CanalApi;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaAgenteDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.AgenteStatusMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaAgenteMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Atribuicao;
import cn.iocoder.yudao.module.atendimento.dominio.Webhooks;
import cn.iocoder.yudao.module.atendimento.enums.ConversaStatus;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.atendimento.service.ContatoService.vazio;

/**
 * Caixas de entrada e quem atende cada uma. O token de entrada do canal API fica só como SHA-256 e o segredo do
 * webhook, cifrado: os dois aparecem uma única vez, quando são gerados.
 */
@Service
public class CaixaService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private CaixaAgenteMapper caixaAgenteMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private AgenteStatusMapper agenteStatusMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private AtendimentoProperties props;
    @Resource
    private cn.iocoder.yudao.module.itsm.service.SlaService slaService;

    public List<AtendimentoVOs.Caixa> listar() {
        return converter(caixaMapper.selectList(new LambdaQueryWrapperX<CaixaDO>().orderByAsc(CaixaDO::getNome)));
    }

    /** Caixas que a pessoa atende (ou todas, para a supervisão): filtro da tela de conversas. */
    public List<AtendimentoVOs.Caixa> minhas(Long userId, boolean todas) {
        List<CaixaDO> lista = caixaMapper.selectList(new LambdaQueryWrapperX<CaixaDO>().orderByAsc(CaixaDO::getNome));
        if (!todas) {
            Set<Long> minhas = caixaAgenteMapper.selectList(CaixaAgenteDO::getUserId, userId).stream().map(CaixaAgenteDO::getCaixaId)
                    .collect(Collectors.toSet());
            lista = lista.stream().filter(c -> minhas.contains(c.getId())).toList();
        }
        return converter(lista);
    }

    public CaixaDO existente(Long id) {
        CaixaDO c = id == null ? null : caixaMapper.selectById(id);
        if (c == null) {
            throw exception(CAIXA_NAO_EXISTE);
        }
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(AtendimentoVOs.Caixa req) {
        CaixaDO atual = req.getId() == null ? null : existente(req.getId());
        String url = vazio(req.getWebhookUrl());
        if (url != null) {
            String motivo = Webhooks.motivoUrlProibida(url);
            if (motivo != null) {
                throw exception(CAIXA_URL_PROIBIDA, motivo);
            }
        }
        Set<Long> agentes = new LinkedHashSet<>(req.getAgenteIds());
        agentes.remove(null);
        if (!agentes.isEmpty()) {
            adminUserApi.validateUserList(agentes);
        }
        CaixaDO c = CaixaDO.builder().id(req.getId()).nome(req.getNome().trim())
                .canal(atual != null ? atual.getCanal() : req.getCanal())   // o canal não muda depois de criada
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).webhookUrl(url).saudacao(vazio(req.getSaudacao()))
                .equipeId(req.getEquipeId()).atribuicaoAutomatica(!Boolean.FALSE.equals(req.getAtribuicaoAutomatica()))
                .limitePorAgente(req.getLimitePorAgente() == null ? 0 : req.getLimitePorAgente())
                .reabrirHoras(req.getReabrirHoras() == null ? 24 : req.getReabrirHoras())
                .calendarioId(req.getCalendarioId()).mensagemForaHorario(vazio(req.getMensagemForaHorario()))
                .csatAtivo(Boolean.TRUE.equals(req.getCsatAtivo())).csatMensagem(vazio(req.getCsatMensagem()))
                .roboAtivo(Boolean.TRUE.equals(req.getRoboAtivo())).roboNome(vazio(req.getRoboNome()))
                .roboInstrucoes(vazio(req.getRoboInstrucoes())).roboMaxRespostas(req.getRoboMaxRespostas() == null ? 10 : req.getRoboMaxRespostas())
                .fluxoAtivo(Boolean.TRUE.equals(req.getFluxoAtivo())).fluxoBoasVindas(vazio(req.getFluxoBoasVindas())).build();
        if (c.getCalendarioId() != null && slaService.calendario(c.getCalendarioId()) == null) {
            throw exception(CAIXA_CALENDARIO_INVALIDO);
        }
        if (atual == null) {
            c.setConfig("{}");
            caixaMapper.insert(c);
        } else {
            caixaMapper.updateById(c);
            caixaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CaixaDO>()
                    .eq(CaixaDO::getId, c.getId()).set(CaixaDO::getWebhookUrl, url).set(CaixaDO::getSaudacao, c.getSaudacao())
                    .set(CaixaDO::getEquipeId, c.getEquipeId())
                    .set(CaixaDO::getCalendarioId, c.getCalendarioId()).set(CaixaDO::getMensagemForaHorario, c.getMensagemForaHorario())
                    .set(CaixaDO::getCsatMensagem, c.getCsatMensagem())
                    .set(CaixaDO::getRoboNome, c.getRoboNome()).set(CaixaDO::getRoboInstrucoes, c.getRoboInstrucoes())
                    .set(CaixaDO::getFluxoBoasVindas, c.getFluxoBoasVindas()));
        }
        // membros: troca pela lista enviada
        List<CaixaAgenteDO> existentes = caixaAgenteMapper.selectList(CaixaAgenteDO::getCaixaId, c.getId());
        existentes.stream().filter(a -> !agentes.contains(a.getUserId())).forEach(a -> caixaAgenteMapper.deleteById(a.getId()));
        Set<Long> ja = existentes.stream().map(CaixaAgenteDO::getUserId).collect(Collectors.toSet());
        agentes.stream().filter(u -> !ja.contains(u)).forEach(u -> caixaAgenteMapper.insert(CaixaAgenteDO.builder().caixaId(c.getId()).userId(u).build()));
        return c.getId();
    }

    /** Gera (ou troca) o token de entrada e o segredo do webhook do canal API. Mostrados só agora. */
    @Transactional(rollbackFor = Exception.class)
    public AtendimentoVOs.Credenciais gerarCredenciais(Long id, Long userId) {
        CaixaDO c = existente(id);
        if (!cifrador.configurado()) {
            throw exception(CAIXA_SEM_CHAVE_MESTRA);
        }
        String token = "coti_" + aleatorio(32);
        String segredo = aleatorio(32);
        caixaMapper.updateById(CaixaDO.builder().id(c.getId()).tokenHash(Webhooks.sha256(token))
                .webhookSegredo(cifrador.cifrar(segredo, CanalApi.CONTEXTO_SEGREDO)).build());
        auditoria.registrar("atendimento.credenciais_geradas", userId, null, Map.of("caixa", c.getId(), "nome", c.getNome()));
        AtendimentoVOs.Credenciais r = new AtendimentoVOs.Credenciais();
        r.setToken(token);
        r.setSegredoWebhook(segredo);
        String base = props.getUrlPublica() == null ? "" : props.getUrlPublica().replaceAll("/+$", "");
        r.setEnderecoEntrada(base + "/admin-api/atendimento/canal/api/mensagem");
        return r;
    }

    /** Caixa do canal API pelo token de entrada (comparação pelo SHA-256, sem guardar o token). */
    public CaixaDO peloToken(String token) {
        if (token == null || token.isBlank()) {
            throw exception(CAIXA_TOKEN_INVALIDO);
        }
        CaixaDO c = caixaMapper.selectOne(CaixaDO::getTokenHash, Webhooks.sha256(token.trim()));
        if (c == null || !Boolean.TRUE.equals(c.getAtivo()) || !"api".equals(c.getCanal())) {
            throw exception(CAIXA_TOKEN_INVALIDO);
        }
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        existente(id);
        if (conversaMapper.selectCount(ConversaDO::getCaixaId, id) > 0) {
            throw exception(CAIXA_EM_USO);
        }
        caixaAgenteMapper.delete(CaixaAgenteDO::getCaixaId, id);
        caixaMapper.deleteById(id);
    }

    private List<AtendimentoVOs.Caixa> converter(List<CaixaDO> lista) {
        Set<Long> ids = lista.stream().map(CaixaDO::getId).collect(Collectors.toSet());
        Map<Long, List<Long>> membros = ids.isEmpty() ? Map.of() : caixaAgenteMapper.selectList(new LambdaQueryWrapperX<CaixaAgenteDO>()
                .in(CaixaAgenteDO::getCaixaId, ids)).stream().collect(Collectors.groupingBy(CaixaAgenteDO::getCaixaId,
                Collectors.mapping(CaixaAgenteDO::getUserId, Collectors.toList())));
        Set<Long> pessoas = membros.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        Map<Long, Map<String, Object>> status = new HashMap<>();
        if (!pessoas.isEmpty()) {
            agenteStatusMapper.listar(pessoas).forEach(r -> status.put(((Number) r.get("user_id")).longValue(), r));
        }
        Map<Long, Long> abertas = ids.isEmpty() ? Map.of() : conversaMapper.selectList(new LambdaQueryWrapperX<ConversaDO>()
                .in(ConversaDO::getCaixaId, ids).eq(ConversaDO::getStatus, ConversaStatus.ABERTA.getCodigo())).stream()
                .collect(Collectors.groupingBy(ConversaDO::getCaixaId, Collectors.counting()));
        LocalDateTime agora = LocalDateTime.now();
        return lista.stream().map(c -> {
            AtendimentoVOs.Caixa v = new AtendimentoVOs.Caixa();
            v.setId(c.getId());
            v.setNome(c.getNome());
            v.setCanal(c.getCanal());
            v.setAtivo(c.getAtivo());
            v.setWebhookUrl(c.getWebhookUrl());
            v.setSaudacao(c.getSaudacao());
            v.setEquipeId(c.getEquipeId());
            v.setAtribuicaoAutomatica(c.getAtribuicaoAutomatica());
            v.setLimitePorAgente(c.getLimitePorAgente());
            v.setReabrirHoras(c.getReabrirHoras());
            v.setCalendarioId(c.getCalendarioId());
            v.setMensagemForaHorario(c.getMensagemForaHorario());
            v.setCsatAtivo(c.getCsatAtivo());
            v.setCsatMensagem(c.getCsatMensagem());
            v.setRoboAtivo(c.getRoboAtivo());
            v.setRoboNome(c.getRoboNome());
            v.setRoboInstrucoes(c.getRoboInstrucoes());
            v.setRoboMaxRespostas(c.getRoboMaxRespostas());
            v.setFluxoAtivo(c.getFluxoAtivo());
            v.setFluxoBoasVindas(c.getFluxoBoasVindas());
            v.setTokenDefinido(c.getTokenHash() != null);
            v.setSegredoDefinido(c.getWebhookSegredo() != null);
            v.setAbertas(abertas.getOrDefault(c.getId(), 0L));
            List<Long> m = membros.getOrDefault(c.getId(), List.of());
            v.setAgenteIds(m);
            v.setAgentes(m.stream().map(u -> {
                AtendimentoVOs.Agente a = new AtendimentoVOs.Agente();
                a.setUserId(u);
                a.setNome(Optional.ofNullable(nomes.get(u)).map(AdminUserRespDTO::getNickname).orElse("#" + u));
                Map<String, Object> s = status.get(u);
                a.setDisponibilidade(s == null ? "offline" : (String) s.get("disponibilidade"));
                a.setDisponivel(s != null && Atribuicao.disponivel((String) s.get("disponibilidade"),
                        s.get("ultimo_sinal") instanceof LocalDateTime d ? d : s.get("ultimo_sinal") instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null, agora));
                return a;
            }).toList());
            return v;
        }).toList();
    }

    private static String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        ALEATORIO.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

}
