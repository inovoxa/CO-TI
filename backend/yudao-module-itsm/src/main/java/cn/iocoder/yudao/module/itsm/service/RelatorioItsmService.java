package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.RELATORIO_GRANDE_DEMAIS;

/**
 * Relatórios em planilha do Service Desk. As consultas são as mesmas das telas, então o escopo de unidade do perfil
 * continua valendo. Toda exportação vai para a trilha de auditoria, porque leva dados de pessoas para fora do portal.
 */
@Service
public class RelatorioItsmService {

    static final int LIMITE_LINHAS = 50_000;
    private static final int LOTE = 500;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Resource
    private PainelService painelService;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private CadastroService cadastros;
    @Resource
    private AuditoriaService auditoria;

    public List<Planilha.Aba> painel(LocalDate de, LocalDate ate, Long deptId, Long categoriaId, Long userId) {
        Map<String, Object> dados = painelService.painel(de, ate, deptId, categoriaId);
        CategoriaDO categoria = categoriaId == null ? null : cadastros.obterCategoria(categoriaId);
        List<Planilha.Aba> abas = abasDoPainel(dados, de, ate, categoria == null ? "Todas" : categoria.getNome());
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("relatorio", "painel");
        detalhes.put("de", de.toString());
        detalhes.put("ate", ate.toString());
        auditoria.registrar("relatorio.exportado", userId, null, detalhes);
        return abas;
    }

    public List<Planilha.Aba> chamados(ChamadoVOs.PaginaReq filtro, Long userId) {
        List<List<Object>> linhas = new ArrayList<>();
        filtro.setPageSize(LOTE);
        int pagina = 1;
        while (true) {
            filtro.setPageNo(pagina++);
            PageResult<ChamadoVOs.Resp> p = chamadoService.pagina(filtro, userId);
            if (p.getTotal() > LIMITE_LINHAS) {
                throw exception(RELATORIO_GRANDE_DEMAIS, p.getTotal(), LIMITE_LINHAS);
            }
            p.getList().forEach(c -> linhas.add(linhaDoChamado(c)));
            if (p.getList().isEmpty() || linhas.size() >= p.getTotal()) {
                break;
            }
        }
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("relatorio", "chamados");
        detalhes.put("fila", filtro.getFila());
        detalhes.put("linhas", linhas.size());
        auditoria.registrar("relatorio.exportado", userId, null, detalhes);
        return List.of(new Planilha.Aba("Chamados", COLUNAS_CHAMADO, linhas));
    }

    static final List<String> COLUNAS_CHAMADO = List.of("Número", "Título", "Tipo", "Situação", "Prioridade", "Categoria",
            "Canal", "Unidade", "Solicitantes", "Técnicos", "Aberto em", "Assumido em", "Solucionado em", "Fechado em",
            "Prazo de solução", "Prazo vencido", "Tempo de solução (h)", "Nota de satisfação");

    static List<Object> linhaDoChamado(ChamadoVOs.Resp c) {
        return Arrays.asList(c.getId(), c.getTitulo(), c.getTipoRotulo(), c.getStatusRotulo(), c.getPrioridadeRotulo(),
                c.getCategoria(), c.getOrigem(), c.getDept(), nomes(c.getSolicitantes()), nomes(c.getTecnicos()),
                dataHora(c.getAbertoEm()), dataHora(c.getAssumidoEm()), dataHora(c.getSolucionadoEm()),
                dataHora(c.getFechadoEm()), dataHora(c.getPrazoSolucao()),
                Boolean.TRUE.equals(c.getSlaVencido()) ? "Sim" : "Não", horas(c.getTempoSolucaoSeg()), c.getSatisfacaoNota());
    }

    @SuppressWarnings("unchecked")
    static List<Planilha.Aba> abasDoPainel(Map<String, Object> p, LocalDate de, LocalDate ate, String categoria) {
        Map<String, Object> r = (Map<String, Object>) p.get("resumo");
        Map<String, Object> s = (Map<String, Object>) p.get("satisfacao");
        Map<String, Object> i = (Map<String, Object>) p.get("idade");
        List<List<Object>> resumo = new ArrayList<>();
        resumo.add(Arrays.asList("Período", DATA.format(de) + " a " + DATA.format(ate)));
        resumo.add(Arrays.asList("Categoria", categoria));
        resumo.add(Arrays.asList("Abertos agora", r.get("abertos")));
        resumo.add(Arrays.asList("Abertos sem técnico", r.get("semTecnico")));
        resumo.add(Arrays.asList("Pendentes", r.get("pendentes")));
        resumo.add(Arrays.asList("Com prazo vencido", r.get("vencidos")));
        resumo.add(Arrays.asList("Novos no período", r.get("novos")));
        resumo.add(Arrays.asList("Solucionados no período", r.get("solucionados")));
        resumo.add(Arrays.asList("Fechados no período", r.get("fechados")));
        resumo.add(Arrays.asList("Solucionados no prazo (%)", r.get("percentualNoPrazo")));
        resumo.add(Arrays.asList("Assumidos no prazo (%)", r.get("percentualAtendimentoNoPrazo")));
        resumo.add(Arrays.asList("Tempo médio para assumir (h, horário útil)", horas(r.get("mediaAssumirSeg"))));
        resumo.add(Arrays.asList("Tempo médio de solução (h, horário útil)", horas(r.get("mediaSolucaoSeg"))));
        resumo.add(Arrays.asList("Satisfação média (1 a 5)", s.get("media")));
        resumo.add(Arrays.asList("Pesquisas respondidas", s.get("respondidas")));
        resumo.add(Arrays.asList("Pesquisas enviadas", s.get("enviadas")));

        List<List<Object>> idade = List.of(
                Arrays.asList("Até 1 dia", i.get("ate1d")),
                Arrays.asList("1 a 3 dias", i.get("ate3d")),
                Arrays.asList("3 a 7 dias", i.get("ate7d")),
                Arrays.asList("7 a 30 dias", i.get("ate30d")),
                Arrays.asList("Mais de 30 dias", i.get("mais30d")));

        return List.of(
                new Planilha.Aba("Resumo", List.of("Indicador", "Valor"), resumo),
                new Planilha.Aba("Por dia", List.of("Dia", "Abertos", "Solucionados"),
                        linhas(p.get("serie"), "dia", "abertos", "solucionados")),
                new Planilha.Aba("Abertos pela idade", List.of("Idade", "Chamados"), idade),
                new Planilha.Aba("Categorias", List.of("Categoria", "Chamados"), linhas(p.get("porCategoria"), "nome", "total")),
                new Planilha.Aba("Prioridades", List.of("Prioridade", "Abertos"), linhas(p.get("porPrioridade"), "nome", "total")),
                new Planilha.Aba("Canais", List.of("Canal", "Chamados"), linhas(p.get("porOrigem"), "nome", "total")),
                new Planilha.Aba("Técnicos", List.of("Técnico", "Abertos agora", "Solucionados", "Tempo médio (h, horário útil)"),
                        ((List<Map<String, Object>>) p.get("porTecnico")).stream()
                                .map(t -> Arrays.asList(t.get("nome"), t.get("abertos"), t.get("solucionados"), horas(t.get("mediaSolucaoSeg"))))
                                .toList()),
                new Planilha.Aba("Equipes", List.of("Equipe", "Abertos agora", "Solucionados"),
                        linhas(p.get("porEquipe"), "nome", "abertos", "solucionados")));
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> linhas(Object lista, String... campos) {
        return ((List<Map<String, Object>>) lista).stream()
                .map(m -> Arrays.stream(campos).map(m::get).collect(Collectors.toCollection(ArrayList::new)))
                .map(l -> (List<Object>) l)
                .toList();
    }

    private static String nomes(List<ChamadoVOs.Ator> atores) {
        return atores == null ? "" : atores.stream().map(ChamadoVOs.Ator::getNome).collect(Collectors.joining(", "));
    }

    private static String dataHora(LocalDateTime d) {
        return d == null ? null : DATA_HORA.format(d);
    }

    private static Double horas(Object segundos) {
        return segundos instanceof Number n ? Math.round(n.doubleValue() / 360.0) / 10.0 : null;
    }

}
