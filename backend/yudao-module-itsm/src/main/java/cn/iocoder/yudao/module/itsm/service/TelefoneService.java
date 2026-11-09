package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.UnitKpiVO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import cn.iocoder.yudao.module.system.service.monitor.MonitorKpiService;
import jakarta.annotation.Resource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Lista de ramais e telefones, a mesma para todas as unidades: quem liga precisa achar o número de outra unidade.
 * Cada contato pode apontar para uma unidade do monitoramento: com o link dela fora, o ramal VoIP para junto, e a lista
 * avisa e destaca o celular.
 *
 * <p>O celular é dado pessoal: só sai daqui para quem tem {@link #P_CELULAR} ou mantém a lista, salvo os corporativos.
 */
@Service
public class TelefoneService {

    static final String P_CELULAR = "itsm:telefone:celular";
    static final String P_UPDATE = "itsm:telefone:update";
    private static final int MAX_LINHAS = 5000;
    private static final int MAX_ERROS = 100;

    @Resource
    private TelefoneMapper telefoneMapper;
    @Resource
    private MonitorUnitMapper unitMapper;
    @Resource
    private MonitorKpiService kpiService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private Validator validator;

    @DataPermission(enable = false)
    public PageResult<TelefoneVOs.Resp> pagina(TelefoneVOs.PaginaReq req, Long userId) {
        boolean ver = verCelular(userId);
        PageResult<TelefoneDO> p = telefoneMapper.pagina(req, ver);
        Map<Long, String> nomes = nomesDasUnidades();
        return new PageResult<>(p.getList().stream().map(t -> resp(t, nomes, ver)).toList(), p.getTotal());
    }

    public List<String> setores() {
        return telefoneMapper.setores();
    }

    /** Para o filtro e o cadastro. A lista é a mesma para todos, sem o recorte por setor de quem consulta. */
    @DataPermission(enable = false)
    public List<TelefoneVOs.Unidade> unidades() {
        return unitMapper.selectActiveList().stream().sorted(Comparator.comparing(MonitorUnitDO::getName)).map(u -> {
            TelefoneVOs.Unidade r = new TelefoneVOs.Unidade();
            r.setId(u.getId());
            r.setNome(u.getName());
            r.setLinkFora(linkFora(u.getId()));
            return r;
        }).toList();
    }

    /** Contatos da unidade pedida ou da unidade a que o setor pertence; null se o setor não estiver em nenhuma. */
    @DataPermission(enable = false)
    public TelefoneVOs.ContatosUnidade daUnidade(Long unidadeId, Long deptId, Long userId) {
        MonitorUnitDO u = unidadeId != null ? unitMapper.selectById(unidadeId) : unidadeDoSetor(deptId);
        if (u == null) {
            return null;
        }
        boolean ver = verCelular(userId);
        Map<Long, String> nomes = Map.of(u.getId(), u.getName());
        TelefoneVOs.ContatosUnidade r = new TelefoneVOs.ContatosUnidade();
        r.setUnidadeId(u.getId());
        r.setUnidade(u.getName());
        r.setLinkFora(linkFora(u.getId()));
        r.setContatos(telefoneMapper.daUnidade(u.getId()).stream().map(t -> resp(t, nomes, ver)).toList());
        return r;
    }

    @DataPermission(enable = false)
    public Long salvar(TelefoneVOs.SalvarReq req) {
        if (req.getId() != null) {
            obter(req.getId());
        }
        TelefoneDO t = montar(req);
        if (req.getId() == null) {
            telefoneMapper.insert(t);
        } else {
            telefoneMapper.updateById(t);
        }
        return t.getId();
    }

    public void excluir(Long id) {
        obter(id);
        telefoneMapper.deleteById(id);
    }

    /** Modelo vazio para preencher, com os nomes das unidades em outra aba para copiar. */
    @DataPermission(enable = false)
    public List<Planilha.Aba> modelo() {
        List<List<Object>> unidades = new ArrayList<>();
        unitMapper.selectActiveList().stream().map(MonitorUnitDO::getName).sorted()
                .forEach(n -> unidades.add(List.of(n)));
        return List.of(new Planilha.Aba("Contatos", TelefonePlanilha.COLUNAS, List.of()),
                new Planilha.Aba("Unidades", List.of("Unidade"), unidades));
    }

    /**
     * Grava linha a linha: uma linha com erro não impede as outras. O contato com o mesmo nome na mesma unidade é
     * atualizado, então dá para corrigir a planilha e enviar de novo.
     */
    @DataPermission(enable = false)
    public TelefoneVOs.ImportacaoResp importar(InputStream arquivo) {
        List<Planilha.Linha> linhas;
        try {
            linhas = TelefonePlanilha.ler(arquivo, MAX_LINHAS);
        } catch (IOException e) {
            throw exception(TELEFONE_PLANILHA_INVALIDA);
        }
        Map<String, Long> unidades = new HashMap<>();
        unitMapper.selectActiveList().forEach(u -> unidades.putIfAbsent(chave(u.getName()), u.getId()));
        Map<String, TelefoneDO> existentes = new HashMap<>();
        telefoneMapper.selectList().forEach(t -> existentes.putIfAbsent(chave(t.getNome(), t.getUnidadeId()), t));

        TelefoneVOs.ImportacaoResp r = new TelefoneVOs.ImportacaoResp();
        for (Planilha.Linha l : linhas) {
            try {
                TelefoneVOs.SalvarReq req = requisicao(l.campos(), unidades, existentes);
                validar(req);
                TelefoneDO t = montar(req);
                if (t.getId() == null) {
                    telefoneMapper.insert(t);
                    r.setCriados(r.getCriados() + 1);
                } else {
                    telefoneMapper.updateById(t);
                    r.setAtualizados(r.getAtualizados() + 1);
                }
                existentes.put(chave(t.getNome(), t.getUnidadeId()), t);
            } catch (ServiceException | IllegalArgumentException e) {
                r.setRecusados(r.getRecusados() + 1);
                if (r.getErros().size() < MAX_ERROS) {
                    r.getErros().add("Linha " + l.numero() + ": " + e.getMessage());
                }
            }
        }
        return r;
    }

    /**
     * O que o filtro alcança, nas colunas que o Importar lê. Para quem não pode ver celulares, Celular e WhatsApp nem vêm: como
     * coluna ausente mantém o cadastro, reimportar esse arquivo não apaga os números.
     */
    public List<Planilha.Aba> planilha(TelefoneVOs.PaginaReq req, Long userId) {
        req.setPageSize(PageParam.PAGE_SIZE_NONE);
        boolean ver = verCelular(userId);
        int celular = TelefonePlanilha.COLUNAS.indexOf("Celular");
        int whatsapp = TelefonePlanilha.COLUNAS.indexOf("WhatsApp");
        List<String> colunas = new ArrayList<>(TelefonePlanilha.COLUNAS);
        if (!ver) {
            colunas.remove(whatsapp);
            colunas.remove(celular);
        }
        List<List<Object>> linhas = pagina(req, userId).getList().stream().map(t -> {
            List<Object> l = new ArrayList<>(Arrays.asList(t.getNome(), t.getUnidade(), t.getSetor(), t.getRamal(), t.getTelefone(),
                    t.getCelular(), Planilha.simNao(t.getWhatsapp()), Planilha.simNao(t.getEmergencia()),
                    Planilha.simNao(t.getCelularPublico()), t.getEmail(), t.getObservacao()));
            if (!ver) {
                l.remove(whatsapp);
                l.remove(celular);
            }
            return l;
        }).toList();
        return List.of(new Planilha.Aba("Contatos", colunas, linhas));
    }

    // ------------------------------------------------------------------ internos

    private TelefoneVOs.SalvarReq requisicao(Map<String, String> c, Map<String, Long> unidades, Map<String, TelefoneDO> existentes) {
        String nome = c.getOrDefault(TelefonePlanilha.NOME, "");
        if (nome.isBlank()) {
            throw new IllegalArgumentException("informe o nome");
        }
        Long unidadeId = null;
        String unidade = c.getOrDefault(TelefonePlanilha.UNIDADE, "");
        if (!unidade.isBlank()) {
            unidadeId = unidades.get(chave(unidade));
            if (unidadeId == null) {
                throw new IllegalArgumentException("unidade \"" + unidade + "\" não encontrada (veja a aba Unidades do modelo)");
            }
        }
        // coluna que não veio na planilha mantém o valor cadastrado
        TelefoneDO a = existentes.get(chave(nome, unidadeId));
        TelefoneVOs.SalvarReq r = new TelefoneVOs.SalvarReq();
        r.setId(a == null ? null : a.getId());
        r.setNome(nome);
        r.setUnidadeId(unidadeId);
        r.setSetor(c.getOrDefault(TelefonePlanilha.SETOR, a == null ? null : a.getSetor()));
        r.setRamal(c.getOrDefault(TelefonePlanilha.RAMAL, a == null ? null : a.getRamal()));
        r.setTelefone(c.getOrDefault(TelefonePlanilha.TELEFONE, a == null ? null : a.getTelefone()));
        r.setCelular(c.getOrDefault(TelefonePlanilha.CELULAR, a == null ? null : a.getCelular()));
        r.setEmail(c.getOrDefault(TelefonePlanilha.EMAIL, a == null ? null : a.getEmail()));
        r.setObservacao(c.getOrDefault(TelefonePlanilha.OBSERVACAO, a == null ? null : a.getObservacao()));
        r.setWhatsapp(c.containsKey(TelefonePlanilha.WHATSAPP) ? TelefonePlanilha.simNao(c.get(TelefonePlanilha.WHATSAPP))
                : a != null && Boolean.TRUE.equals(a.getWhatsapp()));
        r.setEmergencia(c.containsKey(TelefonePlanilha.EMERGENCIA) ? TelefonePlanilha.simNao(c.get(TelefonePlanilha.EMERGENCIA))
                : a != null && Boolean.TRUE.equals(a.getEmergencia()));
        r.setCelularPublico(c.containsKey(TelefonePlanilha.PUBLICO) ? TelefonePlanilha.simNao(c.get(TelefonePlanilha.PUBLICO))
                : a != null && Boolean.TRUE.equals(a.getCelularPublico()));
        return r;
    }

    /** Pela tela, o Spring já validou; a planilha passa pelas mesmas regras aqui. */
    private void validar(TelefoneVOs.SalvarReq req) {
        Set<ConstraintViolation<TelefoneVOs.SalvarReq>> erros = validator.validate(req);
        if (!erros.isEmpty()) {
            throw new IllegalArgumentException(erros.iterator().next().getMessage());
        }
    }

    private TelefoneDO montar(TelefoneVOs.SalvarReq req) {
        String celular = vazioNulo(req.getCelular());
        if (celular != null) {
            int digitos = celular.replaceAll("[^0-9]", "").length();
            if (digitos < 10 || digitos > 13) {
                throw exception(TELEFONE_CELULAR_INVALIDO);
            }
        }
        TelefoneDO t = TelefoneDO.builder().id(req.getId()).nome(req.getNome().trim())
                .setor(vazioNulo(req.getSetor())).unidadeId(req.getUnidadeId())
                .ramal(vazioNulo(req.getRamal())).telefone(vazioNulo(req.getTelefone())).celular(celular)
                // sem celular, não há o que marcar
                .whatsapp(celular != null && Boolean.TRUE.equals(req.getWhatsapp()))
                .celularPublico(celular != null && Boolean.TRUE.equals(req.getCelularPublico()))
                .emergencia(Boolean.TRUE.equals(req.getEmergencia()))
                .email(vazioNulo(req.getEmail())).observacao(vazioNulo(req.getObservacao())).build();
        if (t.getRamal() == null && t.getTelefone() == null && t.getCelular() == null) {
            throw exception(TELEFONE_SEM_NUMERO);
        }
        if (t.getUnidadeId() != null && unitMapper.selectById(t.getUnidadeId()) == null) {
            throw exception(TELEFONE_UNIDADE_NAO_EXISTE);
        }
        // sem rede na unidade, só o celular chega: é o que define um contato de emergência
        if (t.getEmergencia() && (t.getCelular() == null || t.getUnidadeId() == null)) {
            throw exception(TELEFONE_EMERGENCIA_INCOMPLETO);
        }
        return t;
    }

    private TelefoneVOs.Resp resp(TelefoneDO t, Map<Long, String> nomes, boolean verCelular) {
        TelefoneVOs.Resp r = new TelefoneVOs.Resp();
        r.setId(t.getId());
        r.setNome(t.getNome());
        r.setSetor(t.getSetor());
        r.setUnidadeId(t.getUnidadeId());
        r.setUnidade(nomes.get(t.getUnidadeId()));
        r.setRamal(t.getRamal());
        r.setTelefone(t.getTelefone());
        boolean mostrar = verCelular || Boolean.TRUE.equals(t.getCelularPublico());
        r.setCelular(mostrar ? t.getCelular() : null);
        r.setWhatsapp(mostrar && Boolean.TRUE.equals(t.getWhatsapp()));
        r.setCelularOculto(!mostrar && t.getCelular() != null);
        r.setCelularPublico(Boolean.TRUE.equals(t.getCelularPublico()));
        r.setEmergencia(Boolean.TRUE.equals(t.getEmergencia()));
        r.setEmail(t.getEmail());
        r.setObservacao(t.getObservacao());
        r.setLinkFora(t.getUnidadeId() != null && linkFora(t.getUnidadeId()));
        return r;
    }

    private boolean verCelular(Long userId) {
        return permissionApi.hasAnyPermissions(userId, P_CELULAR, P_UPDATE);
    }

    private boolean linkFora(Long unidadeId) {
        UnitKpiVO kpi = kpiService.getKpi(unidadeId);
        return kpi != null && "offline".equals(kpi.getLink());
    }

    private Map<Long, String> nomesDasUnidades() {
        Map<Long, String> nomes = new HashMap<>();
        unitMapper.selectList().forEach(u -> nomes.put(u.getId(), u.getName()));
        return nomes;
    }

    /** Sobe pela árvore de setores até o setor raiz de uma unidade. */
    private MonitorUnitDO unidadeDoSetor(Long deptId) {
        if (deptId == null) {
            return null;
        }
        Map<Long, MonitorUnitDO> porSetor = new HashMap<>();
        unitMapper.selectActiveList().forEach(u -> porSetor.putIfAbsent(u.getDeptId(), u));
        Long atual = deptId;
        for (int nivel = 0; atual != null && nivel < 20; nivel++) {
            MonitorUnitDO u = porSetor.get(atual);
            if (u != null) {
                return u;
            }
            DeptRespDTO d = deptApi.getDept(atual);
            atual = d == null ? null : d.getParentId();
        }
        return null;
    }

    private void obter(Long id) {
        if (telefoneMapper.selectById(id) == null) {
            throw exception(TELEFONE_NAO_EXISTE);
        }
    }

    private static String chave(String nome, Long unidadeId) {
        return chave(nome) + "|" + unidadeId;
    }

    private static String chave(String nome) {
        return nome.trim().toLowerCase(Locale.ROOT);
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
