package cn.iocoder.yudao.module.monitoramento.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class KumaVOs {

    private KumaVOs() {
    }

    /** O que há no kuma.db e o que será importado (antes de confirmar). */
    @Data
    public static class Analise {
        private String token;
        private List<Item> monitores = new ArrayList<>();
        private List<Item> canais = new ArrayList<>();
        private List<Item> manutencoes = new ArrayList<>();
        /** batidas dos últimos 30 dias (histórico que pode vir junto) */
        private long batidas30d;
        private int etiquetas;
    }

    @Data
    public static class Item {
        private Long kumaId;
        private String nome;
        /** tipo no Kuma */
        private String tipoKuma;
        /** tipo no CO-TI; null = não será importado */
        private String tipo;
        private String alvo;
        private Boolean ativo;
        private Boolean jaImportado;
        /** o que precisa de atenção (ou por que não entra) */
        private String aviso;
    }

    @Data
    public static class ConfirmarReq {
        @NotBlank
        private String token;
        /** dias de histórico (batidas) a trazer; 0 = nenhum */
        @Min(0)
        @Max(30)
        private Integer historicoDias;
        private Boolean canais;
        private Boolean manutencoes;
        /** monitores do Kuma que a pessoa desmarcou */
        private List<Long> ignorar;
        /** unidade para todos os monitores importados (opcional; dá para mudar um a um depois) */
        private Long unidadeId;
    }

    @Data
    public static class Resultado {
        private int monitores;
        private int canais;
        private int manutencoes;
        private long batidas;
        private List<String> avisos = new ArrayList<>();
    }

}
