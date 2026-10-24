package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CadastroVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Categorias (árvore), equipes técnicas e origens.
 */
@Service
public class CadastroService {

    @Resource
    private CategoriaMapper categoriaMapper;
    @Resource
    private EquipeMapper equipeMapper;
    @Resource
    private EquipeMembroMapper equipeMembroMapper;
    @Resource
    private OrigemMapper origemMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private SlaService slaService;

    // ======================= categorias =======================

    public List<CategoriaDO> listarCategorias(boolean somenteAtivas, boolean somentePortal) {
        return categoriaMapper.selectList(new LambdaQueryWrapperX<CategoriaDO>()
                .eqIfPresent(CategoriaDO::getAtivo, somenteAtivas ? Boolean.TRUE : null)
                .eqIfPresent(CategoriaDO::getVisivelPortal, somentePortal ? Boolean.TRUE : null)
                .orderByAsc(CategoriaDO::getNomeCompleto));
    }

    public CategoriaDO obterCategoria(Long id) {
        CategoriaDO c = id == null ? null : categoriaMapper.selectById(id);
        if (c == null) {
            throw exception(CATEGORIA_NAO_EXISTE);
        }
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarCategoria(CadastroVOs.CategoriaSalvar req) {
        Long pai = req.getParentId() == null ? CategoriaDO.RAIZ : req.getParentId();
        CategoriaDO atual = req.getId() == null ? null : obterCategoria(req.getId());
        validarPai(req.getId(), pai);
        validarNomeUnico(req.getId(), pai, req.getNome());
        String codigo = req.getCodigo() == null || req.getCodigo().isBlank() ? null : req.getCodigo().trim();
        if (codigo != null) {
            CategoriaDO mesmo = categoriaMapper.selectOne(CategoriaDO::getCodigo, codigo);
            if (mesmo != null && !mesmo.getId().equals(req.getId())) {
                throw exception(CATEGORIA_CODIGO_DUPLICADO);
            }
        }
        if (req.getEquipeId() != null) {
            obterEquipe(req.getEquipeId());
        }
        slaService.validarTipo(req.getSlaAtendimentoId(), SlaService.TIPO_ATENDIMENTO);
        slaService.validarTipo(req.getSlaSolucaoId(), SlaService.TIPO_SOLUCAO);
        CategoriaDO c = BeanUtils.toBean(req, CategoriaDO.class);
        c.setParentId(pai);
        c.setNome(req.getNome().trim());
        c.setCodigo(codigo);
        CategoriaDO paiDO = pai.equals(CategoriaDO.RAIZ) ? null : obterCategoria(pai);
        c.setNivel(paiDO == null ? 1 : paiDO.getNivel() + 1);
        c.setNomeCompleto(paiDO == null ? c.getNome() : paiDO.getNomeCompleto() + " > " + c.getNome());
        if (atual == null) {
            categoriaMapper.insert(c);
        } else {
            categoriaMapper.updateById(c);
            if (!Objects.equals(atual.getNomeCompleto(), c.getNomeCompleto())) {
                atualizarDescendentes(c);
            }
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirCategoria(Long id) {
        obterCategoria(id);
        if (categoriaMapper.selectCount(CategoriaDO::getParentId, id) > 0) {
            throw exception(CATEGORIA_TEM_FILHAS);
        }
        if (chamadoMapper.selectCount(ChamadoDO::getCategoriaId, id) > 0) {
            throw exception(CATEGORIA_EM_USO);
        }
        categoriaMapper.deleteById(id);
    }

    /** Ids da categoria e de todas as descendentes. */
    public Set<Long> descendentes(Long id) {
        Map<Long, List<CategoriaDO>> porPai = categoriaMapper.selectList().stream()
                .collect(Collectors.groupingBy(CategoriaDO::getParentId));
        Set<Long> out = new LinkedHashSet<>();
        Deque<Long> fila = new ArrayDeque<>(List.of(id));
        while (!fila.isEmpty()) {
            Long atual = fila.poll();
            if (out.add(atual)) {
                porPai.getOrDefault(atual, List.of()).forEach(f -> fila.add(f.getId()));
            }
        }
        return out;
    }

    private void validarPai(Long id, Long pai) {
        if (pai.equals(CategoriaDO.RAIZ)) {
            return;
        }
        obterCategoria(pai);
        if (id != null && descendentes(id).contains(pai)) {
            throw exception(CATEGORIA_PAI_INVALIDA);
        }
    }

    private void validarNomeUnico(Long id, Long pai, String nome) {
        CategoriaDO mesmo = categoriaMapper.selectOne(new LambdaQueryWrapperX<CategoriaDO>()
                .eq(CategoriaDO::getParentId, pai)
                .apply("lower(nome) = lower({0})", nome.trim()));
        if (mesmo != null && !mesmo.getId().equals(id)) {
            throw exception(CATEGORIA_NOME_DUPLICADO);
        }
    }

    private void atualizarDescendentes(CategoriaDO pai) {
        for (CategoriaDO f : categoriaMapper.selectList(CategoriaDO::getParentId, pai.getId())) {
            f.setNivel(pai.getNivel() + 1);
            f.setNomeCompleto(pai.getNomeCompleto() + " > " + f.getNome());
            categoriaMapper.updateById(f);
            atualizarDescendentes(f);
        }
    }

    // ======================= equipes =======================

    public List<CadastroVOs.EquipeResp> listarEquipes(boolean somenteAtivas) {
        List<EquipeDO> equipes = equipeMapper.selectList(new LambdaQueryWrapperX<EquipeDO>()
                .eqIfPresent(EquipeDO::getAtivo, somenteAtivas ? Boolean.TRUE : null).orderByAsc(EquipeDO::getNome));
        List<EquipeMembroDO> membros = equipes.isEmpty() ? List.of()
                : equipeMembroMapper.selectList(EquipeMembroDO::getEquipeId, equipes.stream().map(EquipeDO::getId).toList());
        Map<Long, AdminUserRespDTO> usuarios = adminUserApi.getUserMap(
                membros.stream().map(EquipeMembroDO::getUserId).collect(Collectors.toSet()));
        Map<Long, List<EquipeMembroDO>> porEquipe = membros.stream().collect(Collectors.groupingBy(EquipeMembroDO::getEquipeId));
        return equipes.stream().map(e -> {
            CadastroVOs.EquipeResp r = BeanUtils.toBean(e, CadastroVOs.EquipeResp.class);
            r.setMembros(porEquipe.getOrDefault(e.getId(), List.of()).stream().map(m -> {
                CadastroVOs.Membro x = new CadastroVOs.Membro();
                x.setUserId(m.getUserId());
                x.setSupervisor(m.getSupervisor());
                AdminUserRespDTO u = usuarios.get(m.getUserId());
                x.setNome(u == null ? "#" + m.getUserId() : u.getNickname());
                return x;
            }).toList());
            return r;
        }).toList();
    }

    public EquipeDO obterEquipe(Long id) {
        EquipeDO e = id == null ? null : equipeMapper.selectById(id);
        if (e == null) {
            throw exception(EQUIPE_NAO_EXISTE);
        }
        return e;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarEquipe(CadastroVOs.EquipeSalvar req) {
        if (req.getId() != null) {
            obterEquipe(req.getId());
        }
        EquipeDO mesmo = equipeMapper.selectOne(new LambdaQueryWrapperX<EquipeDO>()
                .apply("lower(nome) = lower({0})", req.getNome().trim()));
        if (mesmo != null && !mesmo.getId().equals(req.getId())) {
            throw exception(EQUIPE_NOME_DUPLICADO);
        }
        EquipeDO e = BeanUtils.toBean(req, EquipeDO.class);
        e.setNome(req.getNome().trim());
        if (e.getId() == null) {
            equipeMapper.insert(e);
        } else {
            equipeMapper.updateById(e);
        }
        // membros: substitui a lista (exclui quem saiu, inclui quem entrou, atualiza supervisor)
        Map<Long, EquipeMembroDO> atuais = equipeMembroMapper.selectList(EquipeMembroDO::getEquipeId, e.getId()).stream()
                .collect(Collectors.toMap(EquipeMembroDO::getUserId, m -> m, (a, b) -> a));
        Set<Long> novos = new HashSet<>();
        if (!req.getMembros().isEmpty()) {
            adminUserApi.validateUserList(req.getMembros().stream().map(CadastroVOs.Membro::getUserId).toList());
        }
        for (CadastroVOs.Membro m : req.getMembros()) {
            if (!novos.add(m.getUserId())) {
                continue;
            }
            EquipeMembroDO existente = atuais.get(m.getUserId());
            boolean supervisor = Boolean.TRUE.equals(m.getSupervisor());
            if (existente == null) {
                equipeMembroMapper.insert(EquipeMembroDO.builder().equipeId(e.getId()).userId(m.getUserId())
                        .supervisor(supervisor).build());
            } else if (!Objects.equals(existente.getSupervisor(), supervisor)) {
                existente.setSupervisor(supervisor);
                equipeMembroMapper.updateById(existente);
            }
        }
        atuais.values().stream().filter(m -> !novos.contains(m.getUserId()))
                .forEach(m -> equipeMembroMapper.deleteById(m.getId()));
        return e.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirEquipe(Long id) {
        obterEquipe(id);
        boolean emUso = chamadoAtorMapper.selectList(ChamadoAtorDO::getEquipeId, id).stream()
                .map(a -> chamadoMapper.selectById(a.getChamadoId()))
                .anyMatch(c -> c != null && ChamadoStatus.de(c.getStatus()).aberto());
        if (emUso) {
            throw exception(EQUIPE_EM_USO);
        }
        equipeMembroMapper.delete(EquipeMembroDO::getEquipeId, id);
        equipeMapper.deleteById(id);
    }

    /** Equipes de que o usuário é membro. */
    public List<Long> equipesDoUsuario(Long userId) {
        return equipeMembroMapper.selectList(EquipeMembroDO::getUserId, userId).stream()
                .map(EquipeMembroDO::getEquipeId).toList();
    }

    // ======================= origens =======================

    public List<OrigemDO> listarOrigens(boolean somenteAtivas) {
        return origemMapper.selectList(new LambdaQueryWrapperX<OrigemDO>()
                .eqIfPresent(OrigemDO::getAtivo, somenteAtivas ? Boolean.TRUE : null).orderByAsc(OrigemDO::getId));
    }

    public OrigemDO obterOrigem(Long id) {
        OrigemDO o = id == null ? null : origemMapper.selectById(id);
        if (o == null) {
            throw exception(ORIGEM_NAO_EXISTE);
        }
        return o;
    }

    /** Origem padrão do portal (ou e-mail), para chamados abertos sem origem informada. */
    public Long origemPadrao(boolean email) {
        return listarOrigens(true).stream()
                .filter(o -> email ? Boolean.TRUE.equals(o.getPadraoEmail()) : Boolean.TRUE.equals(o.getPadraoPortal()))
                .map(OrigemDO::getId).findFirst().orElse(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarOrigem(CadastroVOs.OrigemSalvar req) {
        if (req.getId() != null) {
            obterOrigem(req.getId());
        }
        OrigemDO o = BeanUtils.toBean(req, OrigemDO.class);
        o.setNome(req.getNome().trim());
        // só uma origem padrão de cada tipo
        if (Boolean.TRUE.equals(o.getPadraoPortal())) {
            listarOrigens(false).stream().filter(x -> Boolean.TRUE.equals(x.getPadraoPortal()) && !x.getId().equals(o.getId()))
                    .forEach(x -> origemMapper.updateById(OrigemDO.builder().id(x.getId()).padraoPortal(false).build()));
        }
        if (Boolean.TRUE.equals(o.getPadraoEmail())) {
            listarOrigens(false).stream().filter(x -> Boolean.TRUE.equals(x.getPadraoEmail()) && !x.getId().equals(o.getId()))
                    .forEach(x -> origemMapper.updateById(OrigemDO.builder().id(x.getId()).padraoEmail(false).build()));
        }
        if (o.getId() == null) {
            origemMapper.insert(o);
        } else {
            origemMapper.updateById(o);
        }
        return o.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirOrigem(Long id) {
        obterOrigem(id);
        if (chamadoMapper.selectCount(ChamadoDO::getOrigemId, id) > 0
                || interacaoMapper.selectCount(InteracaoDO::getOrigemId, id) > 0) {
            throw exception(ORIGEM_EM_USO);
        }
        origemMapper.deleteById(id);
    }

}
