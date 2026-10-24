package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Csat;
import cn.iocoder.yudao.module.atendimento.dominio.Telefone;
import cn.iocoder.yudao.module.atendimento.enums.ConversaStatus;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoAtorDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoAtorMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.atendimento.service.ContatoService.cortar;
import static cn.iocoder.yudao.module.atendimento.service.ContatoService.vazio;

/**
 * Conversas e mensagens (Chatwoot: Conversation, Message e os serviços de entrada e atribuição).
 *
 * <p>Tudo o que muda publica um {@link AtendimentoEvento}; depois do commit, {@link TempoReal} avisa as telas
 * abertas. Mensagem de saída que não é nota interna entra na fila de envio do canal ({@link EnvioService}).
 */
@Slf4j
@Service
public class ConversaService {

    /** Origens do ITSM (V12) para o chamado aberto a partir da conversa */
    private static final long ORIGEM_WHATSAPP = 4L;
    private static final long ORIGEM_OUTRO = 7L;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    /** Canais em que o provedor só aceita texto livre até 24 h depois da última mensagem do contato */
    public static final Set<String> JANELA_24H = Set.of("whatsapp_cloud", "instagram");

    /** Resultado de uma mensagem recebida pelo canal. */
    public record Entrada(Long conversaId, Long mensagemId, boolean duplicada, boolean nova) {
    }

    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private EtiquetaMapper etiquetaMapper;
    @Resource
    private ConversaEtiquetaMapper conversaEtiquetaMapper;
    @Resource
    private ConversaLeituraMapper conversaLeituraMapper;
    @Resource
    private EnvioMapper envioMapper;
    @Resource
    private ContatoService contatoService;
    @Resource
    private AtribuicaoService atribuicaoService;
    @Resource
    private Acesso acesso;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private ApplicationEventPublisher eventos;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private AvisoLeitura avisoLeitura;
    @Resource
    private HorarioService horarioService;
    @Resource
    private AutomacaoService automacaoService;
    @Resource
    private CsatService csatService;
    @Resource
    private RelatorioService relatorioService;

    // =====================================================================================================
    // Entrada (o canal recebeu uma mensagem do contato)
    // =====================================================================================================

    /**
     * O que um canal recebeu. {@code eco}: mensagem que a própria empresa mandou por fora do CO-TI (o atendente
     * respondeu pelo aplicativo no celular — coexistência do WhatsApp — ou pelo app do Instagram): entra como saída,
     * sem ir para a fila de envio. {@code historico}: conversa antiga trazida quando o número foi conectado, gravada
     * com a data original numa conversa já resolvida, sem saudação, distribuição nem aviso às telas.
     */
    public record Recebida(String origemId, ContatoService.Origem contato, String conteudo, String formato,
                           List<AtendimentoVOs.Anexo> anexos, String idExterno, Map<String, Object> dados,
                           boolean eco, LocalDateTime quando, boolean historico) {

        public static Recebida simples(String origemId, ContatoService.Origem contato, String conteudo, String formato,
                                       List<AtendimentoVOs.Anexo> anexos, String idExterno) {
            return new Recebida(origemId, contato, conteudo, formato, anexos, idExterno, Map.of(), false, null, false);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Entrada receber(CaixaDO caixa, String origemId, ContatoService.Origem origem, String conteudo, String formato,
                           List<AtendimentoVOs.Anexo> anexos, String idExterno) {
        return receber(caixa, Recebida.simples(origemId, origem, conteudo, formato, anexos, idExterno));
    }

    /**
     * Mensagem que chegou pelo canal. Acha a conversa em andamento (ou a resolvida há pouco, que reabre) ou abre uma
     * nova; a mesma mensagem recebida duas vezes ({@code idExterno} repetido) não duplica.
     */
    @Transactional(rollbackFor = Exception.class)
    public Entrada receber(CaixaDO caixa, Recebida r) {
        if (r.idExterno() != null && mensagemMapper.contarExterno(caixa.getId(), r.idExterno()) > 0) {
            return new Entrada(null, null, true, false);
        }
        ContatoCanalDO cc = contatoService.encontrarOuCriar(caixa.getId(), r.origemId(), r.contato());
        ContatoDO contato = contatoMapper.selectById(cc.getContatoId());
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime quando = r.quando() != null && r.quando().isBefore(agora) ? r.quando() : agora;
        ConversaDO c = conversaMapper.selectOne(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getContatoCanalId, cc.getId())
                .orderByDesc(ConversaDO::getId).last("LIMIT 1"));
        boolean nova = false;
        boolean reaberta = false;
        Integer notaCsat = null;
        if (c != null) {
            c = conversaMapper.selectByIdParaAtualizar(c.getId());
        }
        if (r.historico()) {
            // histórico da conexão: entra na conversa que já existe ou numa resolvida, com a data original
            if (c == null) {
                c = ConversaDO.builder().caixaId(caixa.getId()).contatoId(contato.getId()).contatoCanalId(cc.getId())
                        .status(ConversaStatus.RESOLVIDA.getCodigo()).resolvidaEm(agora).ultimaAtividadeEm(quando).atributos("{}").build();
                conversaMapper.insert(c);
                nova = true;
            }
        } else {
            // resposta à pesquisa de satisfação: fica na conversa resolvida, sem reabrir
            if (!r.eco() && c != null && c.getStatus() == ConversaStatus.RESOLVIDA.getCodigo()) {
                notaCsat = csatService.responder(c, r.conteudo());
            }
            if (notaCsat != null) {
                log.debug("[receber][conversa {}: nota {} na pesquisa]", c.getId(), notaCsat);
            } else if (c != null && c.getStatus() == ConversaStatus.RESOLVIDA.getCodigo()
                    && (c.getResolvidaEm() == null || c.getResolvidaEm().isBefore(agora.minusHours(caixa.getReabrirHoras())))) {
                c = null;   // resolvida há muito tempo: conversa nova
            }
            if (notaCsat != null) {
                // já tratado acima
            } else if (c == null) {
                c = ConversaDO.builder().caixaId(caixa.getId()).contatoId(contato.getId()).contatoCanalId(cc.getId())
                        .status(ConversaStatus.ABERTA.getCodigo()).ultimaAtividadeEm(agora).atributos("{}").build();
                conversaMapper.insert(c);
                nova = true;
            } else if (!r.eco() && c.getStatus() != ConversaStatus.ABERTA.getCodigo() && c.getStatus() != ConversaStatus.PENDENTE.getCodigo()) {
                // só o contato reabre: a resposta dada pelo celular numa conversa resolvida fica registrada nela
                String antes = ConversaStatus.de(c.getStatus()).getRotulo().toLowerCase(Locale.ROOT);
                c.setStatus(ConversaStatus.ABERTA.getCodigo());
                c.setAdiadaAte(null);
                c.setResolvidaEm(null);
                // o atendente de antes continua, se ainda atende a caixa
                if (c.getAgenteId() != null && !acesso.membro(caixa.getId(), c.getAgenteId())) {
                    c.setAgenteId(null);
                }
                gravar(c);
                atividade(c, "Conversa " + antes + " reaberta: o contato escreveu de novo");
                reaberta = true;
            }
        }
        if (!r.eco() && Boolean.TRUE.equals(contato.getBloqueado())) {
            log.info("[receber][contato {} bloqueado: mensagem ignorada]", contato.getId());
            return new Entrada(c.getId(), null, false, nova);
        }
        MensagemDO m = MensagemDO.builder().conversaId(c.getId()).tipo(r.eco() ? MensagemTipo.SAIDA : MensagemTipo.ENTRADA).privada(false)
                .remetenteTipo(r.eco() ? "agente" : "contato").remetenteId(r.eco() ? null : contato.getId())
                .conteudo(r.conteudo() == null ? "" : r.conteudo()).formato(r.formato() == null ? "texto" : r.formato())
                .anexos(JsonUtils.toJsonString(r.anexos() == null ? List.of() : r.anexos()))
                .dados(JsonUtils.toJsonString(comNota(r.dados(), notaCsat)))
                .idExterno(r.idExterno()).status("enviada").build();
        m.setCreateTime(quando);
        m.setUpdateTime(quando);
        mensagemMapper.insert(m);
        // datas da conversa só avançam (o histórico chega fora de ordem)
        ConversaDO u = ConversaDO.builder().id(c.getId()).build();
        boolean mudou = false;
        if (c.getUltimaAtividadeEm() == null || quando.isAfter(c.getUltimaAtividadeEm())) {
            u.setUltimaAtividadeEm(quando);
            mudou = true;
        }
        if (!r.eco() && (c.getUltimaEntradaEm() == null || quando.isAfter(c.getUltimaEntradaEm()))) {
            u.setUltimaEntradaEm(quando);
            c.setUltimaEntradaEm(quando);
            mudou = true;
        }
        if (r.eco() && !r.historico() && c.getPrimeiraRespostaEm() == null) {
            u.setPrimeiraRespostaEm(quando);
            relatorioService.primeiraResposta(c, quando);
            mudou = true;
        }
        if (mudou) {
            conversaMapper.updateById(u);
        }
        if (notaCsat != null) {
            saida(c, Csat.AGRADECIMENTO, "sistema", null, List.of());
        } else if (!r.historico() && !r.eco()) {
            ConversaDO alvo = c;
            if (nova && vazio(caixa.getSaudacao()) != null) {
                saida(alvo, caixa.getSaudacao(), "sistema", null, List.of());
            }
            // conversa nova, ou reaberta sem atendente: distribui
            if (alvo.getAgenteId() == null && alvo.getStatus() == ConversaStatus.ABERTA.getCodigo()
                    && Boolean.TRUE.equals(caixa.getAtribuicaoAutomatica())) {
                atribuicaoService.distribuir(alvo, caixa).ifPresent(a -> atividade(alvo, "Atribuída automaticamente a " + nome(a)));
            }
            // fora do expediente da caixa: aviso automático quando a conversa abre ou reabre
            if ((nova || reaberta) && vazio(caixa.getMensagemForaHorario()) != null && !horarioService.emExpediente(caixa)) {
                saida(alvo, caixa.getMensagemForaHorario(), "sistema", null, List.of());
            }
            if (nova) {
                automacaoService.disparar("conversa_criada", c.getId(), m.getConteudo());
            } else if (reaberta) {
                automacaoService.disparar("conversa_reaberta", c.getId(), m.getConteudo());
            }
            automacaoService.disparar("mensagem_recebida", c.getId(), m.getConteudo());
        }
        if (!r.historico()) {
            publicar("mensagem", c.getId(), m.getId());
            publicar("conversa", c.getId(), null);
        }
        return new Entrada(c.getId(), m.getId(), false, nova);
    }

    private static Map<String, Object> comNota(Map<String, Object> dados, Integer nota) {
        if (nota == null) {
            return dados == null ? Map.of() : dados;
        }
        Map<String, Object> d = new LinkedHashMap<>(dados == null ? Map.of() : dados);
        d.put("csat", Map.of("nota", nota));
        return d;
    }

    /**
     * Aviso do provedor sobre uma mensagem que a central mandou (enviada, entregue, lida, falhou), pelo id que ele deu.
     *
     * @return false se a mensagem não é desta caixa (ou ainda não teve o id gravado)
     */
    public boolean entregaPeloExterno(Long caixaId, String idExterno, String status, String erro) {
        if (idExterno == null) {
            return false;
        }
        for (MensagemDO m : mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getIdExterno, idExterno))) {
            ConversaDO c = conversaMapper.selectById(m.getConversaId());
            if (c != null && c.getCaixaId().equals(caixaId)) {
                atualizarEntrega(m.getId(), status, erro, null);
                return true;
            }
        }
        return false;
    }


    // =====================================================================================================
    // Consulta
    // =====================================================================================================

    public PageResult<AtendimentoVOs.ConversaResumo> pagina(AtendimentoVOs.FiltroConversa f, Long userId) {
        LambdaQueryWrapperX<ConversaDO> q = filtro(f, userId);
        if (q == null) {
            return new PageResult<>(List.of(), 0L);
        }
        q.orderByDesc(ConversaDO::getUltimaAtividadeEm).orderByDesc(ConversaDO::getId);
        PageResult<ConversaDO> p = conversaMapper.selectPage(f, q);
        return new PageResult<>(resumos(p.getList(), userId), p.getTotal());
    }

    /** Quantas conversas abertas em cada aba (para os números da tela). */
    public Map<String, Long> contagens(Long caixaId, Long userId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String aba : List.of("minhas", "nao_atribuidas", "todas")) {
            AtendimentoVOs.FiltroConversa f = new AtendimentoVOs.FiltroConversa();
            f.setAba(aba);
            f.setCaixaId(caixaId);
            LambdaQueryWrapperX<ConversaDO> q = filtro(f, userId);
            out.put(aba, q == null ? 0L : conversaMapper.selectCount(q));
        }
        return out;
    }

    /** Monta o filtro da fila; null = a pessoa não participa de nenhuma caixa. */
    private LambdaQueryWrapperX<ConversaDO> filtro(AtendimentoVOs.FiltroConversa f, Long userId) {
        LambdaQueryWrapperX<ConversaDO> q = new LambdaQueryWrapperX<ConversaDO>()
                .eqIfPresent(ConversaDO::getCaixaId, f.getCaixaId()).eqIfPresent(ConversaDO::getContatoId, f.getContatoId());
        if (!acesso.veTodas(userId)) {
            Set<Long> caixas = acesso.caixasDe(userId);
            if (caixas.isEmpty()) {
                return null;
            }
            q.in(ConversaDO::getCaixaId, caixas);
        }
        q.eq(ConversaDO::getStatus, f.getStatus() == null ? ConversaStatus.ABERTA.getCodigo() : f.getStatus());
        switch (f.getAba() == null ? "minhas" : f.getAba()) {
            case "nao_atribuidas" -> q.isNull(ConversaDO::getAgenteId);
            case "todas" -> { }
            default -> q.eq(ConversaDO::getAgenteId, userId);
        }
        if (f.getEtiquetaId() != null) {
            q.apply("EXISTS (SELECT 1 FROM atendimento.conversa_etiqueta e WHERE e.conversa_id = conversa.id AND e.etiqueta_id = {0})", f.getEtiquetaId());
        }
        String b = vazio(f.getBusca());
        if (b != null) {
            String numero = b.startsWith("#") ? b.substring(1) : b;
            String like = "%" + b.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            String tel = Telefone.normalizar(b);
            q.and(w -> {
                w.apply("EXISTS (SELECT 1 FROM atendimento.contato k WHERE k.id = conversa.contato_id AND (k.nome ILIKE {0} OR k.email ILIKE {0} OR k.telefone LIKE {1}))",
                        like, tel == null ? like : "%" + tel.substring(Math.max(1, tel.length() - 8)) + "%");
                if (numero.matches("\\d{1,18}")) {
                    w.or().eq(ConversaDO::getId, Long.parseLong(numero));
                }
            });
        }
        return q;
    }

    public AtendimentoVOs.ConversaResumo obter(Long id, Long userId) {
        ConversaDO c = existente(id);
        acesso.exigir(c, userId);
        return resumos(List.of(c), userId).get(0);
    }

    /** Mensagens da mais antiga para a mais nova; {@code antesDe} pagina para trás (50 de cada vez). */
    public List<AtendimentoVOs.Mensagem> mensagens(Long conversaId, Long antesDe, Long userId) {
        ConversaDO c = existente(conversaId);
        acesso.exigir(c, userId);
        List<MensagemDO> lista = mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, conversaId)
                .ltIfPresent(MensagemDO::getId, antesDe).orderByDesc(MensagemDO::getId).last("LIMIT 50"));
        Collections.reverse(lista);
        return converterMensagens(lista);
    }

    public AtendimentoVOs.Mensagem mensagem(Long mensagemId) {
        MensagemDO m = mensagemMapper.selectById(mensagemId);
        return m == null ? null : converterMensagens(List.of(m)).get(0);
    }

    public AtendimentoVOs.Lateral lateral(Long conversaId, Long userId) {
        ConversaDO c = existente(conversaId);
        acesso.exigir(c, userId);
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        AtendimentoVOs.Lateral l = new AtendimentoVOs.Lateral();
        l.setContato(contatoService.converter(List.of(contato)).get(0));
        // chamados do contato: como pessoa do portal ou pelo e-mail
        Set<Long> chamados = new LinkedHashSet<>();
        if (contato.getUserId() != null) {
            chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().eq(ChamadoAtorDO::getUserId, contato.getUserId())
                    .eq(ChamadoAtorDO::getTipo, 1)).forEach(a -> chamados.add(a.getChamadoId()));
        }
        if (contato.getEmail() != null) {
            chamadoAtorMapper.selectList(new LambdaQueryWrapperX<ChamadoAtorDO>().apply("lower(email) = {0}", contato.getEmail())
                    .eq(ChamadoAtorDO::getTipo, 1)).forEach(a -> chamados.add(a.getChamadoId()));
        }
        if (c.getChamadoId() != null) {
            chamados.add(c.getChamadoId());
        }
        if (!chamados.isEmpty()) {
            chamadoMapper.selectList(new LambdaQueryWrapperX<ChamadoDO>().in(ChamadoDO::getId, chamados).orderByDesc(ChamadoDO::getId).last("LIMIT 10"))
                    .forEach(ch -> {
                        AtendimentoVOs.ChamadoResumo r = new AtendimentoVOs.ChamadoResumo();
                        r.setId(ch.getId());
                        r.setTitulo(ch.getTitulo());
                        r.setStatus(ChamadoStatus.de(ch.getStatus()).getRotulo());
                        r.setAbertoEm(ch.getAbertoEm());
                        l.getChamados().add(r);
                    });
        }
        List<ConversaDO> anteriores = conversaMapper.selectList(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getContatoId, contato.getId())
                .ne(ConversaDO::getId, c.getId()).orderByDesc(ConversaDO::getId).last("LIMIT 10"));
        l.setAnteriores(resumos(anteriores, userId));
        return l;
    }

    // =====================================================================================================
    // Ações do atendente
    // =====================================================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long responder(AtendimentoVOs.ResponderReq req, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        c = conversaMapper.selectByIdParaAtualizar(c.getId());
        boolean privada = Boolean.TRUE.equals(req.getPrivada());
        String conteudo = req.getConteudo() == null ? "" : req.getConteudo().strip();
        List<AtendimentoVOs.Anexo> anexos = req.getAnexos() == null ? List.of() : req.getAnexos();
        if (conteudo.isEmpty() && anexos.isEmpty()) {
            throw exception(CONVERSA_VAZIA);
        }
        for (AtendimentoVOs.Anexo a : anexos) {
            // só arquivos enviados ao próprio CO-TI (o canal precisa conseguir baixar, e não é para apontar para fora)
            if (a.getUrl() == null || !a.getUrl().startsWith("/admin-api/infra/file/")) {
                throw exception(CONVERSA_ANEXO_INVALIDO, "Anexe arquivos enviados pelo próprio sistema");
            }
        }
        if (!privada && c.getStatus() == ConversaStatus.RESOLVIDA.getCodigo()) {
            throw exception(CONVERSA_RESOLVIDA);
        }
        Long id;
        if (privada) {
            MensagemDO m = MensagemDO.builder().conversaId(c.getId()).tipo(MensagemTipo.SAIDA).privada(true).remetenteTipo("agente")
                    .remetenteId(userId).conteudo(conteudo).formato(anexos.isEmpty() ? "texto" : "arquivo")
                    .anexos(JsonUtils.toJsonString(anexos)).status("enviada").build();
            mensagemMapper.insert(m);
            id = m.getId();
            publicar("mensagem", c.getId(), id);
        } else {
            id = saida(c, conteudo, "agente", userId, anexos);
            LocalDateTime agora = LocalDateTime.now();
            ConversaDO u = ConversaDO.builder().id(c.getId()).ultimaAtividadeEm(agora).build();
            if (c.getPrimeiraRespostaEm() == null) {
                u.setPrimeiraRespostaEm(agora);
            }
            // quem responde uma conversa sem atendente fica com ela (como no Chatwoot)
            if (c.getAgenteId() == null) {
                u.setAgenteId(userId);
                c.setAgenteId(userId);
                atividade(c, nome(userId) + " assumiu a conversa");
            }
            // respondeu a quem estava com o robô ou adiada: volta para a fila normal
            if (c.getStatus() != ConversaStatus.ABERTA.getCodigo()) {
                u.setStatus(ConversaStatus.ABERTA.getCodigo());
            }
            conversaMapper.updateById(u);
            if (u.getPrimeiraRespostaEm() != null) {
                relatorioService.primeiraResposta(c, agora);
            }
        }
        conversaLeituraMapper.marcar(c.getId(), userId, id);
        publicar("conversa", c.getId(), null);
        return id;
    }

    /**
     * Modelo aprovado (WhatsApp): é a forma de falar com o contato depois de 24 h. Numa conversa resolvida, reabre —
     * a ideia é justamente retomar o contato.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long responderModelo(Long conversaId, String texto, Map<String, Object> dados, Long userId) {
        ConversaDO c = existente(conversaId);
        acesso.exigir(c, userId);
        c = conversaMapper.selectByIdParaAtualizar(c.getId());
        Long id = saida(c, texto, "agente", userId, List.of(), dados);
        LocalDateTime agora = LocalDateTime.now();
        ConversaDO u = ConversaDO.builder().id(c.getId()).ultimaAtividadeEm(agora).build();
        if (c.getAgenteId() == null) {
            u.setAgenteId(userId);
            c.setAgenteId(userId);
            atividade(c, nome(userId) + " assumiu a conversa");
        }
        if (c.getPrimeiraRespostaEm() == null) {
            u.setPrimeiraRespostaEm(agora);
            relatorioService.primeiraResposta(c, agora);
        }
        if (c.getStatus() != ConversaStatus.ABERTA.getCodigo()) {
            boolean resolvida = c.getStatus() == ConversaStatus.RESOLVIDA.getCodigo();
            c.setStatus(ConversaStatus.ABERTA.getCodigo());
            c.setResolvidaEm(null);
            c.setAdiadaAte(null);
            gravar(c);
            if (resolvida) {
                atividade(c, nome(userId) + " reabriu a conversa com um modelo");
            }
        }
        conversaMapper.updateById(u);
        conversaLeituraMapper.marcar(c.getId(), userId, id);
        publicar("conversa", c.getId(), null);
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void atribuir(AtendimentoVOs.AtribuirReq req, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        Long novo = req.getAgenteId();
        if (novo != null && !novo.equals(userId) && !acesso.pode(userId, Acesso.P_ATRIBUIR)) {
            throw exception(CONVERSA_ATRIBUIR_OUTRO);
        }
        if (novo == null && c.getAgenteId() != null && !c.getAgenteId().equals(userId) && !acesso.pode(userId, Acesso.P_ATRIBUIR)) {
            throw exception(CONVERSA_ATRIBUIR_OUTRO);
        }
        if (novo != null && !acesso.membro(c.getCaixaId(), novo)) {
            throw exception(CONVERSA_AGENTE_NAO_MEMBRO);
        }
        c = conversaMapper.selectByIdParaAtualizar(c.getId());
        if (Objects.equals(c.getAgenteId(), novo)) {
            return;
        }
        c.setAgenteId(novo);
        gravar(c);
        atividade(c, novo == null ? nome(userId) + " tirou o atendente"
                : novo.equals(userId) ? nome(userId) + " assumiu a conversa" : nome(userId) + " atribuiu a " + nome(novo));
        publicar("conversa", c.getId(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void mudarStatus(AtendimentoVOs.StatusReq req, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        c = conversaMapper.selectByIdParaAtualizar(c.getId());
        ConversaStatus novo = ConversaStatus.de(req.getStatus());
        if (novo == ConversaStatus.ADIADA && (req.getAdiadaAte() == null || !req.getAdiadaAte().isAfter(LocalDateTime.now()))) {
            throw exception(CONVERSA_ADIAR_DATA);
        }
        String texto = switch (novo) {
            case RESOLVIDA -> nome(userId) + " resolveu a conversa";
            case ADIADA -> nome(userId) + " adiou até " + HORA.format(req.getAdiadaAte());
            case ABERTA -> nome(userId) + " reabriu a conversa";
            default -> nome(userId) + " passou a conversa para pendente";
        };
        aplicarStatus(c, novo, req.getAdiadaAte(), texto);
    }

    /**
     * Muda a situação (pela pessoa, por macro ou por automação), registra e dispara as automações de conversa
     * resolvida ou reaberta.
     *
     * @return false se já estava assim
     */
    boolean aplicarStatus(ConversaDO c, ConversaStatus novo, LocalDateTime adiadaAte, String registro) {
        if (c.getStatus() == novo.getCodigo() && novo != ConversaStatus.ADIADA) {
            return false;
        }
        boolean estavaFechada = c.getStatus() == ConversaStatus.RESOLVIDA.getCodigo() || c.getStatus() == ConversaStatus.ADIADA.getCodigo();
        switch (novo) {
            case RESOLVIDA -> {
                c.setResolvidaEm(LocalDateTime.now());
                c.setAdiadaAte(null);
            }
            case ADIADA -> c.setAdiadaAte(adiadaAte);
            case ABERTA -> {
                c.setResolvidaEm(null);
                c.setAdiadaAte(null);
            }
            default -> { }
        }
        c.setStatus(novo.getCodigo());
        gravar(c);
        if (registro != null) {
            atividade(c, registro);
        }
        publicar("conversa", c.getId(), null);
        if (novo == ConversaStatus.RESOLVIDA) {
            relatorioService.resolucao(c);
            csatService.aoResolver(c, caixaMapper.selectById(c.getCaixaId()));
            automacaoService.disparar("conversa_resolvida", c.getId(), null);
        } else if (novo == ConversaStatus.ABERTA && estavaFechada) {
            automacaoService.disparar("conversa_reaberta", c.getId(), null);
        }
        return true;
    }

    /** Troca o atendente (macro, automação). */
    void definirAgente(ConversaDO c, Long agenteId, String registro) {
        c.setAgenteId(agenteId);
        gravar(c);
        if (registro != null) {
            atividade(c, registro);
        }
        publicar("conversa", c.getId(), null);
    }

    /** Nota interna escrita por macro (com autor) ou automação (sistema). */
    Long nota(ConversaDO c, String texto, Long userId) {
        MensagemDO m = MensagemDO.builder().conversaId(c.getId()).tipo(MensagemTipo.SAIDA).privada(true)
                .remetenteTipo(userId == null ? "sistema" : "agente").remetenteId(userId).conteudo(texto).formato("texto")
                .anexos("[]").status("enviada").build();
        mensagemMapper.insert(m);
        publicar("mensagem", c.getId(), m.getId());
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void etiquetas(AtendimentoVOs.EtiquetasReq req, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        Set<Long> novas = new HashSet<>(req.getEtiquetaIds());
        if (!novas.isEmpty() && etiquetaMapper.selectBatchIds(novas).size() != novas.size()) {
            throw exception(ETIQUETA_NAO_EXISTE);
        }
        Set<Long> atuais = conversaEtiquetaMapper.listar(List.of(c.getId())).stream()
                .map(r -> ((Number) r.get("etiqueta_id")).longValue()).collect(Collectors.toSet());
        atuais.stream().filter(e -> !novas.contains(e)).forEach(e -> conversaEtiquetaMapper.remover(c.getId(), e));
        novas.stream().filter(e -> !atuais.contains(e)).forEach(e -> conversaEtiquetaMapper.incluir(c.getId(), e));
        publicar("conversa", c.getId(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void prioridade(AtendimentoVOs.PrioridadeReq req, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        conversaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversaDO>()
                .eq(ConversaDO::getId, c.getId()).set(ConversaDO::getPrioridade, req.getPrioridade()));
        publicar("conversa", c.getId(), null);
    }

    public void marcarLida(Long conversaId, Long userId) {
        ConversaDO c = existente(conversaId);
        acesso.exigir(c, userId);
        MensagemDO ultima = mensagemMapper.selectOne(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, conversaId)
                .orderByDesc(MensagemDO::getId).last("LIMIT 1"));
        if (ultima != null) {
            conversaLeituraMapper.marcar(conversaId, userId, ultima.getId());
        }
        // o contato vê que foi lido (WhatsApp oficial, Instagram): uma vez por mensagem dele
        MensagemDO entrada = mensagemMapper.selectOne(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, conversaId)
                .eq(MensagemDO::getTipo, MensagemTipo.ENTRADA).orderByDesc(MensagemDO::getId).last("LIMIT 1"));
        if (entrada != null && entrada.getIdExterno() != null && (entrada.getDados() == null || !entrada.getDados().contains("\"lidaAvisada\""))) {
            avisoLeitura.avisar(c, entrada);
        }
    }

    /** Chamado no Service Desk a partir da conversa: o solicitante é a pessoa do portal ou o e-mail do contato. */
    @Transactional(rollbackFor = Exception.class)
    public Long abrirChamado(AtendimentoVOs.AbrirChamadoReq req, boolean avisarContato, Long userId) {
        ConversaDO c = existente(req.getConversaId());
        acesso.exigir(c, userId);
        c = conversaMapper.selectByIdParaAtualizar(c.getId());
        if (c.getChamadoId() != null) {
            throw exception(CONVERSA_JA_TEM_CHAMADO, c.getChamadoId());
        }
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        StringBuilder d = new StringBuilder("<p>Aberto a partir da conversa #").append(c.getId()).append(" (")
                .append(HtmlUtils.htmlEscape(caixa.getNome())).append(") com <b>").append(HtmlUtils.htmlEscape(contato.getNome())).append("</b>");
        if (contato.getTelefone() != null) {
            d.append(" — ").append(HtmlUtils.htmlEscape(contato.getTelefone()));
        }
        d.append(".</p>");
        if (Boolean.TRUE.equals(req.getIncluirConversa())) {
            List<MensagemDO> ultimas = mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, c.getId())
                    .in(MensagemDO::getTipo, MensagemTipo.ENTRADA, MensagemTipo.SAIDA).eq(MensagemDO::getPrivada, false)
                    .orderByDesc(MensagemDO::getId).last("LIMIT 20"));
            Collections.reverse(ultimas);
            d.append("<ul>");
            for (MensagemDO m : ultimas) {
                d.append("<li><b>").append(m.getTipo() == MensagemTipo.ENTRADA ? HtmlUtils.htmlEscape(contato.getNome()) : "Central")
                        .append("</b> (").append(HORA.format(m.getCreateTime())).append("): ")
                        .append(HtmlUtils.htmlEscape(cortar(m.getConteudo(), 1000))).append("</li>");
            }
            d.append("</ul>");
        }
        ChamadoVOs.CriarReq cr = new ChamadoVOs.CriarReq();
        cr.setTitulo(req.getTitulo().trim());
        cr.setDescricao(d.toString());
        cr.setCategoriaId(req.getCategoriaId());
        cr.setUrgencia(req.getUrgencia() == null ? 3 : req.getUrgencia());
        cr.setOrigemId(caixa.getCanal().startsWith("whatsapp") ? ORIGEM_WHATSAPP : ORIGEM_OUTRO);
        if (contato.getUserId() != null) {
            cr.setSolicitanteIds(List.of(contato.getUserId()));
        }
        Long chamadoId = chamadoService.criarDeConversa(cr, userId, contato.getUserId() == null ? contato.getEmail() : null);
        conversaMapper.updateById(ConversaDO.builder().id(c.getId()).chamadoId(chamadoId).build());
        c.setChamadoId(chamadoId);
        atividade(c, nome(userId) + " abriu o chamado #" + chamadoId);
        if (avisarContato && c.getStatus() != ConversaStatus.RESOLVIDA.getCodigo()) {
            saida(c, "Abrimos o chamado #" + chamadoId + " para o seu pedido. Use esse número se precisar falar sobre ele.", "sistema", null, List.of());
        }
        publicar("conversa", c.getId(), null);
        return chamadoId;
    }

    /** Conversas adiadas cujo prazo chegou voltam a abertas (tarefa agendada). */
    @Transactional(rollbackFor = Exception.class)
    public int reabrirAdiadas() {
        int n = 0;
        for (ConversaDO c : conversaMapper.selectList(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getStatus, ConversaStatus.ADIADA.getCodigo())
                .le(ConversaDO::getAdiadaAte, LocalDateTime.now()))) {
            conversaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversaDO>()
                    .eq(ConversaDO::getId, c.getId()).eq(ConversaDO::getStatus, ConversaStatus.ADIADA.getCodigo())
                    .set(ConversaDO::getStatus, ConversaStatus.ABERTA.getCodigo()).set(ConversaDO::getAdiadaAte, null));
            atividade(c, "O prazo de adiamento acabou: conversa reaberta");
            publicar("conversa", c.getId(), null);
            n++;
        }
        return n;
    }

    /** Resultado da entrega no canal (fila de envio ou aviso do provedor). */
    @Transactional(rollbackFor = Exception.class)
    public void atualizarEntrega(Long mensagemId, String status, String erro, String idExterno) {
        MensagemDO m = mensagemMapper.selectById(mensagemId);
        if (m == null) {
            return;
        }
        // não volta: lida não vira entregue por um aviso atrasado
        List<String> ordem = List.of("pendente", "enviada", "entregue", "lida");
        if (!"falhou".equals(status) && ordem.indexOf(status) <= ordem.indexOf(m.getStatus())) {
            return;
        }
        MensagemDO u = MensagemDO.builder().id(m.getId()).status(status).erro(erro == null ? null : cortar(erro, 500)).build();
        if (idExterno != null && m.getIdExterno() == null) {
            u.setIdExterno(idExterno);
        }
        mensagemMapper.updateById(u);
        publicar("mensagem", m.getConversaId(), m.getId());
    }

    // =====================================================================================================
    // Internos
    // =====================================================================================================

    /** Mensagem para o contato: grava e coloca na fila do canal. */
    Long saida(ConversaDO c, String conteudo, String remetenteTipo, Long remetenteId, List<AtendimentoVOs.Anexo> anexos) {
        return saida(c, conteudo, remetenteTipo, remetenteId, anexos, null);
    }

    Long saida(ConversaDO c, String conteudo, String remetenteTipo, Long remetenteId, List<AtendimentoVOs.Anexo> anexos,
               Map<String, Object> dados) {
        MensagemDO m = MensagemDO.builder().conversaId(c.getId()).tipo(MensagemTipo.SAIDA).privada(false).remetenteTipo(remetenteTipo)
                .remetenteId(remetenteId).conteudo(conteudo).formato(dados != null && dados.containsKey("modelo") ? "modelo" : anexos.isEmpty() ? "texto" : "arquivo")
                .anexos(JsonUtils.toJsonString(anexos)).dados(dados == null ? null : JsonUtils.toJsonString(dados)).status("pendente").build();
        mensagemMapper.insert(m);
        envioMapper.insert(EnvioDO.builder().mensagemId(m.getId()).status("pendente").tentativas(0).criadoEm(LocalDateTime.now()).build());
        publicar("mensagem", c.getId(), m.getId());
        return m.getId();
    }

    /** Registro no meio da conversa ("Ana resolveu a conversa"). */
    void atividade(ConversaDO c, String texto) {
        MensagemDO m = MensagemDO.builder().conversaId(c.getId()).tipo(MensagemTipo.ATIVIDADE).privada(false).remetenteTipo("sistema")
                .conteudo(texto).formato("texto").anexos("[]").status("enviada").build();
        mensagemMapper.insert(m);
        publicar("mensagem", c.getId(), m.getId());
    }

    void publicar(String tipo, Long conversaId, Long mensagemId) {
        eventos.publishEvent(new AtendimentoEvento(tipo, conversaId, mensagemId));
    }

    private void gravar(ConversaDO c) {
        conversaMapper.updateById(c);
        conversaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ConversaDO>()
                .eq(ConversaDO::getId, c.getId()).set(ConversaDO::getAgenteId, c.getAgenteId()).set(ConversaDO::getAdiadaAte, c.getAdiadaAte())
                .set(ConversaDO::getResolvidaEm, c.getResolvidaEm()));
    }

    public ConversaDO existente(Long id) {
        ConversaDO c = id == null ? null : conversaMapper.selectById(id);
        if (c == null) {
            throw exception(CONVERSA_NAO_EXISTE);
        }
        return c;
    }

    String nome(Long userId) {
        AdminUserRespDTO u = userId == null ? null : adminUserApi.getUser(userId);
        return u == null ? "Alguém" : u.getNickname();
    }

    /** Resumos para a lista (contato, caixa, atendente, última mensagem, não lidas de quem consulta, etiquetas). */
    public List<AtendimentoVOs.ConversaResumo> resumos(List<ConversaDO> lista, Long userId) {
        if (lista.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = lista.stream().map(ConversaDO::getId).collect(Collectors.toSet());
        Map<Long, ContatoDO> contatos = contatoMapper.selectBatchIds(lista.stream().map(ConversaDO::getContatoId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ContatoDO::getId, Function.identity()));
        Map<Long, CaixaDO> caixas = caixaMapper.selectBatchIds(lista.stream().map(ConversaDO::getCaixaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(CaixaDO::getId, Function.identity()));
        Map<Long, AdminUserRespDTO> agentes = adminUserApi.getUserMap(lista.stream().map(ConversaDO::getAgenteId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, MensagemDO> ultimas = mensagemMapper.ultimas(ids).stream().collect(Collectors.toMap(MensagemDO::getConversaId, Function.identity()));
        Map<Long, Integer> naoLidas = new HashMap<>();
        if (userId != null) {
            mensagemMapper.naoLidas(ids, userId).forEach(r -> naoLidas.put(((Number) r.get("conversa_id")).longValue(), ((Number) r.get("n")).intValue()));
        }
        Map<Long, List<Long>> etiquetasDe = conversaEtiquetaMapper.listar(ids).stream().collect(Collectors.groupingBy(
                r -> ((Number) r.get("conversa_id")).longValue(), Collectors.mapping(r -> ((Number) r.get("etiqueta_id")).longValue(), Collectors.toList())));
        Map<Long, EtiquetaDO> etiquetas = etiquetasDe.isEmpty() ? Map.of() : etiquetaMapper.selectBatchIds(
                etiquetasDe.values().stream().flatMap(List::stream).collect(Collectors.toSet())).stream().collect(Collectors.toMap(EtiquetaDO::getId, Function.identity()));
        return lista.stream().map(c -> {
            AtendimentoVOs.ConversaResumo r = new AtendimentoVOs.ConversaResumo();
            r.setId(c.getId());
            r.setCaixaId(c.getCaixaId());
            CaixaDO cx = caixas.get(c.getCaixaId());
            r.setCaixa(cx == null ? null : cx.getNome());
            r.setCanal(cx == null ? null : cx.getCanal());
            r.setContatoId(c.getContatoId());
            ContatoDO ct = contatos.get(c.getContatoId());
            r.setContato(ct == null ? "#" + c.getContatoId() : ct.getNome());
            r.setTelefone(ct == null ? null : ct.getTelefone());
            r.setStatus(c.getStatus());
            r.setStatusRotulo(ConversaStatus.de(c.getStatus()).getRotulo());
            r.setAgenteId(c.getAgenteId());
            r.setAgente(c.getAgenteId() == null ? null : Optional.ofNullable(agentes.get(c.getAgenteId())).map(AdminUserRespDTO::getNickname).orElse("#" + c.getAgenteId()));
            r.setPrioridade(c.getPrioridade());
            r.setChamadoId(c.getChamadoId());
            r.setAdiadaAte(c.getAdiadaAte());
            r.setUltimaAtividadeEm(c.getUltimaAtividadeEm());
            r.setUltimaEntradaEm(c.getUltimaEntradaEm());
            // WhatsApp oficial e Instagram: texto livre só até 24 h depois da última mensagem do contato
            if (cx != null && JANELA_24H.contains(cx.getCanal())) {
                r.setJanelaAte(c.getUltimaEntradaEm() == null ? null : c.getUltimaEntradaEm().plusHours(24));
                r.setJanela(true);
            }
            MensagemDO u = ultimas.get(c.getId());
            if (u != null) {
                r.setUltimaMensagem(cortar(u.getConteudo().isEmpty() && !"[]".equals(u.getAnexos()) ? "📎 anexo" : u.getConteudo(), 120));
                r.setUltimaMensagemTipo(u.getTipo());
            }
            r.setNaoLidas(naoLidas.getOrDefault(c.getId(), 0));
            etiquetasDe.getOrDefault(c.getId(), List.of()).stream().map(etiquetas::get).filter(Objects::nonNull).forEach(e -> {
                AtendimentoVOs.Etiqueta v = new AtendimentoVOs.Etiqueta();
                v.setId(e.getId());
                v.setNome(e.getNome());
                v.setCor(e.getCor());
                r.getEtiquetas().add(v);
            });
            return r;
        }).toList();
    }

    private List<AtendimentoVOs.Mensagem> converterMensagens(List<MensagemDO> lista) {
        Set<Long> agentes = lista.stream().filter(m -> "agente".equals(m.getRemetenteTipo())).map(MensagemDO::getRemetenteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> contatos = lista.stream().filter(m -> "contato".equals(m.getRemetenteTipo())).map(MensagemDO::getRemetenteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> nomesAgentes = adminUserApi.getUserMap(agentes);
        Map<Long, String> nomesContatos = contatos.isEmpty() ? Map.of() : contatoMapper.selectBatchIds(contatos).stream()
                .collect(Collectors.toMap(ContatoDO::getId, ContatoDO::getNome));
        return lista.stream().map(m -> {
            AtendimentoVOs.Mensagem v = new AtendimentoVOs.Mensagem();
            v.setId(m.getId());
            v.setConversaId(m.getConversaId());
            v.setTipo(m.getTipo());
            v.setPrivada(m.getPrivada());
            v.setRemetenteTipo(m.getRemetenteTipo());
            v.setRemetenteId(m.getRemetenteId());
            Map<String, Object> dados = m.getDados() == null ? Map.of() : JsonUtils.parseObject(m.getDados(), new TypeReference<Map<String, Object>>() {
            });
            v.setDados(dados);
            v.setRemetente(switch (m.getRemetenteTipo()) {
                case "agente" -> m.getRemetenteId() == null && "celular".equals(dados.get("origem")) ? "Pelo celular"
                        : Optional.ofNullable(nomesAgentes.get(m.getRemetenteId())).map(AdminUserRespDTO::getNickname).orElse("Atendente");
                case "contato" -> nomesContatos.getOrDefault(m.getRemetenteId(), "Contato");
                case "robo" -> "Assistente";
                default -> "Sistema";
            });
            v.setConteudo(m.getConteudo());
            v.setFormato(m.getFormato());
            v.setAnexos(JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
            }));
            v.setStatus(m.getStatus());
            v.setErro(m.getErro());
            v.setCriadaEm(m.getCreateTime());
            return v;
        }).toList();
    }

}
