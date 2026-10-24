package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.MensagemMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Entrada do canal API: um sistema externo, autenticado pelo token da caixa, entrega a mensagem do contato ou
 * avisa que uma resposta foi entregue/lida.
 */
@Service
public class EntradaApiService {

    private static final long ANEXO_MAXIMO = 10L * 1024 * 1024;

    @Resource
    private CaixaService caixaService;
    @Resource
    private ConversaService conversaService;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private FileApi fileApi;

    public AtendimentoVOs.EntradaApiResp receber(String token, AtendimentoVOs.EntradaApi req) {
        CaixaDO caixa = caixaService.peloToken(token);
        String conteudo = req.getConteudo() == null ? "" : req.getConteudo().strip();
        if (conteudo.isEmpty() && req.getAnexos().isEmpty()) {
            throw exception(CONVERSA_VAZIA);
        }
        // anexos vêm no corpo (Base64): o CO-TI não busca endereços informados por terceiros
        List<AtendimentoVOs.Anexo> anexos = new ArrayList<>();
        for (AtendimentoVOs.AnexoApi a : req.getAnexos()) {
            byte[] dados;
            try {
                dados = Base64.getDecoder().decode(a.getDados().replaceAll("\\s", ""));
            } catch (IllegalArgumentException e) {
                throw exception(CONVERSA_ANEXO_INVALIDO, "Anexo \"" + a.getNome() + "\" não está em Base64");
            }
            if (dados.length == 0 || dados.length > ANEXO_MAXIMO) {
                throw exception(CONVERSA_ANEXO_INVALIDO, "Anexo \"" + a.getNome() + "\" vazio ou maior que 10 MB");
            }
            AtendimentoVOs.Anexo x = new AtendimentoVOs.Anexo();
            x.setNome(a.getNome());
            x.setTipo(a.getTipo());
            x.setTamanho((long) dados.length);
            x.setUrl(fileApi.createFile(dados, a.getNome(), "atendimento", a.getTipo()));
            anexos.add(x);
        }
        AtendimentoVOs.ContatoApi ct = req.getContato();
        ConversaService.Entrada e = conversaService.receber(caixa, req.getOrigemId().trim(),
                new ContatoService.Origem(ct.getNome(), ct.getTelefone(), ct.getEmail(), ct.getIdentificador()), conteudo,
                anexos.isEmpty() ? "texto" : formato(anexos.get(0).getTipo()), anexos, req.getIdExterno());
        AtendimentoVOs.EntradaApiResp r = new AtendimentoVOs.EntradaApiResp();
        r.setConversaId(e.conversaId());
        r.setMensagemId(e.mensagemId());
        r.setDuplicada(e.duplicada());
        return r;
    }

    /** O sistema externo avisa que a resposta (que ele identificou com idExterno) foi entregue, lida ou falhou. */
    public boolean status(String token, AtendimentoVOs.StatusApi req) {
        CaixaDO caixa = caixaService.peloToken(token);
        for (MensagemDO m : mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getIdExterno, req.getIdExterno()))) {
            ConversaDO c = conversaMapper.selectById(m.getConversaId());
            if (c != null && c.getCaixaId().equals(caixa.getId())) {
                conversaService.atualizarEntrega(m.getId(), req.getStatus(), req.getErro(), null);
                return true;
            }
        }
        return false;
    }

    private static String formato(String mime) {
        String t = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        return t.startsWith("image/") ? "imagem" : t.startsWith("audio/") ? "audio" : t.startsWith("video/") ? "video" : "arquivo";
    }

}
