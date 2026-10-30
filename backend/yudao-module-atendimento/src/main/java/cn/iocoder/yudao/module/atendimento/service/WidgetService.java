package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoCanalDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ContatoCanalMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.MensagemMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Webhooks;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Assistente da tela de login (caixa do canal "widget"): quem ainda não entrou no portal conversa com o fluxo do
 * catálogo, só para o reset de senha ({@link FluxoService}). Cada visita recebe um token aleatório; no banco fica só o
 * SHA-256 dele (a origem da conversa), então quem lê o banco não continua a conversa de ninguém. O visitante nunca é
 * ligado a uma pessoa do portal: não consulta chamados nem recebe senha pelo chat.
 */
@Service
public class WidgetService {

    public static final String CANAL = "widget";
    public static final String NOME_VISITANTE = "Visitante da tela de login";
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoCanalMapper contatoCanalMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private ConversaService conversaService;

    /** Mensagem como a página mostra: de quem é (voce, assistente, atendente), o texto e quando. */
    public record Mensagem(Long id, String de, String texto, LocalDateTime em) {
    }

    public boolean disponivel() {
        return caixaOuNull() != null;
    }

    /** Nova visita: só gera o token; a conversa nasce com a primeira mensagem. */
    public String iniciar() {
        caixa();
        byte[] b = new byte[32];
        ALEATORIO.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public void receber(String token, String texto) {
        String t = texto == null ? "" : texto.strip();
        if (t.isEmpty()) {
            throw exception(CONVERSA_VAZIA);
        }
        conversaService.receber(caixa(), origem(token), new ContatoService.Origem(NOME_VISITANTE, null, null, null), t, "texto",
                List.of(), null);
    }

    /** Mensagens da conversa desta visita depois de {@code depois} (só o que o contato vê). */
    public List<Mensagem> mensagens(String token, Long depois) {
        CaixaDO caixa = caixa();
        ContatoCanalDO cc = contatoCanalMapper.selectOne(new LambdaQueryWrapperX<ContatoCanalDO>()
                .eq(ContatoCanalDO::getCaixaId, caixa.getId()).eq(ContatoCanalDO::getOrigemId, origem(token)));
        if (cc == null) {
            return List.of();
        }
        ConversaDO c = conversaMapper.selectOne(new LambdaQueryWrapperX<ConversaDO>().eq(ConversaDO::getContatoCanalId, cc.getId())
                .orderByDesc(ConversaDO::getId).last("LIMIT 1"));
        if (c == null) {
            return List.of();
        }
        return mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, c.getId())
                        .in(MensagemDO::getTipo, MensagemTipo.ENTRADA, MensagemTipo.SAIDA).ne(MensagemDO::getPrivada, true)
                        .gt(MensagemDO::getId, depois == null ? 0 : depois).orderByAsc(MensagemDO::getId).last("LIMIT 100"))
                .stream().map(m -> new Mensagem(m.getId(), m.getTipo() == MensagemTipo.ENTRADA ? "voce"
                        : "agente".equals(m.getRemetenteTipo()) ? "atendente" : "assistente", m.getConteudo(), m.getCreateTime()))
                .toList();
    }

    private static String origem(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw exception(WIDGET_SESSAO_INVALIDA);
        }
        return "web:" + Webhooks.sha256(token);
    }

    private CaixaDO caixa() {
        CaixaDO c = caixaOuNull();
        if (c == null) {
            throw exception(WIDGET_DESLIGADO);
        }
        return c;
    }

    private CaixaDO caixaOuNull() {
        return caixaMapper.selectOne(new LambdaQueryWrapperX<CaixaDO>().eq(CaixaDO::getCanal, CANAL).eq(CaixaDO::getAtivo, true)
                .orderByAsc(CaixaDO::getId).last("LIMIT 1"));
    }

}
