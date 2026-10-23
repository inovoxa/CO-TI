package cn.iocoder.yudao.module.identidade.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Registro da trilha de auditoria. Somente inserção (há trigger no banco); sem exclusão lógica nem "updater".
 */
@TableName("identidade.auditoria")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditoriaDO {

    /** Vem de identidade.auditoria_seq antes do insert, porque entra no hash */
    @TableId(type = IdType.INPUT)
    private Long id;
    private LocalDateTime momento;
    private String acao;
    private Long userId;
    private String usuario;
    private String ip;
    /** JSON gravado como texto: o hash é calculado sobre exatamente estes bytes */
    private String detalhes;
    private String hashAnterior;
    private String hash;

}
