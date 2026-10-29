package cn.iocoder.yudao.module.system.service.monitor.fonte;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs.*;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.UnitKpiVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.service.monitor.MonitorKpiService;
import jakarta.annotation.Resource; // no branch master (JDK 8) troque por javax.annotation.Resource
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Implementação SIMULADA de todas as fontes (chamados, links, inventário, logs, câmeras), para as telas
 * funcionarem de ponta a ponta antes de ligar as ferramentas reais. Usa as unidades cadastradas em
 * monitor_unit e o estado de link calculado pelo {@link MonitorKpiService}, então tudo fica coerente com o telão.
 *
 * <p>A cada 5 s empurra pelo WebSocket só o que mudou, com um tipo por módulo:
 * monitor-link, monitor-chamado, monitor-log, monitor-camera. Cada tela assina só o seu tipo.
 */
@Slf4j
@Service
public class FontesSimuladas implements MonitorFontes.Chamados, MonitorFontes.Links, MonitorFontes.Inventario,
        MonitorFontes.Logs, MonitorFontes.Cameras {

    private static final String[] NOMES = "ana bruno carla daniel eduarda felipe gabriela henrique isabela joao karina lucas mariana paulo rafaela rodrigo thiago vanessa larissa marcelo patricia gustavo juliana andre camila fernando leticia ricardo beatriz diego".split(" ");
    private static final String[] SOBRENOMES = "silva santos oliveira souza rodrigues ferreira alves pereira lima gomes costa ribeiro martins carvalho almeida lopes soares fernandes vieira barbosa rocha dias".split(" ");
    private static final Map<String, String[]> CATEGORIAS = new LinkedHashMap<>();
    static {
        CATEGORIAS.put("Rede", new String[]{"Sem acesso à rede", "Wi-Fi instável na sala de reuniões", "Ponto de rede sem sinal"});
        CATEGORIAS.put("Hardware", new String[]{"Notebook não liga", "Monitor piscando", "Teclado com teclas falhando"});
        CATEGORIAS.put("Software", new String[]{"Erro ao abrir o SEI", "Atualizar pacote Office", "Instalar leitor de certificado digital"});
        CATEGORIAS.put("Acesso e senha", new String[]{"Desbloqueio de senha de rede", "Liberar acesso à pasta do setor", "Criar conta para novo servidor"});
        CATEGORIAS.put("Impressão", new String[]{"Impressora sem toner", "Impressora não aparece na rede"});
        CATEGORIAS.put("Segurança", new String[]{"E-mail suspeito recebido (phishing)", "Mensagem de antivírus na tela"});
        CATEGORIAS.put("Telefonia", new String[]{"Ramal mudo", "Transferir ramal para outra mesa"});
    }
    private static final Map<String, Integer> SLA_HORAS = Map.of("Crítica", 4, "Alta", 8, "Média", 24, "Baixa", 72);
    private static final int MAX_LOGS = 3000, MAX_EVENTOS = 500;

    @Resource
    private MonitorKpiService monitorKpiService;
    @Resource
    private WebSocketMessageSender webSocketMessageSender;
    @Resource
    private org.springframework.context.ApplicationContext applicationContext;

    private final Random r = new Random(20261021);
    private final Object lock = new Object();
    private volatile boolean iniciado;
    private final Map<Long, String> nomeUnidade = new LinkedHashMap<>();
    private final Map<Long, String> codigoUnidade = new HashMap<>();
    private final List<ChamadoVO> chamados = new ArrayList<>();
    private long proximoChamado = 26041;
    private final List<LinkVO> links = new ArrayList<>();
    private final List<EquipamentoVO> equipamentos = new ArrayList<>();
    private final Deque<LogVO> logs = new ArrayDeque<>();
    /** por unidade: [entraram, saíram, no estacionamento, vagas, funcionários, visitantes, alertas] */
    private final Map<Long, int[]> contadores = new HashMap<>();
    private final Map<Long, List<CameraVO>> cameras = new HashMap<>();
    private final Deque<CameraEventoVO> eventos = new ArrayDeque<>();

    // ======================================================================================
    // Geração inicial
    // ======================================================================================
    private void iniciar() {
        if (iniciado) {
            return;
        }
        synchronized (lock) {
            if (iniciado) {
                return;
            }
            List<MonitorUnitDO> unidades = monitorKpiService.getUnidadesAtivas();
            if (unidades.isEmpty()) {
                return; // sem unidades cadastradas ainda: tenta de novo na próxima chamada
            }
            long agora = System.currentTimeMillis();
            int seq = 0;
            for (int i = 0; i < unidades.size(); i++) {
                MonitorUnitDO u = unidades.get(i);
                Long id = u.getId();
                boolean sede = i == 0, dc = !sede && u.getName().toLowerCase().contains("datacenter");
                String codigo = semAcento(u.getName().replace("Regional ", "")).replaceAll("[^a-z]", "");
                codigo = (codigo + "xxx").substring(0, 3).toUpperCase() + (i > 9 ? String.valueOf(i) : "");
                nomeUnidade.put(id, u.getName());
                codigoUnidade.put(id, codigo);

                // links e serviços
                for (String papel : new String[]{"principal", "redundante"}) {
                    LinkVO l = new LinkVO();
                    l.setId((long) links.size() + 1);
                    l.setNome("Link " + papel + " · " + u.getName().replace("Regional ", ""));
                    l.setTipo("ping");
                    l.setAlvo("10." + id + ".0." + ("principal".equals(papel) ? 1 : 2));
                    l.setUnidadeId(id);
                    l.setPapel(papel);
                    preencherHistorico(l);
                    links.add(l);
                }
                if (sede) {
                    String[][] servicos = {{"Portal da intranet", "http", "https://intranet.orgao.gov.br", "54"}, {"SEI · processo eletrônico", "http", "https://sei.orgao.gov.br", "19"},
                            {"E-mail institucional", "port", "smtp.orgao.gov.br:587", ""}, {"VPN de acesso remoto", "port", "vpn.orgao.gov.br:443", "143"},
                            {"DNS interno", "dns", "dns1.orgao.local", ""}, {"Active Directory", "port", "dc01.orgao.local:636", ""},
                            {"Graylog", "http", "https://logs.orgao.local", "201"}, {"Matrix Synapse", "http", "https://matrix.orgao.gov.br", "77"}};
                    for (String[] s : servicos) {
                        LinkVO l = new LinkVO();
                        l.setId((long) links.size() + 1);
                        l.setNome(s[0]);
                        l.setTipo(s[1]);
                        l.setAlvo(s[2]);
                        l.setUnidadeId(id);
                        l.setPapel("servico");
                        l.setCertificadoDias(s[3].isEmpty() ? null : Integer.valueOf(s[3]));
                        preencherHistorico(l);
                        links.add(l);
                    }
                }

                // inventário
                int estacoes = sede ? 300 : dc ? 40 : 20 + r.nextInt(100), servidores = sede ? 40 : dc ? 30 : 2;
                for (int k = 0; k < estacoes + servidores; k++) {
                    boolean srv = k >= estacoes, nb = !srv && r.nextInt(100) < 35;
                    EquipamentoVO e = new EquipamentoVO();
                    e.setHostname(codigo + (srv ? "-SRV-" : nb ? "-NB-" : "-DT-") + String.format("%05d", ++seq));
                    e.setTipo(srv ? "Servidor" : nb ? "Notebook" : "Desktop");
                    e.setUnidadeId(id);
                    e.setUsuario(srv ? "" : login());
                    int x = r.nextInt(100);
                    e.setSo(srv ? pick("Windows Server 2022", "Ubuntu Server 24.04", "Red Hat Enterprise Linux 9")
                            : x < 55 ? "Windows 11 Pro" : x < 80 ? "Windows 10 Pro" : x < 92 ? "Ubuntu 24.04 LTS" : "Ubuntu 22.04 LTS");
                    e.setCpu(srv ? pick("Intel Xeon Silver 4314", "AMD EPYC 7313") : pick("Intel Core i5-12500", "Intel Core i7-12700", "AMD Ryzen 5 5600G", "Intel Core i5-1235U"));
                    e.setRamGb(srv ? pick(64, 128, 256) : pick(8, 16, 16, 32));
                    e.setDiscoGb(srv ? pick(2048, 4096) : pick(256, 512, 512, 1024));
                    e.setDiscoLivrePct(6 + r.nextInt(70));
                    int av = r.nextInt(100);
                    e.setAntivirus(av < 88 ? "Atualizado" : av < 95 ? "Desatualizado" : "Ausente");
                    e.setUltimaComunicacao(agora - (r.nextInt(100) < 93 ? (1 + r.nextInt(240)) * 60_000L : (8 + r.nextInt(30)) * 86_400_000L));
                    e.setIp("10." + id + "." + (srv ? 1 : 10 + r.nextInt(50)) + "." + (10 + r.nextInt(240)));
                    equipamentos.add(e);
                }

                // chamados abertos nas últimas 30 h
                int n = sede ? 40 : dc ? 8 : 4 + r.nextInt(12);
                for (int k = 0; k < n; k++) {
                    ChamadoVO c = novoChamado(id, null, login(), "");
                    c.setAbertoEm(agora - (5 + r.nextInt(60 * 30)) * 60_000L);
                    c.setPrazo(c.getAbertoEm() + SLA_HORAS.get(c.getPrioridade()) * 3_600_000L);
                    int st = r.nextInt(100);
                    c.setStatus(st < 25 ? "Novo" : st < 60 ? "Em atendimento" : st < 75 ? "Pendente" : "Solucionado");
                    if (!"Novo".equals(c.getStatus())) {
                        c.setTecnico(login());
                    }
                    if ("Solucionado".equals(c.getStatus())) {
                        c.setSolucionadoEm(Math.min(agora, c.getAbertoEm() + (20 + r.nextInt(280)) * 60_000L));
                    }
                }

                // câmeras
                int nCam = sede ? 4 : dc ? 6 : 3, vagas = sede ? 180 : dc ? 60 : 30 + r.nextInt(40);
                contadores.put(id, new int[]{sede ? 236 : 20 + r.nextInt(60), sede ? 118 : 10 + r.nextInt(30), (int) (vagas * .6), vagas,
                        sede ? 260 : 15 + r.nextInt(80), sede ? 17 : r.nextInt(8), 0});
                List<CameraVO> lista = new ArrayList<>();
                String[] nomesCam = {"Portão de veículos", "Estacionamento", "Recepção", "Catracas", "Sala técnica", "Corredor do datacenter"};
                for (int k = 0; k < nCam; k++) {
                    CameraVO cv = new CameraVO();
                    cv.setId(codigo.toLowerCase() + "_cam" + (k + 1));
                    cv.setNome(nomesCam[k % nomesCam.length]);
                    cv.setUnidadeId(id);
                    cv.setOnline(true);
                    cv.setSnapshotUrl(""); // modo simulado: sem imagem
                    lista.add(cv);
                }
                cameras.put(id, lista);
            }
            chamados.sort(Comparator.comparing(ChamadoVO::getAbertoEm).reversed());
            for (int i = 0; i < 300; i++) {
                novoLog(agora - (300 - i) * 2_000L);
            }
            iniciado = true;
            log.info("[monitor] fontes simuladas prontas: {} unidades, {} chamados, {} monitores, {} equipamentos",
                    nomeUnidade.size(), chamados.size(), links.size(), equipamentos.size());
        }
    }

    private void preencherHistorico(LinkVO l) {
        for (int i = 0; i < 40; i++) {
            l.getHistorico().add(r.nextInt(1000) < 8 ? "d" : r.nextInt(1000) < 20 ? "p" : "u");
        }
        l.getHistorico().set(39, "u");
        l.setEstado("up");
        l.setLatenciaMs("servico".equals(l.getPapel()) ? 40 + r.nextInt(120) : 12 + r.nextInt(50));
        l.setUptime30d(99.5 + r.nextInt(49) / 100.0);
        atualizarUptime(l);
    }

    private void atualizarUptime(LinkVO l) {
        long d = l.getHistorico().stream().filter("d"::equals).count(), p = l.getHistorico().stream().filter("p"::equals).count();
        l.setUptime24h(Math.round(Math.max(95, 100 - d * .35 - p * .05) * 100) / 100.0);
    }

    private ChamadoVO novoChamado(Long unidadeId, ChamadoNovoReqVO req, String solicitante, String origem) {
        ChamadoVO c = new ChamadoVO();
        c.setId(proximoChamado++);
        String cat = req != null && req.getCategoria() != null ? req.getCategoria() : pick(CATEGORIAS.keySet().toArray(new String[0]));
        c.setCategoria(cat);
        c.setTitulo(req != null ? req.getTitulo() : pick(CATEGORIAS.getOrDefault(cat, CATEGORIAS.get("Software"))));
        int p = r.nextInt(100);
        c.setPrioridade(req != null && req.getPrioridade() != null ? req.getPrioridade() : p < 6 ? "Crítica" : p < 25 ? "Alta" : p < 70 ? "Média" : "Baixa");
        c.setStatus("Novo");
        c.setUnidadeId(unidadeId);
        c.setSolicitante(solicitante);
        c.setOrigem(origem);
        c.setAbertoEm(System.currentTimeMillis());
        c.setPrazo(c.getAbertoEm() + SLA_HORAS.getOrDefault(c.getPrioridade(), 24) * 3_600_000L);
        chamados.add(0, c);
        return c;
    }

    private LogVO novoLog(long ts) {
        List<Long> ids = new ArrayList<>(nomeUnidade.keySet());
        Long u = ids.get(r.nextInt(ids.size()));
        String cod = codigoUnidade.get(u).toLowerCase(), quem = login();
        int x = r.nextInt(1000);
        LogVO l = new LogVO();
        l.setTs(ts);
        l.setUnidadeId(u);
        if (x < 320) { l.setOrigem("ad-dc01"); l.setNivel("info"); l.setMensagem("Logon bem-sucedido: " + quem); }
        else if (x < 420) { l.setOrigem("ad-dc01"); l.setNivel("aviso"); l.setMensagem("Falha de logon: " + quem + " (senha incorreta)"); }
        else if (x < 620) { l.setOrigem("fw-" + cod); l.setNivel("info"); l.setMensagem("Conexão bloqueada de " + ipExterno() + " para 10." + u + ".1." + (10 + r.nextInt(90)) + ":" + pick(22, 3389, 445, 1433)); }
        else if (x < 720) { l.setOrigem("vpn-gw"); l.setNivel("info"); l.setMensagem("Usuário " + quem + " conectou à VPN a partir de " + ipExterno()); }
        else if (x < 820) { l.setOrigem("proxy-" + cod); l.setNivel("info"); l.setMensagem("Acesso negado para " + quem + ": categoria " + pick("jogos", "streaming", "apostas")); }
        else if (x < 960) { l.setOrigem(cod + "-srv"); l.setNivel("aviso"); l.setMensagem(pick("Uso de disco acima de 85% em /var", "Serviço reiniciado automaticamente", "Tempo de resposta do banco acima de 2 s")); }
        else { l.setOrigem(cod + "-srv"); l.setNivel("erro"); l.setMensagem(pick("Falha ao gravar no volume de dados", "Timeout na replicação do AD")); }
        logs.addFirst(l);
        while (logs.size() > MAX_LOGS) {
            logs.pollLast();
        }
        return l;
    }

    // ======================================================================================
    // Simulação contínua + envio pelo WebSocket
    // ======================================================================================
    /** Logs e câmeras ainda não têm fonte real e as telas deles ficam desligadas: a simulação só roda se pedida. */
    @Value("${coti.monitor.simulacao:false}")
    private boolean simulacaoLigada;

    @Scheduled(fixedRate = 5000, initialDelay = 12000)
    public void tick() {
        if (!simulacaoLigada) {
            return;
        }
        try {
            iniciar();
            if (!iniciado) {
                return;
            }
            List<LinkVO> linksMudaram = new ArrayList<>();
            List<ChamadoVO> chamadosMudaram = new ArrayList<>();
            List<LogVO> logsNovos = new ArrayList<>();
            List<CameraEventoVO> eventosNovos = new ArrayList<>();
            Map<Long, int[]> contadoresMudaram = new HashMap<>();
            long agora = System.currentTimeMillis();
            synchronized (lock) {
                // links: o principal segue o estado calculado pelo telão; os demais oscilam pouco
                for (LinkVO l : links) {
                    String antes = l.getEstado(), depois;
                    if ("principal".equals(l.getPapel())) {
                        UnitKpiVO k = monitorKpiService.getKpi(l.getUnidadeId());
                        depois = k == null ? "up" : "offline".equals(k.getLink()) ? "down" : "degradado".equals(k.getLink()) ? "lento" : "up";
                        l.setLatenciaMs(k == null || "down".equals(depois) ? null : k.getLatenciaMs());
                    } else {
                        int x = r.nextInt(10000);
                        depois = "down".equals(antes) ? (x < 3000 ? "up" : "down") : x < 15 ? "down" : x < 50 ? "lento" : "up";
                        l.setLatenciaMs("down".equals(depois) ? null : Math.max(2, (l.getLatenciaMs() == null ? 40 : l.getLatenciaMs()) + r.nextInt(11) - 5));
                    }
                    l.setEstado(depois);
                    l.getHistorico().add("up".equals(depois) ? "u" : "lento".equals(depois) ? "p" : "d");
                    l.getHistorico().remove(0);
                    atualizarUptime(l);
                    if (!depois.equals(antes)) {
                        linksMudaram.add(l);
                        if ("down".equals(depois)) {
                            chamadosMudaram.add(novoChamadoAutomatico(l));
                        } else if ("up".equals(depois)) {
                            for (ChamadoVO c : chamados) {
                                if ("Uptime Kuma".equals(c.getOrigem()) && aberto(c) && c.getTitulo().equals(l.getNome() + " fora do ar")) {
                                    c.setStatus("Solucionado");
                                    c.setSolucionadoEm(agora);
                                    chamadosMudaram.add(c);
                                }
                            }
                        }
                    }
                }
                // chamados: novos e andamento
                int novos = r.nextInt(100) < 40 ? 1 + r.nextInt(2) : 0;
                List<Long> ids = new ArrayList<>(nomeUnidade.keySet());
                for (int i = 0; i < novos; i++) {
                    chamadosMudaram.add(novoChamado(ids.get(r.nextInt(ids.size())), null, login(), ""));
                }
                for (ChamadoVO c : chamados) {
                    if (!aberto(c)) {
                        continue;
                    }
                    int x = r.nextInt(1000);
                    String antes = c.getStatus();
                    if ("Novo".equals(antes) && x < 30) { c.setStatus("Em atendimento"); c.setTecnico(login()); }
                    else if ("Em atendimento".equals(antes) && x < 15) { c.setStatus("Solucionado"); c.setSolucionadoEm(agora); }
                    else if ("Pendente".equals(antes) && x < 20) { c.setStatus("Em atendimento"); }
                    if (!antes.equals(c.getStatus())) {
                        chamadosMudaram.add(c);
                    }
                }
                if (chamados.size() > 20_000) {
                    chamados.removeIf(c -> !aberto(c) && agora - c.getSolucionadoEm() > 86_400_000L);
                }
                // logs: amostra contínua e, raramente, um alerta de força bruta
                for (int i = 0, n = 5 + r.nextInt(10); i < n; i++) {
                    logsNovos.add(novoLog(agora));
                }
                if (r.nextInt(1000) < 15) {
                    LogVO alerta = forcaBruta(agora);
                    logsNovos.add(alerta);
                    ChamadoNovoReqVO req = new ChamadoNovoReqVO();
                    req.setTitulo(alerta.getMensagem());
                    req.setCategoria("Segurança");
                    req.setPrioridade("Alta");
                    chamadosMudaram.add(novoChamado(alerta.getUnidadeId(), req, "", "Graylog"));
                }
                // câmeras: entradas, saídas, catracas, visitantes e alertas
                for (Map.Entry<Long, int[]> en : contadores.entrySet()) {
                    int[] c = en.getValue();
                    Long u = en.getKey();
                    boolean mudou = false;
                    if (r.nextInt(100) < 25) {
                        c[0]++; c[2] = Math.min(c[3], c[2] + 1); mudou = true;
                        if (r.nextInt(100) < 6) {
                            c[6]++;
                            eventosNovos.add(evento(u, "Portão de veículos", "Placa " + placa() + " sem cadastro · portaria acionada", true));
                        }
                    }
                    if (r.nextInt(100) < 18) { c[1]++; c[2] = Math.max(0, c[2] - 1); mudou = true; }
                    if (r.nextInt(100) < 30) { c[4]++; mudou = true; }
                    if (r.nextInt(1000) < 8) {
                        c[6]++; mudou = true;
                        eventosNovos.add(evento(u, "Catracas", "Possível carona: 2 pessoas, 1 crachá", true));
                    }
                    if (r.nextInt(100) < 4) {
                        c[5]++; mudou = true;
                        eventosNovos.add(evento(u, "Recepção", "Visitante registrado · crachá V-" + String.format("%03d", c[5]), false));
                    }
                    if (r.nextInt(100) < 3 && c[4] > 0) { c[4]--; mudou = true; }
                    if (r.nextInt(100) < 2 && c[5] > 0) { c[5]--; mudou = true; }
                    if (mudou) {
                        contadoresMudaram.put(u, c.clone());
                    }
                }
            }
            // só o que ainda é simulado: com a fonte real ligada (@Primary), as telas não recebem dados inventados
            if (simulado(MonitorFontes.Links.class)) {
                enviar("monitor-link", linksMudaram);
            }
            if (simulado(MonitorFontes.Chamados.class)) {
                enviar("monitor-chamado", chamadosMudaram.stream().distinct().map(this::comUnidade).collect(Collectors.toList()));
            }
            if (simulado(MonitorFontes.Logs.class)) {
                enviar("monitor-log", logsNovos);
            }
            if (!eventosNovos.isEmpty() || !contadoresMudaram.isEmpty()) {
                Map<String, Object> cam = new LinkedHashMap<>();
                cam.put("eventos", eventosNovos);
                cam.put("unidades", contadoresMudaram.keySet());
                enviar("monitor-camera", Collections.singletonList(cam));
            }
        } catch (Exception e) {
            log.error("[monitor] falha na simulação das fontes", e);
        }
    }

    /** A fonte ativa deste módulo é esta simulação (nenhuma fonte real marcada com @Primary)? */
    private boolean simulado(Class<?> contrato) {
        try {
            return applicationContext.getBean(contrato) == this;
        } catch (org.springframework.beans.BeansException e) {
            return true;
        }
    }

    private ChamadoVO novoChamadoAutomatico(LinkVO l) {
        ChamadoNovoReqVO req = new ChamadoNovoReqVO();
        req.setTitulo(l.getNome() + " fora do ar");
        req.setCategoria("Rede");
        req.setPrioridade("servico".equals(l.getPapel()) ? "Crítica" : "Alta");
        return novoChamado(l.getUnidadeId(), req, "", "Uptime Kuma");
    }

    private LogVO forcaBruta(long ts) {
        LogVO l = novoLog(ts);
        String conta = login(), ip = ipExterno();
        l.setOrigem("graylog-alerta");
        l.setNivel("critico");
        l.setMensagem("Possível força bruta contra a conta " + conta + ": 6 falhas em 30 s vindas de " + ip);
        return l;
    }

    private CameraEventoVO evento(Long unidadeId, String camera, String texto, boolean alerta) {
        CameraEventoVO e = new CameraEventoVO();
        e.setTs(System.currentTimeMillis());
        e.setCamera(camera);
        e.setTexto(texto);
        e.setAlerta(alerta);
        e.setUnidadeId(unidadeId);
        eventos.addFirst(e);
        while (eventos.size() > MAX_EVENTOS) {
            eventos.pollLast();
        }
        return e;
    }

    private void enviar(String tipo, List<?> conteudo) {
        if (!conteudo.isEmpty()) {
            webSocketMessageSender.sendObject(UserTypeEnum.ADMIN.getValue(), tipo, conteudo);
        }
    }

    // ======================================================================================
    // Consultas usadas pelo controller
    // ======================================================================================
    @Override
    public ListaVO<ChamadoVO> listarChamados(Long unidadeId, String status, String busca, int limite) {
        iniciar();
        synchronized (lock) {
            long agora = System.currentTimeMillis();
            List<ChamadoVO> base = chamados.stream().filter(c -> unidadeId == null || unidadeId.equals(c.getUnidadeId())).collect(Collectors.toList());
            ListaVO<ChamadoVO> res = new ListaVO<>();
            Map<String, Long> ct = res.getContagens();
            ct.put("abertos", base.stream().filter(this::aberto).count());
            for (String s : new String[]{"Novo", "Em atendimento", "Pendente", "Solucionado"}) {
                ct.put(s, base.stream().filter(c -> s.equals(c.getStatus())).count());
            }
            ct.put("sla", base.stream().filter(c -> aberto(c) && c.getPrazo() < agora).count());
            ct.put("auto", base.stream().filter(c -> aberto(c) && !c.getOrigem().isEmpty()).count());
            ct.put("todos", (long) base.size());
            String st = status == null ? "abertos" : status;
            Predicate<ChamadoVO> filtro = "todos".equals(st) ? c -> true : "abertos".equals(st) ? this::aberto
                    : "sla".equals(st) ? c -> aberto(c) && c.getPrazo() < agora : "auto".equals(st) ? c -> aberto(c) && !c.getOrigem().isEmpty()
                    : c -> st.equals(c.getStatus());
            String q = busca == null ? "" : semAcento(busca);
            List<ChamadoVO> lista = base.stream().filter(filtro)
                    .filter(c -> q.isEmpty() || semAcento(c.getId() + " " + c.getTitulo() + " " + c.getSolicitante() + " " + nomeUnidade.get(c.getUnidadeId())).contains(q))
                    .sorted(Comparator.comparing((ChamadoVO c) -> !aberto(c)).thenComparing(c -> aberto(c) ? c.getPrazo() : -c.getSolucionadoEm()))
                    .collect(Collectors.toList());
            res.setTotal(lista.size());
            res.setLista(lista.stream().limit(limite).map(this::comUnidade).collect(Collectors.toList()));
            return res;
        }
    }

    @Override
    public ChamadoVO abrirChamado(ChamadoNovoReqVO novo, String solicitante, String origem) {
        iniciar();
        synchronized (lock) {
            Long un = novo.getUnidadeId() != null ? novo.getUnidadeId() : nomeUnidade.keySet().iterator().next();
            ChamadoVO c = comUnidade(novoChamado(un, novo, solicitante, origem == null ? "" : origem));
            enviar("monitor-chamado", Collections.singletonList(c));
            return c;
        }
    }

    @Override
    public ListaVO<LinkVO> listarLinks(Long unidadeId, String filtro, int limite) {
        iniciar();
        synchronized (lock) {
            List<LinkVO> base = links.stream().filter(l -> unidadeId == null || unidadeId.equals(l.getUnidadeId())).collect(Collectors.toList());
            ListaVO<LinkVO> res = new ListaVO<>();
            res.getContagens().put("problema", base.stream().filter(l -> !"up".equals(l.getEstado())).count());
            res.getContagens().put("links", base.stream().filter(l -> !"servico".equals(l.getPapel())).count());
            res.getContagens().put("servicos", base.stream().filter(l -> "servico".equals(l.getPapel())).count());
            res.getContagens().put("todos", (long) base.size());
            String f = filtro == null ? "todos" : filtro;
            List<String> ordem = Arrays.asList("down", "lento", "up");
            List<LinkVO> lista = base.stream().filter(l -> "todos".equals(f) || ("problema".equals(f) && !"up".equals(l.getEstado()))
                            || ("links".equals(f) && !"servico".equals(l.getPapel())) || ("servicos".equals(f) && "servico".equals(l.getPapel())))
                    .sorted(Comparator.comparing((LinkVO l) -> ordem.indexOf(l.getEstado())).thenComparing(LinkVO::getId))
                    .collect(Collectors.toList());
            res.setTotal(lista.size());
            res.setLista(lista.stream().limit(limite).peek(l -> l.setUnidade(nomeUnidade.get(l.getUnidadeId()))).collect(Collectors.toList()));
            res.getResumo().put("uptimeMedio24h", base.stream().mapToDouble(LinkVO::getUptime24h).average().orElse(100));
            res.getResumo().put("certificadosVencendo", base.stream().filter(l -> l.getCertificadoDias() != null && l.getCertificadoDias() < 30).count());
            return res;
        }
    }

    @Override
    public ListaVO<EquipamentoVO> listarEquipamentos(Long unidadeId, String filtro, String busca, int limite) {
        iniciar();
        synchronized (lock) {
            long agora = System.currentTimeMillis();
            Predicate<EquipamentoVO> velho = e -> agora - e.getUltimaComunicacao() > 7 * 86_400_000L;
            List<EquipamentoVO> base = equipamentos.stream().filter(e -> unidadeId == null || unidadeId.equals(e.getUnidadeId())).collect(Collectors.toList());
            ListaVO<EquipamentoVO> res = new ListaVO<>();
            Map<String, Long> ct = res.getContagens();
            ct.put("todos", (long) base.size());
            for (String t : new String[]{"Desktop", "Notebook", "Servidor"}) {
                ct.put(t, base.stream().filter(e -> t.equals(e.getTipo())).count());
            }
            ct.put("eol", base.stream().filter(e -> e.getSo().startsWith("Windows 10")).count());
            ct.put("av", base.stream().filter(e -> !"Atualizado".equals(e.getAntivirus())).count());
            ct.put("old", base.stream().filter(velho).count());
            res.getResumo().put("porSistema", base.stream().collect(Collectors.groupingBy(EquipamentoVO::getSo, TreeMap::new, Collectors.counting())));
            String f = filtro == null ? "todos" : filtro, q = busca == null ? "" : semAcento(busca);
            List<EquipamentoVO> lista = base.stream().filter(e -> "todos".equals(f) || f.equals(e.getTipo())
                            || ("eol".equals(f) && e.getSo().startsWith("Windows 10")) || ("av".equals(f) && !"Atualizado".equals(e.getAntivirus()))
                            || ("old".equals(f) && velho.test(e)))
                    .filter(e -> q.isEmpty() || semAcento(e.getHostname() + " " + e.getSo() + " " + e.getIp() + " " + e.getUsuario()).contains(q))
                    .collect(Collectors.toList());
            res.setTotal(lista.size());
            res.setLista(lista.stream().limit(limite).peek(e -> e.setUnidade(nomeUnidade.get(e.getUnidadeId()))).collect(Collectors.toList()));
            return res;
        }
    }

    @Override
    public ListaVO<LogVO> buscarLogs(Long unidadeId, String nivel, String busca, int limite) {
        iniciar();
        synchronized (lock) {
            long umaHora = System.currentTimeMillis() - 3_600_000L;
            List<LogVO> base = logs.stream().filter(l -> unidadeId == null || unidadeId.equals(l.getUnidadeId())).collect(Collectors.toList());
            ListaVO<LogVO> res = new ListaVO<>();
            res.getContagens().put("todos", (long) base.size());
            for (String n : new String[]{"info", "aviso", "erro", "critico"}) {
                res.getContagens().put(n, base.stream().filter(l -> n.equals(l.getNivel())).count());
            }
            res.getResumo().put("falhasLogon1h", base.stream().filter(l -> l.getTs() > umaHora && l.getMensagem().startsWith("Falha de logon")).count());
            res.getResumo().put("bloqueios1h", base.stream().filter(l -> l.getTs() > umaHora && l.getMensagem().startsWith("Conexão bloqueada")).count());
            res.getResumo().put("alertas1h", base.stream().filter(l -> l.getTs() > umaHora && "critico".equals(l.getNivel())).count());
            String n = nivel == null ? "todos" : nivel, q = busca == null ? "" : semAcento(busca);
            List<LogVO> lista = base.stream().filter(l -> "todos".equals(n) || n.equals(l.getNivel()))
                    .filter(l -> q.isEmpty() || semAcento(l.getOrigem() + " " + l.getMensagem()).contains(q)).collect(Collectors.toList());
            res.setTotal(lista.size());
            res.setLista(lista.stream().limit(limite).collect(Collectors.toList()));
            return res;
        }
    }

    @Override
    public CameraResumoVO resumoCameras(Long unidadeId, int limiteEventos) {
        iniciar();
        synchronized (lock) {
            CameraResumoVO res = new CameraResumoVO();
            for (Map.Entry<Long, int[]> en : contadores.entrySet()) {
                if (unidadeId != null && !unidadeId.equals(en.getKey())) {
                    continue;
                }
                int[] c = en.getValue();
                res.setVeiculosEntraram(res.getVeiculosEntraram() + c[0]);
                res.setVeiculosSairam(res.getVeiculosSairam() + c[1]);
                res.setNoEstacionamento(res.getNoEstacionamento() + c[2]);
                res.setVagas(res.getVagas() + c[3]);
                res.setFuncionarios(res.getFuncionarios() + c[4]);
                res.setVisitantes(res.getVisitantes() + c[5]);
                res.setAlertas(res.getAlertas() + c[6]);
                UnitKpiVO k = monitorKpiService.getKpi(en.getKey());
                for (CameraVO cv : cameras.getOrDefault(en.getKey(), Collections.emptyList())) {
                    cv.setUnidade(nomeUnidade.get(en.getKey()));
                    cv.setOnline(k == null || !"offline".equals(k.getLink())); // sem link, grava só na unidade
                    res.getCameras().add(cv);
                }
            }
            res.setEventos(eventos.stream().filter(e -> unidadeId == null || unidadeId.equals(e.getUnidadeId())).limit(limiteEventos).collect(Collectors.toList()));
            return res;
        }
    }

    // ======================================================================================
    // utilidades
    // ======================================================================================
    private boolean aberto(ChamadoVO c) {
        return !"Solucionado".equals(c.getStatus());
    }

    private ChamadoVO comUnidade(ChamadoVO c) {
        c.setUnidade(nomeUnidade.get(c.getUnidadeId()));
        return c;
    }

    private String login() {
        return NOMES[r.nextInt(NOMES.length)] + "." + SOBRENOMES[r.nextInt(SOBRENOMES.length)];
    }

    private String ipExterno() {
        return pick(177, 186, 191, 45, 103, 185, 201) + "." + (1 + r.nextInt(254)) + "." + (1 + r.nextInt(254)) + "." + (1 + r.nextInt(254));
    }

    private String placa() {
        String l = "ABCDEFGHIJKLMNOPRSTUVWXYZ";
        return "" + l.charAt(r.nextInt(25)) + l.charAt(r.nextInt(25)) + l.charAt(r.nextInt(25)) + r.nextInt(10) + l.charAt(r.nextInt(25)) + r.nextInt(10) + r.nextInt(10);
    }

    @SafeVarargs
    private final <T> T pick(T... opcoes) {
        return opcoes[r.nextInt(opcoes.length)];
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
    }

}
