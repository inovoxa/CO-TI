package cn.iocoder.yudao.module.monitoramento.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class KumaVOs {

    private KumaVOs() {
    }

    /** O que há no kuma.db e o que será importado (antes de confirmar). */
    @Data
    public static class Analise {
        private String token;
        private List<Item> monitores = new ArrayList<>();
        private List<Item> canais = new ArrayList<>();
        private List<Item> manutencoes = new ArrayList<>();
        /** batidas dos últimos 30 dias (histórico que pode vir junto) */
        private long batidas30d;
        private int etiquetas;
    }

    @Data
    public static class Item {
        private Long kumaId;
        private String nome;
        /** tipo no Kuma */
        private String tipoKuma;
        /** tipo no CO-TI; null = não será importado */
        private String tipo;
        private String alvo;
        private Boolean ativo;
        private Boolean jaImportado;
        /** o que precisa de atenção (ou por que não entra) */
        private String aviso;
    }

    @Data
    public static class ConfirmarReq {
        @NotBlank
        private String token;
        /** dias de histórico (batidas) a trazer; 0 = nenhum */
        @Min(0)
        @Max(30)
        private Integer historicoDias;
        private Boolean canais;
        private Boolean manutencoes;
        /** monitores do Kuma que a pessoa desmarcou */
        private List<Long> ignorar;
        /** unidade para todos os monitores importados (opcional; dá para mudar um a um depois) */
        private Long unidadeId;
    }

    @Data
    public static class Resultado {
        private int monitores;
        private int canais;
        private int manutencoes;
        private long batidas;
        private List<String> avisos = new ArrayList<>();
    }

    // ================================================================================== Kuma ao vivo (/metrics)

    /** Uptime Kuma acompanhado ao vivo. A chave nunca volta para a tela. */
    @Data
    public static class Servidor {
        private Long id;
        @NotBlank
        @Size(max = 100)
        private String nome;
        /** endereço do Kuma, ex.: https://kuma.orgao.gov.br */
        @NotBlank
        @Size(max = 500)
        private String url;
        /** só no login comum (Kuma sem chave de API); com chave de API fica vazio */
        @Size(max = 100)
        private String usuario;
        /** chave de API (uk1_...) ou senha; em branco mantém a guardada */
        @Size(max = 500)
        private String chave;
        private Boolean chaveDefinida;
        private Boolean ignorarTls;
        private Integer monitores;
    }

    /** Monitor do Kuma na prévia da sincronização. */
    @Data
    public static class ItemAoVivo {
        /** id no Kuma (2.x) ou, sem id (1.x), o nome: é o que fica em "consulta" no monitor do CO-TI */
        private String chave;
        private String nome;
        private String tipoKuma;
        private String alvo;
        /** estado agora no Kuma (0 fora, 1 no ar, 2 pendente, 3 manutenção) */
        private Integer estado;
        /** novo (não acompanhado), igual, muda (nome ou endereço mudou no Kuma) ou sumiu (não está mais no Kuma) */
        private String situacao;
        /** monitor do CO-TI que já acompanha este */
        private Long monitorId;
        private String monitorNome;
        private String mudanca;
    }

    @Data
    public static class PreviaAoVivo {
        private List<ItemAoVivo> itens = new ArrayList<>();
    }

    @Data
    public static class SincronizarReq {
        @jakarta.validation.constraints.NotNull
        private Long servidorId;
        /** chaves (id ou nome no Kuma) dos monitores a acompanhar */
        private List<String> adicionar;
        /** monitores do CO-TI a atualizar (nome e endereço como estão no Kuma) */
        private List<Long> atualizar;
        /** monitores do CO-TI a excluir (sumiram do Kuma); só os marcados */
        private List<Long> remover;
        private Long unidadeId;
        private Boolean abrirChamado;
        @Min(5)
        @Max(86400)
        private Integer intervaloS;
    }

    @Data
    public static class SincronizarResultado {
        private int adicionados;
        private int atualizados;
        private int removidos;
    }

}
