package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AlertaVOs {

    private AlertaVOs() {
    }

    @Data
    public static class Regra {
        @NotNull
        private Long id;
        private String codigo;
        private String nome;
        @NotNull
        private Boolean ativo;
        @NotNull
        private Boolean abrirChamado;
        private Integer limite;
        private Long categoriaId;
        @NotNull
        @Min(1)
        @Max(5)
        private Integer urgencia;
        /** Alertas abertos agora */
        private Long abertos;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        private String regra;
        /** abertos (padrão) | encerrados | todos */
        private String situacao;
        private String busca;
    }

    @Data
    public static class Resp {
        private Long id;
        private String regra;
        private String regraNome;
        private String objeto;
        private Long objetoId;
        private String titulo;
        private String detalhe;
        private Long deptId;
        private String deptNome;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        private Long chamadoId;
    }

    @Data
    public static class Avaliacao {
        private int abertos;
        private int encerrados;
        private int chamados;
        private List<String> erros = new ArrayList<>();
    }

}
