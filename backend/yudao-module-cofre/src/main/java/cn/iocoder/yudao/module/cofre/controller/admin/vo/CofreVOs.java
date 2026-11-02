package cn.iocoder.yudao.module.cofre.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

public final class CofreVOs {

    private CofreVOs() {
    }

    @Data
    public static class PastaReq {
        private Long id;
        @NotBlank(message = "Informe o nome da pasta")
        @Size(max = 120)
        private String nome;
        @Size(max = 500)
        private String descricao;
    }

    @Data
    public static class Pasta {
        private Long id;
        private String nome;
        private String descricao;
        @Schema(description = "1 vê, 2 edita, 3 gerencia; vazio = não é membro (só para quem administra o cofre)")
        private Integer meuPapel;
        private Long totalSegredos;
        private Boolean podeGerenciar;
    }

    @Data
    public static class MembroReq {
        @NotNull
        private Long pastaId;
        @NotNull(message = "Escolha a pessoa")
        private Long userId;
        @NotNull(message = "Escolha o papel")
        private Integer papel;
    }

    @Data
    public static class Membro {
        private Long userId;
        private String nome;
        private Integer papel;
    }

    @Data
    @ToString(exclude = {"senha", "notas"})
    public static class SegredoReq {
        private Long id;
        @NotNull(message = "Escolha a pasta")
        private Long pastaId;
        @NotBlank(message = "Informe o nome")
        @Size(max = 200)
        private String nome;
        @Size(max = 200)
        private String usuario;
        @Size(max = 500)
        private String url;
        @Schema(description = "Obrigatória ao cadastrar; vazia ao editar mantém a atual")
        @Size(max = 4096)
        private String senha;
        @Schema(description = "Ausente mantém as notas atuais; vazia apaga")
        @Size(max = 8000)
        private String notas;
    }

    /** Nunca traz a senha nem as notas: elas só saem por {@link Revelado}. */
    @Data
    public static class Segredo {
        private Long id;
        private Long pastaId;
        private String pasta;
        private String nome;
        private String usuario;
        private String url;
        private Boolean temNotas;
        private LocalDateTime senhaAlteradaEm;
        private Boolean podeEditar;
    }

    @Data
    @ToString(exclude = {"senha", "notas"})
    public static class Revelado {
        private String senha;
        private String notas;
    }

    @Data
    public static class Resumo {
        private List<Pasta> pastas;
        private Boolean podeAdministrar;
        @Schema(description = "A pessoa já pode ver senhas (tem a verificação em duas etapas, se exigida)")
        private Boolean podeRevelar;
    }

}
