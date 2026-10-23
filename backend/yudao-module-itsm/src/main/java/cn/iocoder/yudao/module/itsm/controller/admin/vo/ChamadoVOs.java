package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entradas e saídas da API de chamados (técnicos e portal).
 */
public final class ChamadoVOs {

    private ChamadoVOs() {
    }

    // ================= consulta =================

    @Schema(description = "Filtro da fila de chamados")
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        @Schema(description = "abertos | novos | meus | minhas-equipes | pendentes | sla | solucionados | fechados | todos")
        private String fila = "abertos";
        @Schema(description = "Número (#123) ou parte do título")
        @Size(max = 100)
        private String busca;
        private Integer status;
        private Integer tipo;
        private Integer prioridade;
        private Long categoriaId;
        private Long deptId;
        private Long tecnicoId;
        private Long equipeId;
        private Long solicitanteId;
    }

    @Data
    public static class Ator {
        private Long id;
        private Integer tipo;
        private Long userId;
        private Long equipeId;
        private String email;
        /** Nome da pessoa ou da equipe, pronto para exibir */
        private String nome;
    }

    @Data
    public static class Resp {
        private Long id;
        private String titulo;
        private String descricao;
        private Integer tipo;
        private String tipoRotulo;
        private Integer status;
        private String statusRotulo;
        private Integer urgencia;
        private Integer impacto;
        private Integer prioridade;
        private String prioridadeRotulo;
        private Long categoriaId;
        private String categoria;
        private Long origemId;
        private String origem;
        private Long deptId;
        private String dept;
        private LocalDateTime abertoEm;
        private LocalDateTime assumidoEm;
        private LocalDateTime solucionadoEm;
        private LocalDateTime fechadoEm;
        private LocalDateTime prazoAtendimento;
        private LocalDateTime prazoSolucao;
        /** Prazo de solução vencido e chamado ainda aberto */
        private Boolean slaVencido;
        private Long esperaSeg;
        private Long tempoAssumirSeg;
        private Long tempoSolucaoSeg;
        private Long tempoGastoSeg;
        private String origemAutomatica;
        private List<Ator> solicitantes = new ArrayList<>();
        private List<Ator> tecnicos = new ArrayList<>();
        private List<Ator> observadores = new ArrayList<>();
        /** Ações que o usuário logado pode fazer agora (a tela mostra só esses botões) */
        private List<String> acoes = new ArrayList<>();
    }

    // ================= criação e edição =================

    @Schema(description = "Novo chamado pela equipe técnica")
    @Data
    public static class CriarReq {
        @NotBlank(message = "Informe o título")
        @Size(max = 255, message = "O título pode ter no máximo 255 caracteres")
        private String titulo;
        @Size(max = 200_000, message = "Descrição longa demais")
        private String descricao;
        @Min(1) @Max(2)
        private Integer tipo = 1;
        @Min(1) @Max(5)
        private Integer urgencia = 3;
        @Min(1) @Max(5)
        private Integer impacto = 3;
        @Schema(description = "Vazio = calculada pela matriz urgência × impacto; 6 = crítica")
        @Min(1) @Max(6)
        private Integer prioridade;
        private Long categoriaId;
        private Long origemId;
        @Schema(description = "Vazio = departamento do primeiro solicitante")
        private Long deptId;
        @Schema(description = "Vazio = quem está registrando")
        private List<Long> solicitanteIds = new ArrayList<>();
        private List<Long> observadorIds = new ArrayList<>();
        private List<Long> tecnicoIds = new ArrayList<>();
        private List<Long> equipeIds = new ArrayList<>();
    }

    @Schema(description = "Novo chamado pelo próprio usuário (autoatendimento)")
    @Data
    public static class AbrirReq {
        @NotBlank(message = "Informe o título")
        @Size(max = 255, message = "O título pode ter no máximo 255 caracteres")
        private String titulo;
        @Size(max = 200_000, message = "Descrição longa demais")
        private String descricao;
        @Min(1) @Max(2)
        private Integer tipo = 1;
        @Min(1) @Max(5)
        private Integer urgencia = 3;
        private Long categoriaId;
    }

    @Data
    public static class AtualizarReq {
        @NotNull
        private Long id;
        @NotBlank(message = "Informe o título")
        @Size(max = 255)
        private String titulo;
        @Size(max = 200_000)
        private String descricao;
        @Min(1) @Max(2)
        private Integer tipo;
        @Min(1) @Max(5)
        private Integer urgencia;
        @Min(1) @Max(5)
        private Integer impacto;
        @Min(1) @Max(6)
        private Integer prioridade;
        private Long categoriaId;
        private Long origemId;
        private Long deptId;
    }

    @Data
    public static class AtorReq {
        @NotNull
        private Long chamadoId;
        @NotNull @Min(1) @Max(3)
        private Integer tipo;
        private Long userId;
        private Long equipeId;
    }

    // ================= linha do tempo =================

    @Data
    public static class InteracaoReq {
        @NotNull
        private Long chamadoId;
        @NotBlank
        @Pattern(regexp = "acompanhamento|tarefa|solucao")
        private String tipo;
        @NotBlank(message = "Escreva o texto")
        @Size(max = 200_000)
        private String conteudo;
        private Boolean privado = false;
        private Long origemId;
        // tarefa
        @Min(0) @Max(2)
        private Integer estadoTarefa;
        private Long tecnicoId;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        @Min(0) @Max(31_536_000)
        private Long tempoGastoSeg;
    }

    @Data
    public static class AvaliarSolucaoReq {
        @NotNull
        private Long chamadoId;
        @NotNull
        private Boolean aprovar;
        @Size(max = 10_000)
        private String comentario;
    }

    @Data
    public static class PendenteReq {
        @NotNull
        private Long chamadoId;
        @Size(max = 10_000)
        private String motivo;
    }

    @Data
    public static class Interacao {
        private Long id;
        private String tipo;
        private Long userId;
        private String autor;
        private String conteudo;
        private Boolean privado;
        private LocalDateTime criadoEm;
        private Integer estadoTarefa;
        private Long tecnicoId;
        private String tecnico;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        private Long tempoGastoSeg;
        private Integer statusSolucao;
        private String avaliadoPorNome;
        private LocalDateTime avaliadoEm;
    }

}
