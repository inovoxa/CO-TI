package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class EstoqueVOs {

    private EstoqueVOs() {
    }

    @Data
    public static class Tipo {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 80)
        private String nome;
        @Pattern(regexp = "^(unidade|quantidade)$", message = "Controle inválido")
        private String controle;
        private Boolean ativo = true;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Trecho da descrição, identificador, linha, patrimônio, responsável ou nota fiscal */
        @Size(max = 120)
        private String busca;
        private Long tipoId;
        private String situacao;
        private String condicao;
        /** Só os que estão no mínimo ou abaixo */
        private Boolean baixo;
    }

    @Data
    public static class ItemSalvar {
        private Long id;
        @NotNull(message = "Escolha o tipo")
        private Long tipoId;
        @NotBlank(message = "Informe a descrição")
        @Size(max = 200)
        private String descricao;
        @Size(max = 80)
        private String identificador;
        @Size(max = 20)
        private String linha;
        @Size(max = 50)
        private String patrimonio;
        @NotNull
        @Pattern(regexp = "^(novo|usado)$", message = "Informe se é novo ou usado")
        private String condicao;
        /** Saldo inicial, no controle por quantidade; só vale ao criar */
        @Min(0)
        private Integer quantidade;
        @Min(0)
        private Integer minimo;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataCompra;
        @Size(max = 150)
        private String fornecedor;
        @Size(max = 60)
        private String notaFiscal;
        @DecimalMin("0.00")
        private BigDecimal valor;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate garantiaAte;
        @Size(max = 150)
        private String usadoPor;
        @Size(max = 1000)
        private String observacao;
    }

    @Data
    public static class ItemResp {
        private Long id;
        private Long tipoId;
        private String tipo;
        private String controle;
        private String descricao;
        private String identificador;
        private String linha;
        private String patrimonio;
        private String condicao;
        private String situacao;
        private Integer quantidade;
        private Integer minimo;
        private Boolean baixo;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataCompra;
        private String fornecedor;
        private String notaFiscal;
        private BigDecimal valor;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate garantiaAte;
        private String usadoPor;
        private String responsavel;
        private String destino;
        private String observacao;
    }

    @Data
    public static class MovimentarReq {
        @NotNull
        private Long itemId;
        @NotBlank
        private String tipo;
        @NotNull
        @Min(1)
        private Integer quantidade = 1;
        @Size(max = 150)
        private String pessoa;
        @Size(max = 150)
        private String destino;
        @Size(max = 500)
        private String motivo;
    }

    @Data
    public static class MovimentoResp {
        private Long id;
        private String tipo;
        private Integer quantidade;
        private String pessoa;
        private String destino;
        private String motivo;
        private String registradoPor;
        private LocalDateTime quando;
    }

    @Data
    public static class Resumo {
        private Long tipoId;
        private String tipo;
        private long disponivel;
        private long emUso;
        private long manutencao;
        private long baixos;
    }

}
