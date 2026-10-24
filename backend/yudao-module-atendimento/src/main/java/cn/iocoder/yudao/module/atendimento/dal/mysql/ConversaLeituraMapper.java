package cn.iocoder.yudao.module.atendimento.dal.mysql;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Até onde cada pessoa leu cada conversa.
 */
@Mapper
public interface ConversaLeituraMapper {

    @Insert("INSERT INTO atendimento.conversa_leitura (conversa_id, user_id, lida_ate) VALUES (#{conversaId}, #{userId}, #{ate})"
            + " ON CONFLICT (conversa_id, user_id) DO UPDATE SET lida_ate = GREATEST(atendimento.conversa_leitura.lida_ate, EXCLUDED.lida_ate)")
    void marcar(@Param("conversaId") Long conversaId, @Param("userId") Long userId, @Param("ate") Long ate);

}
