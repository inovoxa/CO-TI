package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

public final class RedeVOs {

    private RedeVOs() {
    }

    @Data
    public static class Subrede {
        private String subrede;
        private String mascara;
        private long dispositivos;
        private long desconhecidos;
        private List<String> varredores;
        private LocalDateTime ultimaVarredura;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        private String subrede;
        /** desconhecidos (padrão) | conhecidos | ignorados | todos */
        private String situacao;
        /** IP, MAC ou nome */
        private String busca;
    }

    @Data
    public static class Dispositivo {
        private Long id;
        private String mac;
        private String ip;
        private String nome;
        private String subrede;
        private Long ativoId;
        private String ativoNome;
        private String ativoTipo;
        private String vistoPor;
        private Boolean ignorado;
        private String observacao;
        private LocalDateTime primeiraVez;
        private LocalDateTime ultimaVez;
    }

    /** Transforma o equipamento descoberto em ativo cadastrado. */
    @Data
    public static class CadastrarReq {
        @NotNull
        private Long id;
        @NotBlank
        @Size(max = 255)
        private String nome;
        @NotBlank
        private String tipo;
        @Size(max = 255)
        private String fabricante;
        @Size(max = 255)
        private String modelo;
        @Size(max = 100)
        private String patrimonio;
        private Long deptId;
        private String observacao;
    }

    @Data
    public static class IgnorarReq {
        @NotNull
        private Long id;
        @NotNull
        private Boolean ignorado;
        @Size(max = 500)
        private String observacao;
    }

}
