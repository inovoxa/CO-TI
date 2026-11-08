package cn.iocoder.yudao.module.itsm.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leitura da planilha de contatos. As colunas são achadas pelo título, em qualquer ordem: quem tem a própria planilha só
 * precisa ajustar os títulos, e a coluna que não vier mantém o que já estava cadastrado.
 */
final class TelefonePlanilha {

    static final String NOME = "nome", UNIDADE = "unidade", SETOR = "setor", RAMAL = "ramal", TELEFONE = "telefone",
            CELULAR = "celular", WHATSAPP = "whatsapp", EMERGENCIA = "emergencia", PUBLICO = "publico", EMAIL = "email",
            OBSERVACAO = "observacao";

    /** Títulos do modelo, na ordem em que aparecem */
    static final List<String> COLUNAS = List.of("Nome", "Unidade", "Setor", "Ramal", "Telefone", "Celular", "WhatsApp",
            "Emergência", "Celular visível para todos", "E-mail", "Observação");

    private static final Map<String, String> TITULOS = Map.ofEntries(
            Map.entry("nome", NOME), Map.entry("contato", NOME),
            Map.entry("unidade", UNIDADE), Map.entry("local", UNIDADE),
            Map.entry("setor", SETOR),
            Map.entry("ramal", RAMAL),
            Map.entry("telefone", TELEFONE), Map.entry("telefonefixo", TELEFONE), Map.entry("fixo", TELEFONE),
            Map.entry("celular", CELULAR),
            Map.entry("whatsapp", WHATSAPP), Map.entry("temwhatsapp", WHATSAPP),
            Map.entry("emergencia", EMERGENCIA), Map.entry("contatodeemergencia", EMERGENCIA),
            Map.entry("celularvisivelparatodos", PUBLICO), Map.entry("celularcorporativo", PUBLICO),
            Map.entry("email", EMAIL),
            Map.entry("observacao", OBSERVACAO), Map.entry("obs", OBSERVACAO));

    record Linha(int numero, Map<String, String> campos) {
    }

    private TelefonePlanilha() {
    }

    /** @throws IOException arquivo que não é .xlsx ou planilha sem a coluna Nome */
    static List<Linha> ler(InputStream arquivo, int limite) throws IOException {
        try (XSSFWorkbook livro = new XSSFWorkbook(arquivo)) {
            Sheet aba = livro.getSheetAt(0);
            Row cabecalho = aba.getRow(aba.getFirstRowNum());
            Map<Integer, String> colunas = new HashMap<>();
            if (cabecalho != null) {
                for (Cell c : cabecalho) {
                    String campo = TITULOS.get(titulo(texto(c)));
                    if (campo != null) {
                        colunas.putIfAbsent(c.getColumnIndex(), campo);
                    }
                }
            }
            if (!colunas.containsValue(NOME)) {
                throw new IOException("sem a coluna Nome");
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
    static boolean simNao(String v) {
        String s = titulo(v);
        return switch (s) {
            case "sim", "s", "x", "yes", "true", "1", "verdadeiro" -> true;
            case "", "nao", "n", "no", "false", "0", "falso" -> false;
            default -> throw new IllegalArgumentException("use sim ou não em vez de \"" + v + "\"");
        };
    }

    /** "Emergência" e "E-MAIL" viram "emergencia" e "email" */
    private static String titulo(String s) {
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
