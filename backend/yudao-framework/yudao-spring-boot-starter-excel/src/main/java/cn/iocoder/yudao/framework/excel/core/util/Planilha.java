package cn.iocoder.yudao.framework.excel.core.util;

import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.excel.core.handler.ColumnWidthMatchStyleStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

}
