package cn.iocoder.yudao.module.atendimento.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tela Atendimento → Configuração → Assistente virtual. */
public class AssistenteVirtualVOs {

    @Data
    public static class Texto {
        private String chave;
        private String grupo;
        private String rotulo;
        private String ajuda;
        /** TEXTO, SIM_NAO ou NUMERO */
        private String tipo;
        private String padrao;
        private String valor;
        private boolean alterado;
        private List<String> variaveis;
        private Integer minimo;
        private Integer maximo;
        private String atualizadoPor;
        private LocalDateTime atualizadoEm;
    }

    @Data
    public static class Tela {
        private List<Texto> textos = new ArrayList<>();
        private List<AtendimentoVOs.Caixa> caixas = new ArrayList<>();
        /** Regras que nenhuma configuração muda (mostradas só para leitura) */
        private String regrasFixas;
        private boolean iaAtiva;
    }

    @Data
    public static class SalvarTextos {
        /** chave → valor; vazio ou igual ao padrão = volta ao padrão */
        @NotNull
        private Map<String, String> valores = new LinkedHashMap<>();
    }

    @Data
    public static class CaixaRobo {
        @NotNull
        private Long id;
        private Boolean fluxoAtivo = false;
        @Size(max = 1000)
        private String fluxoBoasVindas;
        private Boolean roboAtivo = false;
        @Size(max = 80)
        private String roboNome;
        @Size(max = 4000)
        private String roboInstrucoes;
        @Min(1)
        @Max(100)
        private Integer roboMaxRespostas = 10;
        private Boolean avisosAtivo = true;
        private Long avisoModeloId;
    }

    /** Arquivo de exportação: só textos e opções, sem segredo nenhum. */
    @Data
    public static class Arquivo {
        private String tipo = "co-ti/assistente-virtual";
        private Integer versao = 1;
        private Map<String, String> textos = new LinkedHashMap<>();
    }

    @Data
    public static class LinhaImportacao {
        private String chave;
        private String rotulo;
        private String atual;
        private String novo;
        /** muda, igual, padrao (volta ao padrão), ignorada ou invalida */
        private String situacao;
        private String erro;
    }

}
