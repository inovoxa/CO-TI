package cn.iocoder.yudao.module.itsm.dal.dataobject;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * Formulário do catálogo de serviços (GLPI: Glpi\Form).
 */
@TableName("itsm.formulario")
@KeySequence("itsm.formulario_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormularioDO extends BaseDO {

    @TableId
    private Long id;
    /** Ex.: "1.1", também usado no menu do WhatsApp */
    private String codigo;
    private String nome;
    private String descricao;
    /** Seção do catálogo */
    private String grupo;
    private String icone;
    private Long categoriaId;
    private Integer tipo;
    private Integer urgencia;
    private Integer impacto;
    /** Ex.: "Criação de usuário: {nome_completo}" */
    private String tituloModelo;
    /** JSON: {secoes:[{nome, perguntas:[...]}]} */
    private String definicao;
    /** nenhuma | gestor | campo | fixo */
    private String aprovacao;
    /** Pergunta com o e-mail do aprovador */
    private String aprovadorCampo;
    private Long aprovadorId;
    /** Criar | Reset | Reativar | Desativar | AcessoPasta | Transferir | CriarEmail */
    private String acaoAd;
    /** Aparece com realce no catálogo */
    private Boolean destaque;
    private Boolean ativo;
    private Integer ordem;

}
