package cn.iocoder.yudao.framework.excel.core.util;

import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.excel.core.handler.ColumnWidthMatchStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Planilha .xlsx de relatório, com uma ou mais abas e colunas definidas em tempo de execução.
 *
 * <p>Monta o arquivo inteiro em memória antes de responder: se falhar no meio, o usuário recebe o erro, e não um
 * arquivo corrompido. Quem chama limita o número de linhas. Texto entra como célula de texto, então um título como
 * {@code =CMD()} vindo do WhatsApp é mostrado, nunca avaliado como fórmula.
 */
public final class Planilha {

    private static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public record Aba(String nome, List<String> colunas, List<List<Object>> linhas) {
    }

    /** Linha lida de uma planilha: {@code campos} só tem as colunas que vieram no arquivo. */
    public record Linha(int numero, Map<String, String> campos) {
    }

    private Planilha() {
    }

    public static String dataHora(LocalDateTime momento) {
        return momento == null ? null : momento.format(DATA_HORA);
    }

    public static String simNao(Boolean valor) {
        return Boolean.TRUE.equals(valor) ? "Sim" : "Não";
    }

    public static byte[] gerar(List<Aba> abas) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ExcelWriter escritor = FesodSheet.write(saida)
                .registerWriteHandler(new ColumnWidthMatchStyleStrategy())
                .build()) {
            int numero = 0;
            for (Aba aba : abas) {
                List<List<String>> cabecalho = new ArrayList<>();
                aba.colunas().forEach(c -> cabecalho.add(List.of(c)));
                escritor.write(aba.linhas(), FesodSheet.writerSheet(numero++, aba.nome()).head(cabecalho).build());
            }
        }
        return saida.toByteArray();
    }

    public static void responder(HttpServletResponse resposta, String arquivo, List<Aba> abas) throws IOException {
        byte[] conteudo = gerar(abas);
        resposta.setContentType(TIPO_XLSX);
        resposta.setContentLength(conteudo.length);
        resposta.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(arquivo));
        resposta.getOutputStream().write(conteudo);
    }

    /**
     * Lê a primeira aba. As colunas são achadas pelo título, em qualquer ordem, e as que não estão em {@code titulos} são
     * ignoradas.
     *
     * @param titulos título já passado por {@link #titulo(String)} → nome do campo
     * @throws IOException arquivo que não é .xlsx ou planilha sem a coluna obrigatória
     */
    public static List<Linha> ler(InputStream arquivo, Map<String, String> titulos, String obrigatorio, int limite) throws IOException {
        try (XSSFWorkbook livro = new XSSFWorkbook(arquivo)) {
            Sheet aba = livro.getSheetAt(0);
            Row cabecalho = aba.getRow(aba.getFirstRowNum());
            Map<Integer, String> colunas = new HashMap<>();
            if (cabecalho != null) {
                for (Cell c : cabecalho) {
                    String campo = titulos.get(titulo(texto(c)));
                    if (campo != null) {
                        colunas.putIfAbsent(c.getColumnIndex(), campo);
                    }
                }
            }
            if (!colunas.containsValue(obrigatorio)) {
                throw new IOException("sem a coluna " + obrigatorio);
            }
            List<Linha> linhas = new ArrayList<>();
            for (int i = aba.getFirstRowNum() + 1; i <= aba.getLastRowNum() && linhas.size() < limite; i++) {
                Row r = aba.getRow(i);
                Map<String, String> campos = new LinkedHashMap<>();
                colunas.values().forEach(campo -> campos.put(campo, ""));
                boolean vazia = true;
                if (r != null) {
                    for (Map.Entry<Integer, String> col : colunas.entrySet()) {
                        String v = texto(r.getCell(col.getKey()));
                        campos.put(col.getValue(), v);
                        vazia &= v.isEmpty();
                    }
                }
                if (!vazia) {
                    linhas.add(new Linha(i + 1, campos));
                }
            }
            return linhas;
        } catch (RuntimeException e) {
            // o POI recusa arquivo corrompido ou de outro formato com exceções não verificadas
            throw new IOException(e);
        }
    }

    /** @throws IllegalArgumentException valor que não é sim nem não */
    public static boolean lerSimNao(String v) {
        String s = titulo(v);
        return switch (s) {
            case "sim", "s", "x", "yes", "true", "1", "verdadeiro" -> true;
            case "", "nao", "n", "no", "false", "0", "falso" -> false;
            default -> throw new IllegalArgumentException("use sim ou não em vez de \"" + v + "\"");
        };
    }

    /** "Emergência" e "E-MAIL" viram "emergencia" e "email" */
    public static String titulo(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    /** Celular digitado como número não pode virar 1,6E+10; fórmula vale pelo último resultado calculado */
    private static String texto(Cell c) {
        if (c == null) {
            return "";
        }
        CellType tipo = c.getCellType() == CellType.FORMULA ? c.getCachedFormulaResultType() : c.getCellType();
        return switch (tipo) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(c) ? new DataFormatter().formatCellValue(c)
                    : BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> c.getBooleanCellValue() ? "sim" : "não";
            default -> "";
        };
    }

}
