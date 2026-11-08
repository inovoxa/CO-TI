package cn.iocoder.yudao.module.chat.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.chat.dal.dataobject.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public final class ChatMappers {

    private ChatMappers() {
    }

    @Mapper
    public interface Conversa extends BaseMapperX<ChatConversaDO> {
        /** Trava a conversa: é o que garante números sequenciais sem buraco nem repetição. */
        @Select("SELECT * FROM chat.conversa WHERE id = #{id} AND deleted = 0 FOR UPDATE")
        ChatConversaDO selectByIdParaAtualizar(@Param("id") Long id);
    }

    @Mapper
    public interface Participante extends BaseMapperX<ChatParticipanteDO> {
        /** Nunca volta atrás: uma aba atrasada não desmarca o que outra já leu. */
        @Update("UPDATE chat.participante SET ultima_lida_seq = GREATEST(ultima_lida_seq, #{seq}), update_time = CURRENT_TIMESTAMP"
                + " WHERE conversa_id = #{conversaId} AND user_id = #{userId} AND deleted = 0")
        int marcarLida(@Param("conversaId") Long conversaId, @Param("userId") Long userId, @Param("seq") Long seq);

        @Select("SELECT COALESCE(SUM(GREATEST(c.ultima_seq - GREATEST(p.ultima_lida_seq, p.desde_seq), 0)), 0) FROM chat.participante p"
                + " JOIN chat.conversa c ON c.id = p.conversa_id AND c.deleted = 0 WHERE p.user_id = #{userId} AND p.deleted = 0")
        long totalNaoLidas(@Param("userId") Long userId);
    }

    @Mapper
    public interface Mensagem extends BaseMapperX<ChatMensagemDO> {
        /** Só nas conversas da pessoa, a partir de quando entrou; apagada não aparece. */
        @Select("<script>SELECT m.* FROM chat.mensagem m JOIN chat.participante p ON p.conversa_id = m.conversa_id"
                + " AND p.user_id = #{userId} AND p.deleted = 0"
                + " WHERE m.deleted = 0 AND m.apagada_em IS NULL AND m.tipo = 'texto' AND m.seq &gt; p.desde_seq"
                + " <if test='conversaId != null'>AND m.conversa_id = #{conversaId}</if>"
                + " AND (to_tsvector('portuguese', m.conteudo) @@ plainto_tsquery('portuguese', #{texto})"
                + " OR m.conteudo ILIKE #{padrao}) ORDER BY m.create_time DESC LIMIT 50</script>")
        List<ChatMensagemDO> buscar(@Param("userId") Long userId, @Param("conversaId") Long conversaId,
                                @Param("texto") String texto, @Param("padrao") String padrao);
    }

    @Mapper
    public interface Ausencia extends BaseMapperX<ChatAusenciaDO> {
    }

    @Mapper
    public interface Historico extends BaseMapperX<ChatMensagemHistoricoDO> {
    }

    @Mapper
    public interface Anexo extends BaseMapperX<ChatAnexoDO> {
    }

}
