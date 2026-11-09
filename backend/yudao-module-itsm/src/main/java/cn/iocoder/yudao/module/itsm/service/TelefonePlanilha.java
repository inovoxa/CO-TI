package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.excel.core.util.Planilha;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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

    private TelefonePlanilha() {
    }

    /** @throws IOException arquivo que não é .xlsx ou planilha sem a coluna Nome */
    static List<Planilha.Linha> ler(InputStream arquivo, int limite) throws IOException {
        return Planilha.ler(arquivo, TITULOS, NOME, limite);
    }

    /** @throws IllegalArgumentException valor que não é sim nem não */
    static boolean simNao(String v) {
        return Planilha.lerSimNao(v);
    }

}
