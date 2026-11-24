package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Unidade pela faixa de IP. */
public final class UnidadeRedeVOs {

    private UnidadeRedeVOs() {
    }

    @Data
    public static class Faixa {
        private Long id;
        private String cidr;
        private Long deptId;
        private String deptNome;
        private String observacao;
        /** Ativos (fora os baixados) com IP nesta faixa */
        private Long maquinas;
    }

    @Data
    public static class SalvarReq {
        private Long id;
        @NotBlank(message = "Informe a faixa, ex.: 172.19.21.0/24")
        @Size(max = 50)
        private String cidr;
        @NotNull(message = "Escolha a unidade")
        private Long deptId;
        @Size(max = 255)
        private String observacao;
    }

    /** Rede /24 sem faixa cadastrada em que a maioria das máquinas já é de uma unidade (pela pessoa logada). */
    @Data
    public static class Sugestao {
        private String cidr;
        private Long deptId;
        private String deptNome;
        private Long maquinas;
        /** Quantas dessas máquinas estão na unidade sugerida */
        private Long naUnidade;
    }

}
