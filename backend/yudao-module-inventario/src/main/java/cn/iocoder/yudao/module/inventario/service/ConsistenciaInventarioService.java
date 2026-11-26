package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dominio.Faixas;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Inconsistências dos ativos do inventário e a correção, pelas regras exatas (sem palpite):
 * <ul>
 *   <li><b>unidade</b>: a escolhida à mão; senão a da faixa (Unidades e endereços / Inventário → Rede) do primeiro IP
 *   da máquina que cai numa faixa; senão nenhuma;</li>
 *   <li><b>usuário</b>: a pessoa do portal cujo login (AD ou local) é o que o agente informou; sem achar, nenhuma.</li>
 * </ul>
 * Lê pelo mapper do ativo, então o escopo de unidade de quem pergunta vale como na tela.
 */
@Service
public class ConsistenciaInventarioService {

    public static final String UNIDADE = "unidade";
    public static final String USUARIO = "usuario";
    private static final int LISTA_MAXIMA = 60;

    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private UnidadeRedeService unidadeRedeService;
    @Resource
    private UsuarioDoLogin usuarioDoLogin;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AuditoriaService auditoriaService;

    @Data
    public static class Item {
        private Long id;
        private String nome;
        /** unidade | usuario */
        private String tipo;
        private String atual;
        private String correto;
        private String motivo;
        private String ips;
        private String login;
    }

    @Data
    public static class Resultado {
        private int ativos;
        private int unidadeErrada;
        /** Dos que ficariam sem unidade: nenhum IP numa faixa */
        private int semFaixa;
        private int usuarioErrado;
        private List<Item> itens = new ArrayList<>();
        /** Redes sem faixa com máquinas: cadastrar a faixa resolve a unidade delas */
        private List<Map<String, Object>> redesSemFaixa = new ArrayList<>();
    }

    private record Correcao(AtivoDO a, Item item, Long dept, Long usuario) {
    }

    public Resultado verificar() {
        Resultado r = new Resultado();
        List<Correcao> l = calcular(r);
        l.stream().limit(LISTA_MAXIMA).forEach(c -> r.getItens().add(c.item()));
        return r;
    }

    /**
     * Corrige o que {@link #verificar()} aponta.
     *
     * @param tipos unidade e/ou usuario; vazio = os dois
     * @return quantos ativos mudaram, por tipo
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> corrigir(Collection<String> tipos, Long userId) {
        Set<String> t = tipos == null || tipos.isEmpty() ? Set.of(UNIDADE, USUARIO) : new HashSet<>(tipos);
        Map<Long, String> nomes = unidadeRedeService.nomesDept();
        int unidades = 0, usuarios = 0;
        for (Correcao c : calcular(new Resultado())) {
            if (UNIDADE.equals(c.item().getTipo()) && t.contains(UNIDADE)) {
                unidadeRedeService.mudarUnidade(c.a().getId(), c.a().getDeptId(), c.dept(), nomes, userId);
                unidades++;
            } else if (USUARIO.equals(c.item().getTipo()) && t.contains(USUARIO)) {
                jdbcTemplate.update("UPDATE cmdb.ativo SET usuario_id = ?, update_time = now() WHERE id = ?", c.usuario(), c.a().getId());
                jdbcTemplate.update("""
                        INSERT INTO cmdb.historico (id, ativo_id, quando, tipo, campo, antes, depois, user_id)
                        VALUES (nextval('cmdb.historico_seq'), ?, now(), 'manual', 'usuario_id', ?, ?, ?)
                        """, c.a().getId(), c.item().getAtual(), c.item().getCorreto(), userId);
                usuarios++;
            }
        }
        if (unidades + usuarios > 0) {
            auditoriaService.registrar("inventario.inconsistencias_corrigidas", userId, null, Map.of("unidade", unidades, "usuario", usuarios));
        }
        return Map.of(UNIDADE, unidades, USUARIO, usuarios);
    }

    private List<Correcao> calcular(Resultado r) {
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>()
                .ne(AtivoDO::getSituacao, "baixado").isNull(AtivoDO::getMescladoEm).orderByAsc(AtivoDO::getNome));
        r.setAtivos(ativos.size());
        Map<Long, String> depts = unidadeRedeService.nomesDept();
        Map<String, Optional<Long>> porLogin = new HashMap<>();
        Set<Long> pessoas = new HashSet<>();
        List<Correcao> out = new ArrayList<>();
        Map<String, Long> redes = new TreeMap<>();
        List<Faixas.Faixa> faixas = unidadeRedeService.faixas();
        for (AtivoDO a : ativos) {
            // unidade
            if (!Boolean.TRUE.equals(a.getDeptManual())) {
                Optional<Faixas.Faixa> f = Faixas.doIps(a.getIp(), a.getIps(), faixas);
                Long certo = f.map(Faixas.Faixa::deptId).orElse(null);
                if (f.isEmpty()) {
                    r.setSemFaixa(r.getSemFaixa() + 1);
                    Faixas.lista(a.getIp(), a.getIps()).stream().filter(Faixas::util).findFirst()
                            .map(Faixas::rede24).ifPresent(rede -> redes.merge(rede, 1L, Long::sum));
                }
                if (!Objects.equals(certo, a.getDeptId())) {
                    r.setUnidadeErrada(r.getUnidadeErrada() + 1);
                    Item i = item(a, UNIDADE);
                    i.setAtual(a.getDeptId() == null ? null : depts.getOrDefault(a.getDeptId(), "#" + a.getDeptId()));
                    i.setCorreto(certo == null ? null : depts.getOrDefault(certo, "#" + certo));
                    i.setMotivo(f.map(x -> "IP na faixa " + x.cidr()).orElse(Faixas.lista(a.getIp(), a.getIps()).isEmpty()
                            ? "a máquina não informou IP" : "nenhum IP da máquina cai numa faixa cadastrada"));
                    out.add(new Correcao(a, i, certo, null));
                }
            }
            // usuário (só o que veio do agente; ativo cadastrado à mão tem o usuário escolhido na tela)
            if (a.getUsuarioLogin() != null && !"manual".equals(a.getOrigem())) {
                Long certo = porLogin.computeIfAbsent(a.getUsuarioLogin().toLowerCase(Locale.ROOT),
                        k -> Optional.ofNullable(usuarioDoLogin.usuario(a.getUsuarioLogin()))).orElse(null);
                if (!Objects.equals(certo, a.getUsuarioId())) {
                    r.setUsuarioErrado(r.getUsuarioErrado() + 1);
                    Item i = item(a, USUARIO);
                    i.setAtual(a.getUsuarioId() == null ? null : "#" + a.getUsuarioId());
                    i.setCorreto(certo == null ? null : "#" + certo);
                    i.setMotivo(certo == null ? "o login " + a.getUsuarioLogin() + " não é de ninguém no portal nem no AD"
                            : a.getUsuarioId() == null ? "o login " + a.getUsuarioLogin() + " é de uma pessoa do portal, mas não estava ligado"
                            : "o login " + a.getUsuarioLogin() + " é de outra pessoa");
                    Optional.ofNullable(a.getUsuarioId()).ifPresent(pessoas::add);
                    Optional.ofNullable(certo).ifPresent(pessoas::add);
                    out.add(new Correcao(a, i, null, certo));
                }
            }
        }
        // nomes das pessoas no lugar do #id
        if (!pessoas.isEmpty()) {
            Map<String, String> nomes = new HashMap<>();
            adminUserMapper.selectByIds(pessoas).forEach(u -> nomes.put("#" + u.getId(), nomePessoa(u)));
            out.stream().filter(c -> USUARIO.equals(c.item().getTipo())).forEach(c -> {
                c.item().setAtual(nomes.getOrDefault(c.item().getAtual(), c.item().getAtual()));
                c.item().setCorreto(nomes.getOrDefault(c.item().getCorreto(), c.item().getCorreto()));
            });
        }
        redes.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(20)
                .forEach(e -> r.getRedesSemFaixa().add(Map.of("rede", e.getKey(), "maquinas", e.getValue())));
        return out;
    }

    private static Item item(AtivoDO a, String tipo) {
        Item i = new Item();
        i.setId(a.getId());
        i.setNome(a.getNome());
        i.setTipo(tipo);
        i.setIps(Faixas.lista(a.getIp(), a.getIps()).stream().reduce((x, y) -> x + ", " + y).orElse(null));
        i.setLogin(a.getUsuarioLogin());
        return i;
    }

    private static String nomePessoa(AdminUserDO u) {
        return u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname() + " (" + u.getUsername() + ")";
    }

}
