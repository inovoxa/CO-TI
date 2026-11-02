package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelatorioItsmServiceTest {

    private static Workbook abrir(List<Planilha.Aba> abas) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(Planilha.gerar(abas)));
    }

    @Test
    void painelGeraUmaAbaPorIndicadorComPeriodoEHorasUteis() throws Exception {
        Map<String, Object> resumo = new java.util.HashMap<>(Map.of("abertos", 7L, "mediaSolucaoSeg", 5400L));
        Map<String, Object> dados = Map.of(
                "resumo", resumo,
                "satisfacao", Map.of("respondidas", 3L, "enviadas", 4L),
                "idade", Map.of("ate1d", 1L),
                "serie", List.of(Map.of("dia", "2026-11-01", "abertos", 2L, "solucionados", 1L)),
                "porCategoria", List.of(Map.of("nome", "Rede", "total", 2L)),
                "porPrioridade", List.of(),
                "porOrigem", List.of(),
                "porTecnico", List.of(Map.of("nome", "Ana", "abertos", 1L, "solucionados", 2L, "mediaSolucaoSeg", 3600L)),
                "porEquipe", List.of());

        List<Planilha.Aba> abas = RelatorioItsmService.abasDoPainel(dados, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), "Todas");

        try (Workbook w = abrir(abas)) {
            assertEquals(8, w.getNumberOfSheets());
            Sheet r = w.getSheet("Resumo");
            assertEquals("01/11/2026 a 02/11/2026", r.getRow(1).getCell(1).getStringCellValue());
            assertEquals(7, r.getRow(3).getCell(1).getNumericCellValue());
            assertEquals(1.5, r.getRow(13).getCell(1).getNumericCellValue());
            assertEquals(1.0, w.getSheet("Técnicos").getRow(1).getCell(3).getNumericCellValue());
            assertEquals("Rede", w.getSheet("Categorias").getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void tituloComFormulaViraTextoECampoVazioFicaVazio() throws Exception {
        ChamadoVOs.Resp c = new ChamadoVOs.Resp();
        c.setId(123L);
        c.setTitulo("=HYPERLINK(\"http://x\")");
        c.setAbertoEm(LocalDateTime.of(2026, 11, 2, 9, 5));
        c.setSlaVencido(true);

        try (Workbook w = abrir(List.of(new Planilha.Aba("Chamados", RelatorioItsmService.COLUNAS_CHAMADO,
                List.of(RelatorioItsmService.linhaDoChamado(c)))))) {
            Row l = w.getSheet("Chamados").getRow(1);
            assertEquals(CellType.STRING, l.getCell(1).getCellType());
            assertEquals("=HYPERLINK(\"http://x\")", l.getCell(1).getStringCellValue());
            assertEquals("02/11/2026 09:05", l.getCell(10).getStringCellValue());
            assertEquals("Sim", l.getCell(15).getStringCellValue());
            assertTrue(l.getCell(12) == null || l.getCell(12).getCellType() == CellType.BLANK);
        }
    }

}
