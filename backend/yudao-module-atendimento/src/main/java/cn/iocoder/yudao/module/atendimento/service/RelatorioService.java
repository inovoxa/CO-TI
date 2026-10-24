package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import cn.iocoder.yudao.module.atendimento.dominio.Csat;
import cn.iocoder.yudao.module.itsm.dominio.Calendario;
import cn.iocoder.yudao.module.itsm.service.SlaService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relatórios do atendimento (Chatwoot: reports e reporting events). Os tempos são gravados quando acontecem (primeira
 * resposta, resolução), desde a abertura da conversa, e também só no expediente da caixa.
 */
@Service
public class RelatorioService {

    @Resource
    private EventoRelatorioMapper eventoRelatorioMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private SlaService slaService;
    @Resource
    private AdminUserApi adminUserApi;

    // =====================================================================================================
    // Fatos
    // =====================================================================================================

    void primeiraResposta(ConversaDO c, LocalDateTime quando) {
        registrar("primeira_resposta", c, quando);
    }

    void resolucao(ConversaDO c) {
        registrar("resolucao", c, LocalDateTime.now());
    }

    private void registrar(String tipo, ConversaDO c, LocalDateTime quando) {
        LocalDateTime inicio = c.getCreateTime() == null ? quando : c.getCreateTime();
        long seg = Math.max(0, Duration.between(inicio, quando).getSeconds());
        CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
        Calendario cal = caixa == null || caixa.getCalendarioId() == null ? null : slaService.calendario(caixa.getCalendarioId());
        eventoRelatorioMapper.insert(EventoRelatorioDO.builder().tipo(tipo).conversaId(c.getId()).caixaId(c.getCaixaId())
                .agenteId(c.getAgenteId()).valorSeg(seg).valorUtilSeg(cal == null ? seg : cal.ativoEntre(inicio, quando))
                .ocorridoEm(quando).build());
    }

    // =====================================================================================================
    // Consulta
    // =====================================================================================================

    public AtendimentoVOs.Relatorio resumo(LocalDate de, LocalDate ate, Long caixaId, Long agenteId, boolean util) {
        LocalDateTime ini = de.atStartOfDay();
        LocalDateTime fim = ate.plusDays(1).atStartOfDay();
        AtendimentoVOs.Relatorio r = new AtendimentoVOs.Relatorio();

        // em andamento agora
        for (Map<String, Object> x : eventoRelatorioMapper.emAndamento(caixaId, agenteId)) {
            int status = num(x.get("status")).intValue();
            long n = num(x.get("n"));
            if (status == 0) {
                r.setAbertas(r.getAbertas() + n);
                if (Boolean.TRUE.equals(x.get("sem_agente"))) {
                    r.setSemAtendente(r.getSemAtendente() + n);
                }
            } else {
                r.setPendentes(r.getPendentes() + n);
            }
        }

        // série diária: novas (pela abertura) e resolvidas (pelo evento)
        Map<LocalDate, long[]> serie = new TreeMap<>();
        for (LocalDate d = de; !d.isAfter(ate); d = d.plusDays(1)) {
            serie.put(d, new long[2]);
        }
        for (Map<String, Object> x : eventoRelatorioMapper.novasPorDia(ini, fim, caixaId, agenteId)) {
            long[] s = serie.get(data(x.get("dia")));
            if (s != null) {
                s[0] = num(x.get("n"));
            }
            r.setNovas(r.getNovas() + num(x.get("n")));
        }

        // tempos (média e mediana), no geral, por atendente e por caixa
        String campo = util ? "valor_util_seg" : "valor_seg";
        List<Map<String, Object>> eventos = eventoRelatorioMapper.eventos(ini, fim, caixaId, agenteId);
        Map<Long, Linha> porAgente = new HashMap<>();
        Map<Long, Linha> porCaixa = new HashMap<>();
        List<Long> resposta = new ArrayList<>();
        List<Long> resolucao = new ArrayList<>();
        for (Map<String, Object> e : eventos) {
            long v = num(e.get(campo));
            Long ag = e.get("agente_id") == null ? null : num(e.get("agente_id"));
            Long cx = num(e.get("caixa_id"));
            boolean resolveu = "resolucao".equals(e.get("tipo"));
            (resolveu ? resolucao : resposta).add(v);
            for (Linha l : List.of(porCaixa.computeIfAbsent(cx, k -> new Linha()), porAgente.computeIfAbsent(ag == null ? 0L : ag, k -> new Linha()))) {
                (resolveu ? l.resolucao : l.resposta).add(v);
            }
            if (resolveu) {
                long[] s = serie.get(data(e.get("dia")));
                if (s != null) {
                    s[1]++;
                }
            }
        }
        r.setResolvidas(resolucao.size());
        r.setPrimeiraRespostaMediaSeg(media(resposta));
        r.setPrimeiraRespostaMedianaSeg(mediana(resposta));
        r.setResolucaoMediaSeg(media(resolucao));
        r.setResolucaoMedianaSeg(mediana(resolucao));
        serie.forEach((d, s) -> {
            AtendimentoVOs.RelatorioDia x = new AtendimentoVOs.RelatorioDia();
            x.setDia(d.toString());
            x.setNovas(s[0]);
            x.setResolvidas(s[1]);
            r.getSerie().add(x);
        });

        // mensagens
        for (Map<String, Object> x : eventoRelatorioMapper.mensagens(ini, fim, caixaId, agenteId)) {
            if (num(x.get("tipo")) == 0) {
                r.setMensagensRecebidas(num(x.get("n")));
            } else {
                r.setMensagensEnviadas(num(x.get("n")));
            }
        }
        long[][] mapa = new long[7][24];
        for (Map<String, Object> x : eventoRelatorioMapper.entradasPorHora(ini, fim, caixaId, agenteId)) {
            mapa[num(x.get("dia")).intValue() - 1][num(x.get("hora")).intValue()] = num(x.get("n"));
        }
        r.setPorHora(Arrays.stream(mapa).map(l -> Arrays.stream(l).boxed().toList()).toList());

        // satisfação
        List<Map<String, Object>> pesquisas = eventoRelatorioMapper.pesquisas(ini, fim, caixaId, agenteId);
        long[] dist = new long[5];
        for (Map<String, Object> p : pesquisas) {
            Long ag = p.get("agente_id") == null ? 0L : num(p.get("agente_id"));
            Long cx = num(p.get("caixa_id"));
            porAgente.computeIfAbsent(ag, k -> new Linha()).pesquisas++;
            porCaixa.computeIfAbsent(cx, k -> new Linha()).pesquisas++;
            if (p.get("nota") != null) {
                int n = num(p.get("nota")).intValue();
                dist[n - 1]++;
                porAgente.get(ag).notas.add(n);
                porCaixa.get(cx).notas.add(n);
            }
        }
        AtendimentoVOs.RelatorioCsat cs = r.getSatisfacao();
        cs.setEnviadas(pesquisas.size());
        long respondidas = Arrays.stream(dist).sum();
        cs.setRespondidas(respondidas);
        cs.setDistribuicao(Arrays.stream(dist).boxed().toList());
        if (respondidas > 0) {
            long soma = 0;
            for (int i = 0; i < 5; i++) {
                soma += dist[i] * (i + 1);
            }
            cs.setMedia(Math.round(soma * 10.0 / respondidas) / 10.0);
            cs.setPercentualSatisfeitos(Math.round((dist[3] + dist[4]) * 1000.0 / respondidas) / 10.0);
        }
        cs.setUltimas(ultimas(pesquisas));

        // linhas por atendente e por caixa
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(porAgente.keySet().stream().filter(k -> k != 0).collect(Collectors.toSet()));
        porAgente.forEach((id, l) -> r.getPorAtendente().add(linha(id == 0 ? null : id,
                id == 0 ? "Sem atendente" : Optional.ofNullable(nomes.get(id)).map(AdminUserRespDTO::getNickname).orElse("#" + id), l)));
        Map<Long, String> caixas = porCaixa.isEmpty() ? Map.of() : caixaMapper.selectBatchIds(porCaixa.keySet()).stream()
                .collect(Collectors.toMap(CaixaDO::getId, CaixaDO::getNome));
        porCaixa.forEach((id, l) -> r.getPorCaixa().add(linha(id, caixas.getOrDefault(id, "#" + id), l)));
        Comparator<AtendimentoVOs.RelatorioLinha> ordem = Comparator.comparingLong(AtendimentoVOs.RelatorioLinha::getResolvidas).reversed()
                .thenComparing(AtendimentoVOs.RelatorioLinha::getNome);
        r.getPorAtendente().sort(ordem);
        r.getPorCaixa().sort(ordem);
        return r;
    }

    /** As 20 respostas mais recentes da pesquisa, com contato e atendente. */
    private List<AtendimentoVOs.RelatorioAvaliacao> ultimas(List<Map<String, Object>> pesquisas) {
        List<Map<String, Object>> resp = pesquisas.stream().filter(p -> p.get("nota") != null).limit(20).toList();
        if (resp.isEmpty()) {
            return List.of();
        }
        Map<Long, String> contatos = contatoMapper.selectBatchIds(resp.stream().map(p -> num(p.get("contato_id"))).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ContatoDO::getId, ContatoDO::getNome));
        Map<Long, AdminUserRespDTO> agentes = adminUserApi.getUserMap(resp.stream().map(p -> p.get("agente_id"))
                .filter(Objects::nonNull).map(RelatorioService::num).collect(Collectors.toSet()));
        return resp.stream().map(p -> {
            AtendimentoVOs.RelatorioAvaliacao a = new AtendimentoVOs.RelatorioAvaliacao();
            a.setConversaId(num(p.get("conversa_id")));
            a.setContato(contatos.getOrDefault(num(p.get("contato_id")), "Contato"));
            a.setAtendente(p.get("agente_id") == null ? null
                    : Optional.ofNullable(agentes.get(num(p.get("agente_id")))).map(AdminUserRespDTO::getNickname).orElse(null));
            a.setNota(num(p.get("nota")).intValue());
            a.setRespondidaEm(p.get("respondida_em") instanceof java.sql.Timestamp t ? t.toLocalDateTime()
                    : (LocalDateTime) p.get("respondida_em"));
            return a;
        }).toList();
    }

    private static final class Linha {
        final List<Long> resposta = new ArrayList<>();
        final List<Long> resolucao = new ArrayList<>();
        final List<Integer> notas = new ArrayList<>();
        long pesquisas;
    }

    private static AtendimentoVOs.RelatorioLinha linha(Long id, String nome, Linha l) {
        AtendimentoVOs.RelatorioLinha x = new AtendimentoVOs.RelatorioLinha();
        x.setId(id);
        x.setNome(nome);
        x.setResolvidas(l.resolucao.size());
        x.setPrimeiraRespostaMediaSeg(media(l.resposta));
        x.setResolucaoMediaSeg(media(l.resolucao));
        x.setAvaliacoes(l.notas.size());
        x.setCsatMedia(l.notas.isEmpty() ? null : Math.round(l.notas.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0);
        return x;
    }

    private static Long media(List<Long> v) {
        return v.isEmpty() ? null : Math.round(v.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private static Long mediana(List<Long> v) {
        return Csat.mediana(v.stream().sorted().toList());
    }

    private static Long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static LocalDate data(Object o) {
        return o instanceof java.sql.Date d ? d.toLocalDate() : o instanceof LocalDate l ? l : LocalDate.parse(o.toString());
    }

}
