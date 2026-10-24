package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entradas e saídas do importador do GLPI.
 */
public final class ImportacaoVOs {

    private ImportacaoVOs() {
    }

    @Data
    public static class Conexao {
        @NotBlank(message = "Informe o servidor do banco do GLPI")
        @Size(max = 253)
        private String host;
        @Min(1) @Max(65535)
        private Integer porta = 3306;
        @NotBlank(message = "Informe o nome do banco")
        @Size(max = 64)
        private String banco;
        @NotBlank(message = "Informe o usuário")
        @Size(max = 64)
        private String usuario;
        @Size(max = 200)
        private String senha;
        @Schema(description = "Exigir TLS com o certificado do servidor conferido")
        private Boolean exigirTls = false;
    }

    @Data
    public static class Opcoes {
        @Schema(description = "Pessoas do GLPI que não existem aqui viram usuários desativados (senão, ficam só pelo e-mail)")
        private Boolean criarUsuarios = true;
        private Boolean chamados = true;
        private Boolean baseConhecimento = true;
        private Boolean problemasMudancas = true;
        private Boolean governanca = true;
        @Schema(description = "Chamados, problemas e mudanças mantêm o número do GLPI (exige que os números estejam livres aqui)")
        private Boolean manterNumeros = true;
        @Schema(description = "Unidade para o que não tiver entidade com o mesmo nome de uma unidade daqui")
        private Long deptPadraoId;
        @Schema(description = "Só chamados, problemas e mudanças abertos a partir desta data")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate desde;
        @Schema(description = "Trazer também o que está na lixeira do GLPI")
        private Boolean incluirLixeira = false;
        @Schema(description = "Copiar os anexos (precisa da pasta files/ do GLPI montada no servidor)")
        private Boolean arquivos = true;
    }

    @Data
    public static class IniciarReq {
        @Valid
        @NotNull
        private Conexao conexao;
        @Valid
        @NotNull
        private Opcoes opcoes = new Opcoes();
    }

    /** Resultado da verificação (nada é gravado). */
    @Data
    public static class Verificacao {
        private String versao;
        private Map<String, Long> contagens = new LinkedHashMap<>();
        private Integer usuariosEncontrados;
        private Integer usuariosNovos;
        private Integer usuariosSemLogin;
        private List<Entidade> entidades = new ArrayList<>();
        /** Tipo → quantos números do GLPI já estão em uso aqui */
        private Map<String, Integer> numerosOcupados = new LinkedHashMap<>();
        private Boolean pastaArquivos;
        private List<String> avisos = new ArrayList<>();
    }

    @Data
    public static class Entidade {
        private Long id;
        private String nome;
        private Long deptId;
        private String dept;
    }

    @Data
    public static class Etapa {
        private long lidos;
        private long importados;
        private long existentes;
        private long erros;
    }

    @Data
    public static class Resp {
        private Long id;
        private String status;
        private String host;
        private Integer porta;
        private String banco;
        private String usuario;
        private Opcoes opcoes;
        private String etapa;
        private Map<String, Etapa> progresso;
        private String log;
        private String versaoOrigem;
        private LocalDateTime iniciadoEm;
        private LocalDateTime terminadoEm;
        private String quem;
    }

}
