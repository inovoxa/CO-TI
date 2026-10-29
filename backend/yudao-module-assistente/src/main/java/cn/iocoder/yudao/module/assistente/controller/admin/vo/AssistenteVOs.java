package cn.iocoder.yudao.module.assistente.controller.admin.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AssistenteVOs {

    private AssistenteVOs() {
    }

    @Data
    public static class PerguntaReq {
        /** Vazio = conversa nova */
        private Long sessaoId;
        @NotBlank
        @Size(max = 4000)
        private String texto;
        /** true = a resposta vai ser lida em voz alta (conversa por voz): curta e sem tabelas */
        private Boolean falada;
    }

    @Data
    public static class Voz {
        /** Há provedor de IA com modelo de transcrição: o áudio gravado vira texto no servidor */
        private boolean transcricao;
        /** A IA está ligada (sem ela o assistente não responde) */
        private boolean ia;
    }

    @Data
    public static class Resposta {
        private Long sessaoId;
        private String resposta;
        /** Consultas feitas (ferramenta e argumentos) */
        private List<Map<String, Object>> consultas;
        private boolean erro;
    }

    @Data
    public static class Sessao {
        private Long id;
        private String titulo;
        private LocalDateTime atualizadaEm;
    }

    @Data
    public static class Mensagem {
        private Long id;
        private String papel;
        private String conteudo;
        private Boolean erro;
        private LocalDateTime criadaEm;
        private List<Map<String, Object>> consultas;
    }

}
