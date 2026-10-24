package cn.iocoder.yudao.module.itsm.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailSaidaDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EmailSaidaMapper extends BaseMapperX<EmailSaidaDO> {

    @Select("SELECT id FROM itsm.email_saida WHERE status = 'pendente'"
            + " AND (proxima_tentativa IS NULL OR proxima_tentativa <= now()) ORDER BY id LIMIT #{limite}")
    List<Long> selectIdsPendentes(@Param("limite") int limite);

    /** Só uma instância consegue pegar a mensagem. */
    @Update("UPDATE itsm.email_saida SET status = 'enviando', tentativas = tentativas + 1, proxima_tentativa = now() WHERE id = #{id} AND status = 'pendente'")
    int reivindicar(@Param("id") Long id);

    /** Mensagens presas em "enviando" (o servidor parou no meio) voltam para a fila. */
    @Update("UPDATE itsm.email_saida SET status = 'pendente' WHERE status = 'enviando'"
            + " AND proxima_tentativa < now() - interval '10 minutes'")
    int destravar();

}
