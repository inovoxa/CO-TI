package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.EtiquetaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.RespostaProntaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaEtiquetaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.EtiquetaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.RespostaProntaMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.atendimento.service.ContatoService.vazio;

/**
 * Etiquetas de conversa e respostas prontas (Chatwoot: Label e CannedResponse).
 */
@Service
public class EtiquetaService {

    @Resource
    private EtiquetaMapper etiquetaMapper;
    @Resource
    private ConversaEtiquetaMapper conversaEtiquetaMapper;
    @Resource
    private RespostaProntaMapper respostaProntaMapper;

    public List<AtendimentoVOs.Etiqueta> etiquetas() {
        return etiquetaMapper.selectList(new LambdaQueryWrapperX<EtiquetaDO>().orderByAsc(EtiquetaDO::getNome)).stream().map(e -> {
            AtendimentoVOs.Etiqueta v = new AtendimentoVOs.Etiqueta();
            v.setId(e.getId());
            v.setNome(e.getNome());
            v.setCor(e.getCor());
            v.setDescricao(e.getDescricao());
            return v;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarEtiqueta(AtendimentoVOs.Etiqueta req) {
        if (req.getId() != null && etiquetaMapper.selectById(req.getId()) == null) {
            throw exception(ETIQUETA_NAO_EXISTE);
        }
        String nome = req.getNome().trim();
        EtiquetaDO mesma = etiquetaMapper.selectOne(new LambdaQueryWrapperX<EtiquetaDO>().apply("lower(nome) = lower({0})", nome).last("LIMIT 1"));
        if (mesma != null && !mesma.getId().equals(req.getId())) {
            throw exception(ETIQUETA_DUPLICADA);
        }
        EtiquetaDO e = EtiquetaDO.builder().id(req.getId()).nome(nome).cor(req.getCor() == null ? "#6b7280" : req.getCor().toLowerCase())
                .descricao(vazio(req.getDescricao())).build();
        if (req.getId() == null) {
            etiquetaMapper.insert(e);
        } else {
            etiquetaMapper.updateById(e);
        }
        return e.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirEtiqueta(Long id) {
        if (etiquetaMapper.selectById(id) == null) {
            throw exception(ETIQUETA_NAO_EXISTE);
        }
        conversaEtiquetaMapper.removerEtiqueta(id);
        etiquetaMapper.deleteById(id);
    }

    public List<AtendimentoVOs.RespostaPronta> respostas() {
        return respostaProntaMapper.selectList(new LambdaQueryWrapperX<RespostaProntaDO>().orderByAsc(RespostaProntaDO::getAtalho)).stream().map(r -> {
            AtendimentoVOs.RespostaPronta v = new AtendimentoVOs.RespostaPronta();
            v.setId(r.getId());
            v.setAtalho(r.getAtalho());
            v.setConteudo(r.getConteudo());
            return v;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarResposta(AtendimentoVOs.RespostaPronta req) {
        if (req.getId() != null && respostaProntaMapper.selectById(req.getId()) == null) {
            throw exception(RESPOSTA_NAO_EXISTE);
        }
        RespostaProntaDO mesma = respostaProntaMapper.selectOne(RespostaProntaDO::getAtalho, req.getAtalho());
        if (mesma != null && !mesma.getId().equals(req.getId())) {
            throw exception(RESPOSTA_DUPLICADA);
        }
        RespostaProntaDO r = RespostaProntaDO.builder().id(req.getId()).atalho(req.getAtalho()).conteudo(req.getConteudo().strip()).build();
        if (req.getId() == null) {
            respostaProntaMapper.insert(r);
        } else {
            respostaProntaMapper.updateById(r);
        }
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluirResposta(Long id) {
        if (respostaProntaMapper.selectById(id) == null) {
            throw exception(RESPOSTA_NAO_EXISTE);
        }
        respostaProntaMapper.deleteById(id);
    }

}
