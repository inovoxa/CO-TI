package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.module.itsm.dal.mysql.PainelMapper;
import cn.iocoder.yudao.module.itsm.dominio.Prioridade;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.PAINEL_PERIODO_INVALIDO;

/**
 * Indicadores do Service Desk para o período (GLPI: Stat e Dashboard). O escopo de unidade do perfil vale em
 * todas as consultas (plugin de permissão de dados sobre itsm.chamado).
 */
@Service
public class PainelService {

    private static final int MAX_DIAS = 366;

    @Resource
    private PainelMapper painelMapper;
    @Resource
    private CadastroService cadastros;
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * @param de  primeiro dia (inclusive)
     * @param ate último dia (inclusive)
     */
    public Map<String, Object> painel(LocalDate de, LocalDate ate, Long deptId, Long categoriaId) {
        if (de == null || ate == null || ate.isBefore(de) || de.plusDays(MAX_DIAS).isBefore(ate)) {
            throw exception(PAINEL_PERIODO_INVALIDO);
        }
        LocalDateTime ini = de.atStartOfDay();
        LocalDateTime fim = ate.plusDays(1).atStartOfDay();
        List<Long> cats = categoriaId == null ? null : new ArrayList<>(cadastros.descendentes(categoriaId));

        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> resumo = camel(painelMapper.resumo(ini, fim, deptId, cats));
        resumo.put("percentualNoPrazo", percentual(resumo.get("noPrazo"), resumo.get("comPrazo")));
        resumo.put("percentualAtendimentoNoPrazo", percentual(resumo.get("atendidosNoPrazo"), resumo.get("comPrazoAtendimento")));
        r.put("resumo", resumo);
        Map<String, Object> sat = camel(painelMapper.satisfacao(ini, fim, deptId, cats));
        sat.put("percentualResposta", percentual(sat.get("respondidas"), sat.get("enviadas")));
        r.put("satisfacao", sat);

        // série diária com todos os dias do período, inclusive os sem movimento
        Map<String, long[]> dias = new LinkedHashMap<>();
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            dias.put(d.toString(), new long[2]);
        }
        painelMapper.porDia("aberto_em", ini, fim, deptId, cats)
                .forEach(x -> Optional.ofNullable(dias.get(String.valueOf(x.get("dia")))).ifPresent(v -> v[0] = numero(x.get("total"))));
        painelMapper.porDia("solucionado_em", ini, fim, deptId, cats)
                .forEach(x -> Optional.ofNullable(dias.get(String.valueOf(x.get("dia")))).ifPresent(v -> v[1] = numero(x.get("total"))));
        List<Map<String, Object>> serie = new ArrayList<>();
        dias.forEach((d, v) -> serie.add(Map.of("dia", d, "abertos", v[0], "solucionados", v[1])));
        r.put("serie", serie);

        r.put("porCategoria", lista(painelMapper.porCategoria(ini, fim, deptId, cats)));
        r.put("porOrigem", lista(painelMapper.porOrigem(ini, fim, deptId, cats)));
        List<Map<String, Object>> prio = lista(painelMapper.porPrioridade(deptId, cats));
        prio.forEach(p -> p.put("nome", Prioridade.rotulo((int) numero(p.get("prioridade")))));
        r.put("porPrioridade", prio);
        r.put("idade", camel(painelMapper.idadeDosAbertos(deptId, cats)));
        List<Map<String, Object>> tec = lista(painelMapper.porTecnico(ini, fim, deptId, cats));
        Set<Long> ids = new HashSet<>();
        tec.forEach(t -> ids.add(numero(t.get("userId"))));
        Map<Long, AdminUserRespDTO> nomes = ids.isEmpty() ? Map.of() : adminUserApi.getUserMap(ids);
        tec.forEach(t -> {
            AdminUserRespDTO u = nomes.get(numero(t.get("userId")));
            t.put("nome", u == null ? "#" + t.get("userId") : u.getNickname());
        });
        r.put("porTecnico", tec);
        r.put("porEquipe", lista(painelMapper.porEquipe(ini, fim, deptId, cats)));
        return r;
    }

    // colunas do PostgreSQL (sem_tecnico) → camelCase (semTecnico), números como Long/Double
    private static Map<String, Object> camel(Map<String, Object> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (m == null) {
            return out;
        }
        m.forEach((k, v) -> {
            StringBuilder sb = new StringBuilder();
            boolean subir = false;
            for (char ch : k.toCharArray()) {
                if (ch == '_') {
                    subir = true;
                } else {
                    sb.append(subir ? Character.toUpperCase(ch) : ch);
                    subir = false;
                }
            }
            Object valor = v;
            if (v instanceof java.math.BigDecimal || v instanceof Double || v instanceof Float) {
                valor = Math.round(((Number) v).doubleValue() * 100) / 100.0;
            } else if (v instanceof Number n) {
                valor = n.longValue(); // count, int2, int4
            }
            out.put(sb.toString(), valor);
        });
        return out;
    }

    private static List<Map<String, Object>> lista(List<Map<String, Object>> l) {
        return new ArrayList<>(l.stream().map(PainelService::camel).toList());
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static Double percentual(Object parte, Object total) {
        long t = numero(total);
        return t == 0 ? null : Math.round(numero(parte) * 1000.0 / t) / 10.0;
    }

}
