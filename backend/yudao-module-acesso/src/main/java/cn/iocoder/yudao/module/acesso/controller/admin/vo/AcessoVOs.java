package cn.iocoder.yudao.module.acesso.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public final class AcessoVOs {

    private AcessoVOs() {
    }

    @Data
    public static class Salvar {
        private Long id;
        @NotBlank(message = "Informe o nome do link")
        @Size(max = 100, message = "O nome pode ter até 100 caracteres")
        private String titulo;
        @NotBlank(message = "Informe o endereço")
        @Size(max = 1000, message = "Endereço longo demais")
        private String url;
        @Size(max = 200, message = "A descrição pode ter até 200 caracteres")
        private String descricao;
        @Size(max = 60, message = "A categoria pode ter até 60 caracteres")
        private String categoria;
        private Integer ordem = 0;
    }

    @Data
    public static class Link {
        private Long id;
        private String titulo;
        private String url;
        private String descricao;
        private String categoria;
        private Integer ordem;
    }

    @Data
    public static class Painel {
        private List<Link> publicos;
        private List<Link> meus;
        private Integer limiteMeus;
    }

}
