package cn.iocoder.yudao.module.acesso.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.acesso.controller.admin.vo.AcessoVOs;
import cn.iocoder.yudao.module.acesso.dal.dataobject.AcessoLinkDO;
import cn.iocoder.yudao.module.acesso.dal.mysql.AcessoLinkMapper;
import cn.iocoder.yudao.module.acesso.dominio.LinkRegras;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.acesso.enums.ErrorCodeConstants.*;

/**
 * Links públicos (sem dono), que só quem tem a permissão de gerenciar altera, e pessoais, que só o dono vê e altera,
 * nem mesmo o administrador. Link de outra pessoa responde "não encontrado".
 */
@Service
public class AcessoService {

    /** Distingue este bloqueio de outros que usem o mesmo recurso do banco. */
    private static final long BASE_TRAVA = 7_301_000_000_000L;

    @Resource
    private AcessoLinkMapper linkMapper;

    public AcessoVOs.Painel painel(Long userId) {
        AcessoVOs.Painel p = new AcessoVOs.Painel();
        p.setPublicos(linkMapper.selectList(new LambdaQueryWrapperX<AcessoLinkDO>().isNull(AcessoLinkDO::getDonoId)
                .orderByAsc(AcessoLinkDO::getCategoria).orderByAsc(AcessoLinkDO::getOrdem).orderByAsc(AcessoLinkDO::getTitulo))
                .stream().map(AcessoService::converter).toList());
        p.setMeus(linkMapper.selectList(new LambdaQueryWrapperX<AcessoLinkDO>().eq(AcessoLinkDO::getDonoId, userId)
                .orderByAsc(AcessoLinkDO::getOrdem).orderByAsc(AcessoLinkDO::getTitulo))
                .stream().map(AcessoService::converter).toList());
        p.setLimiteMeus(LinkRegras.MAX_PESSOAIS);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarPublico(AcessoVOs.Salvar req) {
        AcessoLinkDO l = montar(req);
        l.setCategoria(vazioNulo(req.getCategoria()));
        if (req.getId() == null) {
            linkMapper.insert(l);
        } else {
            AcessoLinkDO atual = linkMapper.selectById(req.getId());
            if (atual == null || atual.getDonoId() != null) {
                throw exception(LINK_NAO_EXISTE);
            }
            linkMapper.updateById(l);
        }
        return l.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirPublico(Long id) {
        AcessoLinkDO atual = linkMapper.selectById(id);
        if (atual == null || atual.getDonoId() != null) {
            throw exception(LINK_NAO_EXISTE);
        }
        linkMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarMeu(AcessoVOs.Salvar req, Long userId) {
        AcessoLinkDO l = montar(req);
        if (req.getId() == null) {
            linkMapper.travar(BASE_TRAVA + userId);
            if (linkMapper.selectCount(AcessoLinkDO::getDonoId, userId) >= LinkRegras.MAX_PESSOAIS) {
                throw exception(LINK_LIMITE, LinkRegras.MAX_PESSOAIS);
            }
            l.setDonoId(userId);
            linkMapper.insert(l);
        } else {
            meu(req.getId(), userId);
            linkMapper.updateById(l);
        }
        return l.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirMeu(Long id, Long userId) {
        meu(id, userId);
        linkMapper.deleteById(id);
    }

    private void meu(Long id, Long userId) {
        AcessoLinkDO atual = linkMapper.selectById(id);
        if (atual == null || !userId.equals(atual.getDonoId())) {
            throw exception(LINK_NAO_EXISTE);
        }
    }

    private static AcessoLinkDO montar(AcessoVOs.Salvar req) {
        String url = LinkRegras.normalizar(req.getUrl()).orElseThrow(() -> exception(LINK_ENDERECO_INVALIDO));
        return AcessoLinkDO.builder().id(req.getId()).titulo(req.getTitulo().trim()).url(url)
                .descricao(vazioNulo(req.getDescricao())).ordem(req.getOrdem() == null ? 0 : req.getOrdem()).build();
    }

    private static AcessoVOs.Link converter(AcessoLinkDO l) {
        AcessoVOs.Link r = new AcessoVOs.Link();
        r.setId(l.getId());
        r.setTitulo(l.getTitulo());
        r.setUrl(l.getUrl());
        r.setDescricao(l.getDescricao());
        r.setCategoria(l.getCategoria());
        r.setOrdem(l.getOrdem());
        return r;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
