package cn.iocoder.yudao.module.itsm.service.email;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailCaixaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailConfigDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailRecebidoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailCaixaMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailConfigMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailRecebidoMapper;
import cn.iocoder.yudao.module.itsm.dominio.Email;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FlagTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Coletor de e-mail por IMAP (GLPI: MailCollector). Para cada mensagem não lida:
 * <ol>
 *   <li>ignora as automáticas (férias, listas, avisos de entrega, as próprias notificações);</li>
 *   <li>com "exigir autenticação", recusa remetente sem DMARC/DKIM aprovado (e-mail forjado);</li>
 *   <li>resposta a um chamado (número no assunto ou cabeçalhos) de quem participa vira acompanhamento;</li>
 *   <li>o resto vira chamado novo — de quem tem conta no portal ou, se a caixa aceitar, de qualquer endereço.</li>
 * </ol>
 * Cada mensagem é registrada (itsm.email_recebido) e nunca é processada duas vezes.
 */
@Slf4j
@Service
public class ColetorEmailService {

    public static final String CONTEXTO_SENHA = "email-imap";
    private static final int POR_RODADA = 50;
    private static final int MAX_CORPO = 200_000;

    @Resource
    private EmailCaixaMapper emailCaixaMapper;
    @Resource
    private EmailConfigMapper emailConfigMapper;
    @Resource
    private EmailRecebidoMapper emailRecebidoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private FileApi fileApi;
    @Resource
    private TransactionTemplate transacao;

    /** Todas as caixas ativas. @return mensagens processadas */
    public int coletarTodas() {
        int n = 0;
        for (EmailCaixaDO c : emailCaixaMapper.selectList(EmailCaixaDO::getAtivo, true)) {
            n += coletar(c);
        }
        return n;
    }

    public int coletar(EmailCaixaDO caixa) {
        int n = 0;
        String erro = null;
        Store store = null;
        try {
            store = conectar(caixa);
            Folder pasta = store.getFolder(caixa.getPasta());
            pasta.open(Folder.READ_WRITE);
            try {
                Message[] novas = pasta.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
                for (int i = 0; i < Math.min(novas.length, POR_RODADA); i++) {
                    Message m = novas[i];
                    String resultado = processarComSeguranca(caixa, m);
                    m.setFlag(Flags.Flag.SEEN, true);
                    String destino = "recusado".equals(resultado) ? caixa.getPastaRecusados() : caixa.getPastaProcessados();
                    if (destino != null && !destino.isBlank()) {
                        Folder f = store.getFolder(destino);
                        if (!f.exists()) {
                            f.create(Folder.HOLDS_MESSAGES);
                        }
                        pasta.copyMessages(new Message[]{m}, f);
                        m.setFlag(Flags.Flag.DELETED, true);
                    }
                    n++;
                }
            } finally {
                pasta.close(true); // expunge: tira da caixa o que foi movido
            }
        } catch (Exception e) {
            erro = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("[coletar][caixa {}: {}]", caixa.getNome(), erro);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException ignored) {
                    // fechando de qualquer jeito
                }
            }
        }
        emailCaixaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EmailCaixaDO>()
                .eq(EmailCaixaDO::getId, caixa.getId()).set(EmailCaixaDO::getUltimaColeta, LocalDateTime.now())
                .set(EmailCaixaDO::getUltimoErro, erro == null ? null : erro.length() > 1000 ? erro.substring(0, 1000) : erro));
        return n;
    }

    /** Testa a conexão e a pasta. @return null se deu certo; senão, o erro */
    public String testar(EmailCaixaDO caixa) {
        try {
            Store s = conectar(caixa);
            try {
                Folder f = s.getFolder(caixa.getPasta());
                if (!f.exists()) {
                    return "A pasta \"" + caixa.getPasta() + "\" não existe";
                }
                f.open(Folder.READ_ONLY);
                f.close(false);
            } finally {
                s.close();
            }
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private Store conectar(EmailCaixaDO c) throws MessagingException {
        Properties p = new Properties();
        String proto = "ssl".equals(c.getSeguranca()) ? "imaps" : "imap";
        p.put("mail.store.protocol", proto);
        p.put("mail." + proto + ".connectiontimeout", "15000");
        p.put("mail." + proto + ".timeout", "30000");
        if ("starttls".equals(c.getSeguranca())) {
            p.put("mail.imap.starttls.enable", "true");
            p.put("mail.imap.starttls.required", "true");
        }
        if (!"nenhuma".equals(c.getSeguranca())) {
            p.put("mail." + proto + ".ssl.checkserveridentity", "true");
        }
        Store s = Session.getInstance(p).getStore(proto);
        String senha = c.getSenhaCifrada() == null ? "" : cifrador.decifrar(c.getSenhaCifrada(), CONTEXTO_SENHA);
        s.connect(c.getHost(), c.getPorta(), c.getUsuario(), senha);
        return s;
    }

    // ------------------------------------------------------------------ uma mensagem

    /** Erro numa mensagem não para a coleta: ela fica registrada como recusada com o motivo. */
    private String processarComSeguranca(EmailCaixaDO caixa, Message m) {
        try {
            return DataPermissionUtils.executeIgnore(() -> transacao.execute(st -> {
                try {
                    return processar(caixa, m);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        } catch (Exception e) {
            log.warn("[processar][caixa {}: mensagem com erro: {}]", caixa.getNome(), e.getMessage());
            try {
                registrar(caixa, idDa(m), remetente(m), assunto(m), null, "recusado", "Erro ao ler: " + e.getMessage());
            } catch (Exception ignored) {
                // já registrada ou ilegível: segue
            }
            return "recusado";
        }
    }

    String processar(EmailCaixaDO caixa, Message m) throws Exception {
        String messageId = idDa(m);
        if (emailRecebidoMapper.selectCount(new LambdaQueryWrapperX<EmailRecebidoDO>().eq(EmailRecebidoDO::getCaixaId, caixa.getId())
                .eq(EmailRecebidoDO::getMessageId, messageId)) > 0) {
            return "ignorado";
        }
        String de = remetente(m);
        String assunto = assunto(m);
        Map<String, String> cab = cabecalhos(m);
        EmailConfigDO cfg = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        if (de == null) {
            return registrar(caixa, messageId, null, assunto, null, "recusado", "Sem remetente");
        }
        if (cfg != null && cfg.getRemetenteEmail() != null && de.equalsIgnoreCase(cfg.getRemetenteEmail())) {
            return registrar(caixa, messageId, de, assunto, null, "ignorado", "Enviado pelo próprio CO-TI");
        }
        Optional<String> auto = Email.automatico(cab, de);
        if (auto.isPresent()) {
            return registrar(caixa, messageId, de, assunto, null, "ignorado", auto.get());
        }
        if (Boolean.TRUE.equals(caixa.getExigirAutenticacao()) && !Email.remetenteAutenticado(cab.get("authentication-results"))) {
            return registrar(caixa, messageId, de, assunto, null, "recusado", "Remetente não autenticado (DMARC/DKIM)");
        }
        AdminUserDO usuario = adminUserMapper.selectOne(new LambdaQueryWrapperX<AdminUserDO>()
                .apply("lower(email) = lower({0})", de).last("LIMIT 1"));
        if (usuario != null && !CommonStatusEnum.isEnable(usuario.getStatus())) {
            return registrar(caixa, messageId, de, assunto, null, "recusado", "A conta do remetente está desativada");
        }
        Long userId = usuario == null ? null : usuario.getId();

        Corpo corpo = new Corpo();
        ler(m, corpo, caixa.getAnexoMaxMb() * 1024L * 1024L);
        String html = corpo.html != null ? Email.semCitacao(corpo.html) : Email.textoParaHtml(Email.semCitacao(corpo.texto));
        if (html.length() > MAX_CORPO) {
            html = html.substring(0, MAX_CORPO);
        }
        html = HtmlSeguro.limpar(trocarCid(html, corpo.cids));
        html += corpo.listaAnexos();

        Optional<Long> citado = Email.chamadoCitado(assunto, cab.get("in-reply-to"), cab.get("references"));
        if (citado.isPresent() && chamadoService.acompanhamentoDeEmail(citado.get(), userId, de, html)) {
            return registrar(caixa, messageId, de, assunto, citado.get(), "acompanhamento", null);
        }
        if (userId == null && !Boolean.TRUE.equals(caixa.getAceitarDesconhecidos())) {
            return registrar(caixa, messageId, de, assunto, null, "recusado", "Remetente sem conta no portal");
        }
        String descricao = citado.map(n -> "<p><i>Resposta ao chamado #" + n + ", que está fechado ou de que o remetente não participa.</i></p>")
                .orElse("") + html;
        Long id = chamadoService.criarDeEmail(Email.titulo(assunto), descricao, userId, de.toLowerCase(Locale.ROOT), caixa.getCategoriaId());
        return registrar(caixa, messageId, de, assunto, id, "chamado", null);
    }

    private String registrar(EmailCaixaDO caixa, String messageId, String de, String assunto, Long chamadoId,
                             String resultado, String motivo) {
        emailRecebidoMapper.insert(EmailRecebidoDO.builder().caixaId(caixa.getId()).messageId(messageId)
                .remetente(de).assunto(assunto == null ? null : assunto.length() > 400 ? assunto.substring(0, 400) : assunto)
                .chamadoId(chamadoId).resultado(resultado).motivo(motivo).recebidoEm(LocalDateTime.now()).build());
        return resultado;
    }

    // ------------------------------------------------------------------ leitura do MIME

    private static class Corpo {
        String html;
        String texto;
        final Map<String, String> cids = new HashMap<>();
        final List<String[]> anexos = new ArrayList<>(); // nome, url ou null (grande demais)

        String listaAnexos() {
            if (anexos.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("<p><b>Anexos</b></p><ul>");
            for (String[] a : anexos) {
                sb.append("<li>").append(a[1] == null ? HtmlUtils.htmlEscape(a[0]) + " (não guardado: maior que o limite)"
                        : "<a href=\"" + HtmlUtils.htmlEscape(a[1]) + "\">" + HtmlUtils.htmlEscape(a[0]) + "</a>").append("</li>");
            }
            return HtmlSeguro.limpar(sb.append("</ul>").toString());
        }
    }

    private void ler(Part p, Corpo c, long maxAnexo) throws Exception {
        String disp = p.getDisposition();
        String nome = p.getFileName() == null ? null : MimeUtility.decodeText(p.getFileName());
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                ler(mp.getBodyPart(i), c, maxAnexo);
            }
        } else if (p.isMimeType("message/rfc822")) {
            guardar(p, nome == null ? "mensagem.eml" : nome, c, maxAnexo);
        } else if (nome == null && !Part.ATTACHMENT.equalsIgnoreCase(disp) && p.isMimeType("text/html") && c.html == null) {
            c.html = String.valueOf(p.getContent());
        } else if (nome == null && !Part.ATTACHMENT.equalsIgnoreCase(disp) && p.isMimeType("text/plain") && c.texto == null) {
            c.texto = String.valueOf(p.getContent());
        } else if (nome != null || Part.ATTACHMENT.equalsIgnoreCase(disp) || Part.INLINE.equalsIgnoreCase(disp)) {
            String url = guardar(p, nome == null ? "anexo" : nome, c, maxAnexo);
            String[] cid = p instanceof jakarta.mail.internet.MimePart mp ? mp.getHeader("Content-ID") : null;
            if (url != null && cid != null && cid.length > 0) {
                c.cids.put(cid[0].replaceAll("[<>]", "").trim(), url);
            }
        }
    }

    private String guardar(Part p, String nome, Corpo c, long max) throws Exception {
        byte[] dados;
        try (InputStream in = p.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int lidos;
            while ((lidos = in.read(buf)) > 0) {
                total += lidos;
                if (total > max) {
                    c.anexos.add(new String[]{nome, null});
                    return null;
                }
                out.write(buf, 0, lidos);
            }
            dados = out.toByteArray();
        }
        if (dados.length == 0) {
            return null;
        }
        String tipo = p.getContentType() == null ? null : p.getContentType().split(";")[0].trim();
        String url = fileApi.createFile(dados, nome.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_"), "chamados/email", tipo);
        c.anexos.add(new String[]{nome, url});
        return url;
    }

    /** Imagens coladas no corpo (cid:) passam a apontar para o arquivo guardado. */
    private static String trocarCid(String html, Map<String, String> cids) {
        for (Map.Entry<String, String> e : cids.entrySet()) {
            html = html.replace("cid:" + e.getKey(), e.getValue());
        }
        return html;
    }

    private static Map<String, String> cabecalhos(Message m) throws MessagingException {
        Map<String, String> h = new HashMap<>();
        Enumeration<Header> e = m.getAllHeaders();
        while (e.hasMoreElements()) {
            Header x = e.nextElement();
            h.merge(x.getName().toLowerCase(Locale.ROOT), x.getValue(), (a, b) -> a + " " + b);
        }
        return h;
    }

    private static String idDa(Message m) {
        try {
            String[] id = m.getHeader("Message-ID");
            if (id != null && id.length > 0 && !id[0].isBlank()) {
                return id[0].trim().length() > 990 ? id[0].trim().substring(0, 990) : id[0].trim();
            }
            // sem Message-ID: impressão digital de data, remetente e assunto
            String base = m.getSentDate() + "|" + Arrays.toString(m.getFrom()) + "|" + m.getSubject();
            byte[] d = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return "sem-id:" + HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "sem-id:" + UUID.randomUUID();
        }
    }

    private static String remetente(Message m) {
        try {
            Address[] de = m.getFrom();
            if (de != null && de.length > 0 && de[0] instanceof InternetAddress a && a.getAddress() != null) {
                return a.getAddress().trim().toLowerCase(Locale.ROOT);
            }
        } catch (MessagingException ignored) {
            // cabeçalho From inválido
        }
        return null;
    }

    private static String assunto(Message m) {
        try {
            return m.getSubject();
        } catch (MessagingException e) {
            return null;
        }
    }

}
