package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisa o contato que a mensagem dele foi lida (tracinhos azuis do WhatsApp, "visto" do Instagram), fora da
 * requisição do atendente: o provedor lento não atrasa a tela. Falha só vai para o log.
 */
@Slf4j
@Component
public class AvisoLeitura {

    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atendimento-leitura");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoCanalMapper contatoCanalMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private List<Canal> canais;

    public void avisar(ConversaDO conversa, MensagemDO entrada) {
        fila.execute(() -> {
            try {
                CaixaDO caixa = caixaMapper.selectById(conversa.getCaixaId());
                if (caixa == null || !Boolean.TRUE.equals(caixa.getAtivo())) {
                    return;
                }
                Canal canal = canais.stream().filter(x -> x.tipo().equals(caixa.getCanal())).findFirst().orElse(null);
                if (canal == null) {
                    return;
                }
                canal.avisarLeitura(caixa, contatoCanalMapper.selectById(conversa.getContatoCanalId()), entrada);
                Map<String, Object> dados = entrada.getDados() == null ? new LinkedHashMap<>()
                        : JsonUtils.parseObject(entrada.getDados(), new TypeReference<LinkedHashMap<String, Object>>() {
                });
                dados.put("lidaAvisada", true);
                mensagemMapper.updateById(MensagemDO.builder().id(entrada.getId()).dados(JsonUtils.toJsonString(dados)).build());
            } catch (Exception e) {
                log.info("[avisar][mensagem {}: aviso de leitura não enviado: {}]", entrada.getId(), e.getMessage());
            }
        });
    }

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

}
