package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.ad.AdUsuario;
import cn.iocoder.yudao.module.identidade.config.AdProperties;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Cria ou atualiza o usuário do portal a partir da conta do AD: dados, departamento (espelho das OUs),
 * situação e perfis mapeados por grupo.
 */
@Slf4j
@Service
public class AdProvisionamentoService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Resource
    private AdProperties props;
    @Resource
    private VinculoAdMapper vinculoAdMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private DeptService deptService;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private PermissionService permissionService;
    @Resource
    private PasswordEncoder passwordEncoder;

    /**
     * @return o usuário do portal, ou vazio se a conta colide com uma conta local de mesmo login sem vínculo
     *         (nunca "adotamos" uma conta local: alguém poderia criar no AD um login igual ao do administrador)
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Optional<AdminUserDO> provisionar(AdUsuario conta, LocalDateTime agora) {
        VinculoAdDO vinculo = vinculoAdMapper.selectByGuid(conta.getGuid());
        AdminUserDO usuario = vinculo == null ? null : adminUserMapper.selectById(vinculo.getUserId());

        AdminUserDO mesmoLogin = adminUserMapper.selectByUsername(conta.getLogin());
        if (mesmoLogin != null && (usuario == null || !mesmoLogin.getId().equals(usuario.getId()))) {
            log.warn("[provisionar][login {} do AD já existe como conta local (id {}); ignorado]",
                    conta.getLogin(), mesmoLogin.getId());
            return Optional.empty();
        }

        Long deptId = resolverDepartamento(conta.getCaminhoOus());
        Integer status = conta.isAtivo() ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus();
        if (usuario == null) {
            usuario = AdminUserDO.builder()
                    .username(conta.getLogin())
                    // senha local aleatória e descartada: quem tem vínculo só entra pelo AD
                    .password(passwordEncoder.encode(senhaDescartavel()))
                    .build();
        }
        usuario.setUsername(conta.getLogin());
        usuario.setNickname(limitar(Optional.ofNullable(conta.getNome()).orElse(conta.getLogin()), 100));
        usuario.setEmail(limitar(Optional.ofNullable(conta.getEmail()).orElse(""), 128));
        usuario.setMobile(limitar(Optional.ofNullable(conta.getTelefone()).orElse(""), 32));
        usuario.setRemark(limitar(conta.getCargo(), 500));
        usuario.setDeptId(deptId);
        usuario.setStatus(status);
        if (usuario.getId() == null) {
            adminUserMapper.insert(usuario);
        } else {
            adminUserMapper.updateById(usuario);
        }

        sincronizarPerfis(usuario.getId(), conta.getGrupos());

        if (vinculo == null) {
            vinculo = VinculoAdDO.builder().userId(usuario.getId()).guid(conta.getGuid()).build();
        }
        vinculo.setLogin(conta.getLogin());
        vinculo.setDn(conta.getDn());
        vinculo.setGestorDn(conta.getGestorDn());
        vinculo.setAtivoNoAd(conta.isAtivo());
        vinculo.setUltimaSincronizacao(agora);
        if (vinculo.getId() == null) {
            vinculoAdMapper.insert(vinculo);
        } else {
            vinculoAdMapper.updateById(vinculo);
        }
        return Optional.of(usuario);
    }

    /** Desativa no portal a conta que sumiu do AD (excluída ou fora do filtro). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void desativarAusente(VinculoAdDO vinculo) {
        AdminUserDO u = adminUserMapper.selectById(vinculo.getUserId());
        if (u != null && CommonStatusEnum.isEnable(u.getStatus())) {
            u.setStatus(CommonStatusEnum.DISABLE.getStatus());
            adminUserMapper.updateById(u);
            log.info("[desativarAusente][{} não está mais no AD; desativado no portal]", vinculo.getLogin());
        }
        vinculo.setAtivoNoAd(false);
        vinculoAdMapper.updateById(vinculo);
    }

    /** Cria (se faltar) a cadeia de departamentos que espelha as OUs, abaixo do departamento raiz. */
    Long resolverDepartamento(List<String> caminho) {
        Long pai = props.getDeptRaizId();
        for (String nome : caminho) {
            String nomeDept = limitar(nome, 30);
            DeptDO existente = deptMapper.selectByParentIdAndName(pai, nomeDept);
            if (existente != null) {
                pai = existente.getId();
                continue;
            }
            DeptSaveReqVO novo = new DeptSaveReqVO();
            novo.setName(nomeDept);
            novo.setParentId(pai);
            novo.setSort(0);
            novo.setStatus(CommonStatusEnum.ENABLE.getStatus());
            pai = deptService.createDept(novo);
        }
        return pai;
    }

    /**
     * Perfis mapeados em coti.ad.grupos-perfis são controlados pelo AD (entra e sai conforme o grupo);
     * os demais perfis do usuário, dados à mão no portal, ficam como estão.
     */
    void sincronizarPerfis(Long userId, Set<String> gruposDn) {
        if (props.getGruposPerfis().isEmpty()) {
            return;
        }
        Set<Long> gerenciados = new HashSet<>();
        Set<Long> devidos = new HashSet<>();
        for (Map.Entry<String, String> e : props.getGruposPerfis().entrySet()) {
            RoleDO perfil = roleMapper.selectByCode(e.getValue());
            if (perfil == null) {
                log.warn("[sincronizarPerfis][perfil {} do mapa coti.ad.grupos-perfis não existe]", e.getValue());
                continue;
            }
            gerenciados.add(perfil.getId());
            if (pertenceAoGrupo(gruposDn, e.getKey())) {
                devidos.add(perfil.getId());
            }
        }
        Set<Long> atuais = permissionService.getUserRoleIdListByUserId(userId);
        Set<Long> novos = new HashSet<>(atuais);
        novos.removeAll(gerenciados);
        novos.addAll(devidos);
        if (!novos.equals(atuais)) {
            permissionService.assignUserRole(userId, novos);
        }
    }

    /** A chave do mapa pode ser o DN completo do grupo ou só o CN. */
    static boolean pertenceAoGrupo(Set<String> gruposDn, String chave) {
        for (String dn : gruposDn) {
            if (dn.equalsIgnoreCase(chave)) {
                return true;
            }
            String cn = dn.split(",", 2)[0];
            if (cn.regionMatches(true, 0, "CN=", 0, 3) && cn.substring(3).equalsIgnoreCase(chave)) {
                return true;
            }
        }
        return false;
    }

    private static String senhaDescartavel() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static String limitar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
