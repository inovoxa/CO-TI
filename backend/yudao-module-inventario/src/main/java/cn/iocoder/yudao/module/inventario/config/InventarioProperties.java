package cn.iocoder.yudao.module.inventario.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do inventário ({@code coti.inventario.*}).
 */
@ConfigurationProperties(prefix = "coti.inventario")
@Data
public class InventarioProperties {

    /**
     * Redes de onde os agentes podem mandar inventário (CIDR). Vazio = qualquer origem (não recomendado: o endpoint
     * dos agentes não tem login do portal).
     */
    private List<String> agenteRedes = new ArrayList<>(List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8"));

    /** Usuário e senha (HTTP Basic) que os agentes informam (/USER e /PWD no agente Windows). Vazio = sem senha. */
    private String agenteUsuario = "";
    private String agenteSenha = "";

    /** De quantas em quantas horas o agente pergunta se deve inventariar (PROLOG_FREQ). */
    private int prologoHoras = 12;

    /** Intervalo mínimo entre dois inventários da mesma máquina (dias); 0 = sempre que o agente perguntar. */
    private int inventarioDias = 0;

    /** Sem contato há mais que isto (dias): o ativo aparece como "sem comunicação". */
    private int semContatoDias = 30;

    /** De quanto em quanto tempo os alertas são reavaliados (ms); 0 = só pelo botão "Avaliar agora". */
    private long alertasIntervaloMs = 15 * 60 * 1000L;

}
