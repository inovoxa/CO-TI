package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entradas e saídas da API de problemas e mudanças (só equipe técnica).
 */
public final class ItilVOs {

    private ItilVOs() {
    }

    public static final String PROBLEMA = "problema";
    public static final String MUDANCA = "mudanca";
    public static final String CHAMADO = "chamado";

    // ================= consulta =================

    @Schema(description = "Filtro da fila de problemas ou mudanças")
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        @Schema(description = "abertos | meus | minhas-equipes | encerrados | todos; mudanças também: aprovacao | agenda")
        private String fila = "abertos";
        @Size(max = 100)
        private String busca;
        private Integer status;
        @Schema(description = "Só mudanças: 1 padrão, 2 normal, 3 emergencial")
        private Integer tipo;
        private Integer prioridade;
        private Long categoriaId;
    }

    /** Problema ou mudança. Os campos de um não se aplicam ao outro e vêm vazios. */
    @Data
    public static class Resp {
        private String objeto;
        private Long id;
        private String titulo;
        private String descricao;
        private Integer status;
        private String statusRotulo;
        private Integer urgencia;
        private Integer impacto;
        private Integer prioridade;
        private String prioridadeRotulo;
        private Long categoriaId;
        private String categoria;
        private Long deptId;
        private String dept;
        private LocalDateTime abertoEm;
        private LocalDateTime prazo;
        private Boolean atrasado;
        private LocalDateTime solucionadoEm;
        private LocalDateTime fechadoEm;
        private Long tempoGastoSeg;
        private Long registradoPor;
        private String registradoPorNome;
        private List<ChamadoVOs.Ator> solicitantes = new ArrayList<>();
        private List<ChamadoVOs.Ator> tecnicos = new ArrayList<>();
        private List<ChamadoVOs.Ator> observadores = new ArrayList<>();
        private Integer totalChamados;
        // problema
        private String impactos;
        private String causas;
        private String sintomas;
        // mudança
        private Integer tipo;
        private String tipoRotulo;
        private LocalDateTime inicioPrevisto;
        private LocalDateTime fimPrevisto;
        private String analiseImpacto;
        private String planoControle;
        private String planoImplantacao;
        private String planoRetorno;
        private String checklist;
        private Integer validacao;
        private String validacaoRotulo;
        /** Outras mudanças com a janela sobreposta (só no detalhe) */
        private List<Vinculo> conflitos;
        /** O que quem consulta pode fazer agora */
        private List<String> acoes = new ArrayList<>();
    }

    @Data
    public static class Vinculo {
        private Long id;
        /** chamado | problema | mudanca */
        private String objeto;
        private Long objetoId;
        private String titulo;
        private Integer status;
        private String statusRotulo;
        private LocalDateTime inicioPrevisto;
        private LocalDateTime fimPrevisto;
    }

    // ================= escrita =================

    @Data
    public static class SalvarReq {
        private Long id;
        @NotBlank(message = "Informe o título")
        @Size(max = 255)
        private String titulo;
        @Size(max = 200_000)
        private String descricao;
        @Min(1) @Max(5)
        private Integer urgencia = 3;
        @Min(1) @Max(5)
        private Integer impacto = 3;
        @Schema(description = "Vazio = calcular pela matriz urgência × impacto")
        @Min(1) @Max(6)
        private Integer prioridade;
        private Long categoriaId;
        private Long deptId;
        private LocalDateTime prazo;
        @Schema(description = "Técnicos na abertura")
        private List<Long> tecnicoIds = new ArrayList<>();
        @Schema(description = "Na abertura: chamado que originou (fica vinculado)")
        private Long chamadoId;
        // problema
        @Size(max = 200_000)
        private String impactos;
        @Size(max = 200_000)
        private String causas;
        @Size(max = 200_000)
        private String sintomas;
        // mudança
        @Min(1) @Max(3)
        private Integer tipo;
        private LocalDateTime inicioPrevisto;
        private LocalDateTime fimPrevisto;
        @Size(max = 200_000)
        private String analiseImpacto;
        @Size(max = 200_000)
        private String planoControle;
        @Size(max = 200_000)
        private String planoImplantacao;
        @Size(max = 200_000)
        private String planoRetorno;
        @Size(max = 200_000)
        private String checklist;
    }

    @Data
    public static class StatusReq {
        @NotNull
        private Long id;
        @NotNull
        private Integer status;
    }

    @Data
    public static class AtorReq {
        @NotNull
        private Long objetoId;
        @NotNull @Min(1) @Max(3)
        private Integer tipo;
        private Long userId;
        private Long equipeId;
    }

    @Data
    public static class InteracaoReq {
        @NotNull
        private Long objetoId;
        @NotBlank
        @Pattern(regexp = "acompanhamento|tarefa|solucao")
        private String tipo;
        @NotBlank(message = "Escreva o texto")
        @Size(max = 200_000)
        private String conteudo;
        @Min(0) @Max(2)
        private Integer estadoTarefa;
        private Long tecnicoId;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        @Min(0) @Max(31_536_000)
        private Long tempoGastoSeg;
        @Schema(description = "Na solução: solucionar também os chamados vinculados que estão abertos")
        private Boolean solucionarChamados = false;
    }

    @Data
    public static class VinculoReq {
        @NotNull
        private Long objetoId;
        @NotBlank
        @Pattern(regexp = "chamado|problema")
        private String alvo;
        @NotNull
        private Long alvoId;
    }

    @Data
    public static class PedirAprovacaoReq {
        @NotNull
        private Long mudancaId;
        @NotEmpty(message = "Escolha quem aprova")
        @Size(max = 20)
        private List<Long> aprovadorIds;
        @Size(max = 2000)
        private String comentario;
    }

    @Data
    public static class ResponderAprovacaoReq {
        @NotNull
        private Long aprovacaoId;
        @NotNull
        private Boolean aprovar;
        @Size(max = 2000)
        private String comentario;
    }

    @Data
    public static class Aprovacao {
        private Long id;
        private Long mudancaId;
        private String mudancaTitulo;
        private Long aprovadorId;
        private String aprovador;
        private String solicitadoPorNome;
        private Integer status;
        private String comentarioPedido;
        private String comentarioResposta;
        private LocalDateTime pedidoEm;
        private LocalDateTime respondidoEm;
        // para quem decide, sem precisar abrir a mudança
        private Integer tipo;
        private String tipoRotulo;
        private LocalDateTime inicioPrevisto;
        private LocalDateTime fimPrevisto;
        private String analiseImpacto;
        private String planoRetorno;
    }

    /** Número (#12) ou parte do título; o texto vai como parâmetro, nunca concatenado no SQL. */
    public static <T> void aplicarBusca(LambdaQueryWrapperX<T> q, String busca, SFunction<T, ?> id) {
        if (busca == null || busca.isBlank()) {
            return;
        }
        String b = busca.trim();
        String numero = b.startsWith("#") ? b.substring(1) : b;
        String like = "%" + b.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        if (numero.matches("\\d{1,18}")) {
            q.and(w -> w.eq(id, Long.parseLong(numero)).or().apply("titulo ILIKE {0}", like));
        } else {
            q.apply("titulo ILIKE {0}", like);
        }
    }

}
