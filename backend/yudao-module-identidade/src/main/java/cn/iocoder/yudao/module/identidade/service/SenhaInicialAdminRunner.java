package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * O RuoYi vem com admin/admin123, senha pública. Na primeira partida ela é trocada pela senha definida em
 * {@code coti.seguranca.senha-inicial-admin}; sem essa configuração o backend não sobe.
 */
@Slf4j
@Component
public class SenhaInicialAdminRunner implements ApplicationRunner {

    static final String SENHA_PADRAO_PUBLICA = "admin123";
    static final int TAMANHO_MINIMO = 12;
    private static final Long ID_ADMIN = 1L;

    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Value("${coti.seguranca.senha-inicial-admin:}")
    private String senhaInicial;

    @Override
    @DataPermission(enable = false)
    public void run(ApplicationArguments args) {
        AdminUserDO admin = adminUserMapper.selectById(ID_ADMIN);
        if (admin == null || !passwordEncoder.matches(SENHA_PADRAO_PUBLICA, admin.getPassword())) {
            return;
        }
        validar(senhaInicial);
        admin.setPassword(passwordEncoder.encode(senhaInicial));
        adminUserMapper.updateById(admin);
        log.warn("[SenhaInicialAdminRunner][senha padrão do admin substituída pela de coti.seguranca.senha-inicial-admin;"
                + " remova a variável CO_TI_ADMIN_SENHA_INICIAL do ambiente]");
    }

    static void validar(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException("O usuário admin ainda tem a senha pública admin123. Defina "
                    + "CO_TI_ADMIN_SENHA_INICIAL (mínimo " + TAMANHO_MINIMO + " caracteres) para a primeira partida.");
        }
        if (senha.length() < TAMANHO_MINIMO || SENHA_PADRAO_PUBLICA.equals(senha)) {
            throw new IllegalStateException("CO_TI_ADMIN_SENHA_INICIAL precisa ter pelo menos " + TAMANHO_MINIMO
                    + " caracteres e não pode ser a senha padrão.");
        }
    }

}
