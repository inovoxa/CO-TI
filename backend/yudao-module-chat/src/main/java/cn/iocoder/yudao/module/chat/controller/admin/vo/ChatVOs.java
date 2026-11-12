package cn.iocoder.yudao.module.chat.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ChatVOs {

    private ChatVOs() {
    }

    @Data
    public static class DiretaReq {
        @NotNull
        private Long userId;
    }

    @Data
    public static class GrupoReq {
        @NotBlank(message = "Dê um nome ao grupo")
        @Size(max = 100)
        private String nome;
        @NotEmpty(message = "Escolha ao menos uma pessoa")
        @Size(max = 500)
        private List<Long> membros;
    }

    @Data
    public static class RenomearReq {
        @NotNull
        private Long conversaId;
        @NotBlank(message = "Dê um nome ao grupo")
        @Size(max = 100)
        private String nome;
    }

    @Data
    public static class MembrosReq {
        @NotNull
        private Long conversaId;
        @NotEmpty
        @Size(max = 500)
        private List<Long> userIds;
    }

    @Data
    public static class EnviarReq {
        @NotNull
        private Long conversaId;
        /** Gerado pela tela para esta mensagem: reenviar depois de uma queda não duplica */
        @NotBlank
        @Size(max = 40)
        private String clienteId;
        private String conteudo;
        private Long respostaId;
        private List<Long> mencoes = new ArrayList<>();
        @Size(max = 10)
        private List<Long> anexoIds = new ArrayList<>();
    }

    @Data
    public static class EditarReq {
        @NotNull
        private Long mensagemId;
        @NotBlank
        private String conteudo;
        private List<Long> mencoes = new ArrayList<>();
    }

    @Data
    public static class LidaReq {
        @NotNull
        private Long conversaId;
        @NotNull
        private Long seq;
    }

    @Data
    public static class Ausencia {
        @NotNull
        private Boolean ativa;
        @Size(max = 500, message = "A mensagem pode ter até 500 caracteres")
        private String mensagem;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate inicio;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate fim;
    }

    @Data
    public static class Pessoa {
        private Long id;
        private String nome;
        private String unidade;
        private String avatar;
        private Boolean online;
    }

    @Data
    public static class Membro {
        private Long id;
        private String nome;
        private String avatar;
        private String papel;
        private Boolean online;
    }

    @Data
    public static class ConversaResp {
        private Long id;
        private String tipo;
        private String nome;
        private String avatar;
        /** Na conversa direta, a outra pessoa */
        private Long outroId;
        private Boolean online;
        /** Na conversa direta: a outra pessoa está com a resposta automática valendo */
        private Boolean ausente;
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
        private java.time.LocalDate ausenteAte;
        private String papel;
        private Long naoLidas;
        private Long ultimaSeq;
        private Long ultimaLidaSeq;
        private Long desdeSeq;
        private String ultimoTexto;
        private LocalDateTime ultimaMensagemEm;
        private List<Membro> membros;
    }

    @Data
    public static class AnexoResp {
        private Long id;
        private String nome;
        private Long tamanho;
        private Boolean imagem;
        private Boolean expirado;
    }

    @Data
    public static class Config {
        private boolean anexosLigados;
        private int anexoMaxMb;
        /** 0 = os anexos ficam para sempre */
        private int anexosHoras;
    }

    @Data
    public static class Referencia {
        private Long id;
        private String remetente;
        private String trecho;
    }

    @Data
    public static class MensagemResp {
        private Long id;
        private Long conversaId;
        private Long seq;
        private Long remetenteId;
        private String remetente;
        private String avatar;
        private String tipo;
        /** Vazio se apagada */
        private String conteudo;
        private Boolean apagada;
        private Boolean editada;
        private String clienteId;
        private Referencia resposta;
        private List<Pessoa> mencoes;
        private List<AnexoResp> anexos;
        private LocalDateTime criadaEm;
    }

    /** Resultado da busca: a mensagem e onde ela está. */
    @Data
    public static class Achado {
        private MensagemResp mensagem;
        private String conversa;
    }

}
