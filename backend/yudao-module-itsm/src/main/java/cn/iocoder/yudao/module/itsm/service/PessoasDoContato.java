package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException;
import cn.iocoder.yudao.module.identidade.ad.AdUsuario;
import cn.iocoder.yudao.module.identidade.service.AdConfiguracaoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pessoas para o campo Nome do contato de Ramais e telefones (digitando {@code @}): usuários do CO-TI (locais e os que
 * vieram do AD) e contas do AD que ainda não entraram, lidas na hora. Traz o e-mail e a unidade quando houver.
 */
@Slf4j
@Service
public class PessoasDoContato {

    private static final int LIMITE = 15;

    /**
     * {@code origem}: "local", "ad" (usuário do CO-TI vinculado ao AD) ou "ad-externo" (só no AD). {@code unidadeId} é
     * de Unidades e endereços (monitor_unit), a do campo Unidade do usuário ou, sem ela, a do setor do cadastro.
     */
    public record Pessoa(String origem, Long usuarioId, String login, String nome, String email, String setor,
                         Long unidadeId, String unidade) {
    }

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AdConfiguracaoService adConfiguracao;

    public List<Pessoa> buscar(String busca) {
        String t = busca == null ? "" : busca.strip();
        if (t.startsWith("@")) {
            t = t.substring(1).strip();
        }
        if (t.isEmpty()) {
            return List.of();
        }
        String like = "%" + t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Pessoa> r = new ArrayList<>(jdbcTemplate.query("""
                SELECT u.id, u.username, u.nickname, u.email, s.name AS setor, un.id AS unidade_id, un.name AS unidade,
                       EXISTS (SELECT 1 FROM identidade.vinculo_ad v WHERE v.user_id = u.id) AS do_ad
                FROM system_users u
                LEFT JOIN system_dept s ON s.id = u.dept_id
                LEFT JOIN LATERAL (
                    WITH RECURSIVE acima AS (
                        SELECT d.id, d.parent_id, 0 AS nivel FROM system_dept d
                        WHERE d.id = coalesce(cmdb.unidade_do_usuario(u.id), u.dept_id) AND d.deleted = 0
                        UNION ALL SELECT p.id, p.parent_id, a.nivel + 1 FROM system_dept p JOIN acima a ON p.id = a.parent_id
                        WHERE p.deleted = 0 AND a.nivel < 20)
                    SELECT m.id, m.name FROM acima a JOIN monitor_unit m ON m.dept_id = a.id AND m.deleted = 0
                    ORDER BY a.nivel LIMIT 1) un ON true
                WHERE u.deleted = 0 AND u.status = 0
                  AND (itsm.sem_acento(u.nickname) ILIKE itsm.sem_acento(?) OR u.username ILIKE ? OR u.email ILIKE ?)
                ORDER BY u.nickname LIMIT ?
                """, (rs, i) -> new Pessoa(rs.getBoolean("do_ad") ? "ad" : "local", rs.getLong("id"),
                rs.getString("username"), rs.getString("nickname"), vazioNulo(rs.getString("email")),
                rs.getString("setor"), (Long) rs.getObject("unidade_id"), rs.getString("unidade")),
                like, like, like, LIMITE));
        if (r.size() < LIMITE && t.length() >= 2) {
            r.addAll(doAd(t, r));
        }
        return r;
    }

    /** Contas do AD que ainda não são usuários do CO-TI (as que já são vieram na busca local). */
    private List<Pessoa> doAd(String trecho, List<Pessoa> locais) {
        Optional<AdDiretorio> ad;
        try {
            ad = adConfiguracao.diretorio();
        } catch (AdIndisponivelException e) {
            log.warn("[PessoasDoContato] AD indisponível: {}", e.getMessage());
            return List.of();
        }
        if (ad.isEmpty()) {
            return List.of();
        }
        Set<String> conhecidos = new HashSet<>();
        locais.forEach(p -> conhecidos.add(p.login().toLowerCase(Locale.ROOT)));
        conhecidos.addAll(jdbcTemplate.queryForList(
                "SELECT lower(username) FROM system_users WHERE deleted = 0 UNION SELECT lower(login) FROM identidade.vinculo_ad",
                String.class));
        List<Pessoa> r = new ArrayList<>();
        try {
            for (AdUsuario c : ad.get().buscarPorTrecho(trecho, LIMITE)) {
                if (!c.isAtivo() || c.getLogin() == null || conhecidos.contains(c.getLogin().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                r.add(new Pessoa("ad-externo", null, c.getLogin(), c.getNome() != null ? c.getNome() : c.getLogin(),
                        vazioNulo(c.getEmail()), null, null, null));
                if (locais.size() + r.size() >= LIMITE) {
                    break;
                }
            }
        } catch (AdIndisponivelException e) {
            log.warn("[PessoasDoContato] AD indisponível: {}", e.getMessage());
        }
        return r;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

}
