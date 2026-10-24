package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calendários, feriados, SLAs e níveis de escalonamento.
 */
public final class SlaVOs {

    private SlaVOs() {
    }

    @Data
    public static class Periodo {
        @NotNull @Min(1) @Max(7)
        @Schema(description = "1 = segunda ... 7 = domingo")
        private Integer diaSemana;
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Hora inválida (HH:mm)")
        private String inicio;
        @NotBlank
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$|^24:00$", message = "Hora inválida (HH:mm)")
        @Schema(description = "24:00 = até a meia-noite")
        private String fim;
    }

    @Data
    public static class Calendario {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Size(max = 500)
        private String descricao;
        @Valid
        private List<Periodo> periodos = new ArrayList<>();
        private List<Long> feriadoIds = new ArrayList<>();
    }

    @Data
    public static class Feriado {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @NotNull(message = "Informe a data")
        private LocalDate dataInicio;
        private LocalDate dataFim;
        private Boolean perpetuo = false;
    }

    @Data
    public static class Nivel {
        private Long id;
        @NotBlank(message = "Informe o nome do nível")
        @Size(max = 100)
        private String nome;
        @NotNull
        @Schema(description = "Minutos em relação ao prazo; negativo = antes de vencer")
        @Min(-525_600) @Max(525_600)
        private Long minutos;
        private Long atribuirEquipeId;
        private Long atribuirTecnicoId;
        @Min(1) @Max(6)
        private Integer novaPrioridade;
        private Boolean ativo = true;
    }

    @Data
    public static class Sla {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @NotNull @Min(0) @Max(1)
        @Schema(description = "1 = atendimento (assumir), 0 = solução")
        private Integer tipo;
        @NotNull @Min(0) @Max(100_000)
        private Integer tempo;
        @NotBlank
        @Pattern(regexp = "minuto|hora|dia|mes")
        private String unidade;
        @Schema(description = "Vazio = 24 h, 7 dias")
        private Long calendarioId;
        private Boolean fimExpediente = false;
        @Size(max = 500)
        private String descricao;
        private Boolean ativo = true;
        @Valid
        private List<Nivel> niveis = new ArrayList<>();
    }

    @Data
    public static class PorPrioridade {
        @NotNull @Min(1) @Max(6)
        private Integer prioridade;
        private Long slaAtendimentoId;
        private Long slaSolucaoId;
    }

    @Data
    public static class SatisfacaoReq {
        @NotNull
        private Long chamadoId;
        @NotNull @Min(1) @Max(5)
        private Integer nota;
        @Size(max = 2000)
        private String comentario;
    }

}
