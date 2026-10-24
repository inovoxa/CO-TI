package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.dal.mysql.AgenteStatusMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Disponibilidade do atendente: ele escolhe (disponível, ocupado, ausente) e a tela de conversas manda um sinal por
 * minuto. Sem sinal há 3 minutos (navegador fechado), deixa de receber conversas automaticamente.
 */
@Service
public class AgenteService {

    private static final Set<String> VALORES = Set.of("online", "ocupado", "offline");

    @Resource
    private AgenteStatusMapper agenteStatusMapper;

    public void definir(Long userId, String disponibilidade) {
        agenteStatusMapper.definir(userId, VALORES.contains(disponibilidade) ? disponibilidade : "offline");
    }

    /** Sinal de vida; quem nunca escolheu começa disponível ao abrir a tela. */
    public String sinal(Long userId) {
        if (agenteStatusMapper.sinal(userId) == 0) {
            agenteStatusMapper.definir(userId, "online");
            return "online";
        }
        List<Map<String, Object>> s = agenteStatusMapper.listar(List.of(userId));
        return s.isEmpty() ? "offline" : (String) s.get(0).get("disponibilidade");
    }

}
