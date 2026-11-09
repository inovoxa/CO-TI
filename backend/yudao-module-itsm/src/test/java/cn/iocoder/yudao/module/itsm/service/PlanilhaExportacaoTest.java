package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** As telas exportam células vazias, números de vários tipos e texto vindo de fora: o .xlsx tem de abrir certo. */
class PlanilhaExportacaoTest {

    @Test
    void geraArquivoLegivelComTiposVariados() throws IOException {
        List<List<Object>> linhas = List.of(Arrays.asList("UPA CENTRAL", null, 8, 12L, 99.5d, new BigDecimal("-21.17712"), "=CMD()"));
        byte[] arquivo = Planilha.gerar(List.of(
                new Planilha.Aba("Dados", List.of("Nome", "Vazio", "Inteiro", "Longo", "Real", "Decimal", "Texto"), linhas),
                new Planilha.Aba("Sem linhas", List.of("A"), List.of())));

        try (XSSFWorkbook livro = new XSSFWorkbook(new ByteArrayInputStream(arquivo))) {
            assertEquals(2, livro.getNumberOfSheets());
            Sheet aba = livro.getSheet("Dados");
            assertEquals("Nome", aba.getRow(0).getCell(0).getStringCellValue());
            Row linha = aba.getRow(1);
            assertEquals("UPA CENTRAL", linha.getCell(0).getStringCellValue());
            assertEquals(8d, linha.getCell(2).getNumericCellValue());
            assertEquals(12d, linha.getCell(3).getNumericCellValue());
            assertEquals(99.5d, linha.getCell(4).getNumericCellValue());
            assertEquals(-21.17712d, linha.getCell(5).getNumericCellValue(), 1e-9);
            assertEquals(CellType.STRING, linha.getCell(6).getCellType(), "texto externo não pode virar fórmula");
        }
    }

    @Test
    void formataDataESimNao() {
        assertEquals("09/11/2026 08:05", Planilha.dataHora(LocalDateTime.of(2026, 11, 9, 8, 5)));
        assertNull(Planilha.dataHora(null));
        assertEquals("Sim", Planilha.simNao(true));
        assertEquals("Não", Planilha.simNao(null));
    }
}
