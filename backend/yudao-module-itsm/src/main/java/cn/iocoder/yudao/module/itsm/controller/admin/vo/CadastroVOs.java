package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Cadastros do ITSM: categorias, equipes e origens.
 */
public final class CadastroVOs {

    private CadastroVOs() {
    }

    @Data
    public static class CategoriaSalvar {
        private Long id;
        @Schema(description = "0 = raiz")
        private Long parentId = 0L;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres")
        private String nome;
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "O código só pode ter letras, números, ponto, hífen e sublinhado")
        private String codigo;
        @Size(max = 1000)
        private String descricao;
        private Long tecnicoId;
        private Long equipeId;
        @Schema(description = "SLA de atendimento da categoria; vazio = o da prioridade")
        private Long slaAtendimentoId;
        @Schema(description = "SLA de solução da categoria; vazio = o da prioridade")
        private Long slaSolucaoId;
        private Boolean visivelPortal = true;
        private Boolean paraIncidente = true;
        private Boolean paraRequisicao = true;
        private Boolean ativo = true;
        @Min(0) @Max(100000)
        private Integer ordem = 0;
    }

    @Data
    public static class EquipeSalvar {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Size(max = 500)
        private String descricao;
        @Email(message = "E-mail inválido")
        @Size(max = 128)
        private String email;
        @Size(max = 255)
        private String grupoAd;
        private Boolean ativo = true;
        /** Membros da equipe (substitui a lista atual) */
        private List<Membro> membros = new ArrayList<>();
    }

    @Data
    public static class Membro {
        @NotNull
        private Long userId;
        private Boolean supervisor = false;
        private String nome;
    }

    @Data
    public static class EquipeResp {
        private Long id;
        private String nome;
        private String descricao;
        private String email;
        private String grupoAd;
        private Boolean ativo;
        private List<Membro> membros = new ArrayList<>();
    }

    @Data
    public static class OrigemSalvar {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        private Boolean padraoPortal = false;
        private Boolean padraoEmail = false;
        private Boolean ativo = true;
    }

}
