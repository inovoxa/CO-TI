package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.WebhookEntradaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.WebhookEntradaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Meta;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Webhooks dos provedores. A entrada só confere a assinatura e grava (o provedor recebe 200 na hora e não reenvia);
 * o processamento roda em seguida, numa fila de uma linha só — as mensagens de um contato entram na ordem em que
 * chegaram. Falha passageira (rede, provedor fora ao baixar mídia) tenta de novo em 1, 5 e 15 minutos; mensagem
 * repetida não duplica (id externo).
 */
@Slf4j
@Service
public class WebhookEntradaService {

    private static final int[] ESPERA_MIN = {1, 5, 15};
    /** Eco do Instagram espera um pouco: o envio pela API precisa gravar o id antes, senão a mensagem duplicaria */
    private static final int ESPERA_ECO_SEG = 10;

    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atendimento-webhook");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean varreduraNaFila = new AtomicBoolean();

    @Resource
    private WebhookEntradaMapper webhookEntradaMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private CanalConfigService canalConfigService;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private EntradaWhatsapp entradaWhatsapp;
    @Resource
    private EntradaInstagram entradaInstagram;
    @Resource
    private EntradaTelegram entradaTelegram;

    // =====================================================================================================
    // Meta (WhatsApp Cloud API e Instagram): um endereço só por app
    // =====================================================================================================

    /** Verificação do webhook (GET hub.mode=subscribe): devolve o desafio se a palavra for de alguma caixa. */
    public String verificarMeta(String modo, String palavra, String desafio) {
        if (!"subscribe".equals(modo) || palavra == null || desafio == null) {
            return null;
        }
        for (String canal : List.of("whatsapp_cloud", "instagram")) {
            for (CaixaDO c : canalConfigService.ativas(canal)) {
                if (Meta.iguais(segredos.ler(c).verifyToken(), palavra)) {
                    return desafio;
                }
            }
        }
        return null;
    }

    /**
     * Separa o corpo por caixa (número do WhatsApp ou conta do Instagram), confere a assinatura com a chave secreta
     * do app de cada caixa e grava a parte de cada uma.
     *
     * @return false se nenhuma parte tinha assinatura válida
     */
    @SuppressWarnings("unchecked")
    public boolean registrarMeta(byte[] corpo, String assinatura) {
        Map<String, Object> json;
        try {
            json = JsonUtils.parseObject(new String(corpo, StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return false;
        }
        if (json == null || !(json.get("entry") instanceof List<?> entradas)) {
            return false;
        }
        String objeto = String.valueOf(json.get("object"));
        String canal = "instagram".equals(objeto) ? "instagram" : "whatsapp_business_account".equals(objeto) ? "whatsapp_cloud" : null;
        if (canal == null) {
            log.info("[registrarMeta][objeto {} ignorado]", objeto);
            return true;
        }
        Map<Long, CaixaDO> caixas = new HashMap<>();
        Map<Long, List<Object>> partes = new LinkedHashMap<>();
        Map<String, CaixaDO> porChave = new HashMap<>();
        for (CaixaDO c : canalConfigService.ativas(canal)) {
            CanalSegredos.Config cfg = segredos.ler(c);
            String chave = "instagram".equals(canal) ? cfg.igUserId() : cfg.phoneNumberId();
            if (chave != null) {
                porChave.put(chave, c);
            }
        }
        boolean eco = false;
        for (Object e : entradas) {
            if (!(e instanceof Map<?, ?> entrada)) {
                continue;
            }
            if ("instagram".equals(canal)) {
                CaixaDO c = porChave.get(String.valueOf(entrada.get("id")));
                if (c != null) {
                    caixas.put(c.getId(), c);
                    partes.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(entrada);
                    eco |= entrada.get("messaging") instanceof List<?> ms && ms.stream().anyMatch(m -> m instanceof Map<?, ?> mm
                            && mm.get("message") instanceof Map<?, ?> msg && Boolean.TRUE.equals(msg.get("is_echo")));
                }
                continue;
            }
            // WhatsApp: cada mudança diz de qual número é (metadata.phone_number_id)
            if (!(entrada.get("changes") instanceof List<?> mudancas)) {
                continue;
            }
            Map<Long, List<Object>> porCaixa = new LinkedHashMap<>();
            for (Object mu : mudancas) {
                if (mu instanceof Map<?, ?> mudanca && mudanca.get("value") instanceof Map<?, ?> valor
                        && valor.get("metadata") instanceof Map<?, ?> meta) {
                    CaixaDO c = porChave.get(String.valueOf(meta.get("phone_number_id")));
                    if (c != null) {
                        caixas.put(c.getId(), c);
                        porCaixa.computeIfAbsent(c.getId(), k -> new ArrayList<>()).add(mudanca);
                    }
                }
            }
            porCaixa.forEach((id, lista) -> {
                Map<String, Object> parte = new LinkedHashMap<>();
                parte.put("id", entrada.get("id"));
                parte.put("changes", lista);
                partes.computeIfAbsent(id, k -> new ArrayList<>()).add(parte);
            });
        }
        if (partes.isEmpty()) {
            log.info("[registrarMeta][{}: nenhuma caixa ativa para o que chegou]", canal);
            return true;
        }
        boolean algumaValida = false;
        for (Map.Entry<Long, List<Object>> p : partes.entrySet()) {
            CaixaDO c = caixas.get(p.getKey());
            if (!Meta.assinaturaValida(segredos.ler(c).appSecret(), corpo, assinatura)) {
                log.warn("[registrarMeta][caixa {}: assinatura inválida, webhook descartado]", c.getId());
                continue;
            }
            algumaValida = true;
            Map<String, Object> parte = new LinkedHashMap<>();
            parte.put("object", objeto);
            parte.put("entry", p.getValue());
            gravar(canal, c.getId(), JsonUtils.toJsonString(parte), eco ? LocalDateTime.now().plusSeconds(ESPERA_ECO_SEG) : null);
        }
        return algumaValida;
    }

    // =====================================================================================================
    // Telegram: um endereço por caixa, com o segredo que o CO-TI cadastrou
    // =====================================================================================================

    public boolean registrarTelegram(Long caixaId, byte[] corpo, String segredo) {
        CaixaDO c = caixaMapper.selectById(caixaId);
        if (c == null || !"telegram".equals(c.getCanal()) || !Boolean.TRUE.equals(c.getAtivo())) {
            return false;
        }
        if (!Meta.iguais(segredos.ler(c).secretToken(), segredo)) {
            log.warn("[registrarTelegram][caixa {}: segredo inválido, webhook descartado]", caixaId);
            return false;
        }
        String texto = new String(corpo, StandardCharsets.UTF_8);
        try {
            JsonUtils.parseObject(texto, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return false;
        }
        gravar("telegram", caixaId, texto, null);
        return true;
    }

    // =====================================================================================================
    // Processamento
    // =====================================================================================================

    /**
     * Tarefa agendada: põe a varredura dos pendentes (novas tentativas, ecos que esperavam) na mesma fila de uma
     * linha, para não processar em paralelo com o que acabou de chegar.
     */
    public void agendar() {
        if (varreduraNaFila.compareAndSet(false, true)) {
            fila.execute(() -> {
                varreduraNaFila.set(false);
                try {
                    processarFila();
                } catch (Exception e) {
                    log.error("[agendar][falha na fila de webhooks]", e);
                }
            });
        }
    }

    /** @return quantos webhooks foram processados */
    int processarFila() {
        webhookEntradaMapper.destravar();
        int n = 0;
        for (Long id : webhookEntradaMapper.selectIdsPendentes(50)) {
            if (processar(id)) {
                n++;
            }
        }
        return n;
    }

    public int limpar() {
        return webhookEntradaMapper.limparAntigos();
    }

    @SuppressWarnings("unchecked")
    boolean processar(Long id) {
        if (webhookEntradaMapper.reivindicar(id) != 1) {
            return false;
        }
        WebhookEntradaDO w = webhookEntradaMapper.selectById(id);
        try {
            CaixaDO caixa = caixaMapper.selectById(w.getCaixaId());
            if (caixa == null || !Boolean.TRUE.equals(caixa.getAtivo())) {
                throw new Canal.FalhaDefinitiva("caixa excluída ou desativada");
            }
            Map<String, Object> corpo = JsonUtils.parseObject(w.getCorpo(), new TypeReference<Map<String, Object>>() {
            });
            switch (w.getCanal()) {
                case "whatsapp_cloud" -> entradaWhatsapp.processar(caixa, corpo);
                case "instagram" -> entradaInstagram.processar(caixa, corpo);
                case "telegram" -> entradaTelegram.processar(caixa, corpo);
                default -> throw new Canal.FalhaDefinitiva("canal desconhecido: " + w.getCanal());
            }
            webhookEntradaMapper.updateById(WebhookEntradaDO.builder().id(id).status("ok").erro(null).processadoEm(LocalDateTime.now()).build());
            return true;
        } catch (Canal.FalhaDefinitiva | IllegalArgumentException | ClassCastException e) {
            log.warn("[processar][webhook {}: {}]", id, e.getMessage());
            webhookEntradaMapper.updateById(WebhookEntradaDO.builder().id(id).status("erro").erro(limitar(e.getMessage()))
                    .processadoEm(LocalDateTime.now()).build());
        } catch (Exception e) {
            int t = w.getTentativas() == null ? 1 : w.getTentativas();
            String erro = limitar(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("[processar][webhook {} tentativa {}: {}]", id, t, erro);
            if (t > ESPERA_MIN.length) {
                webhookEntradaMapper.updateById(WebhookEntradaDO.builder().id(id).status("erro").erro(erro).processadoEm(LocalDateTime.now()).build());
            } else {
                webhookEntradaMapper.updateById(WebhookEntradaDO.builder().id(id).status("pendente").erro(erro)
                        .proximaTentativa(LocalDateTime.now().plusMinutes(ESPERA_MIN[t - 1])).build());
            }
        }
        return false;
    }

    private void gravar(String canal, Long caixaId, String corpo, LocalDateTime quando) {
        WebhookEntradaDO w = WebhookEntradaDO.builder().canal(canal).caixaId(caixaId).corpo(corpo).status("pendente").tentativas(0)
                .proximaTentativa(quando).recebidoEm(LocalDateTime.now()).build();
        webhookEntradaMapper.insert(w);
        if (quando == null) {
            fila.execute(() -> {
                try {
                    processar(w.getId());
                } catch (Exception e) {
                    log.error("[gravar][webhook {}]", w.getId(), e);
                }
            });
        }
    }

    private static String limitar(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "...";
    }

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

}
