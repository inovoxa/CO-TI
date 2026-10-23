package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Responde "este chamado está no escopo de unidade do usuário logado?".
 *
 * <p>Fica num bean separado de propósito: o ChamadoService desliga o escopo nos fluxos de participante e, se a
 * checagem fosse um método da própria classe, a anotação seria ignorada (o proxy do Spring não intercepta
 * chamadas internas). Daqui, o escopo é religado no topo da pilha do yudao mesmo dentro de um fluxo desligado.
 */
@Component
public class ChamadoEscopo {

    @Resource
    private ChamadoMapper chamadoMapper;

    @DataPermission
    public boolean visivel(Long chamadoId) {
        return chamadoId != null && chamadoMapper.selectById(chamadoId) != null;
    }

}
