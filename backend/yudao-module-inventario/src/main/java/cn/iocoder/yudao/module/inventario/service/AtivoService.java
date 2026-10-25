package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.*;
import cn.iocoder.yudao.module.inventario.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.*;

/**
 * Consulta e manutenção dos ativos pela equipe. As consultas respeitam o escopo de unidade do perfil (regra registrada
 * em {@code InventarioConfiguration}).
 */
@Service
public class AtivoService {

    static final Set<String> SITUACOES = Set.of("em_uso", "estoque", "manutencao", "baixado");
    static final Set<String> TIPOS = Set.of("desktop", "notebook", "servidor", "virtual", "impressora", "monitor", "rede", "telefone", "outro");

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbComponenteMapper cmdbComponenteMapper;
    @Resource
    private CmdbSoftwareMapper cmdbSoftwareMapper;
    @Resource
    private CmdbAtivoSoftwareMapper cmdbAtivoSoftwareMapper;
    @Resource
    private CmdbHistoricoMapper cmdbHistoricoMapper;
    @Resource
    private CmdbChamadoAtivoMapper cmdbChamadoAtivoMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptApi deptApi;

    // ======================================================================================= consulta

    public PageResult<AtivoVOs.Resp> pagina(AtivoVOs.PaginaReq req) {
        LambdaQueryWrapperX<AtivoDO> q = new LambdaQueryWrapperX<AtivoDO>()
                .eqIfPresent(AtivoDO::getTipo, req.getTipo())
                .eqIfPresent(AtivoDO::getSituacao, req.getSituacao())
                .eqIfPresent(AtivoDO::getUsuarioId, req.getUsuarioId());
        if (req.getDeptId() != null) {
            Set<Long> depts = new HashSet<>(List.of(req.getDeptId()));
            deptApi.getChildDeptList(req.getDeptId()).forEach(d -> depts.add(d.getId()));
            q.in(AtivoDO::getDeptId, depts);
        }
        if (req.getBusca() != null && !req.getBusca().isBlank()) {
            String b = "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", b).or().apply("patrimonio ILIKE {0}", b).or().apply("numero_serie ILIKE {0}", b)
                    .or().apply("ip ILIKE {0}", b).or().apply("usuario_login ILIKE {0}", b).or().apply("modelo ILIKE {0}", b));
        }
        if (req.getAlerta() != null) {
            switch (req.getAlerta()) {
                case "sem_suporte" -> q.eq(AtivoDO::getSoSemSuporte, true);
                case "antivirus" -> q.in(AtivoDO::getAntivirus, "desatualizado", "ausente").ne(AtivoDO::getOrigem, "manual");
                case "sem_contato" -> q.ne(AtivoDO::getOrigem, "manual").lt(AtivoDO::getUltimoContato, limiteContato());
                default -> {
                }
            }
        }
        if (req.getSoftwareId() != null) {
            q.apply("EXISTS (SELECT 1 FROM cmdb.ativo_software x WHERE x.ativo_id = ativo.id AND x.deleted = 0 AND x.software_id = {0})",
                    req.getSoftwareId());
        }
        q.orderByAsc(AtivoDO::getNome);
        PageResult<AtivoDO> p = cmdbAtivoMapper.selectPage(req, q);
        return new PageResult<>(converter(p.getList()), p.getTotal());
    }

    public Map<String, Long> contagens() {
        Map<String, Long> c = new LinkedHashMap<>();
        c.put("todos", cmdbAtivoMapper.selectCount());
        c.put("sem_suporte", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getSoSemSuporte, true)));
        c.put("antivirus", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().in(AtivoDO::getAntivirus, "desatualizado", "ausente")
                .ne(AtivoDO::getOrigem, "manual")));
        c.put("sem_contato", cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getOrigem, "manual")
                .lt(AtivoDO::getUltimoContato, limiteContato())));
        return c;
    }

    public AtivoVOs.Detalhe detalhe(Long id) {
        AtivoDO a = existente(id);
        AtivoVOs.Detalhe d = new AtivoVOs.Detalhe();
        d.setAtivo(converter(List.of(a)).get(0));
        for (ComponenteDO c : cmdbComponenteMapper.selectList(new LambdaQueryWrapperX<ComponenteDO>()
                .eq(ComponenteDO::getAtivoId, id).orderByAsc(ComponenteDO::getTipo).orderByAsc(ComponenteDO::getId))) {
            AtivoVOs.Componente vo = new AtivoVOs.Componente();
            vo.setId(c.getId());
            vo.setTipo(c.getTipo());
            vo.setDescricao(c.getDescricao());
            vo.setDados(lerDados(c.getDados()));
            d.getComponentes().add(vo);
        }
        List<AtivoSoftwareDO> instalados = cmdbAtivoSoftwareMapper.selectList(AtivoSoftwareDO::getAtivoId, id);
        Map<Long, SoftwareDO> catalogo = instalados.isEmpty() ? Map.of() : cmdbSoftwareMapper.selectByIds(
                instalados.stream().map(AtivoSoftwareDO::getSoftwareId).distinct().toList()).stream()
                .collect(Collectors.toMap(SoftwareDO::getId, s -> s));
        for (AtivoSoftwareDO x : instalados) {
            SoftwareDO s = catalogo.get(x.getSoftwareId());
            AtivoVOs.SoftwareInstalado vo = new AtivoVOs.SoftwareInstalado();
            vo.setId(x.getId());
            vo.setSoftwareId(x.getSoftwareId());
            vo.setNome(s == null ? "#" + x.getSoftwareId() : s.getNome());
            vo.setEditora(s == null ? null : s.getEditora());
            vo.setVersao(x.getVersao());
            vo.setInstaladoEm(x.getInstaladoEm());
            vo.setArquitetura(x.getArquitetura());
            d.getSoftwares().add(vo);
        }
        d.getSoftwares().sort(Comparator.comparing(s -> s.getNome().toLowerCase(Locale.ROOT)));
        d.setChamados(chamadosDoAtivo(id));
        return d;
    }

    public PageResult<AtivoVOs.Historico> historico(Long id, PageParam pagina) {
        existente(id);
        PageResult<HistoricoAtivoDO> p = cmdbHistoricoMapper.selectPage(pagina, new LambdaQueryWrapperX<HistoricoAtivoDO>()
                .eq(HistoricoAtivoDO::getAtivoId, id).orderByDesc(HistoricoAtivoDO::getQuando).orderByDesc(HistoricoAtivoDO::getId));
        Set<Long> ids = p.getList().stream().map(HistoricoAtivoDO::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = ids.isEmpty() ? Map.of() : adminUserMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, AdminUserDO::getNickname));
        List<AtivoVOs.Historico> l = new ArrayList<>();
        for (HistoricoAtivoDO h : p.getList()) {
            AtivoVOs.Historico vo = new AtivoVOs.Historico();
            vo.setId(h.getId());
            vo.setQuando(h.getQuando());
            vo.setTipo(h.getTipo());
            vo.setCampo(h.getCampo());
            vo.setAntes(h.getAntes());
            vo.setDepois(h.getDepois());
            vo.setUsuario(h.getUserId() == null ? "Agente" : nomes.getOrDefault(h.getUserId(), "#" + h.getUserId()));
            l.add(vo);
        }
        return new PageResult<>(l, p.getTotal());
    }

    public PageResult<AtivoVOs.SoftwareResp> softwares(String busca, PageParam pagina) {
        String b = busca == null || busca.isBlank() ? null
                : "%" + busca.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        int tamanho = Math.min(Math.max(pagina.getPageSize(), 1), 200);
        int deslocamento = (Math.max(pagina.getPageNo(), 1) - 1) * tamanho;
        List<AtivoVOs.SoftwareResp> l = new ArrayList<>();
        for (Map<String, Object> m : cmdbAtivoMapper.softwares(b, tamanho, deslocamento)) {
            AtivoVOs.SoftwareResp vo = new AtivoVOs.SoftwareResp();
            vo.setId(((Number) m.get("id")).longValue());
            vo.setNome((String) m.get("nome"));
            vo.setEditora((String) m.get("editora"));
            vo.setInstalacoes(((Number) m.get("instalacoes")).longValue());
            vo.setVersoes(((Number) m.get("versoes")).longValue());
            l.add(vo);
        }
        return new PageResult<>(l, cmdbAtivoMapper.contarSoftwares(b));
    }

    /** Ativos de uma pessoa (para escolher no chamado). */
    public List<AtivoVOs.Resp> doUsuario(Long usuarioId) {
        return converter(cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getUsuarioId, usuarioId)
                .ne(AtivoDO::getSituacao, "baixado").orderByDesc(AtivoDO::getUltimoContato)));
    }

    // ======================================================================================= manutenção

    @Transactional(rollbackFor = Exception.class)
    public Long criar(AtivoVOs.CriarReq req, Long userId) {
        if (!TIPOS.contains(req.getTipo())) {
            throw exception(ATIVO_TIPO_INVALIDO);
        }
        AtivoDO a = AtivoDO.builder().nome(req.getNome().trim()).tipo(req.getTipo()).situacao("em_uso")
                .patrimonio(InventarioAgenteService.limpar(req.getPatrimonio(), 100)).numeroSerie(InventarioAgenteService.limpar(req.getNumeroSerie(), 100))
                .fabricante(InventarioAgenteService.limpar(req.getFabricante(), 255)).modelo(InventarioAgenteService.limpar(req.getModelo(), 255))
                .ip(InventarioAgenteService.limpar(req.getIp(), 64)).usuarioId(req.getUsuarioId()).deptId(req.getDeptId())
                .deptManual(req.getDeptId() != null).observacao(req.getObservacao()).origem("manual").antivirus("desconhecido")
                .soSemSuporte(false).build();
        if (a.getDeptId() == null && a.getUsuarioId() != null) {
            AdminUserDO u = adminUserMapper.selectById(a.getUsuarioId());
            a.setDeptId(u == null ? null : u.getDeptId());
        }
        cmdbAtivoMapper.insert(a);
        historico(a.getId(), userId, "manual", "cadastro", null, "Cadastrado à mão");
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizar(AtivoVOs.AtualizarReq req, Long userId) {
        AtivoDO a = existente(req.getId());
        boolean manual = "manual".equals(a.getOrigem());
        AtivoDO m = AtivoDO.builder().id(a.getId()).build();
        if (req.getSituacao() != null && !req.getSituacao().equals(a.getSituacao())) {
            if (!SITUACOES.contains(req.getSituacao())) {
                throw exception(ATIVO_SITUACAO_INVALIDA);
            }
            m.setSituacao(req.getSituacao());
            historico(a.getId(), userId, "manual", "situacao", a.getSituacao(), req.getSituacao());
        }
        if (req.getPatrimonio() != null && !Objects.equals(InventarioAgenteService.limpar(req.getPatrimonio(), 100), a.getPatrimonio())) {
            String novo = InventarioAgenteService.limpar(req.getPatrimonio(), 100);
            m.setPatrimonio(novo == null ? "" : novo);
            historico(a.getId(), userId, "manual", "patrimonio", a.getPatrimonio(), novo);
        }
        if (req.getObservacao() != null && !req.getObservacao().equals(a.getObservacao())) {
            m.setObservacao(req.getObservacao());
        }
        if (Boolean.TRUE.equals(req.getDeptAutomatico())) {
            m.setDeptManual(false);
            Long dept = a.getUsuarioId() == null ? null : Optional.ofNullable(adminUserMapper.selectById(a.getUsuarioId()))
                    .map(AdminUserDO::getDeptId).orElse(null);
            if (dept != null && !dept.equals(a.getDeptId())) {
                m.setDeptId(dept);
                historico(a.getId(), userId, "manual", "dept_id", nomeDept(a.getDeptId()), nomeDept(dept));
            }
        } else if (req.getDeptId() != null && !req.getDeptId().equals(a.getDeptId())) {
            m.setDeptId(req.getDeptId());
            m.setDeptManual(true);
            historico(a.getId(), userId, "manual", "dept_id", nomeDept(a.getDeptId()), nomeDept(req.getDeptId()));
        }
        if (req.getUsuarioId() != null && !req.getUsuarioId().equals(a.getUsuarioId())) {
            if (!manual) {
                throw exception(ATIVO_DO_AGENTE);   // o agente diz quem está logado
            }
            m.setUsuarioId(req.getUsuarioId());
            historico(a.getId(), userId, "manual", "usuario_id", nomeUsuario(a.getUsuarioId()), nomeUsuario(req.getUsuarioId()));
        }
        String[][] doAgente = {
                {"nome", req.getNome(), a.getNome()}, {"tipo", req.getTipo(), a.getTipo()},
                {"numero_serie", req.getNumeroSerie(), a.getNumeroSerie()}, {"fabricante", req.getFabricante(), a.getFabricante()},
                {"modelo", req.getModelo(), a.getModelo()}, {"ip", req.getIp(), a.getIp()}};
        for (String[] c : doAgente) {
            if (c[1] == null || c[1].trim().equals(c[2])) {
                continue;
            }
            if (!manual) {
                throw exception(ATIVO_DO_AGENTE);
            }
            String v = c[1].trim();
            switch (c[0]) {
                case "nome" -> {
                    if (v.isEmpty()) {
                        continue;
                    }
                    m.setNome(v);
                }
                case "tipo" -> {
                    if (!TIPOS.contains(v)) {
                        throw exception(ATIVO_TIPO_INVALIDO);
                    }
                    m.setTipo(v);
                }
                case "numero_serie" -> m.setNumeroSerie(v);
                case "fabricante" -> m.setFabricante(v);
                case "modelo" -> m.setModelo(v);
                default -> m.setIp(v);
            }
            historico(a.getId(), userId, "manual", c[0], c[2], v);
        }
        cmdbAtivoMapper.updateById(m);
    }

    // ======================================================================================= chamados

    /** Liga um ativo a um chamado que o usuário pode ver. */
    @Transactional(rollbackFor = Exception.class)
    public void vincular(AtivoVOs.VinculoReq req, Long userId) {
        chamadoService.obter(req.getChamadoId(), userId);   // confere o escopo do chamado
        AtivoDO a = existente(req.getAtivoId());
        if (cmdbChamadoAtivoMapper.selectCount(new LambdaQueryWrapperX<ChamadoAtivoDO>().eq(ChamadoAtivoDO::getChamadoId, req.getChamadoId())
                .eq(ChamadoAtivoDO::getAtivoId, a.getId())) > 0) {
            return;
        }
        cmdbChamadoAtivoMapper.insert(ChamadoAtivoDO.builder().chamadoId(req.getChamadoId()).ativoId(a.getId()).userId(userId)
                .criadoEm(LocalDateTime.now()).build());
        chamadoService.registrarSistema(req.getChamadoId(), userId, "ativo", null, a.getNome());
        historico(a.getId(), userId, "manual", "chamado", null, "Ligado ao chamado #" + req.getChamadoId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void desvincular(AtivoVOs.VinculoReq req, Long userId) {
        chamadoService.obter(req.getChamadoId(), userId);
        AtivoDO a = existente(req.getAtivoId());
        List<ChamadoAtivoDO> l = cmdbChamadoAtivoMapper.selectList(new LambdaQueryWrapperX<ChamadoAtivoDO>()
                .eq(ChamadoAtivoDO::getChamadoId, req.getChamadoId()).eq(ChamadoAtivoDO::getAtivoId, a.getId()));
        if (l.isEmpty()) {
            return;
        }
        cmdbChamadoAtivoMapper.deleteByIds(l.stream().map(ChamadoAtivoDO::getId).toList());
        chamadoService.registrarSistema(req.getChamadoId(), userId, "ativo", a.getNome(), null);
        historico(a.getId(), userId, "manual", "chamado", "Ligado ao chamado #" + req.getChamadoId(), null);
    }

    /** Ativos ligados a um chamado (bloco do detalhe do chamado). */
    public List<AtivoVOs.Resp> doChamado(Long chamadoId, Long userId) {
        chamadoService.obter(chamadoId, userId);
        List<Long> ids = cmdbChamadoAtivoMapper.selectList(ChamadoAtivoDO::getChamadoId, chamadoId).stream()
                .map(ChamadoAtivoDO::getAtivoId).toList();
        return ids.isEmpty() ? List.of() : converter(cmdbAtivoMapper.selectByIds(ids));
    }

    private List<AtivoVOs.ChamadoVinculado> chamadosDoAtivo(Long ativoId) {
        List<ChamadoAtivoDO> l = cmdbChamadoAtivoMapper.selectList(new LambdaQueryWrapperX<ChamadoAtivoDO>()
                .eq(ChamadoAtivoDO::getAtivoId, ativoId).orderByDesc(ChamadoAtivoDO::getCriadoEm));
        if (l.isEmpty()) {
            return new ArrayList<>();
        }
        // o escopo do chamado vale aqui também: só aparecem os que o perfil enxerga
        Map<Long, ChamadoDO> chamados = chamadoMapper.selectByIds(l.stream().map(ChamadoAtivoDO::getChamadoId).toList()).stream()
                .collect(Collectors.toMap(ChamadoDO::getId, c -> c));
        List<AtivoVOs.ChamadoVinculado> res = new ArrayList<>();
        for (ChamadoAtivoDO x : l) {
            ChamadoDO c = chamados.get(x.getChamadoId());
            if (c == null) {
                continue;
            }
            AtivoVOs.ChamadoVinculado vo = new AtivoVOs.ChamadoVinculado();
            vo.setId(x.getId());
            vo.setChamadoId(c.getId());
            vo.setTitulo(c.getTitulo());
            vo.setStatus(c.getStatus());
            vo.setAbertoEm(c.getAbertoEm());
            res.add(vo);
        }
        return res;
    }

    // ======================================================================================= agentes

    public AtivoVOs.PainelAgentes painel() {
        AtivoVOs.PainelAgentes p = new AtivoVOs.PainelAgentes();
        p.setEndereco("/ocsinventory");
        p.setRedes(inventarioProperties.getAgenteRedes());
        p.setComSenha(inventarioProperties.getAgenteUsuario() != null && !inventarioProperties.getAgenteUsuario().isEmpty());
        p.setPrologoHoras(inventarioProperties.getPrologoHoras());
        p.setInventarioDias(inventarioProperties.getInventarioDias());
        p.setSemContatoDias(inventarioProperties.getSemContatoDias());
        Map<String, Long> porOrigem = new LinkedHashMap<>();
        for (String o : new String[]{"ocs", "glpi_agent", "manual"}) {
            porOrigem.put(o, cmdbAtivoMapper.selectCount(AtivoDO::getOrigem, o));
        }
        p.setPorOrigem(porOrigem);
        p.setVersoes(cmdbAtivoMapper.versoesAgente());
        p.setSemContato(cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getOrigem, "manual")
                .lt(AtivoDO::getUltimoContato, limiteContato())));
        p.setUltimas24h(cmdbAtivoMapper.selectCount(new LambdaQueryWrapperX<AtivoDO>()
                .ge(AtivoDO::getUltimoInventario, LocalDateTime.now().minusHours(24))));
        p.setRecentes(converter(cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getOrigem, "manual")
                .orderByDesc(AtivoDO::getUltimoInventario).last("LIMIT 15"))));
        return p;
    }

    // ======================================================================================= apoio

    AtivoDO existente(Long id) {
        AtivoDO a = id == null ? null : cmdbAtivoMapper.selectById(id);
        if (a == null) {
            throw exception(ATIVO_NAO_EXISTE);
        }
        return a;
    }

    private LocalDateTime limiteContato() {
        return LocalDateTime.now().minusDays(inventarioProperties.getSemContatoDias());
    }

    private void historico(Long ativoId, Long userId, String tipo, String campo, String antes, String depois) {
        cmdbHistoricoMapper.insert(HistoricoAtivoDO.builder().ativoId(ativoId).quando(LocalDateTime.now()).tipo(tipo).campo(campo)
                .antes(antes).depois(depois).userId(userId).build());
    }

    private String nomeDept(Long id) {
        if (id == null) {
            return null;
        }
        DeptRespDTO d = deptApi.getDept(id);
        return d == null ? "#" + id : d.getName();
    }

    private String nomeUsuario(Long id) {
        if (id == null) {
            return null;
        }
        AdminUserDO u = adminUserMapper.selectById(id);
        return u == null ? "#" + id : u.getNickname();
    }

    private static Map<String, Object> lerDados(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JsonUtils.parseObject(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    List<AtivoVOs.Resp> converter(List<AtivoDO> lista) {
        if (lista.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> depts = lista.stream().map(AtivoDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomesDept = depts.isEmpty() ? Map.of() : deptApi.getDeptList(depts).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName));
        Set<Long> usuarios = lista.stream().map(AtivoDO::getUsuarioId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomesUsuario = usuarios.isEmpty() ? Map.of() : adminUserMapper.selectByIds(usuarios).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, AdminUserDO::getNickname));
        LocalDateTime limite = limiteContato();
        List<AtivoVOs.Resp> res = new ArrayList<>();
        for (AtivoDO a : lista) {
            AtivoVOs.Resp r = new AtivoVOs.Resp();
            r.setId(a.getId());
            r.setTipo(a.getTipo());
            r.setNome(a.getNome());
            r.setSituacao(a.getSituacao());
            r.setPatrimonio(a.getPatrimonio());
            r.setNumeroSerie(a.getNumeroSerie());
            r.setFabricante(a.getFabricante());
            r.setModelo(a.getModelo());
            r.setSoNome(a.getSoNome());
            r.setSoVersao(a.getSoVersao());
            r.setSoArquitetura(a.getSoArquitetura());
            r.setSoSemSuporte(a.getSoSemSuporte());
            r.setCpu(a.getCpu());
            r.setCpuNucleos(a.getCpuNucleos());
            r.setMemoriaMb(a.getMemoriaMb());
            r.setDiscoTotalMb(a.getDiscoTotalMb());
            r.setDiscoLivreMb(a.getDiscoLivreMb());
            r.setIp(a.getIp());
            r.setMac(a.getMac());
            r.setDominio(a.getDominio());
            r.setUsuarioLogin(a.getUsuarioLogin());
            r.setUsuarioId(a.getUsuarioId());
            r.setUsuarioNome(a.getUsuarioId() == null ? null : nomesUsuario.get(a.getUsuarioId()));
            r.setDeptId(a.getDeptId());
            r.setDeptNome(a.getDeptId() == null ? null : nomesDept.get(a.getDeptId()));
            r.setDeptManual(a.getDeptManual());
            r.setAntivirus(a.getAntivirus());
            r.setOrigem(a.getOrigem());
            r.setAgente(a.getAgente());
            r.setPrimeiroInventario(a.getPrimeiroInventario());
            r.setUltimoInventario(a.getUltimoInventario());
            r.setUltimoContato(a.getUltimoContato());
            r.setSemContato(!"manual".equals(a.getOrigem()) && (a.getUltimoContato() == null || a.getUltimoContato().isBefore(limite)));
            r.setObservacao(a.getObservacao());
            res.add(r);
        }
        return res;
    }

}
