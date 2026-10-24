package cn.iocoder.yudao.module.atendimento.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entradas e saídas da API do atendimento.
 */
public final class AtendimentoVOs {

    private AtendimentoVOs() {
    }

    // ================= caixas =================

    @Data
    public static class Caixa {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @NotBlank
        @Pattern(regexp = "api|whatsapp_cloud|whatsapp_evolution|telegram|widget")
        private String canal;
        private Boolean ativo = true;
        @Size(max = 500)
        private String webhookUrl;
        @Size(max = 2000)
        private String saudacao;
        private Long equipeId;
        private Boolean atribuicaoAutomatica = true;
        @Min(0) @Max(1000)
        private Integer limitePorAgente = 0;
        @Min(0) @Max(720)
        private Integer reabrirHoras = 24;
        private List<Long> agenteIds = new ArrayList<>();
        // só na resposta
        private Boolean tokenDefinido;
        private Boolean segredoDefinido;
        private List<Agente> agentes;
        private Long abertas;
    }

    /** Credenciais geradas agora: mostradas uma única vez. */
    @Data
    public static class Credenciais {
        private String token;
        private String segredoWebhook;
        private String enderecoEntrada;
    }

    @Data
    public static class Agente {
        private Long userId;
        private String nome;
        private String disponibilidade;
        private Boolean disponivel;
        private Integer abertas;
    }

    // ================= contatos =================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FiltroContato extends PageParam {
        @Size(max = 100)
        private String busca;
    }

    @Data
    public static class Contato {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 30)
        private String telefone;
        @Email
        @Size(max = 255)
        private String email;
        @Size(max = 255)
        private String identificador;
        private Long userId;
        private Map<String, Object> atributos;
        @Size(max = 10_000)
        private String observacao;
        private Boolean bloqueado = false;
        // só na resposta
        private String usuario;
        private LocalDateTime ultimaAtividadeEm;
        private Integer conversas;
    }

    // ================= conversas =================

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class FiltroConversa extends PageParam {
        @Schema(description = "minhas | nao_atribuidas | todas")
        private String aba = "minhas";
        @Schema(description = "0 aberta, 1 resolvida, 2 pendente, 3 adiada; vazio = abertas")
        private Integer status;
        private Long caixaId;
        private Long etiquetaId;
        private Long contatoId;
        @Size(max = 100)
        private String busca;
    }

    @Data
    public static class ConversaResumo {
        private Long id;
        private Long caixaId;
        private String caixa;
        private String canal;
        private Long contatoId;
        private String contato;
        private String telefone;
        private Integer status;
        private String statusRotulo;
        private Long agenteId;
        private String agente;
        private Integer prioridade;
        private Long chamadoId;
        private LocalDateTime adiadaAte;
        private LocalDateTime ultimaAtividadeEm;
        private LocalDateTime ultimaEntradaEm;
        private String ultimaMensagem;
        private Integer ultimaMensagemTipo;
        private Integer naoLidas;
        private List<Etiqueta> etiquetas = new ArrayList<>();
    }

    @Data
    public static class Mensagem {
        private Long id;
        private Long conversaId;
        private Integer tipo;
        private Boolean privada;
        private String remetenteTipo;
        private Long remetenteId;
        private String remetente;
        private String conteudo;
        private String formato;
        private List<Anexo> anexos = new ArrayList<>();
        private String status;
        private String erro;
        private LocalDateTime criadaEm;
    }

    @Data
    public static class Anexo {
        @Size(max = 1000)
        private String url;
        @Size(max = 100)
        private String tipo;
        @Size(max = 255)
        private String nome;
        private Long tamanho;
    }

    @Data
    public static class ResponderReq {
        @NotNull
        private Long conversaId;
        @Size(max = 4096, message = "A mensagem pode ter até 4096 caracteres")
        private String conteudo;
        private Boolean privada = false;
        @Valid
        @Size(max = 10)
        private List<Anexo> anexos = new ArrayList<>();
    }

    @Data
    public static class AtribuirReq {
        @NotNull
        private Long conversaId;
        @Schema(description = "Vazio = tirar o atendente")
        private Long agenteId;
    }

    @Data
    public static class StatusReq {
        @NotNull
        private Long conversaId;
        @NotNull @Min(0) @Max(3)
        private Integer status;
        @Schema(description = "Para adiar")
        private LocalDateTime adiadaAte;
    }

    @Data
    public static class EtiquetasReq {
        @NotNull
        private Long conversaId;
        @NotNull
        private List<Long> etiquetaIds;
    }

    @Data
    public static class PrioridadeReq {
        @NotNull
        private Long conversaId;
        @Min(1) @Max(4)
        private Integer prioridade;
    }

    @Data
    public static class AbrirChamadoReq {
        @NotNull
        private Long conversaId;
        @NotBlank(message = "Informe o título")
        @Size(max = 255)
        private String titulo;
        private Long categoriaId;
        @Min(1) @Max(5)
        private Integer urgencia = 3;
        @Schema(description = "Levar as últimas mensagens da conversa para a descrição")
        private Boolean incluirConversa = true;
    }

    /** Painel lateral: contato, chamados dele e conversas anteriores. */
    @Data
    public static class Lateral {
        private Contato contato;
        private List<ChamadoResumo> chamados = new ArrayList<>();
        private List<ConversaResumo> anteriores = new ArrayList<>();
    }

    @Data
    public static class ChamadoResumo {
        private Long id;
        private String titulo;
        private String status;
        private LocalDateTime abertoEm;
    }

    // ================= etiquetas e respostas =================

    @Data
    public static class Etiqueta {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 50)
        private String nome;
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Cor no formato #RRGGBB")
        private String cor = "#6b7280";
        @Size(max = 255)
        private String descricao;
    }

    @Data
    public static class RespostaPronta {
        private Long id;
        @NotBlank(message = "Informe o atalho")
        @Pattern(regexp = "^[a-z0-9_-]{1,50}$", message = "Atalho: letras minúsculas, números, - e _")
        private String atalho;
        @NotBlank(message = "Escreva o texto")
        @Size(max = 4096)
        private String conteudo;
    }

    // ================= canal API (sistemas externos) =================

    @Data
    public static class EntradaApi {
        @NotBlank(message = "Informe origemId (o id do contato no seu sistema)")
        @Size(max = 255)
        private String origemId;
        @Valid
        private ContatoApi contato = new ContatoApi();
        @Size(max = 4096)
        private String conteudo;
        @Size(max = 255)
        private String idExterno;
        @Valid
        @Size(max = 5)
        private List<AnexoApi> anexos = new ArrayList<>();
    }

    @Data
    public static class ContatoApi {
        @Size(max = 150)
        private String nome;
        @Size(max = 30)
        private String telefone;
        @Email
        @Size(max = 255)
        private String email;
        @Size(max = 255)
        private String identificador;
    }

    @Data
    public static class AnexoApi {
        @NotBlank
        @Size(max = 255)
        private String nome;
        @Size(max = 100)
        private String tipo;
        @Schema(description = "Conteúdo em Base64 (até 10 MB)")
        @NotBlank
        @Size(max = 14_000_000)
        private String dados;
    }

    @Data
    public static class StatusApi {
        @NotBlank
        @Size(max = 255)
        private String idExterno;
        @NotBlank
        @Pattern(regexp = "entregue|lida|falhou")
        private String status;
        @Size(max = 500)
        private String erro;
    }

    @Data
    public static class EntradaApiResp {
        private Long conversaId;
        private Long mensagemId;
        private Boolean duplicada;
    }

}
