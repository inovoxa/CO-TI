package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.canal.Canal;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.*;
import cn.iocoder.yudao.module.atendimento.dal.mysql.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Fila de envio aos canais: tenta de novo em 1, 5 e 15 minutos quando o problema é passageiro (rede, provedor
 * fora); erro definitivo (webhook recusou, número inexistente) marca a mensagem como falha na hora. Segura com mais
 * de uma instância: cada envio é reivindicado antes.
 */
@Slf4j
@Service
public class EnvioService {

    private static final int[] ESPERA_MIN = {1, 5, 15};

    @Resource
    private EnvioMapper envioMapper;
    @Resource
    private MensagemMapper mensagemMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private ContatoCanalMapper contatoCanalMapper;
    @Resource
    private ConversaService conversaService;
    @Resource
    private List<Canal> canais;

    private volatile Map<String, Canal> porTipo;

    /** @return quantas mensagens foram entregues */
    public int processarFila() {
        if (porTipo == null) {
            porTipo = canais.stream().collect(Collectors.toMap(Canal::tipo, Function.identity()));
        }
        envioMapper.destravar();
        int n = 0;
        for (Long id : envioMapper.selectIdsPendentes(50)) {
            if (envioMapper.reivindicar(id) != 1) {
                continue;
            }
            EnvioDO e = envioMapper.selectById(id);
            MensagemDO m = mensagemMapper.selectById(e.getMensagemId());
            if (m == null) {
                envioMapper.updateById(EnvioDO.builder().id(id).status("erro").erro("mensagem excluída").build());
                continue;
            }
            try {
                ConversaDO c = conversaMapper.selectById(m.getConversaId());
                CaixaDO caixa = caixaMapper.selectById(c.getCaixaId());
                Canal canal = porTipo.get(caixa.getCanal());
                if (canal == null) {
                    throw new Canal.FalhaDefinitiva("o canal " + caixa.getCanal() + " ainda não envia mensagens");
                }
                if (!Boolean.TRUE.equals(caixa.getAtivo())) {
                    throw new Canal.FalhaDefinitiva("a caixa de entrada está desativada");
                }
                String idExterno = canal.enviar(caixa, c, contatoMapper.selectById(c.getContatoId()),
                        contatoCanalMapper.selectById(c.getContatoCanalId()), m);
                envioMapper.updateById(EnvioDO.builder().id(id).status("enviado").enviadoEm(LocalDateTime.now()).erro(null).build());
                conversaService.atualizarEntrega(m.getId(), "enviada", null, idExterno);
                n++;
            } catch (Canal.FalhaDefinitiva f) {
                log.warn("[processarFila][mensagem {}: {}]", m.getId(), f.getMessage());
                envioMapper.updateById(EnvioDO.builder().id(id).status("erro").erro(limitar(f.getMessage())).build());
                conversaService.atualizarEntrega(m.getId(), "falhou", f.getMessage(), null);
            } catch (Exception ex) {
                int t = e.getTentativas() == null ? 1 : e.getTentativas();
                String erro = limitar(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.warn("[processarFila][mensagem {} tentativa {}: {}]", m.getId(), t, erro);
                if (t > ESPERA_MIN.length) {
                    envioMapper.updateById(EnvioDO.builder().id(id).status("erro").erro(erro).build());
                    conversaService.atualizarEntrega(m.getId(), "falhou", "não entregue depois de " + t + " tentativas: " + erro, null);
                } else {
                    envioMapper.updateById(EnvioDO.builder().id(id).status("pendente").erro(erro)
                            .proximaTentativa(LocalDateTime.now().plusMinutes(ESPERA_MIN[t - 1])).build());
                }
            }
        }
        return n;
    }

    /** Põe de novo na fila uma mensagem que falhou (botão "tentar de novo"). */
    public void reenviar(Long mensagemId) {
        envioMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<EnvioDO>()
                .eq(EnvioDO::getMensagemId, mensagemId).eq(EnvioDO::getStatus, "erro")
                .set(EnvioDO::getStatus, "pendente").set(EnvioDO::getTentativas, 0).set(EnvioDO::getProximaTentativa, null));
        // falhou → pendente: atualização direta (atualizarEntrega não volta situação)
        mensagemMapper.updateById(MensagemDO.builder().id(mensagemId).status("pendente").build());
    }

    private static String limitar(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "...";
    }

}
