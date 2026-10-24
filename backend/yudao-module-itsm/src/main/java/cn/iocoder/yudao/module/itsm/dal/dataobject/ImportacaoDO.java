package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Uma execução do importador de dados (hoje, do GLPI).
 */
@TableName("itsm.importacao")
@KeySequence("itsm.importacao_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportacaoDO extends BaseDO {

    public static final String PENDENTE = "pendente";
    public static final String EXECUTANDO = "executando";
    public static final String CONCLUIDA = "concluida";
    public static final String ERRO = "erro";
    public static final String CANCELADA = "cancelada";

    @TableId
    private Long id;
    private String origem;
    private String status;
    private String host;
    private Integer porta;
    private String banco;
    private String usuario;
    /** JSON das opções (ImportacaoVOs.Opcoes) */
    private String opcoes;
    private String etapa;
    /** JSON: etapa → {lidos, importados, existentes, erros} */
    private String progresso;
    private String log;
    private String versaoOrigem;
    private Boolean cancelar;
    private LocalDateTime iniciadoEm;
    private LocalDateTime terminadoEm;

}
