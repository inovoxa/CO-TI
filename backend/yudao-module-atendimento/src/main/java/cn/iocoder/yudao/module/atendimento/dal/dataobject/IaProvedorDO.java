package cn.iocoder.yudao.module.atendimento.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provedor de IA compatível com a API da OpenAI (dominio/ProvedoresIa). A chave fica cifrada com a chave-mestra.
 */
@TableName("atendimento.ia_provedor")
@KeySequence("atendimento.ia_provedor_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "chave")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IaProvedorDO extends BaseDO {

    @TableId
    private Long id;
    private String nome;
    private String tipo;
    private String baseUrl;
    private String chave;
    private String modelo;
    /** Modelo de transcrição (/audio/transcriptions); null = não transcreve */
    private String modeloAudio;
    private BigDecimal temperatura;
    private Integer maxTokens;
    /** Pede ao modelo que responda sem raciocinar antes (reasoning_effort = "none") */
    private Boolean semRaciocinio;
    private Boolean ativo;
    /** Menor primeiro: ordem da reserva quando o principal falha */
    private Integer prioridade;
    private LocalDateTime testadoEm;
    private String erroTeste;

}
