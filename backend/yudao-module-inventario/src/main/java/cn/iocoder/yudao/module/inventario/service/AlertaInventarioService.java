package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AlertaVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AlertaAtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AlertaRegraDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.ChamadoAtivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAlertaAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAlertaRegraMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbChamadoAtivoMapper;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaMapper;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.*;

/**
 * Alertas do inventário. Cada avaliação compara o que está errado agora com os alertas abertos: abre os novos, fecha
 * os que normalizaram e, quando a regra pede, abre um chamado por alerta (e avisa no chamado quando normaliza).
 */
@Service
@Slf4j
public class AlertaInventarioService {

    /** Para não inundar o Service Desk quando alguém liga "abrir chamado" numa regra com centenas de máquinas */
    static final int MAXIMO_CHAMADOS_POR_AVALIACAO = 20;
    static final Set<String> COMPUTADORES = Set.of("desktop", "notebook", "servidor", "virtual");
    /** Usuário "sistema" que abre os chamados automáticos (o mesmo do monitoramento) */
    private static final Long SISTEMA = 1L;
    private static final Long ORIGEM_MONITORAMENTO = 6L;

    @Resource
    private CmdbAlertaRegraMapper cmdbAlertaRegraMapper;
    @Resource
    private CmdbAlertaAtivoMapper cmdbAlertaAtivoMapper;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbChamadoAtivoMapper cmdbChamadoAtivoMapper;
    @Resource
    private LicencaInventarioService licencaInventarioService;
    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private DeptApi deptApi;

    // ======================================================================================= regras

    public List<AlertaVOs.Regra> regras() {
        Map<String, Long> abertos = new HashMap<>();
        for (AlertaAtivoDO a : cmdbAlertaAtivoMapper.selectList(new LambdaQueryWrapperX<AlertaAtivoDO>().isNull(AlertaAtivoDO::getFim))) {
            abertos.merge(a.getRegra(), 1L, Long::sum);
        }
        return cmdbAlertaRegraMapper.selectList(new LambdaQueryWrapperX<AlertaRegraDO>().orderByAsc(AlertaRegraDO::getId)).stream()
                .map(r -> {
                    AlertaVOs.Regra vo = new AlertaVOs.Regra();
                    vo.setId(r.getId());
                    vo.setCodigo(r.getCodigo());
                    vo.setNome(r.getNome());
                    vo.setAtivo(r.getAtivo());
                    vo.setAbrirChamado(r.getAbrirChamado());
                    vo.setLimite(r.getLimite());
                    vo.setCategoriaId(r.getCategoriaId());
                    vo.setUrgencia(r.getUrgencia());
                    vo.setAbertos(abertos.getOrDefault(r.getCodigo(), 0L));
                    return vo;
                }).toList();
    }

    public void salvarRegra(AlertaVOs.Regra req) {
        AlertaRegraDO r = cmdbAlertaRegraMapper.selectById(req.getId());
        if (r == null) {
            throw exception(ALERTA_REGRA_NAO_EXISTE);
        }
        Integer limite = null;
        switch (r.getCodigo()) {
            case "disco_cheio" -> {
                if (req.getLimite() == null || req.getLimite() < 1 || req.getLimite() > 90) {
                    throw exception(ALERTA_LIMITE_INVALIDO, "de 1 a 90 (% livre)");
                }
                limite = req.getLimite();
            }
            case "sem_contato" -> {
                if (req.getLimite() == null || req.getLimite() < 1 || req.getLimite() > 365) {
                    throw exception(ALERTA_LIMITE_INVALIDO, "de 1 a 365 dias");
                }
                limite = req.getLimite();
            }
            default -> {
            }
        }
        cmdbAlertaRegraMapper.updateById(AlertaRegraDO.builder().id(r.getId()).ativo(req.getAtivo()).abrirChamado(req.getAbrirChamado())
                .limite(limite).categoriaId(req.getCategoriaId()).urgencia(req.getUrgencia()).build());
        if (req.getCategoriaId() == null && r.getCategoriaId() != null) {
            cmdbAlertaRegraMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AlertaRegraDO>()
                    .set(AlertaRegraDO::getCategoriaId, null).eq(AlertaRegraDO::getId, r.getId()));
        }
    }

    // ======================================================================================= consulta

    public PageResult<AlertaVOs.Resp> pagina(AlertaVOs.PaginaReq req) {
        LambdaQueryWrapperX<AlertaAtivoDO> q = new LambdaQueryWrapperX<AlertaAtivoDO>().eqIfPresent(AlertaAtivoDO::getRegra, req.getRegra());
        String s = req.getSituacao() == null ? "abertos" : req.getSituacao();
        if ("abertos".equals(s)) {
            q.isNull(AlertaAtivoDO::getFim);
        } else if ("encerrados".equals(s)) {
            q.isNotNull(AlertaAtivoDO::getFim);
        }
        if (req.getBusca() != null && !req.getBusca().isBlank()) {
            q.apply("titulo ILIKE {0}", "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        q.orderByDesc(AlertaAtivoDO::getInicio).orderByDesc(AlertaAtivoDO::getId);
        PageResult<AlertaAtivoDO> p = cmdbAlertaAtivoMapper.selectPage(req, q);
        return new PageResult<>(converter(p.getList()), p.getTotal());
    }

    public List<AlertaVOs.Resp> doAtivo(Long ativoId) {
        return converter(cmdbAlertaAtivoMapper.selectList(new LambdaQueryWrapperX<AlertaAtivoDO>().eq(AlertaAtivoDO::getObjeto, "ativo")
                .eq(AlertaAtivoDO::getObjetoId, ativoId).isNull(AlertaAtivoDO::getFim)));
    }

    private List<AlertaVOs.Resp> converter(List<AlertaAtivoDO> lista) {
        if (lista.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> nomes = cmdbAlertaRegraMapper.selectList().stream()
                .collect(Collectors.toMap(AlertaRegraDO::getCodigo, AlertaRegraDO::getNome, (a, b) -> a));
        Set<Long> depts = lista.stream().map(AlertaAtivoDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomesDept = depts.isEmpty() ? Map.of() : deptApi.getDeptList(depts).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName));
        List<AlertaVOs.Resp> res = new ArrayList<>();
        for (AlertaAtivoDO a : lista) {
            AlertaVOs.Resp vo = new AlertaVOs.Resp();
            vo.setId(a.getId());
            vo.setRegra(a.getRegra());
            vo.setRegraNome(nomes.getOrDefault(a.getRegra(), a.getRegra()));
            vo.setObjeto(a.getObjeto());
            vo.setObjetoId(a.getObjetoId());
            vo.setTitulo(a.getTitulo());
            vo.setDetalhe(a.getDetalhe());
            vo.setDeptId(a.getDeptId());
            vo.setDeptNome(a.getDeptId() == null ? null : nomesDept.get(a.getDeptId()));
            vo.setInicio(a.getInicio());
            vo.setFim(a.getFim());
            vo.setChamadoId(a.getChamadoId());
            res.add(vo);
        }
        return res;
    }

    // ======================================================================================= avaliação

    /** O que está errado agora, numa regra: chave "objeto#id" → alerta a abrir. */
    private record Achado(String objeto, Long objetoId, String titulo, String detalhe, Long deptId) {
        String chave() {
            return objeto + "#" + objetoId;
        }
    }

    /** Avalia todas as regras sobre todo o inventário (sem escopo de unidade: é uma rotina do sistema). */
    @DataPermission(enable = false)
    public synchronized AlertaVOs.Avaliacao avaliar() {
        AlertaVOs.Avaliacao res = new AlertaVOs.Avaliacao();
        LocalDateTime agora = LocalDateTime.now();
        int[] chamadosAbertos = {0};
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>()
                .eq(AtivoDO::getSituacao, "em_uso").in(AtivoDO::getTipo, COMPUTADORES));
        for (AlertaRegraDO regra : cmdbAlertaRegraMapper.selectList(new LambdaQueryWrapperX<AlertaRegraDO>().orderByAsc(AlertaRegraDO::getId))) {
            try {
                Map<String, Achado> atuais = new LinkedHashMap<>();
                if (Boolean.TRUE.equals(regra.getAtivo())) {
                    for (Achado a : achados(regra, ativos, agora)) {
                        atuais.putIfAbsent(a.chave(), a);
                    }
                }
                sincronizar(regra, atuais, agora, res, chamadosAbertos);
            } catch (RuntimeException e) {
                log.error("[inventario] falha ao avaliar a regra {}", regra.getCodigo(), e);
                res.getErros().add(regra.getCodigo() + ": " + e.getMessage());
            }
        }
        return res;
    }

    private List<Achado> achados(AlertaRegraDO regra, List<AtivoDO> ativos, LocalDateTime agora) {
        List<Achado> l = new ArrayList<>();
        switch (regra.getCodigo()) {
            case "sem_suporte" -> ativos.stream().filter(a -> Boolean.TRUE.equals(a.getSoSemSuporte()))
                    .forEach(a -> l.add(new Achado("ativo", a.getId(), "Sistema sem suporte: " + a.getNome(),
                            Objects.toString(a.getSoNome(), "") + (a.getSoVersao() == null ? "" : " (" + a.getSoVersao() + ")"), a.getDeptId())));
            case "antivirus" -> ativos.stream()
                    .filter(a -> !"manual".equals(a.getOrigem()) && Set.of("desktop", "notebook").contains(a.getTipo())
                            && Set.of("ausente", "desatualizado").contains(a.getAntivirus()))
                    .forEach(a -> l.add(new Achado("ativo", a.getId(), "Antivírus " + a.getAntivirus() + ": " + a.getNome(),
                            "Último inventário: " + quando(a.getUltimoInventario()), a.getDeptId())));
            case "disco_cheio" -> {
                int limite = regra.getLimite() == null ? 10 : regra.getLimite();
                for (AtivoDO a : ativos) {
                    if (a.getDiscoTotalMb() == null || a.getDiscoTotalMb() <= 0 || a.getDiscoLivreMb() == null) {
                        continue;
                    }
                    long pct = Math.round(100.0 * a.getDiscoLivreMb() / a.getDiscoTotalMb());
                    if (pct < limite) {
                        l.add(new Achado("ativo", a.getId(), "Disco quase cheio: " + a.getNome() + " (" + pct + "% livre)",
                                (a.getDiscoLivreMb() / 1024) + " GB livres de " + (a.getDiscoTotalMb() / 1024) + " GB", a.getDeptId()));
                    }
                }
            }
            case "sem_contato" -> {
                int dias = regra.getLimite() == null ? 30 : regra.getLimite();
                LocalDateTime corte = agora.minusDays(dias);
                ativos.stream().filter(a -> !"manual".equals(a.getOrigem()) && a.getUltimoContato() != null && a.getUltimoContato().isBefore(corte))
                        .forEach(a -> l.add(new Achado("ativo", a.getId(), "Sem comunicação há mais de " + dias + " dias: " + a.getNome(),
                                "Último contato: " + quando(a.getUltimoContato()), a.getDeptId())));
            }
            case "licenca_excedida" -> {
                Map<Long, Long> instalacoes = licencaInventarioService.instalacoesPorLicenca();
                if (!instalacoes.isEmpty()) {
                    for (LicencaDO lic : licencaMapper.selectByIds(instalacoes.keySet())) {
                        long n = instalacoes.get(lic.getId());
                        if (Boolean.TRUE.equals(lic.getAtivo()) && lic.getQuantidade() != null && lic.getQuantidade() >= 0 && n > lic.getQuantidade()) {
                            String nome = lic.getNome() == null || lic.getNome().isBlank() ? lic.getSoftware() : lic.getSoftware() + " · " + lic.getNome();
                            l.add(new Achado("licenca", lic.getId(), "Licença excedida: " + nome,
                                    n + " instalações para " + lic.getQuantidade() + " compradas", lic.getDeptId()));
                        }
                    }
                }
            }
            default -> log.warn("[inventario] regra de alerta desconhecida: {}", regra.getCodigo());
        }
        return l;
    }

    private void sincronizar(AlertaRegraDO regra, Map<String, Achado> atuais, LocalDateTime agora, AlertaVOs.Avaliacao res, int[] chamados) {
        List<AlertaAtivoDO> abertos = cmdbAlertaAtivoMapper.selectList(new LambdaQueryWrapperX<AlertaAtivoDO>()
                .eq(AlertaAtivoDO::getRegra, regra.getCodigo()).isNull(AlertaAtivoDO::getFim));
        Map<String, AlertaAtivoDO> porChave = new HashMap<>();
        for (AlertaAtivoDO a : abertos) {
            porChave.put(a.getObjeto() + "#" + a.getObjetoId(), a);
        }
        // normalizou (ou a regra foi desligada): encerra e avisa no chamado
        for (AlertaAtivoDO a : abertos) {
            if (atuais.containsKey(a.getObjeto() + "#" + a.getObjetoId())) {
                continue;
            }
            cmdbAlertaAtivoMapper.updateById(AlertaAtivoDO.builder().id(a.getId()).fim(agora).build());
            res.setEncerrados(res.getEncerrados() + 1);
            if (a.getChamadoId() != null && chamadoAberto(a.getChamadoId())) {
                String motivo = Boolean.TRUE.equals(regra.getAtivo()) ? "A condição foi normalizada no inventário"
                        : "A regra de alerta foi desligada";
                chamadoService.acompanhamentoSistema(a.getChamadoId(), null, motivo + ": " + a.getTitulo() + ".", false);
            }
        }
        // novos
        for (Achado x : atuais.values()) {
            AlertaAtivoDO a = porChave.get(x.chave());
            if (a == null) {
                a = AlertaAtivoDO.builder().regra(regra.getCodigo()).objeto(x.objeto()).objetoId(x.objetoId())
                        .titulo(cortar(x.titulo(), 255)).detalhe(cortar(x.detalhe(), 500)).deptId(x.deptId()).inicio(agora).build();
                cmdbAlertaAtivoMapper.insert(a);
                res.setAbertos(res.getAbertos() + 1);
            }
            // abre o chamado (também para alertas antigos, se a regra passou a pedir)
            if (Boolean.TRUE.equals(regra.getAbrirChamado()) && a.getChamadoId() == null && chamados[0] < MAXIMO_CHAMADOS_POR_AVALIACAO) {
                Long chamadoId = abrirChamado(regra, a);
                cmdbAlertaAtivoMapper.updateById(AlertaAtivoDO.builder().id(a.getId()).chamadoId(chamadoId).build());
                chamados[0]++;
                res.setChamados(res.getChamados() + 1);
            }
        }
    }

    private Long abrirChamado(AlertaRegraDO regra, AlertaAtivoDO a) {
        ChamadoVOs.CriarReq req = new ChamadoVOs.CriarReq();
        req.setTitulo(cortar("[Inventário] " + a.getTitulo(), 255));
        req.setDescricao("<p>Alerta automático do inventário: <strong>" + HtmlUtils.htmlEscape(regra.getNome()) + "</strong>.</p><p>"
                + HtmlUtils.htmlEscape(a.getTitulo()) + (a.getDetalhe() == null ? "" : "<br>" + HtmlUtils.htmlEscape(a.getDetalhe()))
                + "</p><p>O alerta se encerra sozinho quando o próximo inventário mostrar a condição normalizada.</p>");
        req.setUrgencia(regra.getUrgencia());
        req.setImpacto(2);
        req.setCategoriaId(regra.getCategoriaId());
        req.setOrigemId(ORIGEM_MONITORAMENTO);
        req.setDeptId(a.getDeptId());
        Long id = chamadoService.criar(req, SISTEMA);
        chamadoMapper.updateById(ChamadoDO.builder().id(id).origemAutomatica("inventario:" + regra.getCodigo()).build());
        if ("ativo".equals(a.getObjeto())) {
            cmdbChamadoAtivoMapper.insert(ChamadoAtivoDO.builder().chamadoId(id).ativoId(a.getObjetoId()).criadoEm(LocalDateTime.now()).build());
        }
        return id;
    }

    private boolean chamadoAberto(Long chamadoId) {
        ChamadoDO c = chamadoMapper.selectById(chamadoId);
        return c != null && ChamadoStatus.de(c.getStatus()).aberto();
    }

    private static String quando(LocalDateTime t) {
        return t == null ? "—" : t.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
