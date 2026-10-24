package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.canal.ArquivosCanal;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.MensagemDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.MensagemMapper;
import cn.iocoder.yudao.module.atendimento.enums.MensagemTipo;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Transcrição dos áudios recebidos (no lugar do Whisper do n8n), pelos provedores de IA com modelo de áudio. O texto
 * vira o conteúdo da mensagem (o áudio continua anexado) e a marca {@code dados.transcricao}: o fluxo do catálogo e o
 * assistente passam a entender a mensagem falada, e o atendente lê sem ouvir.
 */
@Slf4j
@Service
public class TranscricaoService {

    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "atendimento-transcricao");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private ArquivosCanal arquivos;
    @Resource
    private IaService iaService;
    @Resource
    private ConversaService conversaService;

    /** Mensagem de áudio recebida numa conversa com gente: transcreve depois do commit, sem segurar a entrada. */
    void agendarAposGravar(Long mensagemId) {
        Runnable r = () -> fila.execute(() -> {
            try {
                transcrever(mensagemMapper.selectById(mensagemId));
            } catch (Exception e) {
                log.warn("[agendar][mensagem {}: {}]", mensagemId, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    /** Antes do robô ler a conversa: transcreve os áudios do contato que ainda não têm texto. */
    void transcreverPendentes(Long conversaId) {
        if (!iaService.transcreve()) {
            return;
        }
        for (MensagemDO m : mensagemMapper.selectList(new LambdaQueryWrapperX<MensagemDO>().eq(MensagemDO::getConversaId, conversaId)
                .eq(MensagemDO::getTipo, MensagemTipo.ENTRADA).eq(MensagemDO::getFormato, "audio").eq(MensagemDO::getConteudo, ""))) {
            transcrever(m);
        }
    }

    private void transcrever(MensagemDO m) {
        if (m == null || !"audio".equals(m.getFormato()) || (m.getConteudo() != null && !m.getConteudo().isEmpty())) {
            return;
        }
        List<AtendimentoVOs.Anexo> anexos = JsonUtils.parseObject(m.getAnexos(), new TypeReference<List<AtendimentoVOs.Anexo>>() {
        });
        if (anexos == null || anexos.isEmpty() || !iaService.transcreve()) {
            return;
        }
        try {
            ArquivosCanal.Local a = arquivos.ler(anexos.get(0));
            String texto = iaService.transcrever(a.dados(), a.nome(), a.tipo(), m.getConversaId());
            if (texto == null || texto.isBlank()) {
                return;
            }
            Map<String, Object> dados = m.getDados() == null ? new LinkedHashMap<>()
                    : JsonUtils.parseObject(m.getDados(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
            dados.put("transcricao", true);
            mensagemMapper.updateById(MensagemDO.builder().id(m.getId()).conteudo(ContatoService.cortar(texto, 4000))
                    .dados(JsonUtils.toJsonString(dados)).build());
            conversaService.publicar("mensagem", m.getConversaId(), m.getId());
        } catch (Exception e) {
            log.warn("[transcrever][mensagem {}: {}]", m.getId(), e.getMessage());
        }
    }

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

}
