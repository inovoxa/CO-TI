package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de conhecimento.
 */
public final class KbVOs {

    private KbVOs() {
    }

    @Data
    public static class CategoriaSalvar {
        private Long id;
        private Long parentId = 0L;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        @Size(max = 1000)
        private String descricao;
        @Min(0) @Max(100000)
        private Integer ordem = 0;
    }

    @Data
    public static class ArtigoSalvar {
        private Long id;
        @NotBlank(message = "Informe o título")
        @Size(max = 255)
        private String titulo;
        @Size(max = 1000)
        private String resumo;
        @NotBlank(message = "Escreva o conteúdo")
        private String conteudo;
        private Long categoriaId;
        private Boolean faq = false;
        private Boolean publicado = true;
        private Boolean fixado = false;
        private LocalDateTime inicioPublicacao;
        private LocalDateTime fimPublicacao;
    }

    @Data
    public static class BuscaReq {
        @Size(max = 200)
        private String q;
        private Long categoriaId;
        @Schema(description = "Só na tela de gestão: filtra FAQ ou internos")
        private Boolean faq;
        @Schema(description = "Só na tela de gestão: rascunhos (false) ou publicados (true)")
        private Boolean publicado;
        @Min(1)
        private Integer pageNo = 1;
        @Min(1) @Max(100)
        private Integer pageSize = 20;
    }

    /** Linha da busca (mapeada direto do SQL). */
    @Data
    public static class ArtigoResumo {
        private Long id;
        private String titulo;
        private String resumo;
        private Long categoriaId;
        private String categoria;
        private Boolean faq;
        private Boolean publicado;
        private Boolean fixado;
        private Integer visualizacoes;
        private Integer util;
        private Integer naoUtil;
        private LocalDateTime updateTime;
        private Double relevancia;
        @Schema(description = "Trecho com as palavras buscadas em <mark> (HTML já escapado)")
        private String trecho;
    }

    @Data
    public static class Artigo {
        private Long id;
        private String titulo;
        private String resumo;
        private String conteudo;
        private Long categoriaId;
        private String categoria;
        private Boolean faq;
        private Boolean publicado;
        private Boolean fixado;
        private LocalDateTime inicioPublicacao;
        private LocalDateTime fimPublicacao;
        private Long autorId;
        private String autor;
        private Integer revisao;
        private Integer visualizacoes;
        private Integer util;
        private Integer naoUtil;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        @Schema(description = "Voto do usuário logado: true útil, false não útil, vazio sem voto")
        private Boolean meuVoto;
        private Boolean podeEditar;
        private List<Long> chamados = new ArrayList<>();
    }

    @Data
    public static class Revisao {
        private Long id;
        private Integer revisao;
        private String titulo;
        private String conteudo;
        private String autor;
        private LocalDateTime criadoEm;
    }

    @Data
    public static class VotoReq {
        @NotNull
        private Long artigoId;
        @NotNull
        private Boolean util;
    }

    @Data
    public static class UsarNoChamadoReq {
        @NotNull
        private Long artigoId;
        @NotNull
        private Long chamadoId;
        @Schema(description = "true registra como solução; false, como acompanhamento")
        private Boolean comoSolucao = true;
    }

}
