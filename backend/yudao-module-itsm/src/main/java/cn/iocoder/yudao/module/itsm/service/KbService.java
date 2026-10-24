package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.KbVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.BuscaTexto;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Base de conhecimento (GLPI: KnowbaseItem). Quem tem {@code itsm:kb:query} lê tudo o que está publicado;
 * os demais, só a FAQ. Rascunhos e artigos fora do período só para quem escreve ({@code itsm:kb:update}).
 */
@Service
public class KbService {

    public static final String P_LER = "itsm:kb:query";
    public static final String P_ESCREVER = "itsm:kb:update";

    @Resource
    private KbCategoriaMapper kbCategoriaMapper;
    @Resource
    private KbArtigoMapper kbArtigoMapper;
    @Resource
    private KbRevisaoMapper kbRevisaoMapper;
    @Resource
    private KbArtigoChamadoMapper kbArtigoChamadoMapper;
    @Resource
    private KbVotoMapper kbVotoMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    // ------------------------------------------------------------------ categorias

    public List<KbCategoriaDO> categorias() {
        return kbCategoriaMapper.selectList(new LambdaQueryWrapperX<KbCategoriaDO>().orderByAsc(KbCategoriaDO::getOrdem)
                .orderByAsc(KbCategoriaDO::getNomeCompleto));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarCategoria(KbVOs.CategoriaSalvar req) {
        Long pai = req.getParentId() == null ? KbCategoriaDO.RAIZ : req.getParentId();
        KbCategoriaDO atual = req.getId() == null ? null : categoria(req.getId());
        KbCategoriaDO paiDO = pai.equals(KbCategoriaDO.RAIZ) ? null : categoria(pai);
        if (req.getId() != null && descendentes(req.getId()).contains(pai)) {
            throw exception(KB_CATEGORIA_PAI_INVALIDA);
        }
        KbCategoriaDO c = KbCategoriaDO.builder().id(req.getId()).parentId(pai).nome(req.getNome().trim())
                .descricao(req.getDescricao()).ordem(req.getOrdem()).build();
        c.setNomeCompleto(paiDO == null ? c.getNome() : paiDO.getNomeCompleto() + " > " + c.getNome());
        if (atual == null) {
            kbCategoriaMapper.insert(c);
        } else {
            kbCategoriaMapper.updateById(c);
            if (!Objects.equals(atual.getNomeCompleto(), c.getNomeCompleto())) {
                renomearDescendentes(c);
            }
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirCategoria(Long id) {
        categoria(id);
        if (kbCategoriaMapper.selectCount(KbCategoriaDO::getParentId, id) > 0
                || kbArtigoMapper.selectCount(KbArtigoDO::getCategoriaId, id) > 0) {
            throw exception(KB_CATEGORIA_EM_USO);
        }
        kbCategoriaMapper.deleteById(id);
    }

    private KbCategoriaDO categoria(Long id) {
        KbCategoriaDO c = id == null ? null : kbCategoriaMapper.selectById(id);
        if (c == null) {
            throw exception(KB_CATEGORIA_NAO_EXISTE);
        }
        return c;
    }

    private Set<Long> descendentes(Long id) {
        Map<Long, List<KbCategoriaDO>> porPai = kbCategoriaMapper.selectList().stream()
                .collect(Collectors.groupingBy(KbCategoriaDO::getParentId));
        Set<Long> out = new LinkedHashSet<>();
        Deque<Long> fila = new ArrayDeque<>(List.of(id));
        while (!fila.isEmpty()) {
            Long a = fila.poll();
            if (out.add(a)) {
                porPai.getOrDefault(a, List.of()).forEach(f -> fila.add(f.getId()));
            }
        }
        return out;
    }

    private void renomearDescendentes(KbCategoriaDO pai) {
        for (KbCategoriaDO f : kbCategoriaMapper.selectList(KbCategoriaDO::getParentId, pai.getId())) {
            f.setNomeCompleto(pai.getNomeCompleto() + " > " + f.getNome());
            kbCategoriaMapper.updateById(f);
            renomearDescendentes(f);
        }
    }

    // ------------------------------------------------------------------ busca e leitura

    /**
     * Busca para a pessoa logada.
     *
     * @param gestao tela de gestão (quem escreve vê rascunhos e pode filtrar por FAQ/publicado)
     */
    public PageResult<KbVOs.ArtigoResumo> buscar(KbVOs.BuscaReq req, Long userId, boolean gestao) {
        boolean escreve = gestao && tem(userId, P_ESCREVER);
        boolean le = tem(userId, P_LER);
        String q = BuscaTexto.consulta(req.getQ());
        Collection<Long> cats = req.getCategoriaId() == null ? null : descendentes(req.getCategoriaId());
        int tamanho = req.getPageSize();
        int deslocamento = (req.getPageNo() - 1) * tamanho;
        Boolean faq = escreve ? req.getFaq() : null;
        Boolean publicado = escreve ? req.getPublicado() : null;
        List<KbVOs.ArtigoResumo> l = kbArtigoMapper.buscar(q, cats, !escreve, !le, faq, publicado, tamanho, deslocamento);
        long total = kbArtigoMapper.contar(q, cats, !escreve, !le, faq, publicado);
        completar(l);
        return new PageResult<>(l, total);
    }

    /** Até 5 artigos parecidos com o texto (título do chamado sendo aberto). */
    public List<KbVOs.ArtigoResumo> sugestoes(String texto, Long userId) {
        String q = BuscaTexto.consultaQualquer(texto);
        if (q == null) {
            return List.of();
        }
        List<KbVOs.ArtigoResumo> l = kbArtigoMapper.buscar(q, null, true, !tem(userId, P_LER), null, null, 5, 0);
        completar(l);
        return l;
    }

    private void completar(List<KbVOs.ArtigoResumo> l) {
        Map<Long, String> nomes = categorias().stream().collect(Collectors.toMap(KbCategoriaDO::getId, KbCategoriaDO::getNomeCompleto));
        l.forEach(a -> {
            a.setCategoria(nomes.get(a.getCategoriaId()));
            a.setTrecho(BuscaTexto.trechoHtml(a.getTrecho()));
        });
    }

    /** Leitura de um artigo (conta a visualização). */
    @Transactional(rollbackFor = Exception.class)
    public KbVOs.Artigo ler(Long id, Long userId, boolean contar) {
        KbArtigoDO a = visivel(id, userId);
        if (contar) {
            kbArtigoMapper.contarVisualizacao(id);
            a.setVisualizacoes(a.getVisualizacoes() + 1);
        }
        KbVOs.Artigo v = new KbVOs.Artigo();
        v.setId(a.getId());
        v.setTitulo(a.getTitulo());
        v.setResumo(a.getResumo());
        v.setConteudo(a.getConteudo());
        v.setCategoriaId(a.getCategoriaId());
        KbCategoriaDO cat = a.getCategoriaId() == null ? null : kbCategoriaMapper.selectById(a.getCategoriaId());
        v.setCategoria(cat == null ? null : cat.getNomeCompleto());
        v.setFaq(a.getFaq());
        v.setPublicado(a.getPublicado());
        v.setFixado(a.getFixado());
        v.setInicioPublicacao(a.getInicioPublicacao());
        v.setFimPublicacao(a.getFimPublicacao());
        v.setAutorId(a.getAutorId());
        AdminUserRespDTO autor = adminUserApi.getUser(a.getAutorId());
        v.setAutor(autor == null ? null : autor.getNickname());
        v.setRevisao(a.getRevisao());
        v.setVisualizacoes(a.getVisualizacoes());
        v.setUtil(a.getUtil());
        v.setNaoUtil(a.getNaoUtil());
        v.setCreateTime(a.getCreateTime());
        v.setUpdateTime(a.getUpdateTime());
        v.setMeuVoto(kbVotoMapper.meuVoto(id, userId));
        boolean escreve = tem(userId, P_ESCREVER);
        v.setPodeEditar(escreve);
        if (tem(userId, P_LER)) {
            v.setChamados(kbArtigoChamadoMapper.selectList(KbArtigoChamadoDO::getArtigoId, id).stream()
                    .map(KbArtigoChamadoDO::getChamadoId).toList());
        }
        return v;
    }

    /** O artigo se a pessoa pode lê-lo; senão "não encontrado" (não revela que existe). */
    private KbArtigoDO visivel(Long id, Long userId) {
        KbArtigoDO a = id == null ? null : kbArtigoMapper.selectById(id);
        if (a == null) {
            throw exception(KB_ARTIGO_NAO_EXISTE);
        }
        if (tem(userId, P_ESCREVER)) {
            return a;
        }
        LocalDateTime agora = LocalDateTime.now();
        boolean noAr = Boolean.TRUE.equals(a.getPublicado())
                && (a.getInicioPublicacao() == null || !a.getInicioPublicacao().isAfter(agora))
                && (a.getFimPublicacao() == null || a.getFimPublicacao().isAfter(agora));
        if (!noAr || !(Boolean.TRUE.equals(a.getFaq()) || tem(userId, P_LER))) {
            throw exception(KB_ARTIGO_NAO_EXISTE);
        }
        return a;
    }

    @Transactional(rollbackFor = Exception.class)
    public void votar(Long artigoId, boolean util, Long userId) {
        visivel(artigoId, userId);
        kbVotoMapper.votar(artigoId, userId, util);
        kbArtigoMapper.recontarVotos(artigoId);
    }

    // ------------------------------------------------------------------ escrita

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(KbVOs.ArtigoSalvar req, Long userId) {
        if (req.getInicioPublicacao() != null && req.getFimPublicacao() != null
                && !req.getFimPublicacao().isAfter(req.getInicioPublicacao())) {
            throw exception(KB_ARTIGO_DATAS);
        }
        if (req.getCategoriaId() != null) {
            categoria(req.getCategoriaId());
        }
        String conteudo = HtmlSeguro.limpar(req.getConteudo());
        KbArtigoDO a = KbArtigoDO.builder().id(req.getId()).titulo(req.getTitulo().trim())
                .resumo(req.getResumo() == null || req.getResumo().isBlank() ? null : req.getResumo().trim())
                .conteudo(conteudo).texto(HtmlSeguro.texto(conteudo)).categoriaId(req.getCategoriaId())
                .faq(Boolean.TRUE.equals(req.getFaq())).publicado(!Boolean.FALSE.equals(req.getPublicado()))
                .fixado(Boolean.TRUE.equals(req.getFixado()))
                .inicioPublicacao(req.getInicioPublicacao()).fimPublicacao(req.getFimPublicacao()).build();
        if (req.getId() == null) {
            a.setAutorId(userId);
            a.setRevisao(1);
            a.setVisualizacoes(0);
            a.setUtil(0);
            a.setNaoUtil(0);
            kbArtigoMapper.insert(a);
            return a.getId();
        }
        KbArtigoDO antes = kbArtigoMapper.selectById(req.getId());
        if (antes == null) {
            throw exception(KB_ARTIGO_NAO_EXISTE);
        }
        // GLPI: guarda a versão anterior quando título ou conteúdo mudam
        if (!antes.getTitulo().equals(a.getTitulo()) || !antes.getConteudo().equals(a.getConteudo())) {
            kbRevisaoMapper.insert(KbRevisaoDO.builder().artigoId(antes.getId()).revisao(antes.getRevisao())
                    .titulo(antes.getTitulo()).conteudo(antes.getConteudo()).autorId(userId).criadoEm(LocalDateTime.now()).build());
            a.setRevisao(antes.getRevisao() + 1);
        }
        kbArtigoMapper.updateById(a);
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        if (kbArtigoMapper.selectById(id) == null) {
            throw exception(KB_ARTIGO_NAO_EXISTE);
        }
        kbArtigoMapper.deleteById(id);
    }

    public List<KbVOs.Revisao> revisoes(Long artigoId) {
        List<KbRevisaoDO> l = kbRevisaoMapper.selectList(new LambdaQueryWrapperX<KbRevisaoDO>()
                .eq(KbRevisaoDO::getArtigoId, artigoId).orderByDesc(KbRevisaoDO::getRevisao));
        Map<Long, AdminUserRespDTO> pessoas = adminUserApi.getUserMap(l.stream().map(KbRevisaoDO::getAutorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return l.stream().map(r -> {
            KbVOs.Revisao v = new KbVOs.Revisao();
            v.setId(r.getId());
            v.setRevisao(r.getRevisao());
            v.setTitulo(r.getTitulo());
            v.setConteudo(r.getConteudo());
            AdminUserRespDTO u = pessoas.get(r.getAutorId());
            v.setAutor(u == null ? null : u.getNickname());
            v.setCriadoEm(r.getCriadoEm());
            return v;
        }).toList();
    }

    /** Volta o artigo ao texto de uma revisão (a versão atual vira revisão, como numa edição). */
    @Transactional(rollbackFor = Exception.class)
    public void restaurar(Long revisaoId, Long userId) {
        KbRevisaoDO r = kbRevisaoMapper.selectById(revisaoId);
        if (r == null) {
            throw exception(KB_REVISAO_NAO_EXISTE);
        }
        KbArtigoDO a = kbArtigoMapper.selectById(r.getArtigoId());
        if (a == null) {
            throw exception(KB_ARTIGO_NAO_EXISTE);
        }
        KbVOs.ArtigoSalvar s = new KbVOs.ArtigoSalvar();
        s.setId(a.getId());
        s.setTitulo(r.getTitulo());
        s.setConteudo(r.getConteudo());
        s.setResumo(a.getResumo());
        s.setCategoriaId(a.getCategoriaId());
        s.setFaq(a.getFaq());
        s.setPublicado(a.getPublicado());
        s.setFixado(a.getFixado());
        s.setInicioPublicacao(a.getInicioPublicacao());
        s.setFimPublicacao(a.getFimPublicacao());
        salvar(s, userId);
    }

    // ------------------------------------------------------------------ chamados

    /**
     * Usa o artigo no chamado: registra como solução (ou acompanhamento) com o conteúdo e o link do artigo.
     * O acesso ao chamado é conferido pelo ChamadoService (técnico com permissão de atender).
     */
    @Transactional(rollbackFor = Exception.class)
    public void usarNoChamado(KbVOs.UsarNoChamadoReq req, Long userId) {
        KbArtigoDO a = visivel(req.getArtigoId(), userId);
        ChamadoVOs.InteracaoReq i = new ChamadoVOs.InteracaoReq();
        i.setChamadoId(req.getChamadoId());
        i.setTipo(Boolean.FALSE.equals(req.getComoSolucao()) ? InteracaoDO.ACOMPANHAMENTO : InteracaoDO.SOLUCAO);
        i.setPrivado(false);
        i.setConteudo("<p><b>Base de conhecimento: <a href=\"/base-conhecimento?artigo=" + a.getId() + "\">"
                + HtmlUtils.htmlEscape(a.getTitulo()) + "</a></b></p>" + a.getConteudo());
        chamadoService.adicionarInteracao(i, userId, true);
        if (kbArtigoChamadoMapper.selectCount(new LambdaQueryWrapperX<KbArtigoChamadoDO>()
                .eq(KbArtigoChamadoDO::getArtigoId, a.getId()).eq(KbArtigoChamadoDO::getChamadoId, req.getChamadoId())) == 0) {
            kbArtigoChamadoMapper.insert(KbArtigoChamadoDO.builder().artigoId(a.getId()).chamadoId(req.getChamadoId())
                    .userId(userId).criadoEm(LocalDateTime.now()).build());
        }
    }

    /** Rascunho de artigo a partir da última solução do chamado (GLPI: "salvar solução na base"). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Long criarDoChamado(Long chamadoId, Long userId) {
        ChamadoVOs.Resp c = chamadoService.obter(chamadoId, userId); // confere o escopo do técnico
        InteracaoDO solucao = interacaoMapper.selectOne(new LambdaQueryWrapperX<InteracaoDO>()
                .eq(InteracaoDO::getChamadoId, chamadoId).eq(InteracaoDO::getTipo, InteracaoDO.SOLUCAO)
                .orderByDesc(InteracaoDO::getId).last("LIMIT 1"));
        if (solucao == null) {
            throw exception(KB_CHAMADO_SEM_SOLUCAO);
        }
        KbVOs.ArtigoSalvar s = new KbVOs.ArtigoSalvar();
        s.setTitulo(c.getTitulo());
        s.setConteudo("<p><b>Problema</b></p>" + (c.getDescricao() == null ? "" : c.getDescricao())
                + "<p><b>Solução</b></p>" + solucao.getConteudo());
        s.setPublicado(false);
        Long id = salvar(s, userId);
        kbArtigoChamadoMapper.insert(KbArtigoChamadoDO.builder().artigoId(id).chamadoId(chamadoId).userId(userId)
                .criadoEm(LocalDateTime.now()).build());
        return id;
    }

    /** Artigos usados no chamado. */
    public List<KbVOs.ArtigoResumo> doChamado(Long chamadoId, Long userId) {
        chamadoService.obter(chamadoId, userId); // confere o escopo do técnico
        List<Long> ids = kbArtigoChamadoMapper.selectList(KbArtigoChamadoDO::getChamadoId, chamadoId).stream()
                .map(KbArtigoChamadoDO::getArtigoId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return kbArtigoMapper.selectBatchIds(ids).stream().map(a -> {
            KbVOs.ArtigoResumo r = new KbVOs.ArtigoResumo();
            r.setId(a.getId());
            r.setTitulo(a.getTitulo());
            r.setFaq(a.getFaq());
            r.setPublicado(a.getPublicado());
            return r;
        }).toList();
    }

    private boolean tem(Long userId, String permissao) {
        return permissionApi.hasAnyPermissions(userId, permissao);
    }

}
