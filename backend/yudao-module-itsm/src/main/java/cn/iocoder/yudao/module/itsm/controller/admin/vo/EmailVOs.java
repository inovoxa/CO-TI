package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Configuração de e-mail: conta de envio, eventos e caixas coletadas. Senhas só entram (nunca saem).
 */
public final class EmailVOs {

    private EmailVOs() {
    }

    @Data
    public static class Config {
        private Boolean ativo = false;
        @Size(max = 255)
        private String smtpHost;
        @Min(1) @Max(65535)
        private Integer smtpPorta = 587;
        @Pattern(regexp = "^(nenhuma|starttls|ssl)$")
        private String smtpSeguranca = "starttls";
        @Size(max = 255)
        private String smtpUsuario;
        @Schema(description = "Só ao trocar; vazio mantém a atual")
        @Size(max = 500)
        private String smtpSenha;
        @Schema(description = "Resposta: há senha guardada")
        private Boolean smtpSenhaDefinida;
        @Email(message = "Remetente inválido")
        @Size(max = 320)
        private String remetenteEmail;
        @Size(max = 100)
        private String remetenteNome = "Central de Serviços";
        @Email(message = "Endereço de resposta inválido")
        @Size(max = 320)
        private String responderPara;
        @Pattern(regexp = "^(https?://[^\\s\"'<>]+)?$", message = "Endereço do portal deve começar com http:// ou https://")
        @Size(max = 255)
        private String urlPortal;
        @Size(max = 4000)
        private String assinatura;
    }

    @Data
    public static class Evento {
        @NotBlank
        private String evento;
        private String nome;
        private Boolean ativo;
        private Boolean paraSolicitante;
        private Boolean paraObservador;
        private Boolean paraTecnico;
        private Boolean paraAprovador;
        @NotBlank(message = "Informe o assunto")
        @Size(max = 255)
        private String assunto;
        @NotBlank(message = "Informe o texto")
        @Size(max = 20000)
        private String corpo;
        private Integer ordem;
    }

    @Data
    public static class Caixa {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 100)
        private String nome;
        private Boolean ativo = true;
        @NotBlank(message = "Informe o servidor IMAP")
        @Size(max = 255)
        private String host;
        @Min(1) @Max(65535)
        private Integer porta = 993;
        @Pattern(regexp = "^(nenhuma|starttls|ssl)$")
        private String seguranca = "ssl";
        @NotBlank(message = "Informe o usuário")
        @Size(max = 255)
        private String usuario;
        @Schema(description = "Só ao trocar; vazio mantém a atual")
        @Size(max = 500)
        private String senha;
        private Boolean senhaDefinida;
        @NotBlank
        @Size(max = 255)
        private String pasta = "INBOX";
        @Size(max = 255)
        private String pastaProcessados;
        @Size(max = 255)
        private String pastaRecusados;
        private Boolean aceitarDesconhecidos = false;
        private Boolean exigirAutenticacao = false;
        private Long categoriaId;
        @Min(1) @Max(50)
        private Integer anexoMaxMb = 10;
        private LocalDateTime ultimaColeta;
        private String ultimoErro;
    }

    @Data
    public static class TesteEnvioReq {
        @NotBlank
        @Email(message = "E-mail inválido")
        private String para;
    }

}
