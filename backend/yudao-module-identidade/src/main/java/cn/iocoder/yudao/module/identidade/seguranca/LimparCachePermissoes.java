package cn.iocoder.yudao.module.identidade.seguranca;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Limpa os caches de permissão do yudao (Redis) quando o servidor sobe.
 *
 * <p>As migrations do CO-TI dão menus a perfis direto no banco (system_role_menu). O yudao guarda no Redis quais
 * perfis têm cada menu, e o Redis sobrevive à atualização: sem esta limpeza, um perfil que ganhou um menu já
 * existente (ex.: "Abrir chamado" para o Atendente) continuaria sem a permissão até o cache expirar. Os caches se
 * refazem sozinhos na primeira consulta.
 */
@Slf4j
@Component
public class LimparCachePermissoes {

    /** Nomes em cn.iocoder.yudao.module.system.dal.redis.RedisKeyConstants */
    private static final List<String> CACHES = List.of("menu_role_ids", "permission_menu_ids", "user_role_ids", "role", "dept_children_ids");

    @Resource
    private CacheManager cacheManager;

    @EventListener(ApplicationReadyEvent.class)
    public void limpar() {
        for (String nome : CACHES) {
            try {
                Cache c = cacheManager.getCache(nome);
                if (c != null) {
                    c.clear();
                }
            } catch (Exception e) {
                // Redis fora no arranque: o cache expira sozinho; não impede o servidor de subir
                log.warn("[limpar][cache {} não limpo: {}]", nome, e.getMessage());
            }
        }
        log.info("[limpar][caches de permissão limpos]");
    }

}
