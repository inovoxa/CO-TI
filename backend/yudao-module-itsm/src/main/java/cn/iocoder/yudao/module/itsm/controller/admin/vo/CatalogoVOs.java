package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.module.itsm.dominio.Formulario;
import cn.iocoder.yudao.module.itsm.dominio.MotorRegras;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Catálogo de serviços (formulários), regras, aprovações e execuções no AD.
 */
public final class CatalogoVOs {

    private CatalogoVOs() {
    }

    // ------------------------------------------------------------------ regras

    @Data
    public static class RegraSalvar {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Size(max = 500)
        private String descricao;
        @Min(0) @Max(100000)
        private Integer ordem = 0;
        private Boolean ativo = true;
        @Pattern(regexp = "^(E|OU)$", message = "Combinação deve ser E ou OU")
        private String combinacao = "E";
        private Boolean parar = false;
        @NotNull
        @Size(max = 30)
        private List<MotorRegras.Criterio> criterios = new ArrayList<>();
        @NotNull
        @Size(max = 30)
        private List<MotorRegras.Acao> acoes = new ArrayList<>();
    }

    @Data
    public static class RegraResp {
        private Long id;
        private String nome;
        private String descricao;
        private Integer ordem;
        private Boolean ativo;
        private String combinacao;
        private Boolean parar;
        private List<MotorRegras.Criterio> criterios;
        private List<MotorRegras.Acao> acoes;
        private LocalDateTime updateTime;
    }

    @Data
    public static class RegraTesteReq {
        @Schema(description = "Fatos do chamado (titulo, descricao, categoria_id, ...)")
        private Map<String, Object> fatos = new HashMap<>();
    }

    // ------------------------------------------------------------------ formulários

    @Data
    public static class FormularioSalvar {
        private Long id;
        @Size(max = 32)
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "O código só pode ter letras, números, ponto, hífen e sublinhado")
        private String codigo;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 2000)
        private String descricao;
        @NotBlank(message = "Informe o grupo do catálogo")
        @Size(max = 100)
        private String grupo;
        @Size(max = 64)
        private String icone;
        private Long categoriaId;
        @Min(1) @Max(2)
        private Integer tipo = 2;
        @Min(1) @Max(5)
        private Integer urgencia = 3;
        @Min(1) @Max(5)
        private Integer impacto = 3;
        @Size(max = 255)
        private String tituloModelo;
        @NotNull(message = "Defina as perguntas")
        private Formulario.Definicao definicao;
        @Pattern(regexp = "^(nenhuma|gestor|campo|fixo)$")
        private String aprovacao = "nenhuma";
        @Size(max = 64)
        private String aprovadorCampo;
        private Long aprovadorId;
        @Pattern(regexp = "^(Criar|Reset|Reativar|Desativar|AcessoPasta|Transferir|CriarEmail)?$")
        private String acaoAd;
        private Boolean ativo = true;
        @Min(0) @Max(100000)
        private Integer ordem = 0;
    }

    @Data
    public static class FormularioResp {
        private Long id;
        private String codigo;
        private String nome;
        private String descricao;
        private String grupo;
        private String icone;
        private Long categoriaId;
        private String categoria;
        private Integer tipo;
        private Integer urgencia;
        private Integer impacto;
        private String tituloModelo;
        private Formulario.Definicao definicao;
        private String aprovacao;
        private String aprovadorCampo;
        private Long aprovadorId;
        private String aprovador;
        private String acaoAd;
        private Boolean ativo;
        private Integer ordem;
    }

    /** O que o portal mostra (sem quem aprova nem a ação no AD). */
    @Data
    public static class FormularioCatalogo {
        private Long id;
        private String codigo;
        private String nome;
        private String descricao;
        private String grupo;
        private String icone;
        private Formulario.Definicao definicao;
        private Boolean precisaAprovacao;
    }

    @Data
    public static class EnviarReq {
        @NotNull
        private Long formularioId;
        @NotNull
        private Map<String, Object> respostas = new HashMap<>();
    }

    @Data
    public static class ImportacaoResp {
        private int criados;
        private int atualizados;
        private List<String> avisos = new ArrayList<>();
    }

    // ------------------------------------------------------------------ aprovações

    @Data
    public static class AprovacaoResp {
        private Long id;
        private Long chamadoId;
        private String chamadoTitulo;
        @Schema(description = "Só na lista \"Minhas aprovações\": o aprovador precisa ver o pedido")
        private String chamadoDescricao;
        private String solicitante;
        private Long aprovadorId;
        private String aprovador;
        private String solicitadoPor;
        private Integer status;
        private String statusRotulo;
        private String comentarioPedido;
        private String comentarioResposta;
        private LocalDateTime pedidoEm;
        private LocalDateTime respondidoEm;
    }

    @Data
    public static class ResponderAprovacaoReq {
        @NotNull
        private Long id;
        @NotNull
        private Boolean aprovar;
        @Size(max = 2000)
        private String comentario;
    }

    @Data
    public static class PedirAprovacaoReq {
        @NotNull
        private Long chamadoId;
        @NotEmpty(message = "Escolha pelo menos um aprovador")
        @Size(max = 20)
        private List<Long> aprovadorIds = new ArrayList<>();
        @Size(max = 2000)
        private String comentario;
    }

    // ------------------------------------------------------------------ execuções no AD

    @Data
    public static class ExecucaoResp {
        private Long id;
        private Long chamadoId;
        private String chamadoTitulo;
        private String acao;
        private String status;
        private Integer tentativas;
        private LocalDateTime proximaTentativa;
        private String mensagem;
        private String resultado;
        @Schema(description = "Dados do formulário enviados ao script; só para quem tem itsm:execucao:query")
        private String dados;
        private Boolean senhaGuardada;
        private LocalDateTime senhaVistaEm;
        private LocalDateTime criadoEm;
        private LocalDateTime executadoEm;
    }

    @Data
    public static class CancelarExecucaoReq {
        @NotNull
        private Long id;
        @Size(max = 500)
        private String motivo;
    }

}
