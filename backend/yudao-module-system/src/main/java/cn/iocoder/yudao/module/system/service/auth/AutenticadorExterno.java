package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CO-TI: diretório externo consultado no login antes da senha local (implementado pelo módulo identidade, para o AD).
 *
 * <p>Regras que a implementação deve seguir:
 * <ul>
 *   <li>{@link Tipo#ACEITO}: credencial válida no diretório; devolve o usuário local já provisionado/atualizado;</li>
 *   <li>{@link Tipo#RECUSADO}: a conta pertence ao diretório e a senha está errada (ou a conta está desativada lá).
 *       Nunca cai para a senha local, senão uma senha antiga do banco continuaria valendo;</li>
 *   <li>{@link Tipo#NAO_APLICAVEL}: a conta não é do diretório (ex.: administrador de contingência) ou o diretório
 *       está desligado; o login segue pela senha local.</li>
 * </ul>
 */
public interface AutenticadorExterno {

    enum Tipo { ACEITO, RECUSADO, NAO_APLICAVEL }

    @Getter
    @AllArgsConstructor
    class Resultado {
        private final Tipo tipo;
        private final AdminUserDO usuario;

        public static Resultado aceito(AdminUserDO usuario) {
            return new Resultado(Tipo.ACEITO, usuario);
        }

        public static Resultado recusado(AdminUserDO usuario) {
            return new Resultado(Tipo.RECUSADO, usuario);
        }

        public static Resultado naoAplicavel() {
            return new Resultado(Tipo.NAO_APLICAVEL, null);
        }
    }

    Resultado autenticar(String username, String password);

}
