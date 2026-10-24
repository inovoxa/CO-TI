package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entradas e saídas da governança: fornecedores, contratos, licenças, orçamentos, projetos e custos.
 *
 * <p>Datas sem hora vão como texto "aaaa-mm-dd" (o padrão do yudao mandaria [aaaa, m, d]).
 */
public final class GovernancaVOs {

    private GovernancaVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class Filtro extends PageParam {
        @Size(max = 100)
        private String busca;
        @Schema(description = "Contrato: vigente | a_vencer | vencido | encerrado | futuro. Licença: vigente | a_vencer | vencida | inativa | excedida. Projeto: 1..5")
        private String situacao;
        private Long fornecedorId;
        private Long deptId;
        @Schema(description = "Projetos: só os meus (responsável, membro ou com tarefa minha)")
        private Boolean meus;
    }

    // ================= fornecedor =================

    @Data
    public static class Fornecedor {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 60)
        private String tipo;
        @Size(max = 20)
        private String documento;
        @Email
        @Size(max = 255)
        private String email;
        @Size(max = 40)
        private String telefone;
        @Size(max = 255)
        private String site;
        @Size(max = 150)
        private String contato;
        @Size(max = 255)
        private String endereco;
        @Size(max = 100)
        private String cidade;
        @Pattern(regexp = "^$|[A-Za-z]{2}", message = "UF com duas letras")
        private String uf;
        @Size(max = 9)
        private String cep;
        @Size(max = 10_000)
        private String observacao;
        private Boolean ativo = true;
        // só na resposta
        private Integer totalContratos;
        private Integer totalLicencas;
    }

    // ================= contrato =================

    @Data
    public static class Contrato {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 60)
        private String numero;
        @Size(max = 60)
        private String tipo;
        private Long fornecedorId;
        private Long deptId;
        private Long responsavelId;
        @NotNull(message = "Informe o início")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicio;
        @NotNull(message = "Informe a duração")
        @Min(1) @Max(1200)
        private Integer duracaoMeses;
        @Min(0) @Max(1199)
        private Integer avisoPrevioMeses = 0;
        @Min(0) @Max(2)
        private Integer renovacao = 0;
        @Min(0) @Max(365)
        private Integer alertaDias = 30;
        @Size(max = 60)
        private String numeroContabil;
        @Size(max = 10_000)
        private String observacao;
        private Boolean encerrado = false;
        // só na resposta
        private String fornecedor;
        private String dept;
        private String responsavel;
        private String renovacaoRotulo;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fim;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate prazoAviso;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate prazo;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate dataAlerta;
        private String situacao;
        private String situacaoRotulo;
        private Integer periodo;
        private BigDecimal custoTotal;
        private Integer totalLicencas;
        private List<Renovacao> renovacoes;
    }

    @Data
    public static class Renovacao {
        private Long id;
        private Integer meses;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimAntes;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimDepois;
        private String observacao;
        private String quem;
        private java.time.LocalDateTime quando;
    }

    @Data
    public static class RenovarReq {
        @NotNull
        private Long contratoId;
        @NotNull @Min(1) @Max(120)
        private Integer meses;
        @Size(max = 1000)
        private String observacao;
    }

    // ================= custos =================

    @Data
    public static class Custo {
        private Long id;
        @NotBlank
        @Pattern(regexp = "contrato|projeto|licenca")
        private String objeto;
        @NotNull
        private Long objetoId;
        @NotBlank(message = "Informe a descrição")
        @Size(max = 150)
        private String nome;
        @NotNull(message = "Informe a data")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicio;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fim;
        @NotNull(message = "Informe o valor")
        @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2)
        private BigDecimal valor;
        private Long orcamentoId;
        @Size(max = 1000)
        private String observacao;
        // só na resposta
        private String orcamento;
        /** Nome do contrato, projeto ou licença */
        private String origem;
    }

    // ================= licença =================

    @Data
    public static class Licenca {
        private Long id;
        @NotBlank(message = "Informe o software")
        @Size(max = 150)
        private String software;
        @Size(max = 100)
        private String fabricante;
        @Size(max = 150)
        private String nome;
        @Size(max = 60)
        private String tipo;
        @NotNull @Min(-1) @Max(10_000_000)
        private Integer quantidade = 1;
        private Boolean permiteExcesso = false;
        @Schema(description = "Escrita: nova chave (vazio mantém a atual)")
        @Size(max = 4000)
        private String chave;
        @Schema(description = "Escrita: apagar a chave guardada")
        private Boolean removerChave = false;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate expira;
        @Min(0) @Max(365)
        private Integer alertaDias = 30;
        private Long fornecedorId;
        private Long contratoId;
        private Long deptId;
        private Long responsavelId;
        @Size(max = 10_000)
        private String observacao;
        private Boolean ativo = true;
        // só na resposta
        private Boolean chaveDefinida;
        private String fornecedor;
        private String contrato;
        private String dept;
        private String responsavel;
        private Integer emUso;
        private Integer disponivel;
        private Boolean excedida;
        private String situacao;
        private String situacaoRotulo;
        private List<Uso> usos;
    }

    @Data
    public static class Uso {
        private Long id;
        @NotNull
        private Long licencaId;
        private Long userId;
        @Size(max = 150)
        private String equipamento;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate desde;
        @Size(max = 500)
        private String observacao;
        // só na resposta
        private String nome;
    }

    // ================= orçamento =================

    @Data
    public static class Orcamento {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 60)
        private String tipo;
        private Long deptId;
        @NotNull(message = "Informe o início")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicio;
        @NotNull(message = "Informe o fim")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fim;
        @NotNull(message = "Informe o valor")
        @DecimalMin(value = "0.00") @Digits(integer = 13, fraction = 2)
        private BigDecimal valor;
        @Size(max = 10_000)
        private String observacao;
        // só na resposta
        private String dept;
        private BigDecimal gasto;
        private BigDecimal saldo;
        private Integer percentualGasto;
        private List<Custo> custos;
    }

    // ================= projeto =================

    @Data
    public static class Projeto {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 150)
        private String nome;
        @Size(max = 40)
        private String codigo;
        @Size(max = 200_000)
        private String descricao;
        @Min(1) @Max(5)
        private Integer situacao = 1;
        @Min(1) @Max(6)
        private Integer prioridade = 3;
        private Long deptId;
        private Long responsavelId;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicioPrevisto;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimPrevisto;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicioReal;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimReal;
        @Min(0) @Max(100)
        private Integer percentual = 0;
        private Boolean percentualAuto = true;
        private Long orcamentoId;
        private List<Long> membroIds = new ArrayList<>();
        // só na resposta
        private String situacaoRotulo;
        private String responsavel;
        private String dept;
        private String orcamento;
        private Boolean atrasado;
        private Integer totalTarefas;
        private Integer tarefasConcluidas;
        private BigDecimal horasPrevistas;
        private BigDecimal horasGastas;
        private BigDecimal custoTotal;
        private List<Membro> membros;
        private List<Tarefa> tarefas;
        private List<Vinculo> vinculos;
        private List<String> acoes;
    }

    @Data
    public static class Membro {
        private Long userId;
        private String nome;
        private String papel;
    }

    @Data
    public static class Tarefa {
        private Long id;
        @NotNull
        private Long projetoId;
        private Long tarefaPaiId;
        @NotBlank(message = "Informe o nome da tarefa")
        @Size(max = 200)
        private String nome;
        @Size(max = 200_000)
        private String descricao;
        private Long responsavelId;
        @Min(1) @Max(4)
        private Integer situacao = 1;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicioPrevisto;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimPrevisto;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate inicioReal;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate fimReal;
        @DecimalMin("0") @Digits(integer = 6, fraction = 2)
        private BigDecimal horasPrevistas = BigDecimal.ZERO;
        @DecimalMin("0") @Digits(integer = 6, fraction = 2)
        private BigDecimal horasGastas = BigDecimal.ZERO;
        @Min(0) @Max(100)
        private Integer percentual = 0;
        private Boolean marco = false;
        private Integer ordem = 0;
        // só na resposta
        private String responsavel;
        private String situacaoRotulo;
        private Boolean atrasada;
        private Boolean podeEditar;
    }

    @Data
    public static class Progresso {
        @NotNull
        private Long tarefaId;
        @NotNull @Min(1) @Max(4)
        private Integer situacao;
        @NotNull @Min(0) @Max(100)
        private Integer percentual;
        @DecimalMin("0") @Digits(integer = 6, fraction = 2)
        private BigDecimal horasGastas;
    }

    @Data
    public static class Vinculo {
        private Long id;
        @NotNull
        private Long projetoId;
        @NotBlank
        @Pattern(regexp = "chamado|problema|mudanca")
        private String objeto;
        @NotNull
        private Long objetoId;
        // só na resposta
        private String titulo;
        private String statusRotulo;
    }

    @Data
    public static class Resumo {
        private Long id;
        private String nome;
    }

}
