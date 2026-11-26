package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Pessoa do portal pelo login que o agente informa: o vínculo com o AD (sAMAccountName), senão o usuário local de mesmo
 * nome. "DOMINIO\\login" e "login@dominio" valem como "login"; maiúsculas não importam. Sem achar, null (sem palpite).
 */
@Component
public class UsuarioDoLogin {

    @Resource
    private VinculoAdMapper vinculoAdMapper;
    @Resource
    private AdminUserMapper adminUserMapper;

    public Long usuario(String login) {
        String l = limpo(login);
        if (l == null) {
            return null;
        }
        VinculoAdDO v = vinculoAdMapper.selectOne(new LambdaQueryWrapperX<VinculoAdDO>()
                .apply("lower(login) = {0}", l).last("LIMIT 1"));
        if (v != null) {
            return v.getUserId();
        }
        List<AdminUserDO> u = adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .apply("lower(username) = {0}", l).orderByAsc(AdminUserDO::getId).last("LIMIT 2"));
        // dois usuários com o mesmo login em maiúsculas diferentes: ambíguo, não escolhe
        return u.size() == 1 ? u.get(0).getId() : u.stream().filter(x -> CommonStatusEnum.isEnable(x.getStatus())).count() == 1
                ? u.stream().filter(x -> CommonStatusEnum.isEnable(x.getStatus())).findFirst().get().getId() : null;
    }

    /** "PMA\\Maria.Souza" → "maria.souza"; "maria@pma.local" → "maria". */
    static String limpo(String login) {
        if (login == null) {
            return null;
        }
        String u = login.strip();
        int barra = u.lastIndexOf('\\');
        if (barra >= 0) {
            u = u.substring(barra + 1);
        }
        int arroba = u.indexOf('@');
        if (arroba > 0) {
            u = u.substring(0, arroba);
        }
        u = u.strip().toLowerCase(Locale.ROOT);
        return u.isEmpty() ? null : u;
    }

}
