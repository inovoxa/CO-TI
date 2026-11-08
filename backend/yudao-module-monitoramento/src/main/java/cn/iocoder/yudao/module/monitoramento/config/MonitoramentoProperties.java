package cn.iocoder.yudao.module.monitoramento.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do monitoramento ({@code coti.monitoramento.*}).
 */
@ConfigurationProperties(prefix = "coti.monitoramento")
@Data
public class MonitoramentoProperties {

    /** Liga o agendador das verificações (desligue numa segunda instância só de leitura, se houver). */
    private boolean ativo = true;

    /**
     * Verificações ao mesmo tempo. Threads comuns, não virtuais: com centenas de monitores, as virtuais presas
     * (pinned) no driver do banco e no Redis travavam todos os núcleos e as verificações paravam.
     */
    private int paralelo = 32;

    /** Dias de batidas guardadas (cada verificação); os totais por hora ficam {@link #retencaoHorasDias} dias. */
    private int retencaoDias = 31;
    private int retencaoHorasDias = 400;

    /** API do Telegram (troque só para apontar a um simulador em teste). */
    private String telegramApi = "https://api.telegram.org";

    /** Busca de endereço pelo CEP (ViaCEP) e de coordenadas pelo endereço (Nominatim/OpenStreetMap). Vazio = desligado. */
    private String cepApi = "https://viacep.com.br/ws";
    private String geocodificacaoApi = "https://nominatim.openstreetmap.org/search";
    /** Contato enviado no User-Agent do Nominatim (pedido da política de uso do OpenStreetMap). */
    private String geocodificacaoContato = "";

    /** Tamanho máximo do kuma.db no importador (MB). */
    private int kumaMaxMb = 2048;

}
