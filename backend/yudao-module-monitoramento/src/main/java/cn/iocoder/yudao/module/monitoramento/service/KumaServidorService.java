package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.KumaVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.KumaServidorDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonKumaServidorMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.service.verificador.ClienteKuma;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Uptime Kuma acompanhado ao vivo: o Kuma continua verificando e cada monitor do tipo "kuma" no CO-TI espelha o
 * estado que ele informa no /metrics (telão, avisos, chamado automático e histórico ficam no CO-TI).
 */
@Service
public class KumaServidorService {

    @Resource
    private MonKumaServidorMapper monKumaServidorMapper;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private SegredosMonitor segredosMonitor;
    @Resource
    private ClienteKuma clienteKuma;
    @Resource
    private MonitorService monitorService;
    @Resource
    private AuditoriaService auditoriaService;

    public List<KumaVOs.Servidor> listar() {
        Map<Long, Long> uso = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().isNotNull(MonitorDO::getKumaServidorId)).stream()
                .collect(Collectors.groupingBy(MonitorDO::getKumaServidorId, Collectors.counting()));
        return monKumaServidorMapper.selectList(new LambdaQueryWrapperX<KumaServidorDO>().orderByAsc(KumaServidorDO::getNome)).stream().map(s -> {
            KumaVOs.Servidor vo = new KumaVOs.Servidor();
            vo.setId(s.getId());
            vo.setNome(s.getNome());
            vo.setUrl(s.getUrl());
            vo.setUsuario(s.getUsuario());
            vo.setIgnorarTls(s.getIgnorarTls());
            vo.setChaveDefinida(s.getChave() != null);
            vo.setMonitores(uso.getOrDefault(s.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(KumaVOs.Servidor req) {
        KumaServidorDO atual = req.getId() == null ? null : existente(req.getId());
        KumaServidorDO s = montar(req, atual, false);
        if (atual == null) {
            monKumaServidorMapper.insert(s);
        } else {
            monKumaServidorMapper.updateById(s);
            monKumaServidorMapper.update(null, new LambdaUpdateWrapper<KumaServidorDO>().eq(KumaServidorDO::getId, s.getId())
                    .set(KumaServidorDO::getUsuario, s.getUsuario()).set(KumaServidorDO::getChave, s.getChave()));
        }
        clienteKuma.esquecer(s.getId());
        return s.getId();
    }

    /** Lê o /metrics sem salvar; com id, vale a chave guardada. @return quantos monitores o Kuma informa */
    public String testar(KumaVOs.Servidor req) {
        KumaServidorDO atual = req.getId() == null ? null : existente(req.getId());
        KumaServidorDO s = montar(req, atual, true);
        s.setId(null); // não usa nem troca a leitura guardada
        try {
            List<ClienteKuma.MonitorKuma> l = clienteKuma.monitores(s, true);
            long fora = l.stream().filter(k -> Integer.valueOf(0).equals(k.estado())).count();
            return l.size() + " monitores ativos no Kuma" + (fora > 0 ? ", " + fora + " fora do ar agora" : "");
        } catch (IllegalStateException e) {
            throw exception(KUMA_SERVIDOR_INVALIDO, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        existente(id);
        long usados = monMonitorMapper.selectCount(MonitorDO::getKumaServidorId, id);
        if (usados > 0) {
            throw exception(KUMA_SERVIDOR_EM_USO, usados);
        }
        monKumaServidorMapper.deleteById(id);
        clienteKuma.esquecer(id);
    }

    // ================================================================================== sincronizar

    /** O que há no Kuma e o que o CO-TI já acompanha; nada é gravado. */
    public KumaVOs.PreviaAoVivo previa(Long servidorId) {
        KumaServidorDO s = existente(servidorId);
        List<ClienteKuma.MonitorKuma> lista;
        try {
            lista = clienteKuma.monitores(s, true);
        } catch (IllegalStateException e) {
            throw exception(KUMA_SERVIDOR_INVALIDO, e.getMessage());
        }
        KumaVOs.PreviaAoVivo p = new KumaVOs.PreviaAoVivo();
        Map<String, MonitorDO> acompanhados = new HashMap<>();
        List<MonitorDO> sumidos = new ArrayList<>();
        for (MonitorDO m : acompanhados(servidorId)) {
            ClienteKuma.MonitorKuma k = ClienteKuma.achar(lista, m.getConsulta());
            if (k == null) {
                sumidos.add(m);
            } else {
                acompanhados.putIfAbsent(chave(k), m);
            }
        }
        for (ClienteKuma.MonitorKuma k : lista) {
            KumaVOs.ItemAoVivo i = new KumaVOs.ItemAoVivo();
            i.setChave(chave(k));
            i.setNome(k.nome());
            i.setTipoKuma(k.tipo());
            i.setAlvo(k.alvo());
            i.setEstado(k.estado());
            MonitorDO m = acompanhados.get(chave(k));
            if (m == null) {
                i.setSituacao("novo");
            } else {
                i.setMonitorId(m.getId());
                i.setMonitorNome(m.getNome());
                String mudanca = mudanca(m, k);
                i.setSituacao(mudanca == null ? "igual" : "muda");
                i.setMudanca(mudanca);
            }
            p.getItens().add(i);
        }
        for (MonitorDO m : sumidos) {
            KumaVOs.ItemAoVivo i = new KumaVOs.ItemAoVivo();
            i.setChave(m.getConsulta());
            i.setNome(m.getNome());
            i.setAlvo(m.getAlvo());
            i.setSituacao("sumiu");
            i.setMonitorId(m.getId());
            i.setMonitorNome(m.getNome());
            i.setMudanca("não aparece no Kuma: pausado ou excluído lá");
            p.getItens().add(i);
        }
        return p;
    }

    public KumaVOs.SincronizarResultado sincronizar(KumaVOs.SincronizarReq req, Long userId) {
        KumaServidorDO s = existente(req.getServidorId());
        List<ClienteKuma.MonitorKuma> lista;
        try {
            lista = clienteKuma.monitores(s, true);
        } catch (IllegalStateException e) {
            throw exception(KUMA_SERVIDOR_INVALIDO, e.getMessage());
        }
        Map<Long, MonitorDO> deste = acompanhados(s.getId()).stream().collect(Collectors.toMap(MonitorDO::getId, m -> m));
        Set<String> jaAcompanhados = new HashSet<>();
        deste.values().forEach(m -> Optional.ofNullable(ClienteKuma.achar(lista, m.getConsulta())).ifPresent(k -> jaAcompanhados.add(chave(k))));

        List<MonitorVOs.Monitor> gravar = new ArrayList<>();
        KumaVOs.SincronizarResultado res = new KumaVOs.SincronizarResultado();
        for (String c : new LinkedHashSet<>(Optional.ofNullable(req.getAdicionar()).orElse(List.of()))) {
            ClienteKuma.MonitorKuma k = ClienteKuma.achar(lista, c);
            if (k == null || !jaAcompanhados.add(chave(k))) {
                continue; // sumiu entre a prévia e a confirmação, ou já acompanhado
            }
            MonitorVOs.Monitor vo = new MonitorVOs.Monitor();
            vo.setNome(cortar(Optional.ofNullable(k.nome()).filter(n -> !n.isBlank()).orElse("Kuma " + chave(k)), 150));
            vo.setDescricao("Estado vindo do Uptime Kuma \"" + s.getNome() + "\"" + (k.tipo() == null ? "" : " (tipo " + k.tipo() + " lá)"));
            vo.setTipo("kuma");
            vo.setKumaServidorId(s.getId());
            vo.setConsulta(chave(k));
            vo.setAlvo(cortar(k.alvo(), 1000));
            vo.setIntervaloS(Optional.ofNullable(req.getIntervaloS()).orElse(30));
            vo.setTentativas(0);
            vo.setUnidadeId(req.getUnidadeId());
            vo.setAbrirChamado(Boolean.TRUE.equals(req.getAbrirChamado()));
            vo.setCertAvisoDias(0); // o próprio Kuma já avisa do certificado; ligue no monitor se quiser o aviso aqui
            vo.setAtivo(true);
            gravar.add(vo);
            res.setAdicionados(res.getAdicionados() + 1);
        }
        for (Long id : new LinkedHashSet<>(Optional.ofNullable(req.getAtualizar()).orElse(List.of()))) {
            MonitorDO m = deste.get(id);
            ClienteKuma.MonitorKuma k = m == null ? null : ClienteKuma.achar(lista, m.getConsulta());
            if (k == null || mudanca(m, k) == null) {
                continue;
            }
            MonitorVOs.Monitor vo = monitorService.semCanais(m);
            if (k.nome() != null && !k.nome().isBlank()) {
                vo.setNome(cortar(k.nome(), 150));
            }
            vo.setAlvo(cortar(k.alvo(), 1000));
            gravar.add(vo);
            res.setAtualizados(res.getAtualizados() + 1);
        }
        List<Long> remover = Optional.ofNullable(req.getRemover()).orElse(List.of()).stream()
                .filter(id -> deste.containsKey(id) && ClienteKuma.achar(lista, deste.get(id).getConsulta()) == null).distinct().toList();
        res.setRemovidos(remover.size());
        monitorService.aplicarImportacao(gravar, remover);
        auditoriaService.registrar("monitoramento.sincronizar_kuma", userId, null, Map.of("servidor", s.getNome(),
                "adicionados", res.getAdicionados(), "atualizados", res.getAtualizados(), "removidos", res.getRemovidos()));
        return res;
    }

    // ================================================================================== apoio

    private List<MonitorDO> acompanhados(Long servidorId) {
        return monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().eq(MonitorDO::getKumaServidorId, servidorId)
                .eq(MonitorDO::getTipo, "kuma").orderByAsc(MonitorDO::getId));
    }

    private static String chave(ClienteKuma.MonitorKuma k) {
        return k.id() != null ? k.id() : k.nome();
    }

    /** O que mudou no Kuma desde que o monitor foi criado; null = nada. */
    private static String mudanca(MonitorDO m, ClienteKuma.MonitorKuma k) {
        List<String> l = new ArrayList<>();
        if (k.id() != null && k.nome() != null && !k.nome().isBlank() && !k.nome().trim().equals(m.getNome())) {
            l.add("nome: " + m.getNome() + " → " + k.nome());
        }
        String alvo = cortar(k.alvo(), 1000);
        if (!Objects.equals(vazio(alvo), vazio(m.getAlvo()))) {
            l.add("endereço: " + Optional.ofNullable(m.getAlvo()).orElse("—") + " → " + Optional.ofNullable(alvo).orElse("—"));
        }
        return l.isEmpty() ? null : String.join("; ", l);
    }

    private KumaServidorDO existente(Long id) {
        KumaServidorDO s = monKumaServidorMapper.selectById(id);
        if (s == null) {
            throw exception(KUMA_SERVIDOR_NAO_EXISTE);
        }
        return s;
    }

    /** @param emClaro para testar sem salvar: a chave digitada vai em claro, só na memória */
    private KumaServidorDO montar(KumaVOs.Servidor r, KumaServidorDO atual, boolean emClaro) {
        String url = r.getUrl() == null ? "" : r.getUrl().trim().replaceAll("/+$", "");
        if (url.toLowerCase(Locale.ROOT).endsWith("/metrics")) {
            url = url.substring(0, url.length() - "/metrics".length());
        }
        if (!url.matches("(?i)https?://[^\\s/$.?#][^\\s]*")) {
            throw exception(KUMA_SERVIDOR_INVALIDO, "use o endereço do Kuma, ex.: https://kuma.orgao.gov.br ou http://10.0.0.5:3001");
        }
        String nova = r.getChave() == null || r.getChave().isBlank() ? null : r.getChave().trim();
        String chave = emClaro && nova != null ? SegredosMonitor.CLARO + nova
                : segredosMonitor.chaveKuma(atual == null ? null : atual.getChave(), nova);
        if (chave == null) {
            throw exception(KUMA_SERVIDOR_INVALIDO, "informe a chave de API do Kuma (Configurações → Chaves de API)");
        }
        return KumaServidorDO.builder().id(r.getId()).nome(r.getNome().trim()).url(url).usuario(vazio(r.getUsuario())).chave(chave)
                .ignorarTls(Boolean.TRUE.equals(r.getIgnorarTls())).build();
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
