package cn.iocoder.yudao.module.monitoramento.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MonitorVOs {

    private MonitorVOs() {
    }

    /** Configuração do monitor (criar e alterar). */
    @Data
    public static class Monitor {
        private Long id;
        @NotBlank
        @Size(max = 150)
        private String nome;
        private String descricao;
        /** http | palavra | json | tcp | ping | dns | push | manual */
        @NotBlank
        private String tipo;
        @Size(max = 1000)
        private String alvo;
        private Integer porta;
        private String metodo;
        private String corpo;
        /** objeto JSON com os cabeçalhos */
        private String cabecalhos;
        private String codigosAceitos;
        @Size(max = 500)
        private String palavra;
        private Boolean palavraInvertida;
        @Size(max = 300)
        private String jsonCaminho;
        private String jsonOperador;
        @Size(max = 500)
        private String jsonEsperado;
        private String dnsTipo;
        private String dnsServidor;
        private String dnsEsperado;
        @Min(5)
        @Max(86400)
        private Integer intervaloS;
        @Min(0)
        @Max(20)
        private Integer tentativas;
        @Min(5)
        @Max(86400)
        private Integer intervaloTentativaS;
        @Min(1)
        @Max(300)
        private Integer tempoLimiteS;
        @Min(0)
        private Integer reenviarACada;
        private Boolean invertido;
        private Boolean ignorarTls;
        @Min(0)
        @Max(30)
        private Integer maxRedirecionamentos;
        private Integer lentoMs;
        @Min(0)
        @Max(365)
        private Integer certAvisoDias;
        private Long unidadeId;
        /** principal | redundante | servico */
        private String papel;
        private Long deptId;
        private Boolean abrirChamado;
        private Long chamadoCategoriaId;
        @Min(1)
        @Max(5)
        private Integer chamadoUrgencia;
        private Boolean chamadoSolucionar;
        private Boolean ativo;
        private List<Long> canalIds;
        // ---- só leitura
        private String pushToken;
        private Long kumaId;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        private String busca;
        private String tipo;
        /** problema | fora | pendente | no_ar | manutencao | pausado */
        private String situacao;
        private Long unidadeId;
        private String papel;
        /** nome | situacao | resposta | uptime24h | uptime30d | unidade | certificado; vazio = fora do ar primeiro */
        private String ordenarPor;
        /** asc | desc */
        private String direcao;
    }

    /** Monitor na lista: configuração resumida, estado atual, últimas batidas e disponibilidade. */
    @Data
    public static class Resp {
        private Long id;
        private String nome;
        private String descricao;
        private String tipo;
        private String alvo;
        private Integer porta;
        private Long unidadeId;
        private String unidadeNome;
        private String papel;
        private Boolean ativo;
        private Integer intervaloS;
        private Integer estado;
        private Boolean lento;
        private LocalDateTime estadoDesde;
        private LocalDateTime ultimaVerificacao;
        private Integer ultimaLatenciaMs;
        private String ultimaMsg;
        private Integer certDias;
        private Long chamadoId;
        private Boolean emManutencao;
        /** últimas batidas, mais antiga primeiro */
        private List<Batida> historico = new ArrayList<>();
        private Double uptime24h;
        private Double uptime30d;
        private Integer latenciaMedia24h;
    }

    @Data
    public static class Batida {
        private LocalDateTime momento;
        private Integer estado;
        private Integer latenciaMs;
        private String msg;
        private Boolean importante;
    }

    @Data
    public static class Detalhe {
        private Monitor config;
        private Resp estado;
        /** mudanças de estado dos últimos 30 dias */
        private List<Evento> eventos = new ArrayList<>();
        private Double uptime1a;
        private String pushUrl;
        private List<String> manutencoes = new ArrayList<>();
    }

    @Data
    public static class Evento {
        private Long monitorId;
        private String monitorNome;
        private LocalDateTime momento;
        private Integer estado;
        private String msg;
        /** quanto tempo ficou no estado anterior (s) */
        private Long duracaoAnteriorS;
    }

    @Data
    public static class Ponto {
        private LocalDateTime momento;
        private Integer media;
        private Integer minima;
        private Integer maxima;
        private Integer falhas;
        private Integer total;
    }

    @Data
    public static class Contagens {
        private long todos;
        private long noAr;
        private long fora;
        private long pendente;
        private long manutencao;
        private long pausado;
        private long lento;
        private long certificadoVencendo;
    }

    /** Teste de uma configuração antes de salvar. */
    @Data
    public static class Teste {
        private boolean ok;
        private Integer latenciaMs;
        private String msg;
        private Integer certDias;
    }

    // ================================================================================== manutenção

    @Data
    public static class Manutencao {
        private Long id;
        @NotBlank
        @Size(max = 150)
        private String titulo;
        private String descricao;
        /** manual | unica | semanal */
        @NotBlank
        private String tipo;
        private Boolean ativa;
        private LocalDateTime inicio;
        private LocalDateTime fim;
        /** 1 (segunda) a 7 (domingo) */
        private List<Integer> diasSemana;
        private LocalTime horaInicio;
        private LocalTime horaFim;
        private Long unidadeId;
        private List<Long> monitorIds;
        // ---- só leitura
        private Boolean valendo;
        private String unidadeNome;
        private List<String> monitorNomes;
    }

    // ================================================================================== canais

    @Data
    public static class Canal {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        /** email | matrix | telegram | webhook */
        @NotBlank
        private String tipo;
        /**
         * email: pessoas (ids) e enderecos; matrix: sala, homeserver e token (opcionais: usam os do servidor);
         * telegram: chatId e token; webhook: url e segredo (assinatura HMAC). Segredos voltam mascarados.
         */
        private Map<String, Object> config;
        private Boolean padrao;
        private Boolean ativo;
        // ---- só leitura
        private Integer monitores;
    }

}
