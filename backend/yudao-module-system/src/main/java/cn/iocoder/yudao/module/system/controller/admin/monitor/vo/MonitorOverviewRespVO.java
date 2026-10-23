package cn.iocoder.yudao.module.system.controller.admin.monitor.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Telão - visão geral (árvore de setores + unidades + indicadores)")
@Data
public class MonitorOverviewRespVO {

    @Schema(description = "Departamentos/setores (lista plana; o front monta a árvore por parentId)")
    private List<DeptNode> depts;

    @Schema(description = "Unidades/filiais com coordenadas e indicadores atuais")
    private List<Unit> units;

    @Schema(description = "Ocorrências recentes, mais novas primeiro")
    private List<OcorrenciaVO> ocorrencias;

    @Data
    public static class DeptNode {
        private Long id;
        private Long parentId;
        private String name;
        private Integer sort;
    }

    @Data
    public static class Unit {
        private Long id;
        private Long deptId;
        private String name;
        private String city;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private UnitKpiVO kpi;
    }

}
