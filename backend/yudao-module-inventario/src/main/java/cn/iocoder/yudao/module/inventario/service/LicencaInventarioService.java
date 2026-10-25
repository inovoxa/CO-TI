package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.LicencaInventarioVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoSoftwareDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.LicencaSoftwareDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.SoftwareDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoSoftwareMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbLicencaSoftwareMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbSoftwareMapper;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.LicencaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.LicencaMapper;
import cn.iocoder.yudao.module.itsm.service.gov.LicencaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.SOFTWARE_NAO_EXISTE;

/**
 * Software × licenças: a equipe diz quais itens do catálogo contam como uso de uma licença do ITSM, e o inventário
 * conta as máquinas onde eles estão instalados.
 */
@Service
public class LicencaInventarioService {

    @Resource
    private CmdbLicencaSoftwareMapper cmdbLicencaSoftwareMapper;
    @Resource
    private CmdbSoftwareMapper cmdbSoftwareMapper;
    @Resource
    private CmdbAtivoSoftwareMapper cmdbAtivoSoftwareMapper;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private LicencaService licencaService;
    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private AtivoService ativoService;

    /** Detalhe para a aba da licença. A licença precisa estar no escopo de quem consulta. */
    public LicencaInventarioVOs.Conformidade conformidade(Long licencaId) {
        GovernancaVOs.Licenca l = licencaService.obter(licencaId);
        LicencaInventarioVOs.Conformidade c = new LicencaInventarioVOs.Conformidade();
        c.setLicencaId(licencaId);
        c.setQuantidade(l.getQuantidade());
        c.setEmUso(l.getEmUso());
        c.setInstalacoes(instalacoesPorLicenca().getOrDefault(licencaId, 0L));
        c.setExcedida(l.getQuantidade() != null && l.getQuantidade() >= 0 && c.getInstalacoes() > l.getQuantidade());
        List<Long> ids = cmdbLicencaSoftwareMapper.selectList(LicencaSoftwareDO::getLicencaId, licencaId).stream()
                .map(LicencaSoftwareDO::getSoftwareId).toList();
        if (!ids.isEmpty()) {
            Map<Long, Set<Long>> maquinas = new HashMap<>();
            for (AtivoSoftwareDO x : cmdbAtivoSoftwareMapper.selectList(new LambdaQueryWrapperX<AtivoSoftwareDO>()
                    .in(AtivoSoftwareDO::getSoftwareId, ids))) {
                maquinas.computeIfAbsent(x.getSoftwareId(), k -> new HashSet<>()).add(x.getAtivoId());
            }
            for (SoftwareDO s : cmdbSoftwareMapper.selectByIds(ids)) {
                AtivoVOs.SoftwareResp vo = new AtivoVOs.SoftwareResp();
                vo.setId(s.getId());
                vo.setNome(s.getNome());
                vo.setEditora(s.getEditora());
                vo.setInstalacoes((long) maquinas.getOrDefault(s.getId(), Set.of()).size());
                c.getSoftwares().add(vo);
            }
            c.getSoftwares().sort(Comparator.comparing(AtivoVOs.SoftwareResp::getNome, String.CASE_INSENSITIVE_ORDER));
            List<Long> ativos = cmdbLicencaSoftwareMapper.ativosDaLicenca(licencaId);
            if (!ativos.isEmpty()) {
                // a lista de máquinas respeita o escopo de unidade (selectByIds passa pela regra)
                c.setAtivos(ativoService.converter(cmdbAtivoMapper.selectByIds(ativos)));
                c.getAtivos().sort(Comparator.comparing(AtivoVOs.Resp::getNome, String.CASE_INSENSITIVE_ORDER));
            }
        }
        return c;
    }

    /** Total de máquinas por licença, sem escopo: a conformidade é da licença inteira. */
    @DataPermission(enable = false)
    public Map<Long, Long> instalacoesPorLicenca() {
        Map<Long, Long> m = new HashMap<>();
        for (Map<String, Object> r : cmdbLicencaSoftwareMapper.instalacoesPorLicenca()) {
            m.put(((Number) r.get("licenca_id")).longValue(), ((Number) r.get("instalacoes")).longValue());
        }
        return m;
    }

    /** Nomes das licenças de cada software (coluna da página de softwares). */
    public Map<Long, List<String>> licencasDosSoftwares(Collection<Long> softwareIds) {
        if (softwareIds.isEmpty()) {
            return Map.of();
        }
        List<LicencaSoftwareDO> l = cmdbLicencaSoftwareMapper.selectList(new LambdaQueryWrapperX<LicencaSoftwareDO>()
                .in(LicencaSoftwareDO::getSoftwareId, softwareIds));
        if (l.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nomes = licencaMapper.selectByIds(l.stream().map(LicencaSoftwareDO::getLicencaId).distinct().toList()).stream()
                .collect(Collectors.toMap(LicencaDO::getId, x -> x.getNome() == null || x.getNome().isBlank()
                        ? x.getSoftware() : x.getSoftware() + " · " + x.getNome()));
        Map<Long, List<String>> res = new HashMap<>();
        for (LicencaSoftwareDO x : l) {
            String n = nomes.get(x.getLicencaId());
            if (n != null) {
                res.computeIfAbsent(x.getSoftwareId(), k -> new ArrayList<>()).add(n);
            }
        }
        return res;
    }

    @Transactional(rollbackFor = Exception.class)
    public void vincular(LicencaInventarioVOs.VinculoReq req, Long userId) {
        licencaService.obter(req.getLicencaId());   // existe e está no escopo
        if (cmdbSoftwareMapper.selectById(req.getSoftwareId()) == null) {
            throw exception(SOFTWARE_NAO_EXISTE);
        }
        if (cmdbLicencaSoftwareMapper.selectCount(new LambdaQueryWrapperX<LicencaSoftwareDO>()
                .eq(LicencaSoftwareDO::getLicencaId, req.getLicencaId()).eq(LicencaSoftwareDO::getSoftwareId, req.getSoftwareId())) > 0) {
            return;
        }
        cmdbLicencaSoftwareMapper.insert(LicencaSoftwareDO.builder().licencaId(req.getLicencaId()).softwareId(req.getSoftwareId())
                .userId(userId).build());
    }

    @Transactional(rollbackFor = Exception.class)
    public void desvincular(LicencaInventarioVOs.VinculoReq req) {
        licencaService.obter(req.getLicencaId());
        cmdbLicencaSoftwareMapper.delete(new LambdaQueryWrapperX<LicencaSoftwareDO>()
                .eq(LicencaSoftwareDO::getLicencaId, req.getLicencaId()).eq(LicencaSoftwareDO::getSoftwareId, req.getSoftwareId()));
    }

}
