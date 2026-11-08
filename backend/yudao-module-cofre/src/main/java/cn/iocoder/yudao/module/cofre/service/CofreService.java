package cn.iocoder.yudao.module.cofre.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.cofre.controller.admin.vo.CofreVOs;
import cn.iocoder.yudao.module.cofre.dal.dataobject.PastaDO;
import cn.iocoder.yudao.module.cofre.dal.dataobject.PastaMembroDO;
import cn.iocoder.yudao.module.cofre.dal.dataobject.SegredoDO;
import cn.iocoder.yudao.module.cofre.dal.mysql.PastaMapper;
import cn.iocoder.yudao.module.cofre.dal.mysql.PastaMembroMapper;
import cn.iocoder.yudao.module.cofre.dal.mysql.SegredoMapper;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.identidade.service.MfaService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.cofre.dal.dataobject.PastaMembroDO.*;
import static cn.iocoder.yudao.module.cofre.enums.ErrorCodeConstants.*;

/**
 * Cofre de senhas da equipe de TI.
 *
 * <ul>
 *   <li>Só vê uma pasta quem é membro dela. Quem administra o cofre decide os membros de qualquer pasta, mas não lê
 *       senha sem antes se incluir, e isso fica na trilha de auditoria.</li>
 *   <li>Senha e notas só saem por {@link #revelar}, que registra quem viu.</li>
 *   <li>O id do segredo entra na cifragem: texto cifrado copiado para outra linha não abre.</li>
 * </ul>
 */
@Service
public class CofreService {

    static final String PERMISSAO_ADMINISTRAR = "cofre:pasta:administrar";

    @Resource
    private PastaMapper pastaMapper;
    @Resource
    private PastaMembroMapper membroMapper;
    @Resource
    private SegredoMapper segredoMapper;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private MfaService mfaService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Value("${coti.cofre.exigir-mfa:true}")
    private boolean exigirMfa = true;

    // ------------------------------------------------------------------ pastas

    public CofreVOs.Resumo resumo(Long userId) {
        boolean admin = administra(userId);
        Map<Long, Integer> meus = membroMapper.selectList(PastaMembroDO::getUserId, userId).stream()
                .collect(Collectors.toMap(PastaMembroDO::getPastaId, PastaMembroDO::getPapel));
        List<PastaDO> pastas = admin ? pastaMapper.selectList()
                : meus.isEmpty() ? List.of() : pastaMapper.selectBatchIds(meus.keySet());
        List<CofreVOs.Pasta> lista = pastas.stream().sorted(Comparator.comparing(PastaDO::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    Integer papel = meus.get(p.getId());
                    CofreVOs.Pasta v = new CofreVOs.Pasta();
                    v.setId(p.getId());
                    v.setNome(p.getNome());
                    v.setDescricao(p.getDescricao());
                    v.setMeuPapel(papel);
                    // quem só administra não vê nem quantas senhas há na pasta
                    v.setTotalSegredos(papel == null ? null : segredoMapper.selectCount(SegredoDO::getPastaId, p.getId()));
                    v.setPodeGerenciar(admin || Objects.equals(papel, GERENCIAR));
                    return v;
                }).toList();
        CofreVOs.Resumo r = new CofreVOs.Resumo();
        r.setPastas(lista);
        r.setPodeAdministrar(admin);
        r.setPodeRevelar(!exigirMfa || mfaService.exige(userId));
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarPasta(CofreVOs.PastaReq req, Long userId) {
        String nome = req.getNome().trim();
        String descricao = vazioNulo(req.getDescricao());
        try {
            if (req.getId() == null) {
                PastaDO p = PastaDO.builder().nome(nome).descricao(descricao).build();
                pastaMapper.insert(p);
                membroMapper.insert(PastaMembroDO.builder().pastaId(p.getId()).userId(userId).papel(GERENCIAR).build());
                auditoria.registrar("cofre.pasta_criada", userId, null, Map.of("pasta", p.getId(), "nome", nome));
                return p.getId();
            }
            exigirGestao(pastaExistente(req.getId()).getId(), userId);
            pastaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PastaDO>()
                    .eq(PastaDO::getId, req.getId()).set(PastaDO::getNome, nome).set(PastaDO::getDescricao, descricao));
            auditoria.registrar("cofre.pasta_alterada", userId, null, Map.of("pasta", req.getId(), "nome", nome));
            return req.getId();
        } catch (DuplicateKeyException e) {
            throw exception(PASTA_NOME_REPETIDO);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirPasta(Long id, Long userId) {
        PastaDO p = pastaExistente(id);
        exigirGestao(id, userId);
        if (segredoMapper.selectCount(SegredoDO::getPastaId, id) > 0) {
            throw exception(PASTA_COM_SEGREDOS);
        }
        membroMapper.delete(PastaMembroDO::getPastaId, id);
        pastaMapper.deleteById(id);
        auditoria.registrar("cofre.pasta_excluida", userId, null, Map.of("pasta", id, "nome", p.getNome()));
    }

    public List<CofreVOs.Membro> membros(Long pastaId, Long userId) {
        pastaExistente(pastaId);
        exigirGestao(pastaId, userId);
        List<PastaMembroDO> lista = membroMapper.selectList(PastaMembroDO::getPastaId, pastaId);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(lista.stream().map(PastaMembroDO::getUserId).toList());
        return lista.stream().map(m -> {
            CofreVOs.Membro v = new CofreVOs.Membro();
            v.setUserId(m.getUserId());
            v.setPapel(m.getPapel());
            AdminUserRespDTO u = nomes.get(m.getUserId());
            v.setNome(u == null ? "#" + m.getUserId() : u.getNickname());
            return v;
        }).sorted(Comparator.comparing(CofreVOs.Membro::getNome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvarMembro(CofreVOs.MembroReq req, Long userId) {
        pastaExistente(req.getPastaId());
        exigirGestao(req.getPastaId(), userId);
        if (req.getPapel() < VER || req.getPapel() > GERENCIAR) {
            throw exception(PAPEL_INVALIDO);
        }
        adminUserApi.validateUser(req.getUserId());
        PastaMembroDO atual = membro(req.getPastaId(), req.getUserId());
        if (atual == null) {
            membroMapper.insert(PastaMembroDO.builder().pastaId(req.getPastaId()).userId(req.getUserId()).papel(req.getPapel()).build());
        } else {
            if (Objects.equals(atual.getPapel(), GERENCIAR) && req.getPapel() != GERENCIAR) {
                exigirOutroGestor(req.getPastaId(), req.getUserId());
            }
            membroMapper.updateById(PastaMembroDO.builder().id(atual.getId()).papel(req.getPapel()).build());
        }
        auditoria.registrar("cofre.membro_alterado", userId, null,
                Map.of("pasta", req.getPastaId(), "pessoa", req.getUserId(), "papel", req.getPapel()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void removerMembro(Long pastaId, Long alvoId, Long userId) {
        pastaExistente(pastaId);
        exigirGestao(pastaId, userId);
        PastaMembroDO atual = membro(pastaId, alvoId);
        if (atual == null) {
            return;
        }
        if (Objects.equals(atual.getPapel(), GERENCIAR)) {
            exigirOutroGestor(pastaId, alvoId);
        }
        membroMapper.deleteById(atual.getId());
        auditoria.registrar("cofre.membro_removido", userId, null, Map.of("pasta", pastaId, "pessoa", alvoId));
    }

    // ------------------------------------------------------------------ senhas

    /** Sem a senha nem as notas. {@code pastaId} vazio busca em todas as pastas de que a pessoa é membro. */
    public List<CofreVOs.Segredo> segredos(Long pastaId, String busca, Long userId) {
        Map<Long, Integer> meus = membroMapper.selectList(PastaMembroDO::getUserId, userId).stream()
                .collect(Collectors.toMap(PastaMembroDO::getPastaId, PastaMembroDO::getPapel));
        if (pastaId != null && !meus.containsKey(pastaId)) {
            throw exception(PASTA_SEM_ACESSO);
        }
        if (meus.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapperX<SegredoDO> q = new LambdaQueryWrapperX<>();
        q.in(SegredoDO::getPastaId, pastaId != null ? Set.of(pastaId) : meus.keySet());
        q.orderByAsc(SegredoDO::getNome);
        String termo = vazioNulo(busca);
        if (termo != null) {
            String like = "%" + termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nome ILIKE {0}", like).or().apply("usuario ILIKE {0}", like).or().apply("url ILIKE {0}", like));
        }
        List<SegredoDO> lista = segredoMapper.selectList(q);
        Map<Long, String> nomes = lista.isEmpty() ? Map.of() : pastaMapper.selectBatchIds(
                lista.stream().map(SegredoDO::getPastaId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PastaDO::getId, PastaDO::getNome));
        return lista.stream().map(s -> {
            CofreVOs.Segredo v = new CofreVOs.Segredo();
            v.setId(s.getId());
            v.setPastaId(s.getPastaId());
            v.setPasta(nomes.get(s.getPastaId()));
            v.setNome(s.getNome());
            v.setUsuario(s.getUsuario());
            v.setUrl(s.getUrl());
            v.setTemNotas(s.getNotasCifradas() != null);
            v.setSenhaAlteradaEm(s.getSenhaAlteradaEm());
            v.setPodeEditar(meus.getOrDefault(s.getPastaId(), 0) >= EDITAR);
            return v;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarSegredo(CofreVOs.SegredoReq req, Long userId) {
        exigirChave();
        pastaExistente(req.getPastaId());
        exigirPapel(req.getPastaId(), userId, EDITAR);
        String senha = req.getSenha();
        boolean temSenha = senha != null && !senha.isEmpty();
        String nome = req.getNome().trim();
        String usuario = vazioNulo(req.getUsuario());
        String url = vazioNulo(req.getUrl());

        if (req.getId() == null) {
            if (!temSenha) {
                throw exception(SEGREDO_SENHA_OBRIGATORIA);
            }
            Long id = segredoMapper.proximoId();
            SegredoDO s = SegredoDO.builder().id(id).pastaId(req.getPastaId()).nome(nome).usuario(usuario).url(url)
                    .senhaCifrada(cifrador.cifrar(senha, contextoSenha(id)))
                    .notasCifradas(vazioNulo(req.getNotas()) == null ? null : cifrador.cifrar(req.getNotas(), contextoNotas(id)))
                    .senhaAlteradaEm(LocalDateTime.now()).build();
            segredoMapper.insert(s);
            auditoria.registrar("cofre.senha_criada", userId, null, Map.of("segredo", id, "pasta", req.getPastaId(), "nome", nome));
            return id;
        }

        SegredoDO atual = segredoExistente(req.getId());
        // mudar de pasta pede o papel de edição nas duas
        exigirPapel(atual.getPastaId(), userId, EDITAR);
        var u = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SegredoDO>()
                .eq(SegredoDO::getId, atual.getId()).set(SegredoDO::getPastaId, req.getPastaId()).set(SegredoDO::getNome, nome)
                .set(SegredoDO::getUsuario, usuario).set(SegredoDO::getUrl, url);
        if (temSenha) {
            u.set(SegredoDO::getSenhaCifrada, cifrador.cifrar(senha, contextoSenha(atual.getId())))
                    .set(SegredoDO::getSenhaAlteradaEm, LocalDateTime.now());
        }
        if (req.getNotas() != null) {
            u.set(SegredoDO::getNotasCifradas, req.getNotas().isBlank() ? null : cifrador.cifrar(req.getNotas(), contextoNotas(atual.getId())));
        }
        segredoMapper.update(null, u);
        auditoria.registrar("cofre.senha_alterada", userId, null, Map.of("segredo", atual.getId(), "pasta", req.getPastaId(),
                "nome", nome, "senhaTrocada", temSenha));
        return atual.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirSegredo(Long id, Long userId) {
        SegredoDO s = segredoExistente(id);
        exigirPapel(s.getPastaId(), userId, EDITAR);
        // a exclusão é lógica: o texto cifrado não fica para trás na linha apagada
        segredoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SegredoDO>()
                .eq(SegredoDO::getId, id).set(SegredoDO::getSenhaCifrada, "").set(SegredoDO::getNotasCifradas, null));
        segredoMapper.deleteById(id);
        auditoria.registrar("cofre.senha_excluida", userId, null, Map.of("segredo", id, "pasta", s.getPastaId(), "nome", s.getNome()));
    }

    /** Devolve a senha e as notas e registra quem viu. Se o registro falhar, a senha não sai. */
    public CofreVOs.Revelado revelar(Long id, Long userId) {
        exigirChave();
        SegredoDO s = segredoExistente(id);
        exigirPapel(s.getPastaId(), userId, VER);
        if (exigirMfa && !mfaService.exige(userId)) {
            throw exception(COFRE_EXIGE_MFA);
        }
        CofreVOs.Revelado r = new CofreVOs.Revelado();
        r.setSenha(cifrador.decifrar(s.getSenhaCifrada(), contextoSenha(id)));
        r.setNotas(s.getNotasCifradas() == null ? null : cifrador.decifrar(s.getNotasCifradas(), contextoNotas(id)));
        auditoria.registrar("cofre.senha_vista", userId, null, Map.of("segredo", id, "pasta", s.getPastaId(), "nome", s.getNome()));
        return r;
    }

    // ------------------------------------------------------------------ internos

    static String contextoSenha(Long id) {
        return "cofre:senha:" + id;
    }

    static String contextoNotas(Long id) {
        return "cofre:notas:" + id;
    }

    private void exigirChave() {
        if (!cifrador.configurado()) {
            throw exception(COFRE_SEM_CHAVE_MESTRA);
        }
    }

    private boolean administra(Long userId) {
        return permissionApi.hasAnyPermissions(userId, PERMISSAO_ADMINISTRAR);
    }

    private PastaMembroDO membro(Long pastaId, Long userId) {
        return membroMapper.selectOne(new LambdaQueryWrapperX<PastaMembroDO>()
                .eq(PastaMembroDO::getPastaId, pastaId).eq(PastaMembroDO::getUserId, userId));
    }

    private void exigirPapel(Long pastaId, Long userId, int minimo) {
        PastaMembroDO m = membro(pastaId, userId);
        if (m == null || m.getPapel() < minimo) {
            throw exception(PASTA_SEM_ACESSO);
        }
    }

    private void exigirGestao(Long pastaId, Long userId) {
        if (!administra(userId)) {
            exigirPapel(pastaId, userId, GERENCIAR);
        }
    }

    private void exigirOutroGestor(Long pastaId, Long alvoId) {
        boolean outro = membroMapper.selectList(PastaMembroDO::getPastaId, pastaId).stream()
                .anyMatch(m -> Objects.equals(m.getPapel(), GERENCIAR) && !m.getUserId().equals(alvoId));
        if (!outro) {
            throw exception(PASTA_ULTIMO_GESTOR);
        }
    }

    private PastaDO pastaExistente(Long id) {
        PastaDO p = id == null ? null : pastaMapper.selectById(id);
        if (p == null) {
            throw exception(PASTA_NAO_EXISTE);
        }
        return p;
    }

    private SegredoDO segredoExistente(Long id) {
        SegredoDO s = id == null ? null : segredoMapper.selectById(id);
        if (s == null) {
            throw exception(SEGREDO_NAO_EXISTE);
        }
        return s;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
