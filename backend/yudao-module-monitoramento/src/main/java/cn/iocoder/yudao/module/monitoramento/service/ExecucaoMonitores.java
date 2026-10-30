package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.monitoramento.config.MonitoramentoProperties;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.BatidaDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import cn.iocoder.yudao.module.monitoramento.service.verificador.Resultado;
import cn.iocoder.yudao.module.monitoramento.service.verificador.Verificadores;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agenda e executa as verificações. A cada segundo, os monitores vencidos (última verificação + intervalo, ou o
 * intervalo de nova tentativa quando pendente) vão para uma thread virtual. Uma trava no Redis por monitor impede que
 * duas instâncias verifiquem o mesmo monitor ao mesmo tempo; depois de pegar a trava, o monitor é relido do banco.
 *
 * <p>Cada resultado passa pela máquina de estados ({@link Estados}), vira uma batida no TimescaleDB e, quando o
 * estado muda, avisa pelos canais e abre (ou soluciona) o chamado.
 */
@Component
@Slf4j
public class ExecucaoMonitores {

    /** Usuário "sistema" que abre os chamados automáticos */
    private static final Long SISTEMA = 1L;
    private static final Long ORIGEM_MONITORAMENTO = 6L;
    public static final String TIPO_WS = "monitoramento-batida";
    private static final long RECARREGAR_MS = 30_000;
    /**
     * Validade da trava de cada monitor: cobre a verificação mais lenta (tempo limite + avisos) e, se o backend cair
     * no meio, libera o monitor em 2 minutos (com 10 minutos ele ficava parado depois de cada reinício).
     */
    private static final long TRAVA_S = 120;

    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private Verificadores verificadores;
    @Resource
    private ManutencaoService manutencaoService;
    @Resource
    private CanalService canalService;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    @Resource
    private MonitoramentoProperties monitoramentoProperties;
    /** Rodada do telão: quando o link principal muda, o telão não espera os 5 s do ciclo */
    @Resource
    private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.system.job.monitor.MonitorPushJob> telao;

    private ExecutorService executor;
    private final Map<Long, MonitorDO> ativos = new ConcurrentHashMap<>();
    private final Set<Long> emAndamento = ConcurrentHashMap.newKeySet();
    private volatile long carregadoEm;

    @PostConstruct
    public void iniciar() {
        executor = Executors.newFixedThreadPool(Math.max(1, monitoramentoProperties.getParalelo()),
                Thread.ofPlatform().name("monitoramento-", 1).daemon(true).factory());
    }

    @PreDestroy
    public void parar() {
        executor.shutdownNow();
    }

    // ================================================================================== agendador

    @Scheduled(fixedDelay = 1000, initialDelay = 15_000)
    public void tick() {
        if (!monitoramentoProperties.isAtivo()) {
            return;
        }
        try {
            if (System.currentTimeMillis() - carregadoEm > RECARREGAR_MS) {
                recarregar();
            }
            Set<Long> manutencao = manutencaoService.monitoresEmManutencao();
            LocalDateTime agora = LocalDateTime.now();
            for (MonitorDO m : ativos.values()) {
                boolean emManutencao = manutencao.contains(m.getId());
                if (!vencido(m, agora, emManutencao)) {
                    continue;
                }
                if (emAndamento.add(m.getId())) {
                    executor.submit(() -> {
                        try {
                            verificar(m.getId(), emManutencao);
                        } finally {
                            emAndamento.remove(m.getId());
                        }
                    });
                }
            }
        } catch (RuntimeException e) {
            log.error("[monitoramento] falha no agendador", e);
        }
    }

    /** Relê os monitores ativos do banco (também chamado quando alguém salva, pausa ou exclui). */
    @DataPermission(enable = false)
    public void recarregar() {
        Map<Long, MonitorDO> novos = new HashMap<>();
        monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getAtivo, true)).forEach(m -> novos.put(m.getId(), m));
        ativos.keySet().retainAll(novos.keySet());
        ativos.putAll(novos);
        carregadoEm = System.currentTimeMillis();
    }

    /**
     * Está na hora de verificar? Push vence quando nenhum sinal chegou no intervalo; manual só muda pela tela (ou ao
     * entrar e sair da manutenção).
     */
    static boolean vencido(MonitorDO m, LocalDateTime agora, boolean emManutencao) {
        if ("manual".equals(m.getTipo())) {
            Integer e = m.getEstado();
            return emManutencao ? !Objects.equals(e, Estados.MANUTENCAO) : Objects.equals(e, Estados.MANUTENCAO);
        }
        LocalDateTime base = m.getUltimaVerificacao();
        if (base == null) {
            if (!"push".equals(m.getTipo())) {
                return true;
            }
            base = Optional.ofNullable(m.getCreateTime()).orElse(agora);
        }
        int intervalo = Objects.equals(m.getEstado(), Estados.PENDENTE) ? m.getIntervaloTentativaS() : m.getIntervaloS();
        return !base.plusSeconds(intervalo).isAfter(agora);
    }

    /** Verifica agora (botão "verificar agora"), sem esperar o intervalo. */
    public void verificarAgora(Long id) {
        if (emAndamento.add(id)) {
            executor.submit(() -> {
                try {
                    verificar(id, manutencaoService.monitoresEmManutencao().contains(id), true);
                } finally {
                    emAndamento.remove(id);
                }
            });
        }
    }

    void verificar(Long id, boolean emManutencao) {
        verificar(id, emManutencao, false);
    }

    @DataPermission(enable = false)
    void verificar(Long id, boolean emManutencao, boolean forcar) {
        RLock trava = redissonClient.getLock("coti:monitoramento:monitor:" + id);
        boolean pegou = false;
        try {
            pegou = trava.tryLock(0, TRAVA_S, TimeUnit.SECONDS);
            if (!pegou) {
                return;
            }
            MonitorDO m = monMonitorMapper.selectById(id);
            if (m == null || !Boolean.TRUE.equals(m.getAtivo())) {
                ativos.remove(id);
                return;
            }
            LocalDateTime agora = LocalDateTime.now();
            if (!forcar && !vencido(m, agora, emManutencao)) {
                ativos.put(id, m); // outra instância já verificou
                return;
            }
            Resultado r;
            if (emManutencao) {
                r = Resultado.ok(null, "em manutenção");
            } else if ("push".equals(m.getTipo())) {
                long s = m.getUltimaVerificacao() == null ? m.getIntervaloS()
                        : Duration.between(m.getUltimaVerificacao(), agora).getSeconds();
                r = Resultado.falha("nenhum sinal (push) há " + MonitorService.duracao(s));
            } else if ("manual".equals(m.getTipo())) {
                // saiu da manutenção: volta como no ar até alguém escolher outro estado
                r = Resultado.ok(null, "estado manual");
            } else {
                r = verificadores.verificar(m);
            }
            processar(m, r, emManutencao, LocalDateTime.now());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("[monitoramento] falha ao verificar o monitor {}", id, e);
        } finally {
            if (pegou && trava.isHeldByCurrentThread()) {
                trava.unlock();
            }
        }
    }

    /** Sinal recebido no endpoint de push (status up ou down, mensagem e latência opcionais). */
    @DataPermission(enable = false)
    public boolean registrarPush(String token, boolean ok, String msg, Integer latenciaMs) {
        MonitorDO m = monMonitorMapper.selectOne(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getPushToken, token).eq(MonitorDO::getTipo, "push"));
        if (m == null || !Boolean.TRUE.equals(m.getAtivo())) {
            return false;
        }
        String texto = msg == null || msg.isBlank() ? (ok ? "sinal recebido" : "sinal de falha recebido") : msg;
        return comTrava(m.getId(), atual -> processar(atual, ok ? Resultado.ok(latenciaMs, texto) : Resultado.falha(latenciaMs, texto),
                manutencaoService.monitoresEmManutencao().contains(atual.getId()), LocalDateTime.now()));
    }

    /** Estado escolhido à mão (monitor manual). */
    @DataPermission(enable = false)
    public void registrarManual(Long id, boolean ok, String msg) {
        String texto = msg == null || msg.isBlank() ? (ok ? "marcado no ar" : "marcado fora do ar") : msg;
        comTrava(id, atual -> processar(atual, ok ? Resultado.ok(null, texto) : Resultado.falha(texto),
                manutencaoService.monitoresEmManutencao().contains(id), LocalDateTime.now()));
    }

    private boolean comTrava(Long id, java.util.function.Consumer<MonitorDO> acao) {
        RLock trava = redissonClient.getLock("coti:monitoramento:monitor:" + id);
        try {
            if (!trava.tryLock(10, TRAVA_S, TimeUnit.SECONDS)) {
                return false;
            }
            try {
                MonitorDO atual = monMonitorMapper.selectById(id);
                if (atual == null) {
                    return false;
                }
                acao.accept(atual);
                return true;
            } finally {
                if (trava.isHeldByCurrentThread()) {
                    trava.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ================================================================================== batida

    void processar(MonitorDO m, Resultado r, boolean emManutencao, LocalDateTime agora) {
        boolean invertido = Boolean.TRUE.equals(m.getInvertido()) && !emManutencao && !Set.of("push", "manual").contains(m.getTipo());
        boolean ok = invertido != r.ok();
        String msg = r.msg();
        if (invertido) {
            msg = (ok ? "invertido: falhou, como esperado (" : "invertido: respondeu, mas devia falhar (") + msg + ")";
        }
        Integer anterior = m.getEstado();
        LocalDateTime desdeAnterior = m.getEstadoDesde();
        Estados.Transicao t = Estados.avaliar(anterior, nz(m.getTentativaAtual()), nz(m.getQuedasSeguidas()), ok, emManutencao,
                nz(m.getTentativas()), nz(m.getReenviarACada()));
        boolean mudou = anterior == null || anterior != t.estado();
        LocalDateTime desde = mudou ? agora : desdeAnterior;
        Integer latencia = t.estado() == Estados.MANUTENCAO ? null : r.latenciaMs();
        String msgCurta = cortar(msg, 500);

        MonitorDO u = MonitorDO.builder().id(m.getId()).estado(t.estado()).estadoDesde(desde).ultimaVerificacao(agora)
                .ultimaMsg(msgCurta).tentativaAtual(t.tentativa()).quedasSeguidas(t.quedasSeguidas()).build();
        monMonitorMapper.updateById(u);
        // latência fica vazia quando não respondeu (o updateById ignora nulos)
        monMonitorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MonitorDO>()
                .eq(MonitorDO::getId, m.getId()).set(MonitorDO::getUltimaLatenciaMs, latencia));
        if (r.certDias() != null) {
            monMonitorMapper.updateById(MonitorDO.builder().id(m.getId()).certDias(r.certDias()).certValidade(r.certValidade()).build());
        }
        monMonitorMapper.inserirBatida(BatidaDO.builder().monitorId(m.getId()).momento(agora).estado(t.estado()).latenciaMs(latencia)
                .msg(msgCurta).importante(t.importante()).build());
        int noAr = t.estado() == Estados.NO_AR ? 1 : 0;
        int fora = t.estado() == Estados.FORA || t.estado() == Estados.PENDENTE ? 1 : 0;
        monMonitorMapper.somarHora(m.getId(), agora, noAr, fora, t.estado() == Estados.MANUTENCAO ? 1 : 0,
                noAr == 1 && latencia != null ? latencia : 0, noAr == 1 && latencia != null ? 1 : 0);

        // estado novo em memória (o agendador usa a última verificação)
        m.setEstado(t.estado());
        m.setEstadoDesde(desde);
        m.setUltimaVerificacao(agora);
        m.setUltimaLatenciaMs(latencia);
        m.setUltimaMsg(msgCurta);
        m.setTentativaAtual(t.tentativa());
        m.setQuedasSeguidas(t.quedasSeguidas());
        if (r.certDias() != null) {
            m.setCertDias(r.certDias());
            m.setCertValidade(r.certValidade());
        }
        if (Boolean.TRUE.equals(m.getAtivo())) {
            ativos.put(m.getId(), m);
        }
        enviarWs(m, t, agora);
        if (t.importante() && "principal".equals(m.getPapel()) && m.getUnidadeId() != null) {
            cn.iocoder.yudao.module.system.job.monitor.MonitorPushJob job = telao.getIfAvailable();
            if (job != null) {
                executor.submit(job::push);
            }
        }
        try {
            efeitos(m, t, anterior, desdeAnterior, agora);
        } catch (RuntimeException e) {
            log.error("[monitoramento] falha nos avisos do monitor {}", m.getId(), e);
        }
    }

    /** Avisos, chamado automático e certificado perto de vencer. */
    private void efeitos(MonitorDO m, Estados.Transicao t, Integer anterior, LocalDateTime desdeAnterior, LocalDateTime agora) {
        String unidade = unidade(m.getUnidadeId());
        String link = "/monitoramento/monitores?id=" + m.getId();
        if (t.estado() == Estados.FORA && (t.avisar() || t.reaviso())) {
            if (t.avisar() && Boolean.TRUE.equals(m.getAbrirChamado()) && !chamadoAberto(m.getChamadoId())) {
                Long chamadoId = abrirChamado(m, unidade);
                m.setChamadoId(chamadoId);
                monMonitorMapper.updateById(MonitorDO.builder().id(m.getId()).chamadoId(chamadoId).build());
            }
            canalService.avisar(new CanalService.Aviso(t.reaviso() ? "reaviso" : "caiu", m, unidade, m.getUltimaMsg(), null, link));
        } else if (t.estado() == Estados.NO_AR && t.avisar()) {
            Long fora = anterior != null && anterior == Estados.FORA && desdeAnterior != null
                    ? Duration.between(desdeAnterior, agora).getSeconds() : null;
            if (m.getChamadoId() != null) {
                normalizarChamado(m, fora);
                m.setChamadoId(null);
                monMonitorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MonitorDO>()
                        .eq(MonitorDO::getId, m.getId()).set(MonitorDO::getChamadoId, null));
            }
            canalService.avisar(new CanalService.Aviso("voltou", m, unidade, m.getUltimaMsg(), fora, link));
        }
        // certificado: um aviso por certificado (renovou = validade nova = novo aviso quando chegar perto)
        if (m.getCertDias() != null && m.getCertValidade() != null && nz(m.getCertAvisoDias()) > 0 && m.getCertDias() <= m.getCertAvisoDias()
                && !m.getCertValidade().equals(m.getCertAvisadoValidade())) {
            m.setCertAvisadoValidade(m.getCertValidade());
            monMonitorMapper.updateById(MonitorDO.builder().id(m.getId()).certAvisadoValidade(m.getCertValidade()).build());
            canalService.avisar(new CanalService.Aviso("certificado", m, unidade, "vence em " + m.getCertDias() + " dias", null, link));
        }
    }

    private Long abrirChamado(MonitorDO m, String unidade) {
        ChamadoVOs.CriarReq req = new ChamadoVOs.CriarReq();
        req.setTitulo(cortar("[Monitoramento] " + m.getNome() + " fora do ar", 255));
        req.setDescricao("<p>Aviso automático do monitoramento: <strong>" + HtmlUtils.htmlEscape(m.getNome()) + "</strong> está fora do ar.</p>"
                + "<p>Alvo: " + HtmlUtils.htmlEscape(Optional.ofNullable(m.getAlvo()).orElse(m.getTipo()))
                + (unidade == null ? "" : "<br>Unidade: " + HtmlUtils.htmlEscape(unidade))
                + "<br>Motivo: " + HtmlUtils.htmlEscape(Optional.ofNullable(m.getUltimaMsg()).orElse("—")) + "</p>"
                + "<p>" + (Boolean.FALSE.equals(m.getChamadoSolucionar()) ? "Quando voltar ao ar, o monitoramento registra no chamado."
                : "Quando voltar ao ar, o monitoramento registra no chamado e o soluciona.") + "</p>");
        int urgencia = Optional.ofNullable(m.getChamadoUrgencia()).orElse(4);
        req.setUrgencia(urgencia);
        req.setImpacto("principal".equals(m.getPapel()) ? 4 : 3);
        req.setCategoriaId(m.getChamadoCategoriaId());
        req.setOrigemId(ORIGEM_MONITORAMENTO);
        req.setDeptId(m.getDeptId());
        Long id = chamadoService.criar(req, SISTEMA);
        chamadoMapper.updateById(ChamadoDO.builder().id(id).origemAutomatica("monitoramento:" + m.getId()).build());
        log.info("[monitoramento] chamado {} aberto para o monitor {} ({})", id, m.getId(), m.getNome());
        return id;
    }

    private void normalizarChamado(MonitorDO m, Long foraS) {
        if (!chamadoAberto(m.getChamadoId())) {
            return;
        }
        String texto = m.getNome() + " voltou ao ar" + (foraS == null ? "" : " depois de " + MonitorService.duracao(foraS) + " fora") + ".";
        if (Boolean.FALSE.equals(m.getChamadoSolucionar())) {
            chamadoService.acompanhamentoSistema(m.getChamadoId(), null, texto, false);
        } else {
            chamadoService.solucionarSistema(m.getChamadoId(), texto + " Solucionado automaticamente pelo monitoramento.");
        }
    }

    private boolean chamadoAberto(Long chamadoId) {
        if (chamadoId == null) {
            return false;
        }
        ChamadoDO c = chamadoMapper.selectById(chamadoId);
        return c != null && ChamadoStatus.de(c.getStatus()).aberto();
    }

    private String unidade(Long unidadeId) {
        if (unidadeId == null) {
            return null;
        }
        MonitorUnitDO u = monitorUnitMapper.selectById(unidadeId);
        return u == null ? null : u.getName();
    }

    private void enviarWs(MonitorDO m, Estados.Transicao t, LocalDateTime agora) {
        try {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("monitorId", m.getId());
            b.put("estado", t.estado());
            b.put("latenciaMs", m.getUltimaLatenciaMs());
            b.put("msg", m.getUltimaMsg());
            b.put("momento", agora.toString());
            b.put("importante", t.importante());
            webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), TIPO_WS, b);
        } catch (RuntimeException e) {
            log.debug("[monitoramento] WebSocket indisponível: {}", e.getMessage());
        }
    }

    void esquecer(Long id) {
        ativos.remove(id);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
