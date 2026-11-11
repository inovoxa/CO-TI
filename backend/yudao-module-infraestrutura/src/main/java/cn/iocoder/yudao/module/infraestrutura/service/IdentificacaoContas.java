package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.service.AdConfiguracaoService;
import cn.iocoder.yudao.module.identidade.service.AdProvisionamentoService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Acha as contas de uma pessoa. Com o AD ligado, a resposta é sempre dele. Sem AD, procura só nas contas do portal, o que permite
 * testar a integração; nesse caso, atributos como matrícula não têm onde ser procurados e a pessoa fica sem identificação.
 */
@Service
public class IdentificacaoContas {

    public record Conta(String login, String nome, Set<String> grupos, boolean ativa) {
    }

    @Resource
    private AdConfiguracaoService configuracao;
    @Resource
    private AdminUserMapper adminUserMapper;

    /** @throws cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException se o AD não responder: tentar de novo, nunca decidir sem ele */
    public List<Conta> buscar(String atributo, String valor) {
        AdDiretorio ad = configuracao.diretorio().orElse(null);
        if (ad != null) {
            return ad.buscarPorAtributo(atributo, valor).stream()
                    .map(u -> new Conta(u.getLogin(), u.getNome(), u.getGrupos(), u.isAtivo())).toList();
        }
        List<AdminUserDO> portal = switch (atributo) {
            case "sAMAccountName", "userPrincipalName" -> adminUserMapper.selectList(AdminUserDO::getUsername, valor);
            case "mail" -> adminUserMapper.selectList(AdminUserDO::getEmail, valor);
            default -> List.of();
        };
        return portal.stream().map(u -> new Conta(u.getUsername(), u.getNickname(), Set.of(), CommonStatusEnum.isEnable(u.getStatus()))).toList();
    }

    public boolean protegida(Conta c, String gruposProtegidos) {
        if (gruposProtegidos == null || gruposProtegidos.isBlank()) {
            return false;
        }
        for (String g : gruposProtegidos.split(";")) {
            if (!g.isBlank() && AdProvisionamentoService.pertenceAoGrupo(c.grupos(), g.strip())) {
                return true;
            }
        }
        return false;
    }

}
