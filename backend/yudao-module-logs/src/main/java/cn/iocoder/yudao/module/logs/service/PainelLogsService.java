package cn.iocoder.yudao.module.logs.service;

import cn.iocoder.yudao.module.logs.controller.admin.vo.LogsVOs;
import cn.iocoder.yudao.module.logs.dominio.Consulta;
import cn.iocoder.yudao.module.logs.dominio.Fluxos;
import cn.iocoder.yudao.module.logs.dominio.Periodo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.logs.enums.ErrorCodeConstants.PAINEL_INVALIDO;
import static cn.iocoder.yudao.module.logs.enums.ErrorCodeConstants.PAINEL_NAO_EXISTE;

/**
 * Painéis (abas) e seus quadros. Os quadros ficam num JSON por painel; ao salvar, cada um é conferido (tipo, consulta,
 * campo, período) para um quadro quebrado não chegar à tela.
 */
@Service
public class PainelLogsService {

    static final Set<String> TIPOS = Set.of("contador", "mensagens", "ranking", "histograma");
    private static final int MAXIMO_QUADROS = 40;
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private JdbcTemplate jdbcTemplate;

    public List<LogsVOs.Painel> listar() {
        return jdbcTemplate.query("SELECT id, nome, ordem, widgets::text AS widgets FROM logs.painel WHERE deleted = 0 "
                + "ORDER BY ordem, id", (rs, i) -> {
            LogsVOs.Painel p = new LogsVOs.Painel();
            p.setId(rs.getLong("id"));
            p.setNome(rs.getString("nome"));
            p.setOrdem(rs.getInt("ordem"));
            p.setWidgets(lerQuadros(rs.getString("widgets")));
            return p;
        });
    }

    public Long salvar(LogsVOs.Painel req, Long userId) {
        validar(req.getWidgets());
        String quadros;
        try {
            quadros = JSON.writeValueAsString(req.getWidgets());
        } catch (Exception e) {
            throw exception(PAINEL_INVALIDO, e.getMessage());
        }
        String nome = req.getNome().strip();
        if (req.getId() == null) {
            Long id = jdbcTemplate.queryForObject("SELECT nextval('logs.painel_seq')", Long.class);
            Integer ordem = req.getOrdem() != null ? req.getOrdem()
                    : jdbcTemplate.queryForObject("SELECT coalesce(max(ordem), 0) + 1 FROM logs.painel WHERE deleted = 0", Integer.class);
            jdbcTemplate.update("INSERT INTO logs.painel (id, nome, ordem, widgets, creator, updater) VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?)",
                    id, nome, ordem, quadros, String.valueOf(userId), String.valueOf(userId));
            return id;
        }
        int n = jdbcTemplate.update("UPDATE logs.painel SET nome = ?, ordem = coalesce(?, ordem), widgets = CAST(? AS jsonb), "
                + "updater = ?, update_time = now() WHERE id = ? AND deleted = 0", nome, req.getOrdem(), quadros,
                String.valueOf(userId), req.getId());
        if (n == 0) {
            throw exception(PAINEL_NAO_EXISTE);
        }
        return req.getId();
    }

    public void excluir(Long id, Long userId) {
        if (jdbcTemplate.update("UPDATE logs.painel SET deleted = 1, updater = ?, update_time = now() WHERE id = ? AND deleted = 0",
                String.valueOf(userId), id) == 0) {
            throw exception(PAINEL_NAO_EXISTE);
        }
    }

    /** Ordem das abas: a lista de ids na nova ordem. */
    public void ordenar(List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            jdbcTemplate.update("UPDATE logs.painel SET ordem = ? WHERE id = ?", i + 1, ids.get(i));
        }
    }

    static void validar(List<Map<String, Object>> quadros) {
        if (quadros.size() > MAXIMO_QUADROS) {
            throw exception(PAINEL_INVALIDO, "no máximo " + MAXIMO_QUADROS + " quadros por painel");
        }
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> q : quadros) {
            String titulo = texto(q, "titulo");
            String onde = titulo == null ? "um quadro" : "\"" + titulo + "\"";
            if (titulo == null || titulo.isBlank() || titulo.length() > 80) {
                throw exception(PAINEL_INVALIDO, "todo quadro precisa de título (até 80 letras)");
            }
            String id = texto(q, "id");
            if (id == null || !ids.add(id)) {
                throw exception(PAINEL_INVALIDO, onde + " sem identificador único");
            }
            String tipo = texto(q, "tipo");
            if (!TIPOS.contains(tipo)) {
                throw exception(PAINEL_INVALIDO, onde + ": tipo desconhecido");
            }
            String fluxo = texto(q, "fluxo");
            if (fluxo != null && !fluxo.isBlank() && !Fluxos.TODOS.contains(fluxo)) {
                throw exception(PAINEL_INVALIDO, onde + ": fluxo desconhecido");
            }
            try {
                Consulta.traduzir(texto(q, "consulta"));
                Periodo.de(texto(q, "periodo"), null, null, LocalDateTime.now());
                if (tipo.equals("ranking")) {
                    String campo = texto(q, "campo");
                    if (campo == null || campo.isBlank()) {
                        throw new IllegalArgumentException("o ranking precisa de um campo");
                    }
                    Consulta.campo(campo);
                }
                String distinto = texto(q, "distinto");
                if (distinto != null && !distinto.isBlank()) {
                    Consulta.campo(distinto);
                }
                String soma = texto(q, "soma");
                if (soma != null && !soma.isBlank() && !soma.equals(ConsultaLogsService.BYTES)
                        && (Consulta.ehColuna(soma) || Consulta.campo(soma) == null)) {
                    throw new IllegalArgumentException("só dá para somar um campo numérico dos logs");
                }
            } catch (IllegalArgumentException e) {
                throw exception(PAINEL_INVALIDO, onde + ": " + e.getMessage());
            }
            Object largura = q.get("largura");
            if (largura != null && !(largura instanceof Number n && n.intValue() >= 1 && n.intValue() <= 4)) {
                throw exception(PAINEL_INVALIDO, onde + ": largura de 1 a 4 colunas");
            }
        }
    }

    private static String texto(Map<String, Object> q, String chave) {
        Object v = q.get(chave);
        return v == null ? null : v.toString();
    }

    private static List<Map<String, Object>> lerQuadros(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

}
