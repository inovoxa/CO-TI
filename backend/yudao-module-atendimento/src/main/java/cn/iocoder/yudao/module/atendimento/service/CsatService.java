package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Csat;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Pesquisa de satisfação pelo próprio canal (Chatwoot: CSAT). Ao resolver, a caixa com a pesquisa ligada pergunta a
 * nota de 1 a 5; a resposta que é claramente uma nota ({@link Csat#nota}) é guardada e não reabre a conversa.
 */
@Service
public class CsatService {

    @Resource
    private PesquisaMapper pesquisaMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private ConversaService conversaService;

    /** Conversa resolvida: manda a pergunta, se a caixa pede e o contato chegou a escrever (não é só histórico). */
    void aoResolver(ConversaDO c, CaixaDO caixa) {
        if (caixa == null || !Boolean.TRUE.equals(caixa.getCsatAtivo())) {
            return;
        }
        ContatoDO contato = contatoMapper.selectById(c.getContatoId());
        if (contato == null || Boolean.TRUE.equals(contato.getBloqueado())) {
            return;
        }
        boolean contatoEscreveu = mensagemMapper.selectCount(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, c.getId())
                .eq(MensagemDO::getTipo, MensagemTipo.ENTRADA).notLike(MensagemDO::getDados, "\"historico\":true")) > 0;
        if (!contatoEscreveu) {
            return;
        }
        PesquisaDO p = PesquisaDO.builder().conversaId(c.getId()).caixaId(c.getCaixaId()).agenteId(c.getAgenteId())
                .contatoId(c.getContatoId()).enviadaEm(LocalDateTime.now()).build();
        pesquisaMapper.insert(p);
        String pergunta = caixa.getCsatMensagem() == null || caixa.getCsatMensagem().isBlank() ? Csat.PERGUNTA_PADRAO : caixa.getCsatMensagem();
        conversaService.saida(c, pergunta, "sistema", null, List.of(), Map.of("csat", Map.of("pesquisa", p.getId())));
    }

    /**
     * O contato escreveu numa conversa resolvida: se há pesquisa esperando resposta e a mensagem é uma nota, guarda.
     *
     * @return a nota, ou null (mensagem comum: segue o fluxo normal)
     */
    Integer responder(ConversaDO c, String texto) {
        Integer nota = Csat.nota(texto);
        if (nota == null) {
            return null;
        }
        PesquisaDO p = pesquisaMapper.selectOne(new LambdaQueryWrapperX<PesquisaDO>().eq(PesquisaDO::getConversaId, c.getId())
                .isNull(PesquisaDO::getNota).ge(PesquisaDO::getEnviadaEm, LocalDateTime.now().minusDays(Csat.DIAS_PARA_RESPONDER))
                .orderByDesc(PesquisaDO::getId).last("LIMIT 1"));
        if (p == null) {
            return null;
        }
        pesquisaMapper.updateById(PesquisaDO.builder().id(p.getId()).nota(nota).respondidaEm(LocalDateTime.now()).build());
        return nota;
    }

}
