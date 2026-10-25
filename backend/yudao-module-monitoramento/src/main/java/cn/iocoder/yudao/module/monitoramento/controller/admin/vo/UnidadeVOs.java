package cn.iocoder.yudao.module.monitoramento.controller.admin.vo;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

public final class UnidadeVOs {

    private UnidadeVOs() {
    }

    /** Unidade (filial) do mapa do telão, com endereço preenchido à mão. */
    @Data
    public static class Unidade {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        /** Departamento raiz da unidade */
        @NotNull
        private Long deptId;
        @Size(max = 9)
        private String cep;
        @Size(max = 200)
        private String logradouro;
        @Size(max = 20)
        private String numero;
        @Size(max = 100)
        private String complemento;
        @Size(max = 100)
        private String bairro;
        @Size(max = 100)
        private String cidade;
        @Size(max = 2)
        private String uf;
        @NotNull
        @DecimalMin("-90")
        @DecimalMax("90")
        private BigDecimal latitude;
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        private BigDecimal longitude;
        private Boolean ativa;
        // ---- só leitura
        private String deptNome;
        private Integer monitores;
    }

    /** Endereço do CEP (ViaCEP). */
    @Data
    public static class Endereco {
        private String cep;
        private String logradouro;
        private String bairro;
        private String cidade;
        private String uf;
    }

    @Data
    public static class GeocodificarReq {
        private String logradouro;
        private String numero;
        private String bairro;
        private String cidade;
        private String uf;
        private String cep;
    }

    /** Coordenadas achadas e quão precisas são. */
    @Data
    public static class Coordenadas {
        private BigDecimal latitude;
        private BigDecimal longitude;
        /** endereco (com número) | rua | cep | cidade */
        private String precisao;
        private String descricao;
    }

}
