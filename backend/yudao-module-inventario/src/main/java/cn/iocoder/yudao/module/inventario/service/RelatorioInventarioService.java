package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.RELATORIO_GRANDE_DEMAIS;

/**
 * Inventário em planilha. Usa a mesma consulta da tela (filtros e escopo de unidade) e registra a exportação na
 * trilha de auditoria.
 */
@Service
public class RelatorioInventarioService {

    static final int LIMITE_LINHAS = 50_000;
    private static final int LOTE = 500;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static final List<String> COLUNAS = List.of("Nome", "Tipo", "Situação", "Patrimônio", "Número de série", "Fabricante",
            "Modelo", "Sistema", "Versão do sistema", "Sistema sem suporte", "Processador", "Memória (GB)", "Disco (GB)",
            "Disco livre (GB)", "IP", "MAC", "Usuário", "Unidade", "Antivírus", "Origem", "Último inventário");

    @Resource
    private AtivoService ativoService;
    @Resource
    private AuditoriaService auditoria;

    public List<Planilha.Aba> ativos(AtivoVOs.PaginaReq filtro, Long userId) {
        List<List<Object>> linhas = new ArrayList<>();
        filtro.setPageSize(LOTE);
        int pagina = 1;
        while (true) {
            filtro.setPageNo(pagina++);
            PageResult<AtivoVOs.Resp> p = ativoService.pagina(filtro);
            if (p.getTotal() > LIMITE_LINHAS) {
                throw exception(RELATORIO_GRANDE_DEMAIS, p.getTotal(), LIMITE_LINHAS);
            }
            p.getList().forEach(a -> linhas.add(linha(a)));
            if (p.getList().isEmpty() || linhas.size() >= p.getTotal()) {
                break;
            }
        }
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("relatorio", "inventario");
        detalhes.put("linhas", linhas.size());
        auditoria.registrar("relatorio.exportado", userId, null, detalhes);
        return List.of(new Planilha.Aba("Ativos", COLUNAS, linhas));
    }

    static List<Object> linha(AtivoVOs.Resp a) {
        return Arrays.asList(a.getNome(), a.getTipo(), a.getSituacao(), a.getPatrimonio(), a.getNumeroSerie(), a.getFabricante(),
                a.getModelo(), a.getSoNome(), a.getSoVersao(), Boolean.TRUE.equals(a.getSoSemSuporte()) ? "Sim" : "Não",
                a.getCpu(), gb(a.getMemoriaMb()), gb(a.getDiscoTotalMb()), gb(a.getDiscoLivreMb()), a.getIp(), a.getMac(),
                a.getUsuarioNome() != null ? a.getUsuarioNome() : a.getUsuarioLogin(), a.getDeptNome(), a.getAntivirus(),
                a.getOrigem(), dataHora(a.getUltimoInventario()));
    }

    private static Double gb(Number mb) {
        return mb == null ? null : Math.round(mb.doubleValue() / 102.4) / 10.0;
    }

    private static String dataHora(LocalDateTime d) {
        return d == null ? null : DATA_HORA.format(d);
    }

}
