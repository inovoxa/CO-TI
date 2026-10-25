package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.RedeVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.ComponenteDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.RedeDispositivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.RedeVarredorDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbComponenteMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbRedeDispositivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbRedeVarredorMapper;
import cn.iocoder.yudao.module.inventario.dominio.Protocolo;
import cn.iocoder.yudao.module.inventario.dominio.Redes;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.DISPOSITIVO_NAO_EXISTE;

/**
 * Descoberta de rede (IPDiscover do agente OCS). Para cada sub-rede onde há agentes, o servidor escolhe um (ou mais)
 * para varrer: na resposta ao PROLOG vai a opção IPDISCOVER com a sub-rede, e no inventário seguinte o agente manda
 * os IPs e MACs que respondem. O que não é máquina inventariada aparece como "desconhecido".
 */
@Service
@Slf4j
public class RedeService {

    /** Varredor sem contato há mais que isto perde a vaga para outro agente da mesma sub-rede */
    static final int DIAS_VARREDOR = 7;
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern MAC = Pattern.compile("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$");

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private CmdbRedeVarredorMapper cmdbRedeVarredorMapper;
    @Resource
    private CmdbRedeDispositivoMapper cmdbRedeDispositivoMapper;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbComponenteMapper cmdbComponenteMapper;
    @Resource
    private AtivoService ativoService;

    // ======================================================================================= protocolo

    /** Sub-rede e máscara de uma placa do ativo */
    record SubRede(String subrede, String mascara) {
    }

    /**
     * Opção IPDISCOVER para a resposta ao PROLOG, se este ativo é (ou passa a ser) o varredor de uma sub-rede.
     *
     * @return null = não varre
     */
    @DataPermission(enable = false)
    public synchronized Protocolo.ComAtributos opcaoProlog(AtivoDO ativo) {
        if (!inventarioProperties.isIpdiscover() || ativo == null || !"ocs".equals(ativo.getOrigem())
                || !"em_uso".equals(ativo.getSituacao())) {
            return null;
        }
        LocalDateTime agora = LocalDateTime.now();
        for (SubRede sr : subredes(ativo.getId())) {
            List<RedeVarredorDO> atuais = cmdbRedeVarredorMapper.selectList(RedeVarredorDO::getSubrede, sr.subrede());
            RedeVarredorDO meu = atuais.stream().filter(v -> v.getAtivoId().equals(ativo.getId())).findFirst().orElse(null);
            if (meu == null) {
                // vaga livre, ou de um varredor que sumiu (sem contato, baixado ou apagado)
                long validos = atuais.stream().filter(v -> valido(v, agora)).count();
                atuais.stream().filter(v -> !valido(v, agora)).forEach(v -> cmdbRedeVarredorMapper.deleteById(v.getId()));
                if (validos >= Math.max(1, inventarioProperties.getIpdiscoverPorRede())) {
                    continue;
                }
                try {
                    cmdbRedeVarredorMapper.insert(RedeVarredorDO.builder().subrede(sr.subrede()).mascara(sr.mascara())
                            .ativoId(ativo.getId()).eleitoEm(agora).build());
                } catch (DuplicateKeyException e) {
                    continue;
                }
            }
            Map<String, Object> atributos = new LinkedHashMap<>();
            atributos.put("IPDISC_LAT", inventarioProperties.getIpdiscoverLatencia());
            atributos.put("SCAN_TYPE_IPDISCOVER", "ICMP");
            atributos.put("SCAN_ARP_BANDWIDTH", 256);
            return new Protocolo.ComAtributos(atributos, sr.subrede());
        }
        return null;
    }

    private boolean valido(RedeVarredorDO v, LocalDateTime agora) {
        AtivoDO a = cmdbAtivoMapper.selectById(v.getAtivoId());
        return a != null && "em_uso".equals(a.getSituacao()) && a.getUltimoContato() != null
                && a.getUltimoContato().isAfter(agora.minusDays(DIAS_VARREDOR));
    }

    /** Sub-redes das placas físicas do ativo (do último inventário), dentro das redes permitidas. */
    List<SubRede> subredes(Long ativoId) {
        Map<String, SubRede> l = new LinkedHashMap<>();
        for (ComponenteDO c : cmdbComponenteMapper.selectList(new LambdaQueryWrapperX<ComponenteDO>()
                .eq(ComponenteDO::getAtivoId, ativoId).eq(ComponenteDO::getTipo, "rede"))) {
            Map<String, String> d;
            try {
                d = JsonUtils.parseObject(c.getDados(), new TypeReference<Map<String, String>>() {
                });
            } catch (Exception e) {
                continue;
            }
            if (d == null || "1".equals(d.get("VIRTUALDEV"))) {
                continue;
            }
            String sub = d.get("IPSUBNET");
            if (sub == null || !IPV4.matcher(sub).matches() || sub.startsWith("127.") || sub.startsWith("169.254.") || sub.startsWith("0.")) {
                continue;
            }
            // a variável de ambiente vazia chega como [""]: entradas em branco não contam
            List<String> permitidas = inventarioProperties.getIpdiscoverRedes() == null ? List.of()
                    : inventarioProperties.getIpdiscoverRedes().stream().filter(x -> x != null && !x.isBlank()).toList();
            if (!permitidas.isEmpty() && !Redes.permitido(sub, permitidas)) {
                continue;
            }
            l.putIfAbsent(sub, new SubRede(sub, d.get("IPMASK")));
        }
        return new ArrayList<>(l.values());
    }

    /** Grava o resultado da varredura que veio junto do inventário (só de quem é varredor da sub-rede). */
    @DataPermission(enable = false)
    @Transactional(rollbackFor = Exception.class)
    public int registrar(Protocolo.Pedido p, Long ativoId) {
        List<Map<String, String>> hosts = p.lista("IPDISCOVER");
        if (hosts.isEmpty() || !inventarioProperties.isIpdiscover()) {
            return 0;
        }
        List<RedeVarredorDO> minhas = cmdbRedeVarredorMapper.selectList(RedeVarredorDO::getAtivoId, ativoId);
        if (minhas.isEmpty()) {
            log.info("[inventario] varredura de rede ignorada: o ativo {} não é varredor", ativoId);
            return 0;
        }
        AtivoDO varredor = cmdbAtivoMapper.selectById(ativoId);
        LocalDateTime agora = LocalDateTime.now();
        int n = 0;
        for (Map<String, String> h : hosts) {
            String ip = Objects.toString(h.get("I"), "").trim();
            String mac = Objects.toString(h.get("M"), "").trim().toUpperCase(Locale.ROOT).replace('-', ':');
            if (!ipValido(ip) || !MAC.matcher(mac).matches() || "00:00:00:00:00:00".equals(mac) || "FF:FF:FF:FF:FF:FF".equals(mac)) {
                continue;
            }
            RedeVarredorDO sub = minhas.stream().filter(v -> naSubrede(ip, v.getSubrede(), v.getMascara())).findFirst().orElse(minhas.get(0));
            String nome = InventarioAgenteService.limpar(h.get("N"), 255);
            if ("-".equals(nome)) {
                nome = null;
            }
            AtivoDO conhecido = cmdbAtivoMapper.selectPorMac(mac);
            RedeDispositivoDO atual = cmdbRedeDispositivoMapper.selectOne(RedeDispositivoDO::getMac, mac);
            if (atual == null) {
                cmdbRedeDispositivoMapper.insert(RedeDispositivoDO.builder().mac(mac).ip(ip).nome(nome).subrede(sub.getSubrede())
                        .mascara(sub.getMascara()).ativoId(conhecido == null ? null : conhecido.getId()).ignorado(false)
                        .vistoPor(ativoId).deptId(varredor == null ? null : varredor.getDeptId()).primeiraVez(agora).ultimaVez(agora).build());
            } else {
                RedeDispositivoDO m = RedeDispositivoDO.builder().id(atual.getId()).ip(ip).subrede(sub.getSubrede())
                        .mascara(sub.getMascara()).vistoPor(ativoId).deptId(varredor == null ? null : varredor.getDeptId())
                        .ultimaVez(agora).build();
                if (nome != null) {
                    m.setNome(nome);
                }
                if (conhecido != null && atual.getAtivoId() == null) {
                    m.setAtivoId(conhecido.getId());
                }
                cmdbRedeDispositivoMapper.updateById(m);
            }
            n++;
        }
        for (RedeVarredorDO v : minhas) {
            cmdbRedeVarredorMapper.updateById(RedeVarredorDO.builder().id(v.getId()).ultimaVarredura(agora).build());
        }
        return n;
    }

    static boolean ipValido(String ip) {
        var m = IPV4.matcher(ip);
        if (!m.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            if (Integer.parseInt(m.group(i)) > 255) {
                return false;
            }
        }
        return true;
    }

    /** O IP está na sub-rede (pela máscara; sem máscara, compara os três primeiros octetos). */
    static boolean naSubrede(String ip, String subrede, String mascara) {
        if (mascara != null && ipValido(mascara) && ipValido(subrede)) {
            long m = numero(mascara);
            return (numero(ip) & m) == (numero(subrede) & m);
        }
        return subrede != null && ip.substring(0, ip.lastIndexOf('.')).equals(subrede.substring(0, Math.max(0, subrede.lastIndexOf('.'))));
    }

    private static long numero(String ip) {
        long n = 0;
        for (String p : ip.split("\\.")) {
            n = (n << 8) | Integer.parseInt(p);
        }
        return n;
    }

    // ======================================================================================= telas

    /** Sub-redes com contagens e o varredor (no escopo de quem consulta). */
    public List<RedeVOs.Subrede> subredes() {
        List<RedeDispositivoDO> todos = cmdbRedeDispositivoMapper.selectList();
        Map<String, List<RedeDispositivoDO>> porSub = todos.stream().collect(Collectors.groupingBy(
                d -> Objects.toString(d.getSubrede(), "?"), TreeMap::new, Collectors.toList()));
        Map<String, List<RedeVarredorDO>> varredores = cmdbRedeVarredorMapper.selectList().stream()
                .collect(Collectors.groupingBy(RedeVarredorDO::getSubrede));
        Set<Long> ids = varredores.values().stream().flatMap(List::stream).map(RedeVarredorDO::getAtivoId).collect(Collectors.toSet());
        Map<Long, String> nomes = ids.isEmpty() ? Map.of() : cmdbAtivoMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(AtivoDO::getId, AtivoDO::getNome));
        Set<String> chaves = new TreeSet<>(porSub.keySet());
        chaves.addAll(varredores.keySet());
        List<RedeVOs.Subrede> res = new ArrayList<>();
        for (String s : chaves) {
            List<RedeDispositivoDO> l = porSub.getOrDefault(s, List.of());
            RedeVOs.Subrede vo = new RedeVOs.Subrede();
            vo.setSubrede(s);
            vo.setMascara(l.stream().map(RedeDispositivoDO::getMascara).filter(Objects::nonNull).findFirst()
                    .orElse(varredores.getOrDefault(s, List.of()).stream().map(RedeVarredorDO::getMascara).filter(Objects::nonNull).findFirst().orElse(null)));
            vo.setDispositivos(l.size());
            vo.setDesconhecidos(l.stream().filter(d -> d.getAtivoId() == null && !Boolean.TRUE.equals(d.getIgnorado())).count());
            vo.setVarredores(varredores.getOrDefault(s, List.of()).stream().map(v -> nomes.getOrDefault(v.getAtivoId(), "#" + v.getAtivoId())).toList());
            vo.setUltimaVarredura(varredores.getOrDefault(s, List.of()).stream().map(RedeVarredorDO::getUltimaVarredura)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
            res.add(vo);
        }
        return res;
    }

    public PageResult<RedeVOs.Dispositivo> pagina(RedeVOs.PaginaReq req) {
        LambdaQueryWrapperX<RedeDispositivoDO> q = new LambdaQueryWrapperX<RedeDispositivoDO>()
                .eqIfPresent(RedeDispositivoDO::getSubrede, req.getSubrede());
        String s = req.getSituacao() == null ? "desconhecidos" : req.getSituacao();
        switch (s) {
            case "desconhecidos" -> q.isNull(RedeDispositivoDO::getAtivoId).eq(RedeDispositivoDO::getIgnorado, false);
            case "conhecidos" -> q.isNotNull(RedeDispositivoDO::getAtivoId);
            case "ignorados" -> q.eq(RedeDispositivoDO::getIgnorado, true);
            default -> {
            }
        }
        if (req.getBusca() != null && !req.getBusca().isBlank()) {
            String b = "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("ip ILIKE {0}", b).or().apply("mac ILIKE {0}", b).or().apply("nome ILIKE {0}", b));
        }
        q.last("ORDER BY subrede, inet(ip)");
        PageResult<RedeDispositivoDO> p = cmdbRedeDispositivoMapper.selectPage(req, q);
        Set<Long> ids = p.getList().stream().map(RedeDispositivoDO::getAtivoId).filter(Objects::nonNull).collect(Collectors.toSet());
        ids.addAll(p.getList().stream().map(RedeDispositivoDO::getVistoPor).filter(Objects::nonNull).toList());
        Map<Long, AtivoDO> ativos = ids.isEmpty() ? Map.of() : cmdbAtivoMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(AtivoDO::getId, a -> a));
        List<RedeVOs.Dispositivo> l = new ArrayList<>();
        for (RedeDispositivoDO d : p.getList()) {
            RedeVOs.Dispositivo vo = new RedeVOs.Dispositivo();
            vo.setId(d.getId());
            vo.setMac(d.getMac());
            vo.setIp(d.getIp());
            vo.setNome(d.getNome());
            vo.setSubrede(d.getSubrede());
            vo.setAtivoId(d.getAtivoId());
            AtivoDO a = d.getAtivoId() == null ? null : ativos.get(d.getAtivoId());
            vo.setAtivoNome(a == null ? null : a.getNome());
            vo.setAtivoTipo(a == null ? null : a.getTipo());
            AtivoDO v = d.getVistoPor() == null ? null : ativos.get(d.getVistoPor());
            vo.setVistoPor(v == null ? null : v.getNome());
            vo.setIgnorado(d.getIgnorado());
            vo.setObservacao(d.getObservacao());
            vo.setPrimeiraVez(d.getPrimeiraVez());
            vo.setUltimaVez(d.getUltimaVez());
            l.add(vo);
        }
        return new PageResult<>(l, p.getTotal());
    }

    /** Cadastra o equipamento descoberto como ativo (impressora, switch, telefone IP...). */
    @Transactional(rollbackFor = Exception.class)
    public Long cadastrar(RedeVOs.CadastrarReq req, Long userId) {
        RedeDispositivoDO d = existente(req.getId());
        AtivoVOs.CriarReq c = new AtivoVOs.CriarReq();
        c.setNome(req.getNome());
        c.setTipo(req.getTipo());
        c.setIp(d.getIp());
        c.setFabricante(req.getFabricante());
        c.setModelo(req.getModelo());
        c.setPatrimonio(req.getPatrimonio());
        c.setDeptId(req.getDeptId() != null ? req.getDeptId() : d.getDeptId());
        c.setObservacao(req.getObservacao());
        Long ativoId = ativoService.criar(c, userId);
        cmdbAtivoMapper.updateById(AtivoDO.builder().id(ativoId).mac(d.getMac()).build());
        cmdbRedeDispositivoMapper.updateById(RedeDispositivoDO.builder().id(d.getId()).ativoId(ativoId).ignorado(false).build());
        return ativoId;
    }

    public void ignorar(Long id, boolean ignorado, String observacao) {
        RedeDispositivoDO d = existente(id);
        RedeDispositivoDO m = RedeDispositivoDO.builder().id(d.getId()).ignorado(ignorado).build();
        if (observacao != null) {
            m.setObservacao(InventarioAgenteService.limpar(observacao, 500));
        }
        cmdbRedeDispositivoMapper.updateById(m);
    }

    private RedeDispositivoDO existente(Long id) {
        RedeDispositivoDO d = id == null ? null : cmdbRedeDispositivoMapper.selectById(id);
        if (d == null) {
            throw exception(DISPOSITIVO_NAO_EXISTE);
        }
        return d;
    }

}
