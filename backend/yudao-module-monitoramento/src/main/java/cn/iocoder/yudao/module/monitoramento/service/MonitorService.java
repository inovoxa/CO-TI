package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.BatidaDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoMonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorCanalDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonManutencaoMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorCanalMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.dominio.Estados;
import cn.iocoder.yudao.module.monitoramento.dominio.Regras;
import cn.iocoder.yudao.module.monitoramento.service.verificador.Resultado;
import cn.iocoder.yudao.module.monitoramento.service.verificador.Verificadores;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Cadastro e consulta dos monitores. A execução das verificações fica em {@link ExecucaoMonitores}.
 */
@Service
public class MonitorService {

    static final int HISTORICO = 40;
    private static final Set<String> TIPOS = Set.of("http", "palavra", "json", "tcp", "ping", "dns", "push", "manual");
    private static final Set<String> METODOS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> OPERADORES = Set.of("==", "!=", "<", "<=", ">", ">=", "contem");
    private static final Set<String> DNS_TIPOS = Set.of("A", "AAAA", "CNAME", "MX", "NS", "TXT", "SOA", "SRV", "PTR", "CAA");
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private MonMonitorCanalMapper monMonitorCanalMapper;
    @Resource
    private MonManutencaoMonitorMapper monManutencaoMonitorMapper;
    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private ExecucaoMonitores execucaoMonitores;
    @Resource
    private ManutencaoService manutencaoService;
    @Resource
    private CanalService canalService;
    @Resource
    private Verificadores verificadores;

    @Value("${coti.atendimento.url-publica:}")
    private String urlPublica;

    // ================================================================================== cadastro

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(MonitorVOs.Monitor req) {
        MonitorDO atual = req.getId() == null ? null : monMonitorMapper.selectById(req.getId());
        if (req.getId() != null && atual == null) {
            throw exception(MONITOR_NAO_EXISTE);
        }
        MonitorDO m = montar(req, atual);
        if (atual == null) {
            monMonitorMapper.insert(m);
        } else {
            monMonitorMapper.updateById(m);
            // campos que podem voltar a ficar vazios
            monMonitorMapper.update(null, new LambdaUpdateWrapper<MonitorDO>().eq(MonitorDO::getId, m.getId())
                    .set(MonitorDO::getUnidadeId, m.getUnidadeId()).set(MonitorDO::getLentoMs, m.getLentoMs())
                    .set(MonitorDO::getChamadoCategoriaId, m.getChamadoCategoriaId()).set(MonitorDO::getPorta, m.getPorta())
                    .set(MonitorDO::getCorpo, m.getCorpo()).set(MonitorDO::getCabecalhos, m.getCabecalhos())
                    .set(MonitorDO::getPalavra, m.getPalavra()).set(MonitorDO::getJsonCaminho, m.getJsonCaminho())
                    .set(MonitorDO::getJsonOperador, m.getJsonOperador()).set(MonitorDO::getJsonEsperado, m.getJsonEsperado())
                    .set(MonitorDO::getDnsServidor, m.getDnsServidor()).set(MonitorDO::getDnsEsperado, m.getDnsEsperado())
                    .set(MonitorDO::getDescricao, m.getDescricao()).set(MonitorDO::getDeptId, m.getDeptId()));
            if (!Objects.equals(atual.getTipo(), m.getTipo()) || !Objects.equals(atual.getAlvo(), m.getAlvo())) {
                // mudou o que é verificado: recomeça a contagem de tentativas e verifica logo
                monMonitorMapper.update(null, new LambdaUpdateWrapper<MonitorDO>().eq(MonitorDO::getId, m.getId())
                        .set(MonitorDO::getTentativaAtual, 0).set(MonitorDO::getUltimaVerificacao, null)
                        .set(MonitorDO::getCertDias, null).set(MonitorDO::getCertValidade, null));
            }
        }
        List<Long> canais = req.getCanalIds() != null ? req.getCanalIds() : atual == null ? canalService.padroes() : null;
        if (canais != null) {
            monMonitorCanalMapper.delete(MonitorCanalDO::getMonitorId, m.getId());
            for (Long c : new LinkedHashSet<>(canais)) {
                monMonitorCanalMapper.insert(MonitorCanalDO.builder().monitorId(m.getId()).canalId(c).build());
            }
        }
        depoisDoCommit(execucaoMonitores::recarregar);
        return m.getId();
    }

    /** Liga ou pausa. Pausado não verifica nem avisa. */
    public void ativar(Long id, boolean ativo) {
        existente(id);
        monMonitorMapper.updateById(MonitorDO.builder().id(id).ativo(ativo).build());
        if (!ativo) {
            execucaoMonitores.esquecer(id);
        }
        execucaoMonitores.recarregar();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        existente(id);
        monMonitorMapper.deleteById(id);
        monMonitorCanalMapper.delete(MonitorCanalDO::getMonitorId, id);
        monManutencaoMonitorMapper.delete(ManutencaoMonitorDO::getMonitorId, id);
        monMonitorMapper.apagarBatidas(id);
        depoisDoCommit(() -> execucaoMonitores.esquecer(id));
    }

    public void verificarAgora(Long id) {
        MonitorDO m = existente(id);
        if (!Boolean.TRUE.equals(m.getAtivo())) {
            throw exception(MONITOR_INVALIDO, "o monitor está pausado");
        }
        if (Set.of("push", "manual").contains(m.getTipo())) {
            throw exception(MONITOR_INVALIDO, "push e manual não são verificados pelo CO-TI");
        }
        execucaoMonitores.verificarAgora(id);
    }

    public void marcarManual(Long id, boolean noAr, String msg) {
        MonitorDO m = existente(id);
        if (!"manual".equals(m.getTipo())) {
            throw exception(MONITOR_INVALIDO, "só o monitor manual tem o estado escolhido à mão");
        }
        execucaoMonitores.registrarManual(id, noAr, msg);
    }

    /** Testa a configuração sem salvar (uma verificação agora). */
    public MonitorVOs.Teste testar(MonitorVOs.Monitor req) {
        MonitorDO m = montar(req, null);
        if (Set.of("push", "manual").contains(m.getTipo())) {
            throw exception(MONITOR_INVALIDO, "push e manual não têm teste: o estado vem de fora");
        }
        Resultado r = verificadores.verificar(m);
        MonitorVOs.Teste t = new MonitorVOs.Teste();
        t.setOk(Boolean.TRUE.equals(m.getInvertido()) != r.ok());
        t.setLatenciaMs(r.latenciaMs());
        t.setMsg(Boolean.TRUE.equals(m.getInvertido()) ? "invertido: " + r.msg() : r.msg());
        t.setCertDias(r.certDias());
        return t;
    }

    private MonitorDO montar(MonitorVOs.Monitor r, MonitorDO atual) {
        String tipo = r.getTipo() == null ? "" : r.getTipo().trim();
        if (!TIPOS.contains(tipo)) {
            throw exception(MONITOR_INVALIDO, "tipo deve ser http, palavra, json, tcp, ping, dns, push ou manual");
        }
        String alvo = vazio(r.getAlvo());
        String metodo = Optional.ofNullable(vazio(r.getMetodo())).orElse("GET").toUpperCase(Locale.ROOT);
        switch (tipo) {
            case "http", "palavra", "json" -> {
                if (alvo == null || !alvo.matches("(?i)https?://[^\\s/$.?#][^\\s]*")) {
                    throw exception(MONITOR_INVALIDO, "informe a URL (http:// ou https://)");
                }
                if (!METODOS.contains(metodo)) {
                    throw exception(MONITOR_INVALIDO, "método HTTP inválido");
                }
                if (!Regras.codigosValidos(r.getCodigosAceitos())) {
                    throw exception(MONITOR_INVALIDO, "códigos aceitos: use faixas e códigos, ex.: 200-299,301");
                }
                if (vazio(r.getCabecalhos()) != null) {
                    try {
                        Object o = JsonUtils.parseObject(r.getCabecalhos(), new TypeReference<Map<String, Object>>() {
                        });
                        if (o == null) {
                            throw new IllegalArgumentException();
                        }
                    } catch (Exception e) {
                        throw exception(MONITOR_INVALIDO, "cabeçalhos devem ser um objeto JSON, ex.: {\"Authorization\": \"Bearer ...\"}");
                    }
                }
                if ("palavra".equals(tipo) && vazio(r.getPalavra()) == null) {
                    throw exception(MONITOR_INVALIDO, "informe a palavra-chave");
                }
                if ("json".equals(tipo)) {
                    if (!Regras.caminhoValido(r.getJsonCaminho())) {
                        throw exception(MONITOR_INVALIDO, "caminho JSON não suportado (use algo como dados.itens[0].estado)");
                    }
                    if (r.getJsonOperador() != null && !OPERADORES.contains(r.getJsonOperador())) {
                        throw exception(MONITOR_INVALIDO, "operador deve ser ==, !=, <, <=, >, >= ou contem");
                    }
                }
            }
            case "tcp" -> {
                if (!Regras.hostValido(alvo) || r.getPorta() == null || r.getPorta() < 1 || r.getPorta() > 65535) {
                    throw exception(MONITOR_INVALIDO, "informe o host (nome ou IP) e a porta (1 a 65535)");
                }
            }
            case "ping" -> {
                if (!Regras.hostValido(alvo)) {
                    throw exception(MONITOR_INVALIDO, "informe o host (nome ou IP)");
                }
            }
            case "dns" -> {
                if (!Regras.hostValido(alvo)) {
                    throw exception(MONITOR_INVALIDO, "informe o nome a consultar");
                }
                if (r.getDnsTipo() != null && !DNS_TIPOS.contains(r.getDnsTipo().toUpperCase(Locale.ROOT))) {
                    throw exception(MONITOR_INVALIDO, "tipo de registro DNS inválido");
                }
                if (vazio(r.getDnsServidor()) != null && !Regras.hostValido(r.getDnsServidor().trim())) {
                    throw exception(MONITOR_INVALIDO, "servidor DNS inválido (nome ou IP, com :porta se precisar)");
                }
            }
            default -> {
            }
        }
        String papel = Optional.ofNullable(vazio(r.getPapel())).orElse("servico");
        if (!Set.of("principal", "redundante", "servico").contains(papel)) {
            throw exception(MONITOR_INVALIDO, "papel deve ser principal, redundante ou servico");
        }
        Long deptId = r.getDeptId();
        if (r.getUnidadeId() != null) {
            MonitorUnitDO u = monitorUnitMapper.selectById(r.getUnidadeId());
            if (u == null) {
                throw exception(UNIDADE_NAO_EXISTE);
            }
            if (deptId == null) {
                deptId = u.getDeptId();
            }
        }
        int intervalo = Optional.ofNullable(r.getIntervaloS()).orElse(60);
        int limite = Optional.ofNullable(r.getTempoLimiteS()).orElse(Math.max(1, Math.min(30, (int) (intervalo * 0.8))));
        String token = atual != null && atual.getPushToken() != null ? atual.getPushToken()
                : "push".equals(tipo) ? Optional.ofNullable(vazio(r.getPushToken())).orElseGet(MonitorService::novoToken) : null;
        return MonitorDO.builder().id(r.getId()).nome(r.getNome().trim()).descricao(r.getDescricao()).tipo(tipo).alvo(alvo)
                .porta("tcp".equals(tipo) ? r.getPorta() : null).metodo(metodo).corpo(vazio(r.getCorpo())).cabecalhos(vazio(r.getCabecalhos()))
                .codigosAceitos(Optional.ofNullable(vazio(r.getCodigosAceitos())).orElse("200-299")).palavra(vazio(r.getPalavra()))
                .palavraInvertida(Boolean.TRUE.equals(r.getPalavraInvertida())).jsonCaminho(vazio(r.getJsonCaminho()))
                .jsonOperador(Optional.ofNullable(vazio(r.getJsonOperador())).orElse("json".equals(tipo) ? "==" : null))
                .jsonEsperado(r.getJsonEsperado()).dnsTipo(Optional.ofNullable(vazio(r.getDnsTipo())).orElse("A").toUpperCase(Locale.ROOT))
                .dnsServidor(vazio(r.getDnsServidor())).dnsEsperado(vazio(r.getDnsEsperado())).intervaloS(intervalo)
                .tentativas(Optional.ofNullable(r.getTentativas()).orElse(0))
                .intervaloTentativaS(Optional.ofNullable(r.getIntervaloTentativaS()).orElse(intervalo)).tempoLimiteS(limite)
                .reenviarACada(Optional.ofNullable(r.getReenviarACada()).orElse(0)).invertido(Boolean.TRUE.equals(r.getInvertido()))
                .ignorarTls(Boolean.TRUE.equals(r.getIgnorarTls())).maxRedirecionamentos(Optional.ofNullable(r.getMaxRedirecionamentos()).orElse(10))
                .lentoMs(r.getLentoMs() == null || r.getLentoMs() <= 0 ? null : r.getLentoMs())
                .certAvisoDias(Optional.ofNullable(r.getCertAvisoDias()).orElse(14)).pushToken(token).unidadeId(r.getUnidadeId()).papel(papel)
                .deptId(deptId).abrirChamado(Boolean.TRUE.equals(r.getAbrirChamado())).chamadoCategoriaId(r.getChamadoCategoriaId())
                .chamadoUrgencia(Optional.ofNullable(r.getChamadoUrgencia()).orElse(4)).chamadoSolucionar(r.getChamadoSolucionar() == null || r.getChamadoSolucionar())
                .ativo(r.getAtivo() == null || r.getAtivo()).kumaId(atual == null ? r.getKumaId() : null).build();
    }

    static String novoToken() {
        StringBuilder s = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            s.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return s.toString();
    }

    // ================================================================================== consulta

    public PageResult<MonitorVOs.Resp> pagina(MonitorVOs.PaginaReq req) {
        LambdaQueryWrapperX<MonitorDO> q = new LambdaQueryWrapperX<MonitorDO>()
                .eqIfPresent(MonitorDO::getTipo, vazio(req.getTipo()))
                .eqIfPresent(MonitorDO::getUnidadeId, req.getUnidadeId())
                .eqIfPresent(MonitorDO::getPapel, vazio(req.getPapel()));
        if (vazio(req.getBusca()) != null) {
            String b = "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", b).or().apply("alvo ILIKE {0}", b).or().apply("descricao ILIKE {0}", b));
        }
        if (req.getSituacao() != null) {
            switch (req.getSituacao()) {
                case "problema" -> q.eq(MonitorDO::getAtivo, true).in(MonitorDO::getEstado, Estados.FORA, Estados.PENDENTE);
                case "fora" -> q.eq(MonitorDO::getAtivo, true).eq(MonitorDO::getEstado, Estados.FORA);
                case "pendente" -> q.eq(MonitorDO::getAtivo, true).eq(MonitorDO::getEstado, Estados.PENDENTE);
                case "no_ar" -> q.eq(MonitorDO::getAtivo, true).eq(MonitorDO::getEstado, Estados.NO_AR);
                case "manutencao" -> q.eq(MonitorDO::getAtivo, true).eq(MonitorDO::getEstado, Estados.MANUTENCAO);
                case "pausado" -> q.eq(MonitorDO::getAtivo, false);
                default -> {
                }
            }
        }
        // fora do ar primeiro, depois pendentes, depois por nome
        q.orderByAsc(MonitorDO::getOrdem).orderByAsc(MonitorDO::getNome);
        PageResult<MonitorDO> p = monMonitorMapper.selectPage(req, q);
        return new PageResult<>(converter(p.getList()), p.getTotal());
    }

    /** Todos os monitores visíveis (para seletores e para o telão). */
    public List<MonitorDO> todos() {
        return monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().orderByAsc(MonitorDO::getNome));
    }

    public MonitorVOs.Contagens contagens() {
        MonitorVOs.Contagens c = new MonitorVOs.Contagens();
        for (MonitorDO m : monMonitorMapper.selectList()) {
            c.setTodos(c.getTodos() + 1);
            if (!Boolean.TRUE.equals(m.getAtivo())) {
                c.setPausado(c.getPausado() + 1);
                continue;
            }
            Integer e = m.getEstado();
            if (e != null) {
                switch (e) {
                    case Estados.NO_AR -> {
                        c.setNoAr(c.getNoAr() + 1);
                        if (lento(m)) {
                            c.setLento(c.getLento() + 1);
                        }
                    }
                    case Estados.FORA -> c.setFora(c.getFora() + 1);
                    case Estados.PENDENTE -> c.setPendente(c.getPendente() + 1);
                    case Estados.MANUTENCAO -> c.setManutencao(c.getManutencao() + 1);
                    default -> {
                    }
                }
            }
            if (m.getCertDias() != null && m.getCertDias() < 30) {
                c.setCertificadoVencendo(c.getCertificadoVencendo() + 1);
            }
        }
        return c;
    }

    public MonitorVOs.Detalhe detalhe(Long id) {
        MonitorDO m = existente(id);
        MonitorVOs.Detalhe d = new MonitorVOs.Detalhe();
        d.setConfig(config(m));
        d.setEstado(converter(List.of(m)).get(0));
        d.setEventos(eventos(List.of(m), LocalDateTime.now().minusDays(30), 100));
        Map<Long, double[]> ano = disponibilidade(List.of(id), LocalDateTime.now().minusDays(365));
        d.setUptime1a(ano.containsKey(id) ? ano.get(id)[0] : null);
        if (m.getPushToken() != null) {
            String base = urlPublica == null ? "" : urlPublica.replaceAll("/+$", "");
            d.setPushUrl(base + "/api/push/" + m.getPushToken() + "?status=up&msg=OK&ping=");
        }
        d.setManutencoes(manutencaoService.valendoPara(m));
        return d;
    }

    /** Mudanças de estado (com quanto tempo durou o estado anterior), mais recente primeiro. */
    public List<MonitorVOs.Evento> eventos(Collection<MonitorDO> monitores, LocalDateTime desde, int limite) {
        if (monitores.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nomes = monitores.stream().collect(Collectors.toMap(MonitorDO::getId, MonitorDO::getNome, (a, b) -> a));
        List<BatidaDO> lista = monMonitorMapper.eventos(nomes.size() == 1 ? nomes.keySet().iterator().next() : null,
                nomes.size() == 1 ? null : nomes.keySet(), desde, limite);
        // a duração do estado anterior é a distância até o evento anterior do mesmo monitor
        Map<Long, List<BatidaDO>> porMonitor = lista.stream().collect(Collectors.groupingBy(BatidaDO::getMonitorId));
        Map<BatidaDO, Long> duracoes = new IdentityHashMap<>();
        porMonitor.values().forEach(l -> {
            for (int i = 0; i + 1 < l.size(); i++) {
                duracoes.put(l.get(i), Duration.between(l.get(i + 1).getMomento(), l.get(i).getMomento()).getSeconds());
            }
        });
        return lista.stream().map(b -> {
            MonitorVOs.Evento e = new MonitorVOs.Evento();
            e.setMonitorId(b.getMonitorId());
            e.setMonitorNome(nomes.get(b.getMonitorId()));
            e.setMomento(b.getMomento());
            e.setEstado(b.getEstado());
            e.setMsg(b.getMsg());
            e.setDuracaoAnteriorS(duracoes.get(b));
            return e;
        }).toList();
    }

    /** Latência e falhas no período (24h, 7d, 30d ou 3h), agrupadas para o gráfico. */
    public List<MonitorVOs.Ponto> serie(Long id, String periodo) {
        existente(id);
        LocalDateTime agora = LocalDateTime.now();
        String[] p = switch (Optional.ofNullable(periodo).orElse("24h")) {
            case "3h" -> new String[]{"3", "1 minute"};
            case "7d" -> new String[]{"168", "1 hour"};
            case "30d" -> new String[]{"720", "6 hours"};
            default -> new String[]{"24", "10 minutes"};
        };
        return monMonitorMapper.serie(id, agora.minusHours(Long.parseLong(p[0])), p[1]).stream().map(r -> {
            MonitorVOs.Ponto pt = new MonitorVOs.Ponto();
            Object t = r.get("momento");
            pt.setMomento(t instanceof java.sql.Timestamp ts ? ts.toLocalDateTime() : t instanceof LocalDateTime l ? l : null);
            pt.setMedia(inteiro(r.get("media")));
            pt.setMinima(inteiro(r.get("minima")));
            pt.setMaxima(inteiro(r.get("maxima")));
            pt.setFalhas(inteiro(r.get("falhas")));
            pt.setTotal(inteiro(r.get("total")));
            return pt;
        }).toList();
    }

    /** Estado, últimas batidas e disponibilidade dos monitores (para a lista e para o Centro de Operações). */
    public List<MonitorVOs.Resp> respostas(List<MonitorDO> lista) {
        return converter(lista);
    }

    /** Disponibilidade das últimas 24 h por monitor (%), só dos que têm verificações. */
    public Map<Long, Double> uptime24h(Collection<Long> ids) {
        Map<Long, Double> out = new HashMap<>();
        disponibilidade(ids, LocalDateTime.now().minusHours(24)).forEach((k, v) -> out.put(k, v[0]));
        return out;
    }

    List<MonitorVOs.Resp> converter(List<MonitorDO> lista) {
        if (lista.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = lista.stream().map(MonitorDO::getId).toList();
        Map<Long, List<BatidaDO>> historico = monMonitorMapper.ultimasDeVarios(ids, HISTORICO).stream()
                .collect(Collectors.groupingBy(BatidaDO::getMonitorId));
        LocalDateTime agora = LocalDateTime.now();
        Map<Long, double[]> dia = disponibilidade(ids, agora.minusHours(24));
        Map<Long, double[]> mes = disponibilidade(ids, agora.minusDays(30));
        Map<Long, String> unidades = monitorUnitMapper.selectList().stream().collect(Collectors.toMap(MonitorUnitDO::getId, MonitorUnitDO::getName));
        Set<Long> manutencao = manutencaoService.monitoresEmManutencao();
        List<MonitorVOs.Resp> out = new ArrayList<>();
        for (MonitorDO m : lista) {
            MonitorVOs.Resp r = new MonitorVOs.Resp();
            r.setId(m.getId());
            r.setNome(m.getNome());
            r.setDescricao(m.getDescricao());
            r.setTipo(m.getTipo());
            r.setAlvo(m.getAlvo());
            r.setPorta(m.getPorta());
            r.setUnidadeId(m.getUnidadeId());
            r.setUnidadeNome(m.getUnidadeId() == null ? null : unidades.get(m.getUnidadeId()));
            r.setPapel(m.getPapel());
            r.setAtivo(m.getAtivo());
            r.setIntervaloS(m.getIntervaloS());
            r.setEstado(m.getEstado());
            r.setLento(lento(m));
            r.setEstadoDesde(m.getEstadoDesde());
            r.setUltimaVerificacao(m.getUltimaVerificacao());
            r.setUltimaLatenciaMs(m.getUltimaLatenciaMs());
            r.setUltimaMsg(m.getUltimaMsg());
            r.setCertDias(m.getCertDias());
            r.setChamadoId(m.getChamadoId());
            r.setEmManutencao(manutencao.contains(m.getId()));
            List<BatidaDO> h = new ArrayList<>(historico.getOrDefault(m.getId(), List.of()));
            Collections.reverse(h);
            r.setHistorico(h.stream().map(MonitorService::batida).toList());
            double[] d = dia.get(m.getId());
            r.setUptime24h(d == null ? null : d[0]);
            r.setLatenciaMedia24h(d == null || d[1] < 0 ? null : (int) Math.round(d[1]));
            double[] mm = mes.get(m.getId());
            r.setUptime30d(mm == null ? null : mm[0]);
            out.add(r);
        }
        return out;
    }

    /** monitorId → [disponibilidade %, latência média (ou -1)] desde o momento. */
    Map<Long, double[]> disponibilidade(Collection<Long> ids, LocalDateTime desde) {
        Map<Long, double[]> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Map<String, Object> r : monMonitorMapper.totais(ids, desde)) {
            long noAr = numero(r.get("no_ar"));
            long fora = numero(r.get("fora"));
            long nLat = numero(r.get("n_latencia"));
            if (noAr + fora == 0) {
                continue;
            }
            out.put(numero(r.get("monitor_id")), new double[]{Math.round(10000.0 * noAr / (noAr + fora)) / 100.0,
                    nLat == 0 ? -1 : (double) numero(r.get("soma_latencia")) / nLat});
        }
        return out;
    }

    public MonitorVOs.Monitor config(MonitorDO m) {
        MonitorVOs.Monitor vo = new MonitorVOs.Monitor();
        vo.setId(m.getId());
        vo.setNome(m.getNome());
        vo.setDescricao(m.getDescricao());
        vo.setTipo(m.getTipo());
        vo.setAlvo(m.getAlvo());
        vo.setPorta(m.getPorta());
        vo.setMetodo(m.getMetodo());
        vo.setCorpo(m.getCorpo());
        vo.setCabecalhos(m.getCabecalhos());
        vo.setCodigosAceitos(m.getCodigosAceitos());
        vo.setPalavra(m.getPalavra());
        vo.setPalavraInvertida(m.getPalavraInvertida());
        vo.setJsonCaminho(m.getJsonCaminho());
        vo.setJsonOperador(m.getJsonOperador());
        vo.setJsonEsperado(m.getJsonEsperado());
        vo.setDnsTipo(m.getDnsTipo());
        vo.setDnsServidor(m.getDnsServidor());
        vo.setDnsEsperado(m.getDnsEsperado());
        vo.setIntervaloS(m.getIntervaloS());
        vo.setTentativas(m.getTentativas());
        vo.setIntervaloTentativaS(m.getIntervaloTentativaS());
        vo.setTempoLimiteS(m.getTempoLimiteS());
        vo.setReenviarACada(m.getReenviarACada());
        vo.setInvertido(m.getInvertido());
        vo.setIgnorarTls(m.getIgnorarTls());
        vo.setMaxRedirecionamentos(m.getMaxRedirecionamentos());
        vo.setLentoMs(m.getLentoMs());
        vo.setCertAvisoDias(m.getCertAvisoDias());
        vo.setUnidadeId(m.getUnidadeId());
        vo.setPapel(m.getPapel());
        vo.setDeptId(m.getDeptId());
        vo.setAbrirChamado(m.getAbrirChamado());
        vo.setChamadoCategoriaId(m.getChamadoCategoriaId());
        vo.setChamadoUrgencia(m.getChamadoUrgencia());
        vo.setChamadoSolucionar(m.getChamadoSolucionar());
        vo.setAtivo(m.getAtivo());
        vo.setPushToken(m.getPushToken());
        vo.setKumaId(m.getKumaId());
        vo.setCanalIds(monMonitorCanalMapper.selectList(MonitorCanalDO::getMonitorId, m.getId()).stream().map(MonitorCanalDO::getCanalId).toList());
        return vo;
    }

    public MonitorDO existente(Long id) {
        MonitorDO m = monMonitorMapper.selectById(id);
        if (m == null) {
            throw exception(MONITOR_NAO_EXISTE);
        }
        return m;
    }

    static boolean lento(MonitorDO m) {
        return Objects.equals(m.getEstado(), Estados.NO_AR) && m.getLentoMs() != null && m.getUltimaLatenciaMs() != null
                && m.getUltimaLatenciaMs() > m.getLentoMs();
    }

    private static MonitorVOs.Batida batida(BatidaDO b) {
        MonitorVOs.Batida vo = new MonitorVOs.Batida();
        vo.setMomento(b.getMomento());
        vo.setEstado(b.getEstado());
        vo.setLatenciaMs(b.getLatenciaMs());
        vo.setMsg(b.getMsg());
        vo.setImportante(b.getImportante());
        return vo;
    }

    /** "45 s", "12 min", "3 h 5 min", "2 d 4 h". */
    public static String duracao(long segundos) {
        long s = Math.max(0, segundos);
        if (s < 60) {
            return s + " s";
        }
        long min = s / 60;
        if (min < 60) {
            return min + " min";
        }
        long h = min / 60;
        if (h < 24) {
            return h + " h" + (min % 60 == 0 ? "" : " " + (min % 60) + " min");
        }
        return (h / 24) + " d" + (h % 24 == 0 ? "" : " " + (h % 24) + " h");
    }

    private static void depoisDoCommit(Runnable r) {
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

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static long numero(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static Integer inteiro(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

}
