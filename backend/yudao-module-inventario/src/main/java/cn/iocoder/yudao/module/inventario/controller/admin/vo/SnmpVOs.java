package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class SnmpVOs {

    private SnmpVOs() {
    }

    /** Credencial. Os segredos só entram (escrita); na leitura vem só se estão definidos. */
    @Data
    public static class Credencial {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        /** v2c | v3 */
        @NotBlank
        private String versao;
        @Size(max = 200)
        private String comunidade;
        @Size(max = 100)
        private String usuario;
        /** noAuthNoPriv | authNoPriv | authPriv */
        private String nivel;
        /** MD5 | SHA | SHA256 | SHA512 */
        private String authProtocolo;
        @Size(max = 200)
        private String authSenha;
        /** DES | AES | AES256 */
        private String privProtocolo;
        @Size(max = 200)
        private String privSenha;
        @Min(1)
        @Max(65535)
        private Integer porta;
        private Integer ordem;
        private Boolean ativo;
        private boolean comunidadeDefinida;
        private boolean authSenhaDefinida;
        private boolean privSenhaDefinida;
    }

    @Data
    public static class Resultado {
        private String ip;
        private boolean respondeu;
        private String credencial;
        private String nome;
        private String tipo;
        private String fabricante;
        private String modelo;
        private String numeroSerie;
        private String descricao;
        private Long paginas;
        private List<String> suprimentos;
        private int interfaces;
        private Long ativoId;
        private String erro;
    }

    @Data
    public static class Varredura {
        private int alvos;
        private int responderam;
        private int gravados;
        private long duracaoMs;
    }

    @Data
    public static class Alvo {
        private String ip;
        private String credencial;
        private Long ativoId;
        private String ativoNome;
        private String ativoTipo;
        private LocalDateTime ultimaTentativa;
        private LocalDateTime ultimaResposta;
        private String erro;
    }

}
