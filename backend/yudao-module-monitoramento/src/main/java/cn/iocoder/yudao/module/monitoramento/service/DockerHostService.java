package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.DockerHostDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonDockerHostMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.monitoramento.service.verificador.ClienteDocker;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/** Servidores Docker (API remota) usados pelos monitores de container. */
@Service
public class DockerHostService {

    @Resource
    private MonDockerHostMapper monDockerHostMapper;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private SegredosMonitor segredosMonitor;
    @Resource
    private ClienteDocker clienteDocker;

    public List<MonitorVOs.DockerHost> listar() {
        Map<Long, Long> uso = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().isNotNull(MonitorDO::getDockerHostId)).stream()
                .collect(Collectors.groupingBy(MonitorDO::getDockerHostId, Collectors.counting()));
        return monDockerHostMapper.selectList(new LambdaQueryWrapperX<DockerHostDO>().orderByAsc(DockerHostDO::getNome)).stream().map(h -> {
            MonitorVOs.DockerHost vo = new MonitorVOs.DockerHost();
            vo.setId(h.getId());
            vo.setNome(h.getNome());
            vo.setUrl(h.getUrl());
            vo.setCa(h.getCa());
            vo.setCertificado(h.getCertificado());
            vo.setChaveDefinida(h.getChave() != null);
            vo.setMonitores(uso.getOrDefault(h.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(MonitorVOs.DockerHost req) {
        DockerHostDO atual = req.getId() == null ? null : monDockerHostMapper.selectById(req.getId());
        if (req.getId() != null && atual == null) {
            throw exception(DOCKER_HOST_NAO_EXISTE);
        }
        DockerHostDO h = montar(req, atual);
        if (atual == null) {
            monDockerHostMapper.insert(h);
        } else {
            monDockerHostMapper.updateById(h);
            monDockerHostMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DockerHostDO>()
                    .eq(DockerHostDO::getId, h.getId()).set(DockerHostDO::getCa, h.getCa()).set(DockerHostDO::getCertificado, h.getCertificado())
                    .set(DockerHostDO::getChave, h.getChave()));
        }
        clienteDocker.esquecer(h.getId());
        return h.getId();
    }

    /** Testa a conexão sem salvar; com id, vale a chave guardada. @return a versão do Docker */
    public String testar(MonitorVOs.DockerHost req) {
        DockerHostDO atual = req.getId() == null ? null : monDockerHostMapper.selectById(req.getId());
        DockerHostDO h = montar(req, atual);
        try {
            return clienteDocker.versao(h);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw exception(DOCKER_HOST_INVALIDO, e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        if (monDockerHostMapper.selectById(id) == null) {
            throw exception(DOCKER_HOST_NAO_EXISTE);
        }
        long usados = monMonitorMapper.selectCount(MonitorDO::getDockerHostId, id);
        if (usados > 0) {
            throw exception(DOCKER_HOST_EM_USO, usados);
        }
        monDockerHostMapper.deleteById(id);
        clienteDocker.esquecer(id);
    }

    private DockerHostDO montar(MonitorVOs.DockerHost r, DockerHostDO atual) {
        String url = r.getUrl() == null ? "" : r.getUrl().trim().replaceAll("/+$", "");
        if (!url.matches("(?i)https?://[A-Za-z0-9_.\\-\\[\\]:]+(:\\d{1,5})?")) {
            throw exception(DOCKER_HOST_INVALIDO, "use https://servidor:2376 (com certificado) ou http://servidor:2375");
        }
        String ca = vazio(r.getCa());
        String cert = vazio(r.getCertificado());
        String chave = segredosMonitor.chaveDocker(atual == null ? null : atual.getChave(), r.getChave());
        boolean https = url.regionMatches(true, 0, "https", 0, 5);
        if (!https && (cert != null || chave != null)) {
            throw exception(DOCKER_HOST_INVALIDO, "certificado de cliente só vale com https://");
        }
        if ((cert == null) != (chave == null)) {
            throw exception(DOCKER_HOST_INVALIDO, "informe o certificado e a chave do cliente juntos");
        }
        DockerHostDO h = DockerHostDO.builder().id(r.getId()).nome(r.getNome().trim()).url(url).ca(ca).certificado(cert).chave(chave).build();
        try {
            clienteDocker.validar(h);
        } catch (IllegalArgumentException e) {
            throw exception(DOCKER_HOST_INVALIDO, e.getMessage());
        }
        return h;
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
