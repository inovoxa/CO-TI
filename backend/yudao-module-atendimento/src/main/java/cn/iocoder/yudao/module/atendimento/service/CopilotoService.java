package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.MensagemMapper;
import cn.iocoder.yudao.module.atendimento.dominio.PromptsIa;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Copiloto do atendente: sugere resposta, resume a conversa e reescreve o texto digitado. Nunca envia nada sozinho.
 */
@Service
public class CopilotoService {

    @Resource
    private ConversaService conversaService;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private Acesso acesso;
    @Resource
    private IaService iaService;
    @Resource
    private FerramentasAssistente ferramentas;

    public String sugerir(Long conversaId, Long userId) {
        ConversaDO c = acessivel(conversaId, userId);
        List<MensagemDO> hist = historico(c.getId());
        String ultimaDoContato = hist.stream().filter(m -> m.getTipo() == MensagemTipo.ENTRADA).reduce((a, b) -> b)
                .map(MensagemDO::getConteudo).orElse("");
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(PromptsIa.mensagem("system", PromptsIa.sistemaSugerir(ferramentas.artigos(ultimaDoContato, false, 3))));
        msgs.addAll(PromptsIa.historico(falas(hist)));
        return chamar(msgs, "sugerir", c.getId(), userId);
    }

    public String resumir(Long conversaId, Long userId) {
        ConversaDO c = acessivel(conversaId, userId);
        StringBuilder t = new StringBuilder();
        for (MensagemDO m : historico(c.getId())) {
            t.append(m.getTipo() == MensagemTipo.ENTRADA ? "Contato: " : "Central: ").append(m.getConteudo()).append('\n');
        }
        return chamar(List.of(PromptsIa.mensagem("system", PromptsIa.sistemaResumir()), PromptsIa.mensagem("user", t.toString())),
                "resumir", c.getId(), userId);
    }

    public String reescrever(Long conversaId, String texto, String estilo, Long userId) {
        ConversaDO c = acessivel(conversaId, userId);
        if (texto == null || texto.isBlank()) {
            throw exception(CONVERSA_VAZIA);
        }
        return chamar(List.of(PromptsIa.mensagem("system", PromptsIa.sistemaReescrever(estilo)), PromptsIa.mensagem("user", texto.strip())),
                "reescrever", c.getId(), userId);
    }

    private ConversaDO acessivel(Long conversaId, Long userId) {
        ConversaDO c = conversaService.existente(conversaId);
        acesso.exigir(c, userId);
        if (!iaService.ativo()) {
            throw exception(IA_DESLIGADA);
        }
        return c;
    }

    private List<MensagemDO> historico(Long conversaId) {
        List<MensagemDO> l = new ArrayList<>(mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, conversaId)
                .eq(MensagemDO::getPrivada, false).in(MensagemDO::getTipo, MensagemTipo.ENTRADA, MensagemTipo.SAIDA)
                .orderByDesc(MensagemDO::getId).last("LIMIT 30")));
        Collections.reverse(l);
        return l;
    }

    private static List<PromptsIa.Fala> falas(List<MensagemDO> hist) {
        return hist.stream().map(m -> new PromptsIa.Fala(m.getTipo() == MensagemTipo.ENTRADA, m.getConteudo())).toList();
    }

    private String chamar(List<Map<String, Object>> msgs, String finalidade, Long conversaId, Long userId) {
        try {
            String t = PromptsIa.paraCanal(iaService.conversar(msgs, null, finalidade, conversaId, userId).conteudo());
            if (t.isEmpty()) {
                throw exception(IA_FALHOU, "o modelo respondeu sem texto");
            }
            return t;
        } catch (IllegalStateException e) {
            throw exception(IA_FALHOU, e.getMessage());
        }
    }

}
