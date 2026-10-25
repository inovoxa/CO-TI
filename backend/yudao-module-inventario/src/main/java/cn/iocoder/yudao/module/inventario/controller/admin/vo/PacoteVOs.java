package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PacoteVOs {

    private PacoteVOs() {
    }

    @Data
    public static class Pacote {
        private Long id;
        @NotBlank
        @Size(max = 150)
        private String nome;
        private String descricao;
        /** windows | linux | macos */
        private String plataforma;
        /** EXECUTE | STORE | LAUNCH */
        @NotBlank
        private String acao;
        @Size(max = 1000)
        private String comando;
        @Size(max = 500)
        private String caminho;
        @Min(0)
        @Max(10)
        private Integer prioridade;
        private Boolean notificar;
        @Size(max = 500)
        private String notificarTexto;
        private Boolean ativo;
        // só leitura
        private String fileid;
        private String arquivoNome;
        private Long tamanho;
        private String sha256;
        private Integer fragmentos;
        /** situação → quantidade de máquinas */
        private Map<String, Long> contagens;
    }

    @Data
    public static class ImplantarReq {
        @NotNull
        private Long pacoteId;
        /** Máquinas escolhidas; vazio = todas da unidade */
        private List<Long> ativoIds;
        private Long deptId;
    }

    @Data
    public static class Resultado {
        private int pedidos;
        private int jaPendentes;
        /** Sem agente OCS, fora de uso, de outra plataforma ou fora do escopo */
        private int ignorados;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ImplantacoesReq extends PageParam {
        private Long pacoteId;
        private Long ativoId;
        private String situacao;
    }

    @Data
    public static class Implantacao {
        private Long id;
        private Long pacoteId;
        private String pacoteNome;
        private Long ativoId;
        private String ativoNome;
        private String situacao;
        private String codigo;
        private LocalDateTime pedidoEm;
        private LocalDateTime notificadoEm;
        private LocalDateTime concluidoEm;
    }

}
