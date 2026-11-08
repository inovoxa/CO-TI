package cn.iocoder.yudao.module.infraestrutura.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class IntegracaoVOs {

    private IntegracaoVOs() {
    }

    /** Nunca traz o segredo. */
    @Data
    public static class Entrada {
        private Long id;
        @Size(max = 40)
        private String codigo;
        @NotBlank
        @Size(max = 100)
        private String nome;
        private Boolean ativa;
        private String modo;
        @NotBlank
        private String autenticacao;
        @Size(max = 500)
        private String ipsPermitidos;
        @NotNull
        private Long formularioId;
        @NotNull
        private Long solicitanteId;
        @NotBlank
        private String atributoAd;
        @NotBlank
        @Size(max = 150)
        private String caminhoId;
        @Size(max = 150)
        private String caminhoNome;
        private Boolean conferirNome;
        @NotBlank
        @Size(max = 150)
        private String caminhoSituacao;
        @NotBlank
        @Size(max = 300)
        private String valoresDesligar;
        @Size(max = 300)
        private String valoresCancelar;
        @Size(max = 150)
        private String caminhoData;
        @Size(max = 150)
        private String caminhoChave;
        private Map<String, String> mapa;
        @Size(max = 1000)
        private String gruposProtegidos;
        @Size(max = 1000)
        private String loginsProtegidos;
        @NotNull
        private Integer limiteDia;
    }

    /** @param segredo só na criação; não é possível ver de novo */
    public record Salva(Long id, String segredo) {
    }

    @Data
    public static class TesteReq {
        @NotNull
        private Long id;
        @NotBlank
        @Size(max = 262144)
        private String json;
    }

    public record Teste(String tipo, String mensagem, String login, Map<String, String> campos, LocalDateTime executarEm) {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EventosReq extends PageParam {
        private Long entradaId;
        private String status;
    }

    @Data
    public static class AbrirChamado {
        @NotNull
        private Long formularioId;
        private Map<String, Object> respostas;
    }

    public record ChamadoApi(Long id, String titulo, Integer status, Long categoriaId, LocalDateTime abertoEm, LocalDateTime solucionadoEm,
                             LocalDateTime fechadoEm) {
    }

    /** Nunca traz a chave. */
    @Data
    public static class ChaveApi {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        private String prefixo;
        private List<String> escopos;
        @NotNull
        private Long solicitanteId;
        @Size(max = 500)
        private String ipsPermitidos;
        private Boolean ativa;
        private LocalDateTime expiraEm;
        private LocalDateTime ultimoUso;
    }

    /** Nunca traz o segredo. */
    @Data
    public static class Saida {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        @NotBlank
        @Size(max = 1000)
        private String url;
        private List<String> eventos;
        private Boolean ativa;
    }

    public record Entrega(Long id, String saida, String evento, Long chamadoId, String status, Integer tentativas, Integer respostaCodigo, String erro,
                          LocalDateTime criadoEm, LocalDateTime enviadoEm) {
    }

    public record Evento(Long id, Long entradaId, String status, String resultado, String login, Long chamadoId, String ip, Integer tentativas,
                         LocalDateTime recebidoEm, LocalDateTime processadoEm) {
    }

}
