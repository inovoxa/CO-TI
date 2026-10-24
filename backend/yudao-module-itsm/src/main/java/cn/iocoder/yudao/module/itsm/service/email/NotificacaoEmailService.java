package cn.iocoder.yudao.module.itsm.service.email;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.dominio.Email;
import cn.iocoder.yudao.module.itsm.dominio.Prioridade;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoEvento;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
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
import java.util.stream.Collectors;

/**
 * Notificações por e-mail dos chamados (GLPI: NotificationEventAbstract + NotificationTargetTicket).
 *
 * <p>Roda depois que a mudança no chamado foi confirmada e grava a mensagem na fila (itsm.email_saida); o envio é
 * da tarefa agendada. Quem fez a ação não recebe o aviso dela. Acompanhamento privado só vai para os técnicos.
 */
@Slf4j
@Service
public class NotificacaoEmailService {

    /** Ação do chamado → evento de e-mail. Ações fora daqui não geram e-mail. */
    static final Map<String, String> EVENTOS = Map.ofEntries(
            Map.entry("criado", "criado"), Map.entry("ator", "atribuido"),
            Map.entry(InteracaoDO.ACOMPANHAMENTO, "acompanhamento"),
            Map.entry(InteracaoDO.SOLUCAO, "solucao"), Map.entry("solucionado", "solucao"),
            Map.entry("fechado", "fechado"), Map.entry("solucao-aprovada", "fechado"),
            Map.entry("reaberto", "reaberto"), Map.entry("solucao-recusada", "reaberto"),
            Map.entry("pendente", "pendente"), Map.entry("aprovacao-solicitada", "aprovacao"),
            Map.entry("aprovado", "aprovado"), Map.entry("recusado", "recusado"), Map.entry("escalonado", "escalonado"));
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Resource
    private EmailConfigMapper emailConfigMapper;
    @Resource
    private EmailEventoMapper emailEventoMapper;
    @Resource
    private EmailSaidaMapper emailSaidaMapper;
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
    private CategoriaMapper categoriaMapper;
    @Resource
    private AdminUserMapper adminUserMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void aoMudarChamado(ChamadoEvento e) {
        String evento = EVENTOS.get(e.acao());
        if (evento == null) {
            return;
        }
        try {
            gerar(e, evento);
        } catch (Exception ex) {
            // aviso não pode desfazer nada no chamado; fica no log
            log.error("[aoMudarChamado][chamado {} ação {}]", e.chamadoId(), e.acao(), ex);
        }
    }

    void gerar(ChamadoEvento e, String evento) {
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAtivo()) || cfg.getRemetenteEmail() == null) {
            return;
        }
        EmailEventoDO modelo = emailEventoMapper.selectById(evento);
        if (modelo == null || !Boolean.TRUE.equals(modelo.getAtivo())) {
            return;
        }
        ChamadoDO c = chamadoMapper.selectById(e.chamadoId());
        if (c == null) {
            return;
        }
        List<ChamadoAtorDO> atores = chamadoAtorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId());
        InteracaoDO interacao = e.refId() != null && !"ator".equals(e.acao()) ? interacaoMapper.selectById(e.refId()) : null;
        boolean privado = interacao != null && Boolean.TRUE.equals(interacao.getPrivado());

        // destinatários: e-mail → "tecnico" ou "usuario" (define o link e o que pode ver)
        Map<String, String> para = new LinkedHashMap<>();
        Set<Long> pessoasUsuario = new LinkedHashSet<>();
        Set<String> enderecosUsuario = new LinkedHashSet<>();
        if (!privado) {
            for (ChamadoAtorDO a : atores) {
                boolean sol = a.getTipo() == AtorTipo.SOLICITANTE.getCodigo() && Boolean.TRUE.equals(modelo.getParaSolicitante());
                boolean obs = a.getTipo() == AtorTipo.OBSERVADOR.getCodigo() && Boolean.TRUE.equals(modelo.getParaObservador());
                if ((sol || obs) && Boolean.TRUE.equals(a.getNotificar())) {
                    if (a.getUserId() != null) {
                        pessoasUsuario.add(a.getUserId());
                    } else if (a.getEmail() != null) {
                        enderecosUsuario.add(a.getEmail().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        Set<Long> tecnicos = new LinkedHashSet<>();
        if ("atribuido".equals(evento)) {
            // só quem acabou de entrar como técnico
            ChamadoAtorDO novo = e.refId() == null ? null : chamadoAtorMapper.selectById(e.refId());
            if (novo == null || novo.getTipo() != AtorTipo.TECNICO.getCodigo()) {
                return;
            }
            tecnicos.addAll(pessoasDoAtor(novo));
        } else if (Boolean.TRUE.equals(modelo.getParaTecnico())) {
            atores.stream().filter(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo()).forEach(a -> tecnicos.addAll(pessoasDoAtor(a)));
        }
        Set<Long> aprovadores = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(modelo.getParaAprovador())) {
            aprovacaoMapper.selectList(new LambdaQueryWrapperX<AprovacaoDO>().eq(AprovacaoDO::getChamadoId, c.getId())
                    .eq(AprovacaoDO::getStatus, Aprovacoes.AGUARDANDO)).forEach(a -> aprovadores.add(a.getAprovadorId()));
        }
        Set<Long> todos = new HashSet<>(pessoasUsuario);
        todos.addAll(tecnicos);
        todos.addAll(aprovadores);
        Map<Long, AdminUserDO> usuarios = new HashMap<>();
        if (!todos.isEmpty()) {
            adminUserMapper.selectBatchIds(todos).stream().filter(u -> CommonStatusEnum.isEnable(u.getStatus()))
                    .forEach(u -> usuarios.put(u.getId(), u));
        }
        // quem agiu não recebe o próprio aviso
        if (e.userId() != null) {
            usuarios.remove(e.userId());
        }
        tecnicos.forEach(id -> adicionar(para, usuarios.get(id), "tecnico"));
        aprovadores.forEach(id -> adicionar(para, usuarios.get(id), "usuario"));
        pessoasUsuario.forEach(id -> adicionar(para, usuarios.get(id), "usuario"));
        enderecosUsuario.forEach(m -> para.putIfAbsent(m, "usuario"));
        if (para.isEmpty()) {
            return;
        }

        Map<String, String> v = valores(c, atores, interacao, e.userId(), cfg);
        String dominio = dominio(cfg.getRemetenteEmail());
        LocalDateTime agora = LocalDateTime.now();
        for (Map.Entry<String, String> d : para.entrySet()) {
            Map<String, String> vv = new HashMap<>(v);
            vv.put("chamado.link", "tecnico".equals(d.getValue()) ? vv.get("link.tecnico") : vv.get("link.usuario"));
            String corpo = absolutos(envelope(Email.preencher(modelo.getCorpo(), vv), cfg, c.getId()), vv.get("portal"));
            emailSaidaMapper.insert(EmailSaidaDO.builder().chamadoId(c.getId()).evento(evento).para(d.getKey())
                    .assunto(Email.preencherAssunto(modelo.getAssunto(), vv)).corpo(corpo)
                    .messageId("<coti." + c.getId() + "." + UUID.randomUUID().toString().replace("-", "") + "@" + dominio + ">")
                    .status("pendente").tentativas(0).criadoEm(agora).build());
        }
    }

    private List<Long> pessoasDoAtor(ChamadoAtorDO a) {
        if (a.getUserId() != null) {
            return List.of(a.getUserId());
        }
        if (a.getEquipeId() != null) {
            return equipeMembroMapper.selectList(EquipeMembroDO::getEquipeId, a.getEquipeId()).stream().map(EquipeMembroDO::getUserId).toList();
        }
        return List.of();
    }

    private static void adicionar(Map<String, String> para, AdminUserDO u, String papel) {
        if (u != null && u.getEmail() != null && u.getEmail().contains("@")) {
            para.putIfAbsent(u.getEmail().trim().toLowerCase(Locale.ROOT), papel);
        }
    }

    private Map<String, String> valores(ChamadoDO c, List<ChamadoAtorDO> atores, InteracaoDO interacao, Long autorId,
                                        EmailConfigDO cfg) {
        String portal = cfg.getUrlPortal() == null ? "" : cfg.getUrlPortal().replaceAll("/+$", "");
        Map<String, String> v = new HashMap<>();
        v.put("portal", portal);
        v.put("chamado.id", String.valueOf(c.getId()));
        v.put("chamado.titulo", c.getTitulo());
        v.put("chamado.status", ChamadoStatus.de(c.getStatus()).getRotulo());
        v.put("chamado.prioridade", Prioridade.rotulo(c.getPrioridade()));
        CategoriaDO cat = c.getCategoriaId() == null ? null : categoriaMapper.selectById(c.getCategoriaId());
        v.put("chamado.categoria", cat == null ? "—" : cat.getNomeCompleto());
        v.put("chamado.prazo", c.getPrazoSolucao() == null ? "—" : DATA.format(c.getPrazoSolucao()));
        v.put("chamado.descricao", c.getDescricao() == null ? "" : c.getDescricao()); // já saneada
        List<Long> solIds = atores.stream().filter(a -> a.getTipo() == AtorTipo.SOLICITANTE.getCodigo() && a.getUserId() != null)
                .map(ChamadoAtorDO::getUserId).toList();
        List<String> nomes = new ArrayList<>(solIds.isEmpty() ? List.of() : adminUserMapper.selectBatchIds(solIds).stream()
                .map(AdminUserDO::getNickname).toList());
        atores.stream().filter(a -> a.getTipo() == AtorTipo.SOLICITANTE.getCodigo() && a.getUserId() == null && a.getEmail() != null)
                .forEach(a -> nomes.add(a.getEmail()));
        v.put("chamado.solicitante", String.join(", ", nomes));
        v.put("link.usuario", portal + "/meus-chamados?chamado=" + c.getId());
        v.put("link.tecnico", portal + "/servicedesk/chamados?id=" + c.getId());
        AdminUserDO autor = autorId == null ? null : adminUserMapper.selectById(autorId);
        v.put("autor", autor == null ? "Sistema" : autor.getNickname());
        v.put("conteudo", interacao == null || interacao.getConteudo() == null ? "" : interacao.getConteudo()); // já saneado
        return v;
    }

    /**
     * Aviso fora do ciclo do chamado (aprovação de mudança, contrato ou licença perto do prazo). Quem chama decide os
     * destinatários; o modelo vem de itsm.email_evento. Grava na fila dentro da transação de quem chama: se ela
     * desfizer, o aviso some junto.
     *
     * @param valores variáveis do modelo, em texto puro (o modelo escapa)
     * @param linkRelativo caminho no portal, ex.: "/aprovacoes"
     * @return quantas mensagens entraram na fila
     */
    @DataPermission(enable = false)
    public int avisar(String evento, Collection<Long> pessoas, Map<String, String> valores, String linkRelativo) {
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAtivo()) || cfg.getRemetenteEmail() == null || pessoas.isEmpty()) {
            return 0;
        }
        EmailEventoDO modelo = emailEventoMapper.selectById(evento);
        if (modelo == null || !Boolean.TRUE.equals(modelo.getAtivo())) {
            return 0;
        }
        Map<String, String> para = new LinkedHashMap<>();
        adminUserMapper.selectBatchIds(new HashSet<>(pessoas)).stream().filter(u -> CommonStatusEnum.isEnable(u.getStatus()))
                .forEach(u -> adicionar(para, u, "tecnico"));
        if (para.isEmpty()) {
            return 0;
        }
        String portal = cfg.getUrlPortal() == null ? "" : cfg.getUrlPortal().replaceAll("/+$", "");
        Map<String, String> v = new HashMap<>(valores);
        v.put("portal", portal);
        v.put("link", portal + linkRelativo);
        String corpo = absolutos("<div style=\"font-family:Arial,sans-serif;font-size:14px\">" + Email.preencher(modelo.getCorpo(), v)
                + (cfg.getAssinatura() == null ? "" : "<div style=\"margin-top:16px;color:#666\">" + cfg.getAssinatura() + "</div>")
                + "<p style=\"margin-top:20px;color:#9a9a9a;font-size:11px\">Aviso automático do CO-TI. Não responda este e-mail.</p></div>", portal);
        String assunto = Email.preencherAssunto(modelo.getAssunto(), v);
        String dominio = dominio(cfg.getRemetenteEmail());
        LocalDateTime agora = LocalDateTime.now();
        for (String endereco : para.keySet()) {
            emailSaidaMapper.insert(EmailSaidaDO.builder().evento(evento).para(endereco).assunto(assunto).corpo(corpo)
                    .messageId("<coti.aviso." + UUID.randomUUID().toString().replace("-", "") + "@" + dominio + ">")
                    .status("pendente").tentativas(0).criadoEm(agora).build());
        }
        return para.size();
    }

    /** Marca de resposta no topo, o corpo do modelo, a assinatura e o rodapé. */
    static String envelope(String corpo, EmailConfigDO cfg, Long chamadoId) {
        return "<div style=\"color:#9a9a9a;font-size:11px\">" + Email.MARCA_RESPOSTA + "</div>"
                + "<div style=\"font-family:Arial,sans-serif;font-size:14px\">" + corpo
                + (cfg.getAssinatura() == null ? "" : "<div style=\"margin-top:16px;color:#666\">" + cfg.getAssinatura() + "</div>")
                + "<p style=\"margin-top:20px;color:#9a9a9a;font-size:11px\">Chamado #" + chamadoId
                + ". Responda este e-mail para escrever no chamado.</p></div>";
    }

    /** Anexos e imagens do portal têm endereço relativo ("/admin-api/..."): no e-mail precisam do endereço completo. */
    static String absolutos(String html, String portal) {
        if (portal == null || portal.isBlank()) {
            return html;
        }
        return html.replace("href=\"/", "href=\"" + portal + "/").replace("src=\"/", "src=\"" + portal + "/");
    }

    static String dominio(String email) {
        int i = email == null ? -1 : email.lastIndexOf('@');
        return i < 0 ? "coti.local" : email.substring(i + 1).toLowerCase(Locale.ROOT);
    }

}
