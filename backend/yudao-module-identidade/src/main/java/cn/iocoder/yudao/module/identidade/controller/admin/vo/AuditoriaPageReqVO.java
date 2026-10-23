package cn.iocoder.yudao.module.identidade.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "CO-TI - filtro da trilha de auditoria")
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditoriaPageReqVO extends PageParam {

    @Schema(description = "Ação exata, ex.: login.falha")
    private String acao;

    @Schema(description = "Usuário (parte do login)")
    private String usuario;

    @Schema(description = "ID do usuário")
    private Long userId;

    @Schema(description = "Intervalo [início, fim]")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] momento;

}
