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
        @Pattern(regexp = "api|whatsapp_cloud|instagram|telegram")
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
        /** Calendário do Service Desk com o expediente da caixa (vazio = sempre em horário) */
        private Long calendarioId;
        @Size(max = 2000)
        private String mensagemForaHorario;
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
        /** Canal com janela de 24 h (WhatsApp oficial, Instagram) */
        private Boolean janela = false;
        /** Até quando dá para mandar texto livre; depois, só modelo aprovado */
        private LocalDateTime janelaAte;
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
        /** modelo usado, localização, resposta de botão, reação, origem (celular, histórico) */
        private Map<String, Object> dados;
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


    // ================= macros e automações =================

    @Data
    public static class AcaoVO {
        @NotBlank
        @Size(max = 30)
        private String tipo;
        /** id, lista de ids, texto ou código, conforme o tipo */
        private Object valor;
    }

    @Data
    public static class Condicao {
        @NotBlank
        @Size(max = 30)
        private String campo;
        @NotBlank
        @Size(max = 20)
        private String operador;
        private Object valor;
    }

    @Data
    public static class Automacao {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Size(max = 500)
        private String descricao;
        @NotBlank
        private String evento;
        @Valid
        @Size(max = 20)
        private List<Condicao> condicoes = new ArrayList<>();
        @Valid
        @Size(max = 20)
        private List<AcaoVO> acoes = new ArrayList<>();
        private Boolean ativa = true;
        private Integer ordem = 0;
        // só na resposta
        private Long execucoes;
        private LocalDateTime ultimaExecucao;
    }

    @Data
    public static class Macro {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Pattern(regexp = "pessoal|todos")
        private String visibilidade = "pessoal";
        @Valid
        @Size(max = 20)
        private List<AcaoVO> acoes = new ArrayList<>();
        // só na resposta
        private Boolean minha;
    }

    @Data
    public static class MacroExecReq {
        @NotNull
        private Long macroId;
        @NotNull
        private Long conversaId;
    }

    // ================= canais (WhatsApp oficial, Instagram, Telegram) =================

    /** Configuração do canal da caixa. Segredo em branco = mantém o que está gravado. */
    @Data
    public static class CanalConfig {
        @NotNull
        private Long caixaId;
        // WhatsApp Cloud API
        @Size(max = 40) @Pattern(regexp = "\\d*", message = "O identificador do número tem só dígitos")
        private String phoneNumberId;
        @Size(max = 40) @Pattern(regexp = "\\d*", message = "O identificador da conta (WABA) tem só dígitos")
        private String wabaId;
        /** Número também usado no aplicativo WhatsApp Business (coexistência): traz ecos, histórico e contatos */
        private Boolean coexistencia = false;
        // Instagram
        @Size(max = 40) @Pattern(regexp = "\\d*", message = "O identificador da conta do Instagram tem só dígitos")
        private String igUserId;
        // segredos (Meta: token de acesso e chave secreta do app; Telegram: token do robô)
        @Size(max = 1000)
        private String token;
        @Size(max = 200)
        private String appSecret;
        @Size(max = 200)
        private String botToken;
    }

    @Data
    public static class CanalConfigResp {
        private Long caixaId;
        private String canal;
        private String phoneNumberId;
        private String wabaId;
        private Boolean coexistencia;
        private String igUserId;
        private Boolean tokenDefinido;
        private Boolean appSecretDefinido;
        private Boolean botTokenDefinido;
        /** Meta: palavra de verificação para cadastrar o webhook no app */
        private String verifyToken;
        /** Endereço a cadastrar no provedor (Meta) ou já cadastrado (Telegram) */
        private String enderecoWebhook;
        /** O que o provedor informou no último teste: número exibido, nome verificado, qualidade, usuário... */
        private Map<String, Object> info;
        private LocalDateTime testadoEm;
        private String erroTeste;
    }

    @Data
    public static class Modelo {
        private Long id;
        private String nome;
        private String idioma;
        private String categoria;
        private String status;
        private String corpo;
        private Integer variaveis;
        /** Nomes das variáveis quando o modelo usa variáveis com nome ({{nome}}) */
        private List<String> nomesVariaveis = new ArrayList<>();
    }

    @Data
    public static class ModeloReq {
        @NotNull
        private Long conversaId;
        @NotNull
        private Long modeloId;
        @Size(max = 20)
        private List<@Size(max = 1000) String> parametros = new ArrayList<>();
    }

}
