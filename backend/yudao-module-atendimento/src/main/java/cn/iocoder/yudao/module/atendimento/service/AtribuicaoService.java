package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.AgenteStatusMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Atribuicao;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Atribuição automática de conversas (docs/specs/atendimento-atribuicao.md).
 */
@Service
public class AtribuicaoService {

    @Resource
    private Acesso acesso;
    @Resource
    private AgenteStatusMapper agenteStatusMapper;
    @Resource
    private ConversaMapper conversaMapper;

    /** Escolhe e grava o atendente; vazio se ninguém está disponível (fica em "Não atribuídas"). */
    public Optional<Long> distribuir(ConversaDO c, CaixaDO caixa) {
        Set<Long> membros = acesso.membros(caixa.getId());
        if (membros.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime agora = LocalDateTime.now();
        Map<Long, Map<String, Object>> status = new HashMap<>();
        agenteStatusMapper.listar(membros).forEach(r -> status.put(((Number) r.get("user_id")).longValue(), r));
        Map<Long, int[]> abertas = new HashMap<>();
        conversaMapper.abertasPorAgente(caixa.getId(), membros).forEach(r -> abertas.put(((Number) r.get("agente_id")).longValue(),
                new int[]{((Number) r.get("na_caixa")).intValue(), ((Number) r.get("total")).intValue()}));
        List<Atribuicao.Candidato> candidatos = new ArrayList<>();
        for (Long u : membros) {
            Map<String, Object> s = status.get(u);
            int[] n = abertas.getOrDefault(u, new int[]{0, 0});
            candidatos.add(new Atribuicao.Candidato(u,
                    s != null && Atribuicao.disponivel((String) s.get("disponibilidade"), data(s.get("ultimo_sinal")), agora),
                    n[0], n[1], s == null ? null : data(s.get("ultima_atribuicao"))));
        }
        Optional<Long> escolhido = Atribuicao.escolher(candidatos, caixa.getLimitePorAgente() == null ? 0 : caixa.getLimitePorAgente());
        escolhido.ifPresent(u -> {
            conversaMapper.updateById(ConversaDO.builder().id(c.getId()).agenteId(u).build());
            c.setAgenteId(u);
            agenteStatusMapper.marcarAtribuicao(u);
        });
        return escolhido;
    }

    private static LocalDateTime data(Object v) {
        return v instanceof LocalDateTime d ? d : v instanceof java.sql.Timestamp t ? t.toLocalDateTime() : null;
    }

}
