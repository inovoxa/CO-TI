package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException;
import cn.iocoder.yudao.module.identidade.ad.AdUsuario;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.auth.AutenticadorExterno;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Login pelo AD. Contas com vínculo só entram com a senha do AD; contas puramente locais (ex.: o administrador
 * de contingência) seguem pela senha local, para o portal não ficar inacessível se o AD cair.
 */
@Slf4j
public class AdAutenticador implements AutenticadorExterno {

    private final AdConfiguracaoService configuracao;
    private final AdProvisionamentoService provisionamento;
    private final AdminUserMapper adminUserMapper;
    private final VinculoAdMapper vinculoAdMapper;

    public AdAutenticador(AdConfiguracaoService configuracao, AdProvisionamentoService provisionamento,
                          AdminUserMapper adminUserMapper, VinculoAdMapper vinculoAdMapper) {
        this.configuracao = configuracao;
        this.provisionamento = provisionamento;
        this.adminUserMapper = adminUserMapper;
        this.vinculoAdMapper = vinculoAdMapper;
    }

    @Override
    @DataPermission(enable = false)
    public Resultado autenticar(String username, String password) {
        if (!configuracao.atual().isHabilitado()) {
            return Resultado.naoAplicavel();
        }
        AdminUserDO local = adminUserMapper.selectByUsername(username);
        VinculoAdDO vinculo = local == null ? null : vinculoAdMapper.selectByUserId(local.getId());
        if (local != null && vinculo == null) {
            return Resultado.naoAplicavel();
        }

        Optional<AdUsuario> conta;
        try {
            Optional<AdDiretorio> diretorio = configuracao.diretorio();
            if (diretorio.isEmpty()) {
                return Resultado.naoAplicavel();
            }
            conta = diretorio.get().autenticar(username, password);
        } catch (AdIndisponivelException e) {
            log.error("[autenticar][AD indisponível no login de {}]", username, e);
            // conta do AD sem AD não entra; conta desconhecida segue o fluxo local (que vai recusar)
            return vinculo != null ? Resultado.recusado(local) : Resultado.naoAplicavel();
        }
        if (conta.isEmpty()) {
            return vinculo != null ? Resultado.recusado(local) : Resultado.naoAplicavel();
        }
        Optional<AdminUserDO> usuario = provisionamento.provisionar(conta.get(), LocalDateTime.now());
        return usuario.map(Resultado::aceito).orElseGet(Resultado::naoAplicavel);
    }

}
