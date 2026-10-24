package cn.iocoder.yudao.module.itsm.service.email;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.EmailVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailCaixaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailConfigDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailEventoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailRecebidoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailCaixaMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailConfigMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailEventoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EmailRecebidoMapper;
import cn.iocoder.yudao.module.itsm.service.CadastroService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Configuração de e-mail. As senhas (SMTP e IMAP) são cifradas com a chave-mestra e nunca voltam para a tela.
 */
@Service
public class EmailConfigService {

    @Resource
    private EmailConfigMapper emailConfigMapper;
    @Resource
    private EmailEventoMapper emailEventoMapper;
    @Resource
    private EmailCaixaMapper emailCaixaMapper;
    @Resource
    private EmailRecebidoMapper emailRecebidoMapper;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private CadastroService cadastros;

    public EmailVOs.Config config() {
        EmailConfigDO c = emailConfigMapper.selectById(EmailConfigDO.UNICA);
        EmailVOs.Config v = new EmailVOs.Config();
        v.setAtivo(c.getAtivo());
        v.setSmtpHost(c.getSmtpHost());
        v.setSmtpPorta(c.getSmtpPorta());
        v.setSmtpSeguranca(c.getSmtpSeguranca());
        v.setSmtpUsuario(c.getSmtpUsuario());
        v.setSmtpSenhaDefinida(c.getSmtpSenhaCifrada() != null);
        v.setRemetenteEmail(c.getRemetenteEmail());
        v.setRemetenteNome(c.getRemetenteNome());
        v.setResponderPara(c.getResponderPara());
        v.setUrlPortal(c.getUrlPortal());
        v.setAssinatura(c.getAssinatura());
        return v;
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvarConfig(EmailVOs.Config v) {
        if (Boolean.TRUE.equals(v.getAtivo()) && (blank(v.getSmtpHost()) || blank(v.getRemetenteEmail()))) {
            throw exception(EMAIL_CONFIG_INCOMPLETA);
        }
        EmailConfigDO c = EmailConfigDO.builder().id(EmailConfigDO.UNICA).ativo(v.getAtivo()).smtpHost(v.getSmtpHost())
                .smtpPorta(v.getSmtpPorta()).smtpSeguranca(v.getSmtpSeguranca()).smtpUsuario(vazioNulo(v.getSmtpUsuario()))
                .remetenteEmail(vazioNulo(v.getRemetenteEmail())).remetenteNome(v.getRemetenteNome())
                .responderPara(vazioNulo(v.getResponderPara())).urlPortal(vazioNulo(v.getUrlPortal()))
                .assinatura(vazioNulo(v.getAssinatura())).updateTime(LocalDateTime.now()).build();
        if (!blank(v.getSmtpSenha())) {
            c.setSmtpSenhaCifrada(cifrar(v.getSmtpSenha(), EnvioEmailService.CONTEXTO_SENHA));
        }
        emailConfigMapper.updateById(c);
    }

    public List<EmailEventoDO> eventos() {
        return emailEventoMapper.selectList(new LambdaQueryWrapperX<EmailEventoDO>().orderByAsc(EmailEventoDO::getOrdem));
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvarEvento(EmailVOs.Evento v) {
        EmailEventoDO e = emailEventoMapper.selectById(v.getEvento());
        if (e == null) {
            throw exception(EMAIL_EVENTO_NAO_EXISTE);
        }
        e.setAtivo(v.getAtivo());
        e.setParaSolicitante(v.getParaSolicitante());
        e.setParaObservador(v.getParaObservador());
        e.setParaTecnico(v.getParaTecnico());
        e.setParaAprovador(v.getParaAprovador());
        e.setAssunto(v.getAssunto());
        e.setCorpo(v.getCorpo());
        emailEventoMapper.updateById(e);
    }

    public List<EmailVOs.Caixa> caixas() {
        return emailCaixaMapper.selectList(new LambdaQueryWrapperX<EmailCaixaDO>().orderByAsc(EmailCaixaDO::getNome)).stream()
                .map(c -> {
                    EmailVOs.Caixa v = new EmailVOs.Caixa();
                    v.setId(c.getId());
                    v.setNome(c.getNome());
                    v.setAtivo(c.getAtivo());
                    v.setHost(c.getHost());
                    v.setPorta(c.getPorta());
                    v.setSeguranca(c.getSeguranca());
                    v.setUsuario(c.getUsuario());
                    v.setSenhaDefinida(c.getSenhaCifrada() != null);
                    v.setPasta(c.getPasta());
                    v.setPastaProcessados(c.getPastaProcessados());
                    v.setPastaRecusados(c.getPastaRecusados());
                    v.setAceitarDesconhecidos(c.getAceitarDesconhecidos());
                    v.setExigirAutenticacao(c.getExigirAutenticacao());
                    v.setCategoriaId(c.getCategoriaId());
                    v.setAnexoMaxMb(c.getAnexoMaxMb());
                    v.setUltimaColeta(c.getUltimaColeta());
                    v.setUltimoErro(c.getUltimoErro());
                    return v;
                }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarCaixa(EmailVOs.Caixa v) {
        if (v.getCategoriaId() != null) {
            cadastros.obterCategoria(v.getCategoriaId());
        }
        EmailCaixaDO c = EmailCaixaDO.builder().id(v.getId()).nome(v.getNome().trim()).ativo(v.getAtivo()).host(v.getHost().trim())
                .porta(v.getPorta()).seguranca(v.getSeguranca()).usuario(v.getUsuario().trim()).pasta(v.getPasta().trim())
                .pastaProcessados(vazioNulo(v.getPastaProcessados())).pastaRecusados(vazioNulo(v.getPastaRecusados()))
                .aceitarDesconhecidos(v.getAceitarDesconhecidos()).exigirAutenticacao(v.getExigirAutenticacao())
                .categoriaId(v.getCategoriaId()).anexoMaxMb(v.getAnexoMaxMb()).build();
        if (!blank(v.getSenha())) {
            c.setSenhaCifrada(cifrar(v.getSenha(), ColetorEmailService.CONTEXTO_SENHA));
        }
        if (v.getId() == null) {
            emailCaixaMapper.insert(c);
        } else {
            caixaObrigatoria(v.getId());
            emailCaixaMapper.updateById(c);
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirCaixa(Long id) {
        caixaObrigatoria(id);
        emailCaixaMapper.deleteById(id);
    }

    public EmailCaixaDO caixaObrigatoria(Long id) {
        EmailCaixaDO c = id == null ? null : emailCaixaMapper.selectById(id);
        if (c == null) {
            throw exception(EMAIL_CAIXA_NAO_EXISTE);
        }
        return c;
    }

    public PageResult<EmailRecebidoDO> recebidos(PageParam p, Long caixaId, String resultado) {
        return emailRecebidoMapper.selectPage(p, new LambdaQueryWrapperX<EmailRecebidoDO>()
                .eqIfPresent(EmailRecebidoDO::getCaixaId, caixaId).eqIfPresent(EmailRecebidoDO::getResultado, resultado)
                .orderByDesc(EmailRecebidoDO::getId));
    }

    private String cifrar(String senha, String contexto) {
        if (!cifrador.configurado()) {
            throw exception(EMAIL_SEM_CHAVE_MESTRA);
        }
        return cifrador.cifrar(senha, contexto);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String vazioNulo(String s) {
        return blank(s) ? null : s.trim();
    }

}
