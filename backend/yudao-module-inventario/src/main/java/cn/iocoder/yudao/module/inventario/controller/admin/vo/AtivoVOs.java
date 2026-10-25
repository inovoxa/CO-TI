package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AtivoVOs {

    private AtivoVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Nome, patrimônio, série, IP, usuário */
        private String busca;
        private String tipo;
        private String situacao;
        private Long deptId;
        private Long usuarioId;
        /** sem_suporte | antivirus | sem_contato */
        private String alerta;
        private Long softwareId;
    }

    @Data
    public static class Resp {
        private Long id;
        private String tipo;
        private String nome;
        private String situacao;
        private String patrimonio;
        private String numeroSerie;
        private String fabricante;
        private String modelo;
        private String soNome;
        private String soVersao;
        private String soArquitetura;
        private Boolean soSemSuporte;
        private String cpu;
        private Integer cpuNucleos;
        private Integer memoriaMb;
        private Long discoTotalMb;
        private Long discoLivreMb;
        private String ip;
        private String mac;
        private String dominio;
        private String usuarioLogin;
        private Long usuarioId;
        private String usuarioNome;
        private Long deptId;
        private String deptNome;
        private Boolean deptManual;
        private String antivirus;
        private String origem;
        private String agente;
        private LocalDateTime primeiroInventario;
        private LocalDateTime ultimoInventario;
        private LocalDateTime ultimoContato;
        /** Sem contato há mais dias que o configurado */
        private Boolean semContato;
        private String observacao;
    }

    @Data
    public static class Componente {
        private Long id;
        private String tipo;
        private String descricao;
        private Map<String, Object> dados;
    }

    @Data
    public static class SoftwareInstalado {
        private Long id;
        private Long softwareId;
        private String nome;
        private String editora;
        private String versao;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate instaladoEm;
        private String arquitetura;
    }

    @Data
    public static class ChamadoVinculado {
        private Long id;
        private Long chamadoId;
        private String titulo;
        private Integer status;
        private LocalDateTime abertoEm;
    }

    @Data
    public static class Detalhe {
        private Resp ativo;
        private List<Componente> componentes = new ArrayList<>();
        private List<SoftwareInstalado> softwares = new ArrayList<>();
        private List<ChamadoVinculado> chamados = new ArrayList<>();
    }

    @Data
    public static class Historico {
        private Long id;
        private LocalDateTime quando;
        private String tipo;
        private String campo;
        private String antes;
        private String depois;
        private String usuario;
    }

    /** Ativo cadastrado à mão (impressora, telefone, equipamento sem agente). */
    @Data
    public static class CriarReq {
        @NotBlank
        @Size(max = 255)
        private String nome;
        @NotBlank
        private String tipo;
        @Size(max = 100)
        private String patrimonio;
        @Size(max = 100)
        private String numeroSerie;
        @Size(max = 255)
        private String fabricante;
        @Size(max = 255)
        private String modelo;
        @Size(max = 64)
        private String ip;
        private Long deptId;
        private Long usuarioId;
        private String observacao;
    }

    /** O que a equipe pode mudar. Campos nulos ficam como estão; o que vem do agente só muda em ativo manual. */
    @Data
    public static class AtualizarReq {
        @NotNull
        private Long id;
        private String situacao;
        @Size(max = 100)
        private String patrimonio;
        /** Unidade escolhida à mão (deixa de seguir a do usuário). Use {@link #deptAutomatico} para voltar. */
        private Long deptId;
        private Boolean deptAutomatico;
        private Long usuarioId;
        private String observacao;
        // só para ativo manual
        @Size(max = 255)
        private String nome;
        private String tipo;
        @Size(max = 100)
        private String numeroSerie;
        @Size(max = 255)
        private String fabricante;
        @Size(max = 255)
        private String modelo;
        @Size(max = 64)
        private String ip;
    }

    @Data
    public static class VinculoReq {
        @NotNull
        private Long chamadoId;
        @NotNull
        private Long ativoId;
    }

    @Data
    public static class SoftwareResp {
        private Long id;
        private String nome;
        private String editora;
        private Long instalacoes;
        private Long versoes;
    }

    /** Painel da página "Agentes": como apontar os agentes e quem está reportando. */
    @Data
    public static class PainelAgentes {
        private String endereco;
        private List<String> redes;
        private boolean comSenha;
        private int prologoHoras;
        private int inventarioDias;
        private int semContatoDias;
        private Map<String, Long> porOrigem;
        private List<Map<String, Object>> versoes;
        private long semContato;
        private long ultimas24h;
        private List<Resp> recentes;
    }

}
