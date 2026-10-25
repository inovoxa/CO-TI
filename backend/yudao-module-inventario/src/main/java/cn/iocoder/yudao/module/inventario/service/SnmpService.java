package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.SnmpVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.RedeDispositivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.SnmpAlvoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.SnmpCredencialDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbRedeDispositivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbSnmpAlvoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbSnmpCredencialMapper;
import cn.iocoder.yudao.module.inventario.dominio.Normalizacao;
import cn.iocoder.yudao.module.inventario.dominio.SnmpLeitura;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.*;

/**
 * Inventário por SNMP do que não tem agente: impressoras, switches, roteadores, nobreaks. Os alvos são os IPs que a
 * descoberta de rede achou (menos as máquinas com agente) e os ativos cadastrados com IP. As credenciais são tentadas
 * em ordem; a que respondeu fica lembrada para o IP.
 */
@Service
@Slf4j
public class SnmpService {

    public static final String CONTEXTO = "inventario-snmp";
    static final int PARALELO = 16;

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private CmdbSnmpCredencialMapper cmdbSnmpCredencialMapper;
    @Resource
    private CmdbSnmpAlvoMapper cmdbSnmpAlvoMapper;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbRedeDispositivoMapper cmdbRedeDispositivoMapper;
    @Resource
    private InventarioAgenteService inventarioAgenteService;
    @Resource
    private CifradorSegredos cifradorSegredos;

    private volatile boolean varrendo;

    // ======================================================================================= credenciais

    public List<SnmpVOs.Credencial> credenciais() {
        return cmdbSnmpCredencialMapper.selectList(new LambdaQueryWrapperX<SnmpCredencialDO>()
                .orderByAsc(SnmpCredencialDO::getOrdem).orderByAsc(SnmpCredencialDO::getId)).stream().map(c -> {
            SnmpVOs.Credencial vo = new SnmpVOs.Credencial();
            vo.setId(c.getId());
            vo.setNome(c.getNome());
            vo.setVersao(c.getVersao());
            vo.setUsuario(c.getUsuario());
            vo.setNivel(c.getNivel());
            vo.setAuthProtocolo(c.getAuthProtocolo());
            vo.setPrivProtocolo(c.getPrivProtocolo());
            vo.setPorta(c.getPorta());
            vo.setOrdem(c.getOrdem());
            vo.setAtivo(c.getAtivo());
            // segredos nunca voltam: só se estão definidos
            vo.setComunidadeDefinida(c.getComunidadeCifrada() != null);
            vo.setAuthSenhaDefinida(c.getAuthSenhaCifrada() != null);
            vo.setPrivSenhaDefinida(c.getPrivSenhaCifrada() != null);
            return vo;
        }).toList();
    }

    public Long salvarCredencial(SnmpVOs.Credencial req) {
        if (!cifradorSegredos.configurado()) {
            throw exception(SNMP_SEM_CHAVE_MESTRA);
        }
        SnmpCredencialDO atual = req.getId() == null ? null : cmdbSnmpCredencialMapper.selectById(req.getId());
        if (req.getId() != null && atual == null) {
            throw exception(SNMP_CREDENCIAL_NAO_EXISTE);
        }
        SnmpCredencialDO c = SnmpCredencialDO.builder().id(req.getId()).nome(req.getNome().strip()).versao(req.getVersao())
                .porta(req.getPorta() == null ? 161 : req.getPorta()).ordem(req.getOrdem() == null ? 0 : req.getOrdem())
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        if ("v2c".equals(req.getVersao())) {
            String comunidade = vazio(req.getComunidade());
            if (comunidade == null && (atual == null || atual.getComunidadeCifrada() == null)) {
                throw exception(SNMP_CREDENCIAL_INCOMPLETA, "informe a comunidade");
            }
            if (comunidade != null) {
                c.setComunidadeCifrada(cifradorSegredos.cifrar(comunidade, CONTEXTO));
            }
        } else if ("v3".equals(req.getVersao())) {
            if (vazio(req.getUsuario()) == null) {
                throw exception(SNMP_CREDENCIAL_INCOMPLETA, "informe o usuário v3");
            }
            String nivel = Optional.ofNullable(req.getNivel()).orElse("authPriv");
            if (!Set.of("noAuthNoPriv", "authNoPriv", "authPriv").contains(nivel)) {
                throw exception(SNMP_CREDENCIAL_INCOMPLETA, "nível de segurança inválido");
            }
            c.setUsuario(req.getUsuario().strip());
            c.setNivel(nivel);
            c.setAuthProtocolo(Optional.ofNullable(req.getAuthProtocolo()).orElse("SHA"));
            c.setPrivProtocolo(Optional.ofNullable(req.getPrivProtocolo()).orElse("AES"));
            if (!"noAuthNoPriv".equals(nivel)) {
                c.setAuthSenhaCifrada(senha(req.getAuthSenha(), atual == null ? null : atual.getAuthSenhaCifrada(), "senha de autenticação"));
            }
            if ("authPriv".equals(nivel)) {
                c.setPrivSenhaCifrada(senha(req.getPrivSenha(), atual == null ? null : atual.getPrivSenhaCifrada(), "senha de privacidade"));
            }
        } else {
            throw exception(SNMP_CREDENCIAL_INCOMPLETA, "versão deve ser v2c ou v3");
        }
        if (c.getId() == null) {
            cmdbSnmpCredencialMapper.insert(c);
        } else {
            cmdbSnmpCredencialMapper.updateById(c);
        }
        return c.getId();
    }

    /** v3: senha nova (mínimo 8, exigência do USM) ou a que já estava guardada. */
    private String senha(String nova, String guardada, String rotulo) {
        String s = vazio(nova);
        if (s == null) {
            if (guardada == null) {
                throw exception(SNMP_CREDENCIAL_INCOMPLETA, "informe a " + rotulo);
            }
            return guardada;
        }
        if (s.length() < 8) {
            throw exception(SNMP_CREDENCIAL_INCOMPLETA, "a " + rotulo + " precisa de pelo menos 8 caracteres");
        }
        return cifradorSegredos.cifrar(s, CONTEXTO);
    }

    public void excluirCredencial(Long id) {
        cmdbSnmpCredencialMapper.deleteById(id);
    }

    private SnmpCliente.Credencial decifrar(SnmpCredencialDO c) {
        return new SnmpCliente.Credencial(c.getVersao(),
                c.getComunidadeCifrada() == null ? null : cifradorSegredos.decifrar(c.getComunidadeCifrada(), CONTEXTO),
                c.getUsuario(), c.getNivel(), c.getAuthProtocolo(),
                c.getAuthSenhaCifrada() == null ? null : cifradorSegredos.decifrar(c.getAuthSenhaCifrada(), CONTEXTO),
                c.getPrivProtocolo(), c.getPrivSenhaCifrada() == null ? null : cifradorSegredos.decifrar(c.getPrivSenhaCifrada(), CONTEXTO),
                c.getPorta() == null ? 161 : c.getPorta());
    }

    // ======================================================================================= varredura

    /** Consulta um IP agora (botão "Testar" da tela). Grava o ativo se responder. */
    @DataPermission(enable = false)
    public SnmpVOs.Resultado consultar(String ip) {
        if (!RedeService.ipValido(ip)) {
            throw exception(SNMP_IP_INVALIDO);
        }
        List<SnmpCredencialDO> creds = ativas();
        if (creds.isEmpty()) {
            throw exception(SNMP_SEM_CREDENCIAL);
        }
        try (SnmpCliente cliente = new SnmpCliente(inventarioProperties.getSnmpTempoLimiteMs(), 1)) {
            return consultar(cliente, ip, creds);
        } catch (java.io.IOException e) {
            throw exception(SNMP_FALHA, e.getMessage());
        }
    }

    /** Varre todos os alvos. Uma varredura por vez; devolve o resumo. */
    @DataPermission(enable = false)
    public SnmpVOs.Varredura varrer() {
        if (varrendo) {
            throw exception(SNMP_VARRENDO);
        }
        varrendo = true;
        SnmpVOs.Varredura res = new SnmpVOs.Varredura();
        long ini = System.currentTimeMillis();
        try {
            List<SnmpCredencialDO> creds = ativas();
            Set<String> alvos = alvos();
            res.setAlvos(alvos.size());
            if (creds.isEmpty() || alvos.isEmpty()) {
                return res;
            }
            ExecutorService pool = Executors.newFixedThreadPool(PARALELO);
            try (SnmpCliente cliente = new SnmpCliente(inventarioProperties.getSnmpTempoLimiteMs(), 1)) {
                List<Future<SnmpVOs.Resultado>> fs = new ArrayList<>();
                for (String ip : alvos) {
                    fs.add(pool.submit(() -> consultar(cliente, ip, creds)));
                }
                for (Future<SnmpVOs.Resultado> f : fs) {
                    try {
                        SnmpVOs.Resultado r = f.get(5, TimeUnit.MINUTES);
                        if (r.isRespondeu()) {
                            res.setResponderam(res.getResponderam() + 1);
                            if (r.getAtivoId() != null) {
                                res.setGravados(res.getGravados() + 1);
                            }
                        }
                    } catch (ExecutionException | TimeoutException e) {
                        log.warn("[snmp] falha numa consulta: {}", e.getMessage());
                    }
                }
            } finally {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.io.IOException e) {
            throw exception(SNMP_FALHA, e.getMessage());
        } finally {
            varrendo = false;
            res.setDuracaoMs(System.currentTimeMillis() - ini);
        }
        log.info("[snmp] varredura: {} alvos, {} responderam, {} gravados em {} ms", res.getAlvos(), res.getResponderam(), res.getGravados(),
                res.getDuracaoMs());
        return res;
    }

    private List<SnmpCredencialDO> ativas() {
        return cmdbSnmpCredencialMapper.selectList(new LambdaQueryWrapperX<SnmpCredencialDO>().eq(SnmpCredencialDO::getAtivo, true)
                .orderByAsc(SnmpCredencialDO::getOrdem).orderByAsc(SnmpCredencialDO::getId));
    }

    /**
     * IPs a consultar: o que a descoberta achou (sem as máquinas com agente e sem os ignorados) e os ativos manuais ou
     * SNMP com IP.
     */
    Set<String> alvos() {
        Set<Long> comAgente = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().in(AtivoDO::getOrigem, "ocs", "glpi_agent"))
                .stream().map(AtivoDO::getId).collect(Collectors.toSet());
        Set<String> l = new TreeSet<>();
        for (RedeDispositivoDO d : cmdbRedeDispositivoMapper.selectList(new LambdaQueryWrapperX<RedeDispositivoDO>()
                .eq(RedeDispositivoDO::getIgnorado, false))) {
            if (d.getAtivoId() == null || !comAgente.contains(d.getAtivoId())) {
                l.add(d.getIp());
            }
        }
        for (AtivoDO a : cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().in(AtivoDO::getOrigem, "manual", "snmp")
                .ne(AtivoDO::getSituacao, "baixado").isNotNull(AtivoDO::getIp))) {
            if (RedeService.ipValido(a.getIp())) {
                l.add(a.getIp());
            }
        }
        return l;
    }

    private SnmpVOs.Resultado consultar(SnmpCliente cliente, String ip, List<SnmpCredencialDO> creds) {
        SnmpVOs.Resultado res = new SnmpVOs.Resultado();
        res.setIp(ip);
        SnmpAlvoDO alvo = cmdbSnmpAlvoMapper.selectOne(SnmpAlvoDO::getIp, ip);
        // a credencial que respondeu da última vez primeiro
        List<SnmpCredencialDO> ordem = new ArrayList<>(creds);
        if (alvo != null && alvo.getCredencialId() != null) {
            ordem.sort(Comparator.comparing(c -> !c.getId().equals(alvo.getCredencialId())));
        }
        LocalDateTime agora = LocalDateTime.now();
        SnmpLeitura.Dados dados = null;
        SnmpCredencialDO usada = null;
        String erro = null;
        for (SnmpCredencialDO c : ordem) {
            try {
                dados = cliente.ler(ip, decifrar(c));
            } catch (Exception e) {
                erro = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (dados != null) {
                usada = c;
                break;
            }
        }
        Long ativoId = null;
        if (dados != null) {
            res.setRespondeu(true);
            res.setCredencial(usada.getNome());
            SnmpLeitura.Resultado r = SnmpLeitura.interpretar(ip, dados);
            res.setNome(r.resumo().nome());
            res.setTipo(r.resumo().tipo());
            res.setFabricante(r.resumo().fabricante());
            res.setModelo(r.resumo().modelo());
            res.setNumeroSerie(r.resumo().numeroSerie());
            res.setDescricao(r.descricao());
            res.setPaginas(r.paginas());
            res.setSuprimentos(r.suprimentos().stream().map(s -> s.descricao() + (s.percentual() == null ? "" : ": " + s.percentual() + "%")).toList());
            res.setInterfaces(r.interfaces().size());
            try {
                ativoId = gravar(ip, r);
                res.setAtivoId(ativoId);
            } catch (RuntimeException e) {
                log.warn("[snmp] {} respondeu mas não foi gravado: {}", ip, e.getMessage());
                erro = "não gravado: " + e.getMessage();
            }
        } else if (erro == null) {
            erro = "sem resposta (nenhuma credencial funcionou)";
        }
        SnmpAlvoDO m = SnmpAlvoDO.builder().ip(ip).ultimaTentativa(agora).erro(dados == null || erro != null ? cortar(erro, 255) : null).build();
        if (dados != null) {
            m.setCredencialId(usada.getId());
            m.setUltimaResposta(agora);
            m.setAtivoId(ativoId);
        }
        if (alvo == null) {
            try {
                cmdbSnmpAlvoMapper.insert(m);
            } catch (org.springframework.dao.DuplicateKeyException ignorado) {
                // outra thread gravou o mesmo IP
            }
        } else {
            m.setId(alvo.getId());
            cmdbSnmpAlvoMapper.updateById(m);
            if (dados != null && erro == null) {
                cmdbSnmpAlvoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SnmpAlvoDO>()
                        .set(SnmpAlvoDO::getErro, null).eq(SnmpAlvoDO::getId, alvo.getId()));
            }
        }
        res.setErro(dados == null ? erro : null);
        return res;
    }

    /**
     * Grava pelo mesmo caminho dos agentes. Um ativo que já existe para este IP/MAC (cadastrado pela descoberta, à mão
     * ou numa varredura anterior) é atualizado; máquina com agente nunca é sobrescrita pelo SNMP.
     */
    Long gravar(String ip, SnmpLeitura.Resultado r) {
        String mac = r.resumo().mac();
        AtivoDO existente = null;
        SnmpAlvoDO alvo = cmdbSnmpAlvoMapper.selectOne(SnmpAlvoDO::getIp, ip);
        if (alvo != null && alvo.getAtivoId() != null) {
            existente = cmdbAtivoMapper.selectById(alvo.getAtivoId());
        }
        if (existente == null && mac != null) {
            existente = cmdbAtivoMapper.selectPorMac(mac);
        }
        RedeDispositivoDO descoberto = mac == null ? null : cmdbRedeDispositivoMapper.selectOne(RedeDispositivoDO::getMac, mac);
        if (descoberto == null) {
            descoberto = cmdbRedeDispositivoMapper.selectOne(new LambdaQueryWrapperX<RedeDispositivoDO>().eq(RedeDispositivoDO::getIp, ip)
                    .orderByDesc(RedeDispositivoDO::getUltimaVez).last("LIMIT 1"));
        }
        if (existente == null && descoberto != null && descoberto.getAtivoId() != null) {
            existente = cmdbAtivoMapper.selectById(descoberto.getAtivoId());
        }
        if (existente == null) {
            existente = cmdbAtivoMapper.selectOne(new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getIp, ip).in(AtivoDO::getOrigem, "manual", "snmp")
                    .ne(AtivoDO::getSituacao, "baixado").last("LIMIT 1"));
        }
        if (existente != null && Set.of("ocs", "glpi_agent").contains(existente.getOrigem())) {
            return null;
        }
        String deviceId = "snmp:" + Optional.ofNullable(r.resumo().numeroSerie()).or(() -> Optional.ofNullable(mac)).orElse(ip);
        if (existente != null && !deviceId.equals(existente.getDeviceId())) {
            // assume o ativo existente (o histórico registra a troca de identificador)
            cmdbAtivoMapper.updateById(AtivoDO.builder().id(existente.getId()).deviceId(deviceId).build());
        }
        List<Normalizacao.Componente> componentes = new ArrayList<>();
        for (SnmpLeitura.Suprimento s : r.suprimentos()) {
            Map<String, String> d = new LinkedHashMap<>();
            putSe(d, "nivel", s.nivel());
            putSe(d, "maximo", s.maximo());
            putSe(d, "percentual", s.percentual());
            componentes.add(new Normalizacao.Componente("suprimento", s.indice() + "|" + s.descricao(),
                    s.descricao() + (s.percentual() == null ? "" : " · " + s.percentual() + "%"), d));
        }
        for (SnmpLeitura.Interface i : r.interfaces()) {
            Map<String, String> d = new LinkedHashMap<>();
            putSe(d, "MACADDR", i.mac());
            putSe(d, "STATUS", i.estado());
            putSe(d, "SPEED", i.velocidade());
            componentes.add(new Normalizacao.Componente("rede", (i.mac() == null ? "" : i.mac()) + "|" + i.indice() + "|" + i.descricao(),
                    i.descricao() + (i.estado() == null ? "" : " · " + i.estado()), d));
        }
        Normalizacao.Resultado n = new Normalizacao.Resultado(r.resumo(), componentes, List.of());
        String versao = "SNMP " + Optional.ofNullable(r.sysObjectId()).orElse("");
        Long id = inventarioAgenteService.gravarResultado(n, deviceId, versao.strip(), "snmp");
        AtivoDO gravado = cmdbAtivoMapper.selectById(id);
        AtivoDO m = AtivoDO.builder().id(id).paginas(r.paginas()).localizacao(InventarioAgenteService.limpar(r.localizacao(), 255)).build();
        if (gravado.getDeptId() == null && descoberto != null && descoberto.getDeptId() != null) {
            m.setDeptId(descoberto.getDeptId());
        }
        cmdbAtivoMapper.updateById(m);
        if (descoberto != null && descoberto.getAtivoId() == null) {
            cmdbRedeDispositivoMapper.updateById(RedeDispositivoDO.builder().id(descoberto.getId()).ativoId(id).build());
        }
        return id;
    }

    // ======================================================================================= tela

    public List<SnmpVOs.Alvo> alvosConsultados() {
        Map<Long, String> creds = cmdbSnmpCredencialMapper.selectList().stream()
                .collect(Collectors.toMap(SnmpCredencialDO::getId, SnmpCredencialDO::getNome));
        List<SnmpAlvoDO> l = cmdbSnmpAlvoMapper.selectList(new LambdaQueryWrapperX<SnmpAlvoDO>().orderByDesc(SnmpAlvoDO::getUltimaResposta)
                .last("LIMIT 500"));
        Set<Long> ids = l.stream().map(SnmpAlvoDO::getAtivoId).filter(Objects::nonNull).collect(Collectors.toSet());
        // ativos pela regra de escopo: quem não vê a unidade do ativo não vê o nome
        Map<Long, AtivoDO> ativos = ids.isEmpty() ? Map.of() : cmdbAtivoMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(AtivoDO::getId, a -> a));
        return l.stream().map(a -> {
            SnmpVOs.Alvo vo = new SnmpVOs.Alvo();
            vo.setIp(a.getIp());
            vo.setCredencial(a.getCredencialId() == null ? null : creds.get(a.getCredencialId()));
            AtivoDO at = a.getAtivoId() == null ? null : ativos.get(a.getAtivoId());
            vo.setAtivoId(at == null ? null : at.getId());
            vo.setAtivoNome(at == null ? null : at.getNome());
            vo.setAtivoTipo(at == null ? null : at.getTipo());
            vo.setUltimaTentativa(a.getUltimaTentativa());
            vo.setUltimaResposta(a.getUltimaResposta());
            vo.setErro(a.getErro());
            return vo;
        }).toList();
    }

    private static void putSe(Map<String, String> m, String k, Object v) {
        if (v != null) {
            m.put(k, String.valueOf(v));
        }
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
