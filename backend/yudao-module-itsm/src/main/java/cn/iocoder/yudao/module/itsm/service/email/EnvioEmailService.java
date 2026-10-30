package cn.iocoder.yudao.module.itsm.service.email;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailConfigDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailSaidaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailConfigMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailSaidaMapper;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

/**
 * Envio das notificações por SMTP, a partir da fila (itsm.email_saida). Falha de envio tenta de novo em 1, 5, 15
 * e 60 minutos; depois disso a mensagem fica com erro.
 *
 * <p>Cabeçalhos: Auto-Submitted (evita resposta automática de férias em laço), Message-ID próprio e
 * In-Reply-To/References com o chamado (o cliente de e-mail agrupa a conversa e o coletor acha o chamado).
 */
@Slf4j
@Service
public class EnvioEmailService {

    public static final String CONTEXTO_SENHA = "email-smtp";
    /** Senha provisória mandada por e-mail: cifrada na fila até o envio */
    static final String CONTEXTO_SEGREDO = "email-segredo";
    /** Lugar da senha no corpo guardado (o corpo na fila nunca tem a senha) */
    static final String MARCA_SEGREDO = "[[SENHA_PROVISORIA]]";
    private static final int[] ESPERA_MIN = {1, 5, 15, 60};

    @Resource
    private EmailConfigMapper emailConfigMapper;
    @Resource
    private EmailSaidaMapper emailSaidaMapper;
    @Resource
    private CifradorSegredos cifrador;

    /** @return quantas mensagens foram enviadas */
    public int processarFila() {
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAtivo()) || cfg.getSmtpHost() == null) {
            return 0;
        }
        emailSaidaMapper.destravar();
        Session sessao = sessao(cfg);
        int enviadas = 0;
        for (Long id : emailSaidaMapper.selectIdsPendentes(50)) {
            if (emailSaidaMapper.reivindicar(id) != 1) {
                continue;
            }
            EmailSaidaDO m = emailSaidaMapper.selectById(id);
            try {
                if (m.getCorpo() != null && m.getCorpo().contains(MARCA_SEGREDO)) {
                    if (m.getSegredo() == null) {
                        // a senha já foi apagada (reenvio de um e-mail entregue ou que esgotou as tentativas)
                        emailSaidaMapper.updateById(EmailSaidaDO.builder().id(id).status("erro")
                                .erro("A senha provisória deste e-mail não está mais guardada: peça um novo reset").build());
                        continue;
                    }
                    m.setCorpo(m.getCorpo().replace(MARCA_SEGREDO, HtmlUtils.htmlEscape(cifrador.decifrar(m.getSegredo(), CONTEXTO_SEGREDO))));
                }
                enviar(sessao, cfg, m);
                emailSaidaMapper.updateById(EmailSaidaDO.builder().id(id).status("enviado").enviadoEm(LocalDateTime.now()).erro(null).build());
                apagarSegredo(m);
                enviadas++;
            } catch (Exception e) {
                int t = m.getTentativas() == null ? 1 : m.getTentativas();
                String erro = limitar(e.getClass().getSimpleName() + ": " + e.getMessage());
                log.warn("[processarFila][e-mail {} para {} tentativa {}: {}]", id, m.getPara(), t, erro);
                EmailSaidaDO u = EmailSaidaDO.builder().id(id).erro(erro).build();
                if (t > ESPERA_MIN.length) {
                    u.setStatus("erro");
                    apagarSegredo(m);
                } else {
                    u.setStatus("pendente");
                    u.setProximaTentativa(LocalDateTime.now().plusMinutes(ESPERA_MIN[t - 1]));
                }
                emailSaidaMapper.updateById(u);
            }
        }
        return enviadas;
    }

    /** Envia uma mensagem de teste agora, sem fila. @return null se deu certo; senão, o erro */
    public String testar(String destino) {
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (cfg == null || cfg.getSmtpHost() == null || cfg.getRemetenteEmail() == null) {
            return "Preencha o servidor SMTP e o remetente";
        }
        try {
            enviar(sessao(cfg), cfg, EmailSaidaDO.builder().para(destino).assunto("CO-TI: teste de envio")
                    .corpo("<p>Se você recebeu esta mensagem, o envio de e-mails do CO-TI está funcionando.</p>")
                    .messageId("<coti.teste." + System.currentTimeMillis() + "@" + NotificacaoEmailService.dominio(cfg.getRemetenteEmail()) + ">")
                    .build());
            return null;
        } catch (Exception e) {
            return limitar(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public PageResult<EmailSaidaDO> pagina(PageParam p, String status, Long chamadoId) {
        PageResult<EmailSaidaDO> r = emailSaidaMapper.selectPage(p, new LambdaQueryWrapperX<EmailSaidaDO>()
                .eqIfPresent(EmailSaidaDO::getStatus, status).eqIfPresent(EmailSaidaDO::getChamadoId, chamadoId)
                .orderByDesc(EmailSaidaDO::getId));
        r.getList().forEach(m -> {
            m.setCorpo(null); // a lista não traz o corpo
            m.setSegredo(null);
        });
        return r;
    }

    public void reenviar(Long id) {
        emailSaidaMapper.updateById(EmailSaidaDO.builder().id(id).status("pendente").tentativas(0).proximaTentativa(null).build());
    }

    /**
     * Põe na fila o e-mail com a senha provisória de um chamado. O corpo guardado tem só a marca; a senha vai cifrada
     * à parte, entra no envio e é apagada depois (entregue ou desistido).
     *
     * @return false se o envio de e-mails não está ligado (a senha então fica para o portal ou o canal)
     */
    public boolean enfileirarSenha(Long chamadoId, String para, String titulo, String senha) {
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (cfg == null || !Boolean.TRUE.equals(cfg.getAtivo()) || cfg.getSmtpHost() == null || cfg.getRemetenteEmail() == null) {
            return false;
        }
        String corpo = "<div style=\"font-family:Arial,sans-serif;font-size:14px\">"
                + "<p>Olá! O pedido de reset de senha (chamado #" + chamadoId + ": " + HtmlUtils.htmlEscape(titulo) + ") foi aprovado e executado.</p>"
                + "<p>Sua senha provisória é:</p>"
                + "<p style=\"font-family:Consolas,monospace;font-size:20px;letter-spacing:1px;padding:10px 14px;background:#f2f4f7;"
                + "border-radius:6px;display:inline-block\">" + MARCA_SEGREDO + "</p>"
                + "<p>Ela vale para um único acesso: no primeiro login o sistema pede uma senha nova.</p>"
                + "<p>Se não foi você que pediu, avise a equipe de TI imediatamente.</p>"
                + (cfg.getAssinatura() == null ? "" : "<div style=\"margin-top:16px;color:#666\">" + cfg.getAssinatura() + "</div>")
                + "<p style=\"margin-top:20px;color:#9a9a9a;font-size:11px\">Aviso automático do CO-TI. Por segurança, apague este e-mail"
                + " depois de usar a senha.</p></div>";
        emailSaidaMapper.insert(EmailSaidaDO.builder().chamadoId(chamadoId).evento("senha_provisoria").para(para.trim().toLowerCase(Locale.ROOT))
                .assunto("Sua senha provisória (chamado #" + chamadoId + ")").corpo(corpo).segredo(cifrador.cifrar(senha, CONTEXTO_SEGREDO))
                .messageId("<coti.senha." + UUID.randomUUID().toString().replace("-", "") + "@"
                        + NotificacaoEmailService.dominio(cfg.getRemetenteEmail()) + ">")
                .status("pendente").tentativas(0).criadoEm(LocalDateTime.now()).build());
        return true;
    }

    private void apagarSegredo(EmailSaidaDO m) {
        if (m.getSegredo() != null) {
            emailSaidaMapper.update(null, new LambdaUpdateWrapper<EmailSaidaDO>().eq(EmailSaidaDO::getId, m.getId())
                    .set(EmailSaidaDO::getSegredo, null));
        }
    }

    // ------------------------------------------------------------------ SMTP

    Session sessao(EmailConfigDO cfg) {
        Properties p = new Properties();
        String seg = cfg.getSmtpSeguranca();
        p.put("mail.transport.protocol", "ssl".equals(seg) ? "smtps" : "smtp");
        String pre = "ssl".equals(seg) ? "mail.smtps." : "mail.smtp.";
        p.put(pre + "host", cfg.getSmtpHost());
        p.put(pre + "port", String.valueOf(cfg.getSmtpPorta()));
        p.put(pre + "auth", String.valueOf(cfg.getSmtpUsuario() != null && !cfg.getSmtpUsuario().isBlank()));
        p.put(pre + "connectiontimeout", "15000");
        p.put(pre + "timeout", "30000");
        p.put(pre + "writetimeout", "30000");
        if ("starttls".equals(seg)) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        if (!"nenhuma".equals(seg)) {
            // confere o nome no certificado (sem isso, STARTTLS aceitaria qualquer servidor)
            p.put(pre + "ssl.checkserveridentity", "true");
        }
        return Session.getInstance(p);
    }

    private void enviar(Session sessao, EmailConfigDO cfg, EmailSaidaDO m) throws Exception {
        String messageId = m.getMessageId();
        MimeMessage msg = new MimeMessage(sessao) {
            @Override
            protected void updateMessageID() throws MessagingException {
                setHeader("Message-ID", messageId);
            }
        };
        msg.setFrom(new InternetAddress(cfg.getRemetenteEmail(), cfg.getRemetenteNome(), StandardCharsets.UTF_8.name()));
        if (cfg.getResponderPara() != null && !cfg.getResponderPara().isBlank()) {
            msg.setReplyTo(InternetAddress.parse(cfg.getResponderPara()));
        }
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(m.getPara(), true));
        msg.setSubject(m.getAssunto(), StandardCharsets.UTF_8.name());
        msg.setSentDate(new java.util.Date());
        msg.setHeader("Auto-Submitted", "auto-generated");
        msg.setHeader("X-Auto-Response-Suppress", "All");
        if (m.getChamadoId() != null) {
            String fio = "<coti.chamado." + m.getChamadoId() + "@" + NotificacaoEmailService.dominio(cfg.getRemetenteEmail()) + ">";
            msg.setHeader("In-Reply-To", fio);
            msg.setHeader("References", fio);
            msg.setHeader("X-CO-TI-Chamado", String.valueOf(m.getChamadoId()));
        }
        MimeMultipart alternativa = new MimeMultipart("alternative");
        MimeBodyPart texto = new MimeBodyPart();
        texto.setText(HtmlSeguro.texto(m.getCorpo()), StandardCharsets.UTF_8.name());
        MimeBodyPart html = new MimeBodyPart();
        html.setContent(m.getCorpo(), "text/html; charset=UTF-8");
        alternativa.addBodyPart(texto);
        alternativa.addBodyPart(html);
        msg.setContent(alternativa);
        msg.saveChanges();
        Transport t = sessao.getTransport();
        try {
            String usuario = cfg.getSmtpUsuario();
            if (usuario != null && !usuario.isBlank()) {
                String senha = cfg.getSmtpSenhaCifrada() == null ? "" : cifrador.decifrar(cfg.getSmtpSenhaCifrada(), CONTEXTO_SENHA);
                t.connect(cfg.getSmtpHost(), cfg.getSmtpPorta(), usuario, senha);
            } else {
                t.connect();
            }
            t.sendMessage(msg, msg.getAllRecipients());
        } finally {
            t.close();
        }
    }

    private static String limitar(String s) {
        return s == null ? null : s.length() > 1000 ? s.substring(0, 1000) : s;
    }

}
