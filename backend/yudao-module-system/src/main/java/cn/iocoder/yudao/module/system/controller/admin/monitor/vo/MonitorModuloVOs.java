package cn.iocoder.yudao.module.system.controller.admin.monitor.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Objetos trocados entre o portal e as telas de cada módulo do Centro de Operações.
 * Ficam juntos neste arquivo para facilitar a leitura; cada um corresponde a uma tela.
 */
public final class MonitorModuloVOs {

    private MonitorModuloVOs() {
    }

    /** Lista com contagens por filtro (para os "chips" das telas) e total antes do corte. */
    @Data
    public static class ListaVO<T> {
        private Map<String, Long> contagens = new LinkedHashMap<>();
        private List<T> lista = new ArrayList<>();
        private long total;
        private Map<String, Object> resumo = new LinkedHashMap<>();
    }

    // ---------------- Chamados (GLPI) ----------------
    @Data
    public static class ChamadoVO {
        private Long id;
        private String titulo;
        private String categoria;
        /** Crítica | Alta | Média | Baixa */
        private String prioridade;
        /** Novo | Em atendimento | Pendente | Solucionado */
        private String status;
        private Long unidadeId;
        private String unidade;
        private String solicitante;
        private String tecnico;
        private Long abertoEm;
        private Long prazo;
        private Long solucionadoEm;
        /** vazio = aberto por pessoa; senão, quem abriu automaticamente (Uptime Kuma, Graylog, Matrix...) */
        private String origem;
    }

    @Data
    public static class ChamadoNovoReqVO {
        private String titulo;
        private String categoria;
        private String prioridade;
        private Long unidadeId;
    }

    // ---------------- Links e serviços (Uptime Kuma / Zabbix) ----------------
    @Data
    public static class LinkVO {
        private Long id;
        private String nome;
        /** descrição do monitor (os links importados da planilha trazem a faixa: "Faixa 172.19.40.0/24") */
        private String descricao;
        /** ping | http | port | dns */
        private String tipo;
        private String alvo;
        private Long unidadeId;
        private String unidade;
        /** principal | redundante | servico */
        private String papel;
        /** up | lento | down */
        private String estado;
        private Integer latenciaMs;
        /** última mensagem da verificação e desde quando está no estado atual */
        private String mensagem;
        private java.time.LocalDateTime desde;
        /** últimas verificações, mais antiga primeiro: u (ok), p (lento), d (fora) */
        private List<String> historico = new ArrayList<>();
        private Double uptime24h;
        private Double uptime30d;
        private Integer certificadoDias;
    }

    // ---------------- Inventário (OCS Inventory) ----------------
    @Data
    public static class EquipamentoVO {
        private String hostname;
        /** Desktop | Notebook | Servidor */
        private String tipo;
        private Long unidadeId;
        private String unidade;
        private String usuario;
        private String so;
        private String cpu;
        private Integer ramGb;
        private Integer discoGb;
        private Integer discoLivrePct;
        /** Atualizado | Desatualizado | Ausente */
        private String antivirus;
        private Long ultimaComunicacao;
        private String ip;
    }

    // ---------------- Logs (Graylog) ----------------
    @Data
    public static class LogVO {
        private Long ts;
        private String origem;
        /** info | aviso | erro | critico */
        private String nivel;
        private String mensagem;
        private Long unidadeId;
    }

    // ---------------- Câmeras e acesso (Frigate + controle de acesso) ----------------
    @Data
    public static class CameraVO {
        private String id;
        private String nome;
        private Long unidadeId;
        private String unidade;
        /** URL da imagem mais recente (Frigate: /api/&lt;camera&gt;/latest.jpg via proxy). Vazio no modo simulado. */
        private String snapshotUrl;
        private boolean online;
    }

    @Data
    public static class CameraEventoVO {
        private Long ts;
        private String camera;
        private String texto;
        private boolean alerta;
        private Long unidadeId;
    }

    @Data
    public static class CameraResumoVO {
        private int veiculosEntraram;
        private int veiculosSairam;
        private int noEstacionamento;
        private int vagas;
        private int funcionarios;
        private int visitantes;
        private int alertas;
        private List<CameraVO> cameras = new ArrayList<>();
        private List<CameraEventoVO> eventos = new ArrayList<>();
    }

}
