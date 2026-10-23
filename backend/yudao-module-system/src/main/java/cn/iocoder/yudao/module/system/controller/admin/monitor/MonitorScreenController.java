package cn.iocoder.yudao.module.system.controller.admin.monitor;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorOverviewRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.monitor.MonitorKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource; // no branch master (JDK 8) troque por javax.annotation.Resource
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Gestão - Telão de monitoramento")
@RestController
@RequestMapping("/system/monitor-screen")
@Validated
public class MonitorScreenController {

    @Resource
    private DeptService deptService;
    @Resource
    private MonitorKpiService monitorKpiService;

    @GetMapping("/overview")
    @Operation(summary = "Carga inicial do telão: setores, unidades e indicadores atuais")
    @PreAuthorize("@ss.hasPermission('system:monitor-screen:query')")
    public CommonResult<MonitorOverviewRespVO> getOverview() {
        MonitorOverviewRespVO resp = new MonitorOverviewRespVO();

        // 1. Árvore de setores = departamentos ativos do próprio sistema
        DeptListReqVO req = new DeptListReqVO();
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        List<DeptDO> depts = deptService.getDeptList(req);
        resp.setDepts(depts.stream().map(d -> {
            MonitorOverviewRespVO.DeptNode n = new MonitorOverviewRespVO.DeptNode();
            n.setId(d.getId());
            n.setParentId(d.getParentId());
            n.setName(d.getName());
            n.setSort(d.getSort());
            return n;
        }).collect(Collectors.toList()));

        // 2. Unidades com coordenadas + última leitura conhecida
        List<MonitorUnitDO> unidades = monitorKpiService.getUnidadesAtivas();
        resp.setUnits(unidades.stream().map(u -> {
            MonitorOverviewRespVO.Unit v = new MonitorOverviewRespVO.Unit();
            v.setId(u.getId());
            v.setDeptId(u.getDeptId());
            v.setName(u.getName());
            v.setCity(u.getCity());
            v.setLatitude(u.getLatitude());
            v.setLongitude(u.getLongitude());
            v.setKpi(monitorKpiService.getKpi(u.getId()));
            return v;
        }).collect(Collectors.toList()));

        // 3. Ocorrências recentes (o feed continua ao vivo pelo WebSocket)
        resp.setOcorrencias(monitorKpiService.getOcorrencias());

        return success(resp);
    }

}
