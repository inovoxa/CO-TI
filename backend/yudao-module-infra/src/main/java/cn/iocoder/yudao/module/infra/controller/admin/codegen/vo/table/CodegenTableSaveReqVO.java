package cn.iocoder.yudao.module.infra.controller.admin.codegen.vo.table;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.module.infra.enums.codegen.CodegenSceneEnum;
import cn.iocoder.yudao.module.infra.enums.codegen.CodegenTemplateTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "管理后台 - 代码生成表定义创建/修改 Response VO")
@Data
public class CodegenTableSaveReqVO {

    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "生成场景，参见 CodegenSceneEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o tipo de importação")
    private Integer scene;

    @Schema(description = "表名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao")
    @NotNull(message = "Informe a tabela")
    private String tableName;

    @Schema(description = "表描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道")
    @NotNull(message = "Informe a descrição")
    private String tableComment;

    @Schema(description = "备注", example = "我是备注")
    private String remark;

    @Schema(description = "模块名", requiredMode = Schema.RequiredMode.REQUIRED, example = "system")
    @NotNull(message = "Informe o módulo")
    @Pattern(regexp = "^[a-z][a-z0-9]*$", message = "O módulo só pode ter letras minúsculas e números, sem começar por número")
    private String moduleName;

    @Schema(description = "业务名", requiredMode = Schema.RequiredMode.REQUIRED, example = "codegen")
    @NotNull(message = "Informe a funcionalidade")
    @Pattern(regexp = "^[a-z][a-z0-9]*$", message = "A funcionalidade só pode ter letras minúsculas e números, sem começar por número")
    private String businessName;

    @Schema(description = "类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "CodegenTable")
    @NotNull(message = "Informe a classe")
    @Pattern(regexp = "^[A-Z][A-Za-z0-9]*$", message = "A classe deve começar com maiúscula e ter só letras e números")
    private String className;

    @Schema(description = "类描述", requiredMode = Schema.RequiredMode.REQUIRED, example = "代码生成器的表定义")
    @NotNull(message = "Informe a descrição da classe")
    private String classComment;

    @Schema(description = "作者", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    @NotNull(message = "Informe o autor")
    private String author;

    @Schema(description = "模板类型，参见 CodegenTemplateTypeEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "Informe o modelo")
    private Integer templateType;

    @Schema(description = "前端类型，参见 CodegenFrontTypeEnum 枚举", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    @NotNull(message = "Informe o frontend")
    private Integer frontType;

    @Schema(description = "父菜单编号", example = "1024")
    private Long parentMenuId;

    @Schema(description = "主表的编号", example = "2048")
    private Long masterTableId;
    @Schema(description = "子表关联主表的字段编号", example = "4096")
    private Long subJoinColumnId;
    @Schema(description = "主表与子表是否一对多", example = "4096")
    private Boolean subJoinMany;

    @Schema(description = "树表的父字段编号", example = "8192")
    private Long treeParentColumnId;
    @Schema(description = "树表的名字字段编号", example = "16384")
    private Long treeNameColumnId;

    @AssertTrue(message = "Informe o menu superior em [Editar configuração de geração -> Geração]")
    @JsonIgnore
    public boolean isParentMenuIdValid() {
        // 生成场景为管理后台时，必须设置上级菜单，不然生成的菜单 SQL 是无父级菜单的
        return ObjectUtil.notEqual(getScene(), CodegenSceneEnum.ADMIN.getScene())
                || getParentMenuId() != null;
    }

    @AssertTrue(message = "Faltam dados da tabela mestre")
    @JsonIgnore
    public boolean isSubValid() {
        return ObjectUtil.notEqual(getTemplateType(), CodegenTemplateTypeEnum.SUB)
                || (ObjectUtil.isAllNotEmpty(masterTableId, subJoinColumnId, subJoinMany));
    }

    @AssertTrue(message = "Faltam dados da árvore")
    @JsonIgnore
    public boolean isTreeValid() {
        return ObjectUtil.notEqual(templateType, CodegenTemplateTypeEnum.TREE)
                || (ObjectUtil.isAllNotEmpty(treeParentColumnId, treeNameColumnId));
    }

}
