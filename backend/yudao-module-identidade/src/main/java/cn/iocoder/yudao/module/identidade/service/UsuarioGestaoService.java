package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.identidade.controller.admin.vo.UsuarioGestaoVOs;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.identidade.enums.ErrorCodeConstants.USUARIOS_INVALIDO;

/**
 * Sistema → Usuários do CO-TI, por cima do cadastro do yudao: perfis, origem (AD ou local), último acesso, segundo
 * fator e sessões abertas; ações em lote; revisão de acessos; planilha com prévia.
 * <p>
 * A lista passa pelo escopo de dados do perfil (mapper do yudao com a regra de unidade); o resto completa por id.
 * Contas do AD: nome, e-mail, celular e situação vêm do AD e a próxima sincronização sobrescreve o que mudar aqui.
 */
@Service
public class UsuarioGestaoService {

    /** Quem tem alguma destas permissões (ou o perfil super_admin) administra o acesso dos outros. */
    static final List<String> PRIVILEGIOS = List.of("system:user:create", "system:user:update", "system:user:delete",
            "system:user:update-password", "system:role:create", "system:role:update", "system:permission:assign-user-role",
            "system:permission:assign-role-menu", "system:permission:assign-role-data-scope", "system:menu:update",
            "identidade:mfa:redefinir");
    static final int DIAS_SEM_ACESSO = 90;
    private static final int MAX_LINHAS = 5000;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Map<Integer, String> ESCOPOS = Map.of(1, "todas as unidades", 2, "unidades escolhidas",
            3, "só a própria unidade", 4, "a unidade e as subunidades", 5, "só os próprios registros");

    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private AdminUserService adminUserService;
    @Resource
    private DeptService deptService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private MfaService mfaService;
    @Resource
    private AdConfiguracaoService adConfiguracaoService;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    // ======================================================================================= lista

    public PageResult<UsuarioGestaoVOs.Linha> pagina(UsuarioGestaoVOs.PaginaReq req) {
        PageResult<AdminUserDO> p = adminUserMapper.selectPage(req, filtro(req).last(ordenacao(req.getOrdem(), req.getDirecao())));
        return new PageResult<>(linhas(p.getList()), p.getTotal());
    }

    private LambdaQueryWrapperX<AdminUserDO> filtro(UsuarioGestaoVOs.PaginaReq req) {
        LambdaQueryWrapperX<AdminUserDO> q = new LambdaQueryWrapperX<AdminUserDO>().eqIfPresent(AdminUserDO::getStatus, req.getStatus());
        if (req.getBusca() != null && !req.getBusca().isBlank()) {
            String b = "%" + req.getBusca().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            q.and(w -> w.apply("nickname ILIKE {0}", b).or().apply("username ILIKE {0}", b).or().apply("email ILIKE {0}", b)
                    .or().apply("mobile ILIKE {0}", b));
        }
        if (req.getDeptId() != null) {
            Set<Long> depts = new HashSet<>(deptService.getChildDeptIdListFromCache(req.getDeptId()));
            depts.add(req.getDeptId());
            q.in(AdminUserDO::getDeptId, depts);
        }
        if (req.getUnidadeId() != null) {
            if (req.getUnidadeId() < 0) {
                q.apply(SQL_UNIDADE + " IS NULL");
            } else {
                Set<Long> us = new TreeSet<>(deptService.getChildDeptIdListFromCache(req.getUnidadeId()));
                us.add(req.getUnidadeId());
                // só números (Long) entram no texto
                q.apply(SQL_UNIDADE + " IN (" + us.stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")");
            }
        }
        if (req.getRoleId() != null) {
            q.apply("EXISTS (SELECT 1 FROM system_user_role r WHERE r.user_id = system_users.id AND r.deleted = 0 AND r.role_id = {0})",
                    req.getRoleId());
        }
        if ("ad".equals(req.getOrigem())) {
            q.apply("EXISTS (SELECT 1 FROM identidade.vinculo_ad v WHERE v.user_id = system_users.id AND v.deleted = 0)");
        } else if ("local".equals(req.getOrigem())) {
            q.apply("NOT EXISTS (SELECT 1 FROM identidade.vinculo_ad v WHERE v.user_id = system_users.id AND v.deleted = 0)");
        }
        if (req.getSemAcessoDias() != null && req.getSemAcessoDias() > 0) {
            LocalDateTime corte = LocalDateTime.now().minusDays(req.getSemAcessoDias());
            q.and(w -> w.isNull(AdminUserDO::getLoginDate).or().lt(AdminUserDO::getLoginDate, corte));
        }
        if (Boolean.TRUE.equals(req.getSemMfa())) {
            q.apply("NOT EXISTS (SELECT 1 FROM identidade.mfa_totp m WHERE m.user_id = system_users.id AND m.deleted = 0 AND m.ativo)");
        }
        if (Boolean.TRUE.equals(req.getPrivilegiado())) {
            q.apply("system_users.id IN (" + SQL_PRIVILEGIADOS + ")");
        }
        return q;
    }

    /** Usuários com perfil super_admin ou com perfil que tem alguma permissão de {@link #PRIVILEGIOS}. */
    static final String SQL_PRIVILEGIADOS = "SELECT ur.user_id FROM system_user_role ur JOIN system_role ro ON ro.id = ur.role_id"
            + " AND ro.deleted = 0 AND ro.status = 0 WHERE ur.deleted = 0 AND (ro.code = 'super_admin' OR EXISTS (SELECT 1"
            + " FROM system_role_menu rm JOIN system_menu me ON me.id = rm.menu_id AND me.deleted = 0 WHERE rm.role_id = ro.id"
            + " AND rm.deleted = 0 AND me.permission IN ("
            + PRIVILEGIOS.stream().map(p -> "'" + p + "'").collect(Collectors.joining(", ")) + ")))";

    /** Unidade da pessoa pela máquina que ela usa (função da V93) */
    static final String SQL_UNIDADE = "cmdb.unidade_do_usuario(system_users.id)";

    static final Map<String, String> ORDENS = Map.of("nome", "lower(nickname)", "usuario", "lower(username)",
            "unidade", "lower((SELECT d.name FROM system_dept d WHERE d.id = " + SQL_UNIDADE + "))", "acesso", "login_date",
            "criado", "create_time", "situacao", "status", "email", "lower(email)");

    static String ordenacao(String ordem, String direcao) {
        String expr = ORDENS.getOrDefault(ordem == null ? "nome" : ordem, ORDENS.get("nome"));
        return "ORDER BY " + expr + ("desc".equalsIgnoreCase(direcao) ? " DESC" : " ASC") + " NULLS LAST, id";
    }

    /** Completa os usuários com perfis, unidade, origem, segundo fator e sessões. */
    List<UsuarioGestaoVOs.Linha> linhas(List<AdminUserDO> usuarios) {
        if (usuarios.isEmpty()) {
            return new ArrayList<>();
        }
        String ids = usuarios.stream().map(u -> String.valueOf(u.getId())).collect(Collectors.joining(",", "{", "}"));
        Map<Long, List<UsuarioGestaoVOs.Perfil>> perfis = new HashMap<>();
        jdbcTemplate.query("""
                SELECT ur.user_id, ro.id, ro.name, ro.code, ro.data_scope FROM system_user_role ur
                JOIN system_role ro ON ro.id = ur.role_id AND ro.deleted = 0
                WHERE ur.deleted = 0 AND ur.user_id = ANY(?::int8[]) ORDER BY ro.sort, ro.id
                """, (RowCallbackHandler) rs -> perfis.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(
                new UsuarioGestaoVOs.Perfil(rs.getLong(2), rs.getString(3), rs.getString(4), ESCOPOS.get(rs.getInt(5)))), ids);
        Set<Long> privilegiados = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT user_id FROM (" + SQL_PRIVILEGIADOS + ") p WHERE user_id = ANY(?::int8[])", Long.class, ids));
        Map<Long, Object[]> ad = new HashMap<>();
        jdbcTemplate.query("SELECT user_id, ativo_no_ad, ultima_sincronizacao FROM identidade.vinculo_ad WHERE deleted = 0 AND user_id = ANY(?::int8[])",
                (RowCallbackHandler) rs -> ad.put(rs.getLong(1), new Object[]{rs.getBoolean(2), data(rs.getTimestamp(3))}), ids);
        Set<Long> mfa = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT user_id FROM identidade.mfa_totp WHERE deleted = 0 AND ativo AND user_id = ANY(?::int8[])", Long.class, ids));
        Map<Long, Long> sessoes = new HashMap<>();
        jdbcTemplate.query("""
                SELECT user_id, count(*) FROM system_oauth2_access_token WHERE deleted = 0 AND user_type = ? AND expires_time > now()
                AND user_id = ANY(?::int8[]) GROUP BY user_id
                """, (RowCallbackHandler) rs -> sessoes.put(rs.getLong(1), rs.getLong(2)), UserTypeEnum.ADMIN.getValue(), ids);
        // máquina de contato mais recente (a mesma ordem da função) e a unidade que ela dá
        Map<Long, Object[]> maquinas = new HashMap<>();
        jdbcTemplate.query("""
                SELECT DISTINCT ON (usuario_id) usuario_id, nome, ip, dept_manual, cmdb.unidade_do_usuario(usuario_id)
                FROM cmdb.ativo WHERE deleted = 0 AND situacao <> 'baixado' AND mesclado_em IS NULL AND usuario_id = ANY(?::int8[])
                ORDER BY usuario_id, ultimo_contato DESC NULLS LAST, id DESC
                """, (RowCallbackHandler) rs -> maquinas.put(rs.getLong(1), new Object[]{rs.getString(2), rs.getString(3),
                rs.getBoolean(4), (Long) rs.getObject(5, Long.class)}), ids);
        Set<Long> deptIds = new HashSet<>();
        usuarios.stream().map(AdminUserDO::getDeptId).filter(Objects::nonNull).forEach(deptIds::add);
        maquinas.values().stream().map(m -> (Long) m[3]).filter(Objects::nonNull).forEach(deptIds::add);
        Map<Long, String> depts = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM system_dept WHERE deleted = 0 AND id = ANY(?::int8[])",
                (RowCallbackHandler) rs -> depts.put(rs.getLong(1), rs.getString(2)),
                deptIds.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}")));
        List<UsuarioGestaoVOs.Linha> l = new ArrayList<>();
        for (AdminUserDO u : usuarios) {
            UsuarioGestaoVOs.Linha x = new UsuarioGestaoVOs.Linha();
            x.setId(u.getId());
            x.setUsername(u.getUsername());
            x.setNickname(u.getNickname());
            x.setEmail(u.getEmail());
            x.setMobile(u.getMobile());
            x.setDeptId(u.getDeptId());
            x.setDeptNome(u.getDeptId() == null ? null : depts.get(u.getDeptId()));
            Object[] m = maquinas.get(u.getId());
            if (m == null) {
                x.setUnidadeOrigem("sem_maquina");
            } else {
                Long unidade = (Long) m[3];
                x.setUnidadeMaquina((String) m[0]);
                x.setUnidadeIp((String) m[1]);
                x.setUnidadeId(unidade);
                x.setUnidadeNome(unidade == null ? null : depts.get(unidade));
                x.setUnidadeOrigem(unidade == null ? "fora" : (Boolean) m[2] ? "manual" : "faixa");
            }
            x.setStatus(u.getStatus());
            x.setPerfis(perfis.getOrDefault(u.getId(), new ArrayList<>()));
            x.setPrivilegiado(privilegiados.contains(u.getId()));
            Object[] v = ad.get(u.getId());
            x.setOrigem(v == null ? "local" : "ad");
            x.setAdAtivo(v == null ? null : (Boolean) v[0]);
            x.setAdSincronizacao(v == null ? null : (LocalDateTime) v[1]);
            x.setMfa(mfa.contains(u.getId()));
            x.setUltimoAcesso(u.getLoginDate());
            x.setUltimoIp(u.getLoginIp());
            x.setCriadoEm(u.getCreateTime());
            x.setSessoes(sessoes.getOrDefault(u.getId(), 0L));
            l.add(x);
        }
        return l;
    }

    // ======================================================================================= ficha

    public UsuarioGestaoVOs.Ficha ficha(Long id) {
        AdminUserDO u = adminUserMapper.selectById(id);   // escopo de dados: fora dele, não acha
        if (u == null) {
            throw exception(USUARIOS_INVALIDO, "usuário não encontrado");
        }
        UsuarioGestaoVOs.Ficha f = new UsuarioGestaoVOs.Ficha();
        f.setUsuario(linhas(List.of(u)).get(0));
        f.setObservacao(u.getRemark());
        jdbcTemplate.query("SELECT login, dn, gestor_dn FROM identidade.vinculo_ad WHERE deleted = 0 AND user_id = ?", (RowCallbackHandler) rs -> {
            f.setAdLogin(rs.getString(1));
            f.setAdDn(rs.getString(2));
            f.setAdGestorDn(rs.getString(3));
        }, id);
        jdbcTemplate.query("SELECT ativado_em FROM identidade.mfa_totp WHERE deleted = 0 AND ativo AND user_id = ?",
                (RowCallbackHandler) rs -> f.setMfaAtivadoEm(data(rs.getTimestamp(1))), id);
        f.setSessoes(jdbcTemplate.query("""
                SELECT create_time, expires_time, client_id FROM system_oauth2_access_token
                WHERE deleted = 0 AND user_type = ? AND user_id = ? AND expires_time > now() ORDER BY create_time DESC LIMIT 20
                """, (rs, i) -> {
            UsuarioGestaoVOs.Sessao s = new UsuarioGestaoVOs.Sessao();
            s.setCriada(data(rs.getTimestamp(1)));
            s.setExpira(data(rs.getTimestamp(2)));
            s.setCliente(rs.getString(3));
            return s;
        }, UserTypeEnum.ADMIN.getValue(), id));
        f.setAcessos(jdbcTemplate.query("""
                SELECT create_time, user_ip, user_agent, result, log_type FROM system_login_log
                WHERE deleted = 0 AND (user_id = ? OR (user_id = 0 AND username = ?)) ORDER BY id DESC LIMIT 15
                """, (rs, i) -> {
            UsuarioGestaoVOs.Acesso a = new UsuarioGestaoVOs.Acesso();
            a.setQuando(data(rs.getTimestamp(1)));
            a.setIp(rs.getString(2));
            a.setNavegador(rs.getString(3));
            a.setResultado(rs.getInt(4));
            a.setTipo(switch (rs.getInt(5)) {
                case 100 -> "entrada";
                case 200 -> "saída";
                default -> "outro";
            });
            return a;
        }, id, u.getUsername()));
        f.setAuditoria(jdbcTemplate.query("""
                SELECT momento, acao, ip, detalhes FROM identidade.auditoria WHERE user_id = ? ORDER BY id DESC LIMIT 20
                """, (rs, i) -> {
            UsuarioGestaoVOs.Evento e = new UsuarioGestaoVOs.Evento();
            e.setMomento(data(rs.getTimestamp(1)));
            e.setAcao(rs.getString(2));
            e.setIp(rs.getString(3));
            String d = rs.getString(4);
            e.setDetalhes(d == null || d.equals("{}") ? null : d.length() > 300 ? d.substring(0, 300) + "…" : d);
            return e;
        }, id));
        return f;
    }

    // ======================================================================================= lote

    @Transactional(rollbackFor = Exception.class)
    public UsuarioGestaoVOs.LoteResp lote(UsuarioGestaoVOs.LoteReq req, Long adminId, String adminLogin) {
        UsuarioGestaoVOs.LoteResp r = new UsuarioGestaoVOs.LoteResp();
        Set<Long> ids = new LinkedHashSet<>(req.getIds());
        ids.remove(null);
        // só quem o escopo de dados deixa ver
        List<AdminUserDO> usuarios = adminUserMapper.selectByIds(ids);
        r.setIgnorados(ids.size() - usuarios.size());
        Set<Long> doAd = new HashSet<>(jdbcTemplate.queryForList("SELECT user_id FROM identidade.vinculo_ad WHERE deleted = 0", Long.class));
        boolean espelhaOus = adConfiguracaoService.atual().isEspelharOus();
        switch (req.getAcao()) {
            case "mover" -> {
                if (req.getDeptId() == null || deptService.getDept(req.getDeptId()) == null) {
                    throw exception(USUARIOS_INVALIDO, "escolha o departamento");
                }
            }
            case "perfil", "tirar_perfil" -> {
                if (req.getRoleId() == null || jdbcTemplate.queryForObject("SELECT count(*) FROM system_role WHERE id = ? AND deleted = 0",
                        Long.class, req.getRoleId()) == 0) {
                    throw exception(USUARIOS_INVALIDO, "escolha o perfil");
                }
            }
            default -> {
            }
        }
        int ad = 0;
        for (AdminUserDO u : usuarios) {
            boolean deAd = doAd.contains(u.getId());
            try {
                switch (req.getAcao()) {
                    case "ativar" -> adminUserService.updateUserStatus(u.getId(), CommonStatusEnum.ENABLE.getStatus());
                    // o serviço do yudao já encerra as sessões de quem é desativado
                    case "desativar" -> {
                        if (u.getId().equals(adminId)) {
                            throw new IllegalArgumentException("você não pode desativar a própria conta");
                        }
                        adminUserService.updateUserStatus(u.getId(), CommonStatusEnum.DISABLE.getStatus());
                    }
                    case "mover" -> adminUserMapper.updateById(AdminUserDO.builder().id(u.getId()).deptId(req.getDeptId()).build());
                    case "perfil" -> {
                        Set<Long> atuais = new HashSet<>(permissionService.getUserRoleIdListByUserId(u.getId()));
                        if (atuais.add(req.getRoleId())) {
                            permissionService.assignUserRole(u.getId(), atuais);
                        }
                    }
                    case "tirar_perfil" -> {
                        Set<Long> atuais = new HashSet<>(permissionService.getUserRoleIdListByUserId(u.getId()));
                        if (u.getId().equals(adminId) && "super_admin".equals(codigoPerfil(req.getRoleId()))) {
                            throw new IllegalArgumentException("você não pode tirar de si mesmo o perfil de administrador");
                        }
                        if (atuais.remove(req.getRoleId())) {
                            permissionService.assignUserRole(u.getId(), atuais);
                        }
                    }
                    case "mfa" -> mfaService.redefinir(u.getId(), adminId, adminLogin);
                    case "encerrar" -> oauth2TokenService.removeAccessToken(u.getId(), UserTypeEnum.ADMIN.getValue());
                    default -> throw new IllegalArgumentException("ação desconhecida");
                }
                r.setFeitos(r.getFeitos() + 1);
                if (deAd && (req.getAcao().equals("ativar") || req.getAcao().equals("desativar") || (req.getAcao().equals("mover") && espelhaOus))) {
                    ad++;
                }
            } catch (ServiceException | IllegalArgumentException e) {
                r.setIgnorados(r.getIgnorados() + 1);
                if (r.getAvisos().size() < 20) {
                    r.getAvisos().add(u.getUsername() + ": " + e.getMessage());
                }
            }
        }
        if (ad > 0) {
            r.getAvisos().add(ad + " conta(s) vêm do AD: a próxima sincronização volta " + (req.getAcao().equals("mover")
                    ? "o departamento para o da OU no AD" : "a situação para a que está no AD") + ". Mude no AD para valer.");
        }
        auditoriaService.registrar("identidade.usuarios_lote", adminId, adminLogin, Map.of("acao", req.getAcao(),
                "usuarios", usuarios.stream().map(AdminUserDO::getUsername).limit(200).toList(), "feitos", r.getFeitos(),
                "departamento", Objects.toString(req.getDeptId(), ""), "perfil", Objects.toString(req.getRoleId(), "")));
        return r;
    }

    private String codigoPerfil(Long roleId) {
        List<String> l = jdbcTemplate.queryForList("SELECT code FROM system_role WHERE id = ?", String.class, roleId);
        return l.isEmpty() ? null : l.get(0);
    }

    // ======================================================================================= revisão

    /** O que vale olhar de tempos em tempos (ativos, no escopo de quem vê). */
    public UsuarioGestaoVOs.Revisao revisao() {
        UsuarioGestaoVOs.Revisao r = new UsuarioGestaoVOs.Revisao();
        r.setDiasSemAcesso(DIAS_SEM_ACESSO);
        List<UsuarioGestaoVOs.Linha> ativos = linhas(adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus()).orderByAsc(AdminUserDO::getNickname).last("LIMIT 5000")));
        LocalDateTime corte = LocalDateTime.now().minusDays(DIAS_SEM_ACESSO);
        for (UsuarioGestaoVOs.Linha l : ativos) {
            if (Boolean.TRUE.equals(l.getPrivilegiado())) {
                r.getPrivilegiados().add(l);
                if (!Boolean.TRUE.equals(l.getMfa())) {
                    r.getPrivilegiadosSemMfa().add(l);
                }
            }
            if ((l.getUltimoAcesso() == null && l.getCriadoEm() != null && l.getCriadoEm().isBefore(corte))
                    || (l.getUltimoAcesso() != null && l.getUltimoAcesso().isBefore(corte))) {
                r.getSemAcesso().add(l);
            }
            if ("local".equals(l.getOrigem())) {
                r.getLocais().add(l);
            }
        }
        // desativadas no AD que ainda têm algo no portal: conta ativa ou sessão aberta
        List<AdminUserDO> noAd = adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>().apply(
                "EXISTS (SELECT 1 FROM identidade.vinculo_ad v WHERE v.user_id = system_users.id AND v.deleted = 0 AND NOT v.ativo_no_ad)"));
        linhas(noAd).stream().filter(l -> CommonStatusEnum.isEnable(l.getStatus()) || l.getSessoes() > 0).forEach(r.getDesativadosNoAd()::add);
        jdbcTemplate.query("SELECT momento, usuario FROM identidade.auditoria WHERE acao = 'identidade.revisao_acessos' ORDER BY id DESC LIMIT 1",
                (RowCallbackHandler) rs -> {
                    r.setUltimaRevisao(data(rs.getTimestamp(1)));
                    r.setRevisadoPor(rs.getString(2));
                });
        return r;
    }

    public void revisado(Long adminId, String adminLogin, String observacao) {
        UsuarioGestaoVOs.Revisao r = revisao();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("privilegiados", r.getPrivilegiados().stream().map(UsuarioGestaoVOs.Linha::getUsername).toList());
        d.put("privilegiados_sem_mfa", r.getPrivilegiadosSemMfa().size());
        d.put("sem_acesso", r.getSemAcesso().size());
        d.put("locais", r.getLocais().size());
        d.put("desativados_no_ad", r.getDesativadosNoAd().size());
        if (observacao != null && !observacao.isBlank()) {
            d.put("observacao", observacao.trim().length() > 500 ? observacao.trim().substring(0, 500) : observacao.trim());
        }
        auditoriaService.registrar("identidade.revisao_acessos", adminId, adminLogin, d);
    }

    // ======================================================================================= planilha

    static final List<String> COLUNAS = List.of("Usuário", "Nome", "E-mail", "Celular", "Departamento", "Situação", "Perfis",
            "Origem", "Verificação em duas etapas", "Último acesso", "IP do último acesso");

    public List<Planilha.Aba> exportar(UsuarioGestaoVOs.PaginaReq req, Long adminId) {
        List<List<Object>> linhas = new ArrayList<>();
        req.setPageSize(500);
        for (int pagina = 1; ; pagina++) {
            req.setPageNo(pagina);
            PageResult<UsuarioGestaoVOs.Linha> p = pagina(req);
            if (p.getTotal() > 50_000) {
                throw exception(USUARIOS_INVALIDO, "são " + p.getTotal() + " usuários; use mais filtros (limite de 50.000)");
            }
            for (UsuarioGestaoVOs.Linha l : p.getList()) {
                linhas.add(Arrays.asList(l.getUsername(), l.getNickname(), l.getEmail(), l.getMobile(), l.getDeptNome(),
                        CommonStatusEnum.isEnable(l.getStatus()) ? "Ativo" : "Inativo",
                        l.getPerfis().stream().map(UsuarioGestaoVOs.Perfil::getNome).collect(Collectors.joining(", ")),
                        "ad".equals(l.getOrigem()) ? "AD" : "Local", Boolean.TRUE.equals(l.getMfa()) ? "Sim" : "Não",
                        l.getUltimoAcesso() == null ? null : DATA_HORA.format(l.getUltimoAcesso()), l.getUltimoIp()));
            }
            if (p.getList().isEmpty() || linhas.size() >= p.getTotal()) {
                break;
            }
        }
        auditoriaService.registrar("relatorio.exportado", adminId, null, Map.of("relatorio", "usuarios", "linhas", linhas.size()));
        return List.of(new Planilha.Aba("Usuários", COLUNAS, linhas));
    }

    private static final Map<String, String> TITULOS = Map.of("usuario", "usuario", "nome", "nome", "email", "email",
            "celular", "celular", "departamento", "departamento", "situacao", "situacao", "perfis", "perfis");

    private record Mudar(AdminUserDO usuario, String nome, String email, String celular, Long deptId, Integer status,
                         Set<Long> perfis) {
    }

    private record Plano(List<Mudar> mudar, ImportacaoVOs.Previa previa) {
    }

    public ImportacaoVOs.Previa analisar(InputStream arquivo) {
        return planejar(arquivo).previa();
    }

    /**
     * Aplica a planilha. Só altera quem já existe (conta nova entra pela sincronização do AD ou pelo botão Novo: um
     * login criado aqui impediria o AD de vincular a conta). Ausente do arquivo só é desativado se for marcado.
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportacaoVOs.Resultado importar(InputStream arquivo, Collection<Long> desativarIds, Long adminId, String adminLogin) {
        Plano p = planejar(arquivo);
        for (Mudar m : p.mudar()) {
            AdminUserDO u = m.usuario();
            AdminUserDO upd = AdminUserDO.builder().id(u.getId()).nickname(m.nome()).email(m.email()).mobile(m.celular())
                    .deptId(m.deptId()).build();
            adminUserMapper.updateById(upd);
            if (m.status() != null && !m.status().equals(u.getStatus())) {
                adminUserService.updateUserStatus(u.getId(), m.status());
            }
            if (m.perfis() != null) {
                permissionService.assignUserRole(u.getId(), m.perfis());
            }
        }
        Set<Long> desativar = desativarIds == null ? Set.of() : p.previa().getAusentes().stream().map(ImportacaoVOs.Item::getId)
                .filter(desativarIds::contains).filter(id -> !id.equals(adminId)).collect(Collectors.toSet());
        desativar.forEach(id -> adminUserService.updateUserStatus(id, CommonStatusEnum.DISABLE.getStatus()));
        ImportacaoVOs.Resultado r = new ImportacaoVOs.Resultado();
        r.setAtualizados(p.previa().getAlterados().size());
        r.setRemovidos(desativar.size());
        r.setSemMudanca(p.previa().getSemMudanca());
        r.setRecusados(p.previa().getRecusados());
        auditoriaService.registrar("identidade.importar_usuarios", adminId, adminLogin, Map.of("atualizados", r.getAtualizados(),
                "desativados", r.getRemovidos(), "recusados", r.getRecusados()));
        return r;
    }

    private Plano planejar(InputStream arquivo) {
        List<Planilha.Linha> linhas;
        try {
            linhas = Planilha.ler(arquivo, TITULOS, "usuario", MAX_LINHAS + 1);
        } catch (IOException e) {
            throw exception(USUARIOS_INVALIDO, "envie um arquivo .xlsx com a coluna Usuário (o mesmo do Exportar)");
        }
        if (linhas.isEmpty() || linhas.size() > MAX_LINHAS) {
            throw exception(USUARIOS_INVALIDO, linhas.isEmpty() ? "o arquivo não tem nenhuma linha preenchida" : "o limite é de " + MAX_LINHAS + " linhas");
        }
        List<AdminUserDO> todos = adminUserMapper.selectList();   // no escopo de quem importa
        Map<String, AdminUserDO> porLogin = todos.stream().collect(Collectors.toMap(u -> chave(u.getUsername()), u -> u, (a, b) -> a));
        Map<Long, String> deptNomes = new HashMap<>();
        Map<String, List<Long>> deptPorNome = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM system_dept WHERE deleted = 0", (RowCallbackHandler) rs -> {
            deptNomes.put(rs.getLong(1), rs.getString(2));
            deptPorNome.computeIfAbsent(chave(rs.getString(2)), k -> new ArrayList<>()).add(rs.getLong(1));
        });
        Map<Long, String> perfilNomes = new HashMap<>();
        Map<String, Long> perfilPorNome = new HashMap<>();
        jdbcTemplate.query("SELECT id, name, code FROM system_role WHERE deleted = 0", (RowCallbackHandler) rs -> {
            perfilNomes.put(rs.getLong(1), rs.getString(2));
            perfilPorNome.putIfAbsent(chave(rs.getString(2)), rs.getLong(1));
            perfilPorNome.putIfAbsent(chave(rs.getString(3)), rs.getLong(1));
        });
        List<UsuarioGestaoVOs.Linha> enriquecidos = linhas(todos);
        Map<Long, UsuarioGestaoVOs.Linha> info = enriquecidos.stream().collect(Collectors.toMap(UsuarioGestaoVOs.Linha::getId, l -> l));
        boolean espelhaOus = adConfiguracaoService.atual().isEspelharOus();

        Plano plano = new Plano(new ArrayList<>(), new ImportacaoVOs.Previa());
        Set<Long> presentes = new HashSet<>();
        for (Planilha.Linha l : linhas) {
            try {
                Map<String, String> c = l.campos();
                String login = vazio(c.get("usuario"));
                if (login == null) {
                    throw new IllegalArgumentException("informe o usuário");
                }
                AdminUserDO u = porLogin.get(chave(login));
                if (u == null) {
                    throw new IllegalArgumentException("o usuário \"" + login + "\" não existe: contas novas entram pela sincronização do AD ou pelo botão Novo");
                }
                if (!presentes.add(u.getId())) {
                    throw new IllegalArgumentException("o usuário \"" + login + "\" aparece mais de uma vez no arquivo");
                }
                UsuarioGestaoVOs.Linha atual = info.get(u.getId());
                boolean deAd = "ad".equals(atual.getOrigem());
                List<String> ignorados = new ArrayList<>();
                List<ImportacaoVOs.Mudanca> mud = new ArrayList<>();

                String nome = u.getNickname(), email = u.getEmail(), celular = u.getMobile();
                if (c.containsKey("nome") && vazio(c.get("nome")) != null && !Objects.equals(vazio(c.get("nome")), u.getNickname())) {
                    if (deAd) {
                        ignorados.add("nome");
                    } else {
                        nome = limitar(vazio(c.get("nome")), 30);
                    }
                }
                if (c.containsKey("email") && !Objects.equals(vazio(c.get("email")), vazio(u.getEmail()))) {
                    if (deAd) {
                        ignorados.add("e-mail");
                    } else {
                        email = Objects.toString(vazio(c.get("email")), "");
                        if (!email.isEmpty() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                            throw new IllegalArgumentException("e-mail inválido \"" + email + "\"");
                        }
                    }
                }
                if (c.containsKey("celular") && !Objects.equals(vazio(c.get("celular")), vazio(u.getMobile()))) {
                    if (deAd) {
                        ignorados.add("celular");
                    } else {
                        celular = Objects.toString(vazio(c.get("celular")), "");
                    }
                }
                Long dept = u.getDeptId();
                String deptTexto = vazio(c.get("departamento"));
                if (deptTexto != null && !Objects.equals(chave(deptTexto), chave(deptNomes.get(u.getDeptId())))) {
                    List<Long> achados = deptPorNome.getOrDefault(chave(deptTexto), List.of());
                    if (achados.size() != 1) {
                        throw new IllegalArgumentException(achados.isEmpty() ? "o departamento \"" + deptTexto + "\" não existe"
                                : "há mais de um departamento chamado \"" + deptTexto + "\"");
                    }
                    if (deAd && espelhaOus) {
                        ignorados.add("departamento");
                    } else {
                        dept = achados.get(0);
                    }
                }
                Integer status = u.getStatus();
                String sit = vazio(c.get("situacao"));
                if (sit != null) {
                    String s = Planilha.titulo(sit);
                    Integer lido = s.startsWith("ativ") ? CommonStatusEnum.ENABLE.getStatus()
                            : s.startsWith("inativ") || s.startsWith("desativ") ? CommonStatusEnum.DISABLE.getStatus() : null;
                    if (lido == null) {
                        throw new IllegalArgumentException("situação \"" + sit + "\": use Ativo ou Inativo");
                    }
                    if (!lido.equals(u.getStatus())) {
                        if (deAd) {
                            ignorados.add("situação");
                        } else {
                            status = lido;
                        }
                    }
                }
                Set<Long> perfis = null;
                String perfisTexto = vazio(c.get("perfis"));
                if (perfisTexto != null) {
                    Set<Long> lidos = new LinkedHashSet<>();
                    for (String nomePerfil : perfisTexto.split("[,;]")) {
                        if (nomePerfil.isBlank()) {
                            continue;
                        }
                        Long rid = perfilPorNome.get(chave(nomePerfil));
                        if (rid == null) {
                            throw new IllegalArgumentException("o perfil \"" + nomePerfil.trim() + "\" não existe");
                        }
                        lidos.add(rid);
                    }
                    Set<Long> atuais = atual.getPerfis().stream().map(UsuarioGestaoVOs.Perfil::getId).collect(Collectors.toSet());
                    if (!lidos.equals(atuais)) {
                        perfis = lidos;
                    }
                }

                comparar(mud, "Nome", u.getNickname(), nome);
                comparar(mud, "E-mail", u.getEmail(), email);
                comparar(mud, "Celular", u.getMobile(), celular);
                comparar(mud, "Departamento", deptNomes.get(u.getDeptId()), deptNomes.get(dept));
                comparar(mud, "Situação", situacao(u.getStatus()), situacao(status));
                if (perfis != null) {
                    comparar(mud, "Perfis", atual.getPerfis().stream().map(UsuarioGestaoVOs.Perfil::getNome).sorted().collect(Collectors.joining(", ")),
                            perfis.stream().map(perfilNomes::get).sorted().collect(Collectors.joining(", ")));
                }
                String aviso = ignorados.isEmpty() ? null
                        : "Conta do AD: " + String.join(", ", ignorados) + " não muda aqui (a sincronização sobrescreveria). Mude no AD.";
                if (mud.isEmpty()) {
                    plano.previa().setSemMudanca(plano.previa().getSemMudanca() + 1);
                    if (aviso != null && plano.previa().getErros().size() < 100) {
                        plano.previa().getErros().add("Linha " + l.numero() + " (" + login + "): " + aviso);
                    }
                } else {
                    ImportacaoVOs.Item it = new ImportacaoVOs.Item();
                    it.setId(u.getId());
                    it.setNome(u.getUsername() + " · " + u.getNickname());
                    it.setMudancas(mud);
                    it.setAviso(aviso);
                    plano.previa().getAlterados().add(it);
                    plano.mudar().add(new Mudar(u, nome, email, celular, dept, status, perfis));
                }
            } catch (IllegalArgumentException | ServiceException e) {
                // a linha recusada ainda marca o usuário como presente: um erro de digitação não vira desativação
                String login = vazio(l.campos().get("usuario"));
                AdminUserDO u = login == null ? null : porLogin.get(chave(login));
                if (u != null) {
                    presentes.add(u.getId());
                }
                plano.previa().setRecusados(plano.previa().getRecusados() + 1);
                if (plano.previa().getErros().size() < 100) {
                    plano.previa().getErros().add("Linha " + l.numero() + ": " + e.getMessage());
                }
            }
        }
        for (UsuarioGestaoVOs.Linha x : enriquecidos) {
            // ausente: só os ativos e locais podem ser desativados (no AD, quem manda é o AD)
            if (!presentes.contains(x.getId()) && CommonStatusEnum.isEnable(x.getStatus()) && "local".equals(x.getOrigem())) {
                ImportacaoVOs.Item it = new ImportacaoVOs.Item();
                it.setId(x.getId());
                it.setNome(x.getUsername() + " · " + x.getNickname());
                it.setAviso("Fica inativo (não é excluído) e as sessões abertas acabam.");
                plano.previa().getAusentes().add(it);
            }
        }
        return plano;
    }

    private static String situacao(Integer s) {
        return s == null ? null : CommonStatusEnum.isEnable(s) ? "Ativo" : "Inativo";
    }

    private static void comparar(List<ImportacaoVOs.Mudanca> l, String campo, String de, String para) {
        String a = vazio(de), b = vazio(para);
        if (!Objects.equals(a, b)) {
            l.add(new ImportacaoVOs.Mudanca(campo, a, b));
        }
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String limitar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String chave(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static LocalDateTime data(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

}
