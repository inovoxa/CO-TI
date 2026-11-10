package cn.iocoder.yudao.module.monitoramento.controller.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Importação de planilha com prévia: primeiro mostra o que vai mudar, depois aplica. */
public final class ImportacaoVOs {

    private ImportacaoVOs() {
    }

    /** {@code de} vazio: dado adicionado; {@code para} vazio: dado removido; os dois: dado atualizado. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mudanca {
        private String campo;
        private String de;
        private String para;
    }

    @Data
    public static class Item {
        private Long id;
        private String nome;
        private List<Mudanca> mudancas = new ArrayList<>();
        /** Consequência de remover, quando houver */
        private String aviso;
    }

    @Data
    public static class Previa {
        private List<Item> novos = new ArrayList<>();
        private List<Item> alterados = new ArrayList<>();
        /** Cadastrados que não estão no arquivo: só saem se o usuário escolher removê-los */
        private List<Item> ausentes = new ArrayList<>();
        private int semMudanca;
        private int recusados;
        private List<String> erros = new ArrayList<>();
        /** Departamentos que a importação vai criar, como "CAPS (em "Saúde")" */
        private List<String> departamentos = new ArrayList<>();
    }

    @Data
    public static class Resultado {
        private int adicionados;
        private int atualizados;
        private int removidos;
        private int semMudanca;
        private int recusados;
    }

}
