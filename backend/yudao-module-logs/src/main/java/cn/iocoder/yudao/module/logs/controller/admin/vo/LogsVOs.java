package cn.iocoder.yudao.module.logs.controller.admin.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LogsVOs {

    private LogsVOs() {
    }

    /** O que filtrar: consulta do quadro, filtro geral da tela, fluxo e período. */
    @Data
    public static class Filtro {
        @Size(max = 2000)
        private String consulta;
        /** Filtro "para todos os quadros" da tela, somado com AND */
        @Size(max = 2000)
        private String filtroGeral;
        /** firewall | contas | maquinas | arquivos | geral; vazio = todos */
        private String fluxo;
        /** 5m | 15m | 30m | 1h | 4h | 12h | 1d | 2d | 7d | 30d | tudo; ignorado se vier "de" */
        private String periodo = "1d";
        private LocalDateTime de;
        private LocalDateTime ate;
    }

    @Data
    public static class MensagensReq {
        @Valid
        @NotNull
        private Filtro filtro;
        @Min(1)
        private int pagina = 1;
        @Min(1)
        @Max(500)
        private int tamanho = 50;
    }

    @Data
    public static class ContagemReq {
        @Valid
        @NotNull
        private Filtro filtro;
        /** Conta valores distintos deste campo em vez de mensagens */
        private String distinto;
        /** Soma este campo numérico em vez de contar ("bytes" = sentbyte + rcvdbyte); vale mais que distinto */
        @Size(max = 100)
        private String soma;
    }

    @Data
    public static class RankingReq {
        @Valid
        @NotNull
        private Filtro filtro;
        @NotBlank
        private String campo;
        @Min(1)
        @Max(200)
        private int limite = 10;
        /** Ordena pela soma deste campo numérico em vez da quantidade ("bytes" = sentbyte + rcvdbyte) */
        @Size(max = 100)
        private String soma;
    }

    @Data
    public static class Mensagem {
        private LocalDateTime momento;
        private String fluxo;
        private String origem;
        private String ip;
        private Integer nivel;
        private String aplicacao;
        private String mensagem;
        private Map<String, Object> campos;
    }

    @Data
    public static class Mensagens {
        private List<Mensagem> lista = new ArrayList<>();
        /** Total encontrado (até {@link #limiteTotal}; acima disso mostra "mais de") */
        private long total;
        private long limiteTotal;
        private long ms;
    }

    @Data
    public static class Contagem {
        private long valor;
        /** O mesmo tamanho de período logo antes (null em "tudo") */
        private Long anterior;
        private String rotuloPeriodo;
        private long ms;
    }

    @Data
    public static class Item {
        private String valor;
        private long total;
        /** Com soma: quantas mensagens deram aquele total */
        private Long mensagens;

        public Item(String valor, long total) {
            this.valor = valor;
            this.total = total;
        }
    }

    @Data
    public static class Ranking {
        private List<Item> itens = new ArrayList<>();
        /** Mensagens (ou a soma) que entraram na conta (para o percentual) */
        private long total;
        private long ms;
    }

    @Data
    public static class Histograma {
        /** Tamanho de cada barra, em segundos */
        private long intervalo;
        private List<LocalDateTime> momentos = new ArrayList<>();
        private List<Long> totais = new ArrayList<>();
        private long ms;
    }

    @Data
    public static class Origem {
        private String origem;
        private String fluxo;
        private String ip;
        private long total;
        private LocalDateTime ultima;
    }

    @Data
    public static class Situacao {
        private List<Map<String, Object>> entradas = new ArrayList<>();
        private long gravadas;
        private long descartadas;
        private long invalidas;
        private long falhas;
        private int fila;
        private int retencaoDias;
        private Map<String, Long> ultimas24h;
        private String tamanho;
    }

    @Data
    public static class Painel {
        private Long id;
        @NotBlank
        @Size(max = 60)
        private String nome;
        private Integer ordem;
        /** Quadros: [{id, tipo, titulo, fluxo, consulta, periodo, largura, campo, distinto, colunas, limite}] */
        @NotNull
        private List<Map<String, Object>> widgets = new ArrayList<>();
    }

}
