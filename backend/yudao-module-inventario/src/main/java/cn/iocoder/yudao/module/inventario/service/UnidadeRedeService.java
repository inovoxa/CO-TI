package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.UnidadeRedeVOs;
import cn.iocoder.yudao.module.inventario.dominio.Faixas;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.FAIXA_INVALIDA;

/**
 * Unidade pela faixa de IP. Ordem de prioridade da unidade de um ativo: escolhida à mão; faixa do IP (a mais
 * específica); unidade da pessoa logada. As faixas ficam em memória (recarregadas a cada mudança e a cada minuto),
 * porque são consultadas em todo inventário recebido.
 */
@Service
@Slf4j
public class UnidadeRedeService {

    private static final long RECARGA_MS = 60_000;
    private static final int MAX_LINHAS = 5000;

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private AuditoriaService auditoriaService;

    private volatile List<Faixas.Faixa> cache;
    private volatile long carregadoEm;

    // ======================================================================================= consulta

    /** Unidade da faixa do primeiro IP da máquina que cai numa (o principal, depois os outros), ou null. */
    public Long unidadeDosIps(String ip, String ips) {
        return Faixas.doIps(ip, ips, faixas()).map(Faixas.Faixa::deptId).orElse(null);
    }

    /** Unidade da faixa onde o IP cai, ou null. */
    public Long unidadeDoIp(String ip) {
        return Faixas.doIp(ip, faixas()).map(Faixas.Faixa::deptId).orElse(null);
    }

    List<Faixas.Faixa> faixas() {
        List<Faixas.Faixa> c = cache;
        if (c == null || System.currentTimeMillis() - carregadoEm > RECARGA_MS) {
            List<Faixas.Faixa> l = new ArrayList<>();
            try {
                jdbcTemplate.query("SELECT id, cidr, dept_id FROM cmdb.unidade_rede", (RowCallbackHandler) rs -> {
                    try {
                        l.add(Faixas.Faixa.de(rs.getLong("id"), rs.getString("cidr"), rs.getLong("dept_id")));
                    } catch (IllegalArgumentException e) {
                        log.warn("[inventario] faixa ignorada: {}", e.getMessage());
                    }
                });
            } catch (RuntimeException e) {
                log.warn("[inventario] não foi possível ler as faixas de IP: {}", e.getMessage());
                return c == null ? List.of() : c;
            }
            cache = c = List.copyOf(l);
            carregadoEm = System.currentTimeMillis();
        }
        return c;
    }

    private void esquecer() {
        cache = null;
    }

    public List<UnidadeRedeVOs.Faixa> listar() {
        List<UnidadeRedeVOs.Faixa> l = jdbcTemplate.query("""
                SELECT f.id, f.cidr, f.dept_id, d.name AS dept_nome, f.observacao FROM cmdb.unidade_rede f
                LEFT JOIN system_dept d ON d.id = f.dept_id ORDER BY f.cidr
                """, (rs, i) -> {
            UnidadeRedeVOs.Faixa f = new UnidadeRedeVOs.Faixa();
            f.setId(rs.getLong("id"));
            f.setCidr(rs.getString("cidr"));
            f.setDeptId(rs.getLong("dept_id"));
            f.setDeptNome(rs.getString("dept_nome"));
            f.setObservacao(rs.getString("observacao"));
            f.setMaquinas(0L);
            return f;
        });
        // quantos ativos caem em cada faixa (a mais específica de cada IP)
        Map<Long, Long> porFaixa = new HashMap<>();
        List<Faixas.Faixa> faixas = l.stream().map(f -> Faixas.Faixa.de(f.getId(), f.getCidr(), f.getDeptId())).toList();
        for (String ip : jdbcTemplate.queryForList("SELECT ip FROM cmdb.ativo WHERE deleted = 0 AND situacao <> 'baixado' AND ip IS NOT NULL",
                String.class)) {
            Faixas.doIp(ip, faixas).ifPresent(f -> porFaixa.merge(f.id(), 1L, Long::sum));
        }
        l.forEach(f -> f.setMaquinas(porFaixa.getOrDefault(f.getId(), 0L)));
        return l;
    }

    /** Redes /24 sem faixa, com pelo menos 2 máquinas, em que 60% ou mais delas já estão numa mesma unidade. */
    public List<UnidadeRedeVOs.Sugestao> sugestoes() {
        List<Faixas.Faixa> faixas = faixas();
        Map<String, Map<Long, Long>> porRede = new TreeMap<>();
        Map<String, Long> totais = new HashMap<>();
        jdbcTemplate.query("SELECT ip, dept_id FROM cmdb.ativo WHERE deleted = 0 AND situacao <> 'baixado' AND ip IS NOT NULL",
                (RowCallbackHandler) rs -> {
                    String ip = rs.getString("ip");
                    String rede = Faixas.rede24(ip);
                    if (rede == null || Faixas.doIp(ip, faixas).isPresent()) {
                        return;
                    }
                    totais.merge(rede, 1L, Long::sum);
                    long d = rs.getLong("dept_id");
                    if (!rs.wasNull()) {
                        porRede.computeIfAbsent(rede, k -> new HashMap<>()).merge(d, 1L, Long::sum);
                    }
                });
        Map<Long, String> nomes = nomesDept();
        List<UnidadeRedeVOs.Sugestao> l = new ArrayList<>();
        porRede.forEach((rede, depts) -> {
            long total = totais.get(rede);
            Map.Entry<Long, Long> maior = Collections.max(depts.entrySet(), Map.Entry.comparingByValue());
            if (total >= 2 && maior.getValue() * 10 >= total * 6 && nomes.containsKey(maior.getKey())) {
                UnidadeRedeVOs.Sugestao s = new UnidadeRedeVOs.Sugestao();
                s.setCidr(rede);
                s.setDeptId(maior.getKey());
                s.setDeptNome(nomes.get(maior.getKey()));
                s.setMaquinas(total);
                s.setNaUnidade(maior.getValue());
                l.add(s);
            }
        });
        return l;
    }

    // ======================================================================================= cadastro

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(UnidadeRedeVOs.SalvarReq req, Long userId) {
        String cidr = Faixas.normalizar(req.getCidr());
        if (cidr == null) {
            throw exception(FAIXA_INVALIDA, "use o formato 172.19.21.0/24 (prefixo de 8 a 32)");
        }
        if (!nomesDept().containsKey(req.getDeptId())) {
            throw exception(FAIXA_INVALIDA, "a unidade escolhida não existe");
        }
        String obs = req.getObservacao() == null || req.getObservacao().isBlank() ? null : req.getObservacao().trim();
        Long id = req.getId();
        try {
            if (id == null) {
                id = jdbcTemplate.queryForObject("INSERT INTO cmdb.unidade_rede (cidr, dept_id, observacao) VALUES (?, ?, ?) RETURNING id",
                        Long.class, cidr, req.getDeptId(), obs);
            } else if (jdbcTemplate.update("UPDATE cmdb.unidade_rede SET cidr = ?, dept_id = ?, observacao = ?, atualizado_em = now() WHERE id = ?",
                    cidr, req.getDeptId(), obs, id) == 0) {
                throw exception(FAIXA_INVALIDA, "faixa não encontrada");
            }
        } catch (DuplicateKeyException e) {
            throw exception(FAIXA_INVALIDA, "a faixa " + cidr + " já está cadastrada");
        }
        esquecer();
        auditoriaService.registrar("inventario.faixa_salva", userId, null, Map.of("faixa", cidr, "unidade", req.getDeptId()));
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        List<String> l = jdbcTemplate.queryForList("DELETE FROM cmdb.unidade_rede WHERE id = ? RETURNING cidr", String.class, id);
        esquecer();
        if (!l.isEmpty()) {
            auditoriaService.registrar("inventario.faixa_excluida", userId, null, Map.of("faixa", l.get(0)));
        }
    }

    /**
     * Recalcula a unidade dos ativos (fora os de unidade escolhida à mão e os baixados) pela regra das faixas: a do
     * primeiro IP da máquina que cai numa faixa; quem não cai em nenhuma fica sem unidade (sem palpite).
     */
    @Transactional(rollbackFor = Exception.class)
    public int aplicar(Long userId) {
        esquecer();
        List<Faixas.Faixa> faixas = faixas();
        Map<Long, String> nomes = nomesDept();
        record Ativo(long id, String ip, String ips, Long dept) {
        }
        List<Ativo> ativos = jdbcTemplate.query("""
                SELECT id, ip, ips, dept_id FROM cmdb.ativo WHERE deleted = 0 AND situacao <> 'baixado' AND dept_manual = false
                """, (rs, i) -> {
            long d = rs.getLong("dept_id");
            return new Ativo(rs.getLong("id"), rs.getString("ip"), rs.getString("ips"), rs.wasNull() ? null : d);
        });
        int n = 0;
        for (Ativo a : ativos) {
            Long novo = Faixas.doIps(a.ip(), a.ips(), faixas).map(Faixas.Faixa::deptId).orElse(null);
            if (!Objects.equals(novo, a.dept())) {
                mudarUnidade(a.id(), a.dept(), novo, nomes, userId);
                n++;
            }
        }
        if (n > 0) {
            auditoriaService.registrar("inventario.faixas_aplicadas", userId, null, Map.of("ativos", n));
        }
        return n;
    }

    /** Troca a unidade de um ativo e registra no histórico de onde veio. */
    void mudarUnidade(long ativoId, Long antes, Long depois, Map<Long, String> nomes, Long userId) {
        jdbcTemplate.update("UPDATE cmdb.ativo SET dept_id = ?, update_time = now() WHERE id = ?", depois, ativoId);
        jdbcTemplate.update("""
                INSERT INTO cmdb.historico (id, ativo_id, quando, tipo, campo, antes, depois, user_id)
                VALUES (nextval('cmdb.historico_seq'), ?, now(), 'manual', 'dept_id', ?, ?, ?)
                """, ativoId, antes == null ? null : nomes.get(antes),
                depois == null ? "nenhuma (nenhum IP numa faixa)" : nomes.get(depois) + " (pela faixa de IP)", userId);
    }

    // ======================================================================================= planilha

    public List<Planilha.Aba> exportar(Long userId) {
        List<List<Object>> linhas = listar().stream()
                .map(f -> Arrays.<Object>asList(f.getCidr(), f.getDeptNome(), f.getObservacao(), f.getMaquinas())).collect(Collectors.toList());
        auditoriaService.registrar("relatorio.exportado", userId, null, Map.of("relatorio", "inventario_faixas", "linhas", linhas.size()));
        return List.of(new Planilha.Aba("Faixas de IP", List.of("Faixa", "Unidade", "Observação", "Máquinas"), linhas));
    }

    private static final Map<String, String> TITULOS = Map.of("faixa", "faixa", "unidade", "unidade", "observacao", "observacao");

    private record Linha(String cidr, Long deptId, String observacao) {
    }

    private record Plano(List<Linha> gravar, Map<String, Long> ids, ImportacaoVOs.Previa previa) {
    }

    public ImportacaoVOs.Previa analisar(InputStream arquivo) {
        return planejar(arquivo).previa();
    }

    /** Refaz a análise e aplica; só sai quem está em {@code removerIds} e ausente do arquivo. Depois, aplica nos ativos. */
    @Transactional(rollbackFor = Exception.class)
    public ImportacaoVOs.Resultado importar(InputStream arquivo, Collection<Long> removerIds, Long userId) {
        Plano p = planejar(arquivo);
        for (Linha l : p.gravar()) {
            Long id = p.ids().get(l.cidr());
            if (id == null) {
                jdbcTemplate.update("INSERT INTO cmdb.unidade_rede (cidr, dept_id, observacao) VALUES (?, ?, ?)", l.cidr(), l.deptId(), l.observacao());
            } else {
                jdbcTemplate.update("UPDATE cmdb.unidade_rede SET dept_id = ?, observacao = ?, atualizado_em = now() WHERE id = ?",
                        l.deptId(), l.observacao(), id);
            }
        }
        Set<Long> remover = removerIds == null ? Set.of() : p.previa().getAusentes().stream().map(ImportacaoVOs.Item::getId)
                .filter(removerIds::contains).collect(Collectors.toSet());
        remover.forEach(id -> jdbcTemplate.update("DELETE FROM cmdb.unidade_rede WHERE id = ?", id));
        esquecer();
        ImportacaoVOs.Resultado r = new ImportacaoVOs.Resultado();
        r.setAdicionados(p.previa().getNovos().size());
        r.setAtualizados(p.previa().getAlterados().size());
        r.setRemovidos(remover.size());
        r.setSemMudanca(p.previa().getSemMudanca());
        r.setRecusados(p.previa().getRecusados());
        auditoriaService.registrar("inventario.importar_faixas", userId, null, Map.of("adicionados", r.getAdicionados(),
                "atualizados", r.getAtualizados(), "removidos", r.getRemovidos(), "recusados", r.getRecusados()));
        aplicar(userId);
        return r;
    }

    private Plano planejar(InputStream arquivo) {
        List<Planilha.Linha> linhas;
        try {
            linhas = Planilha.ler(arquivo, TITULOS, "faixa", MAX_LINHAS + 1);
        } catch (IOException e) {
            throw exception(FAIXA_INVALIDA, "envie um arquivo .xlsx com as colunas Faixa e Unidade");
        }
        if (linhas.isEmpty() || linhas.size() > MAX_LINHAS) {
            throw exception(FAIXA_INVALIDA, linhas.isEmpty() ? "o arquivo não tem nenhuma linha preenchida" : "o limite é de " + MAX_LINHAS + " linhas");
        }
        Map<Long, String> nomes = nomesDept();
        Map<String, List<Long>> deptPorNome = new HashMap<>();
        nomes.forEach((id, nome) -> deptPorNome.computeIfAbsent(chave(nome), k -> new ArrayList<>()).add(id));
        Map<String, Long> ids = new HashMap<>();
        Map<String, Long> deptAtual = new HashMap<>();
        Map<String, String> obsAtual = new HashMap<>();
        jdbcTemplate.query("SELECT id, cidr, dept_id, observacao FROM cmdb.unidade_rede", (RowCallbackHandler) rs -> {
            ids.put(rs.getString("cidr"), rs.getLong("id"));
            deptAtual.put(rs.getString("cidr"), rs.getLong("dept_id"));
            obsAtual.put(rs.getString("cidr"), rs.getString("observacao"));
        });
        Plano plano = new Plano(new ArrayList<>(), ids, new ImportacaoVOs.Previa());
        Set<String> vistas = new HashSet<>();
        for (Planilha.Linha l : linhas) {
            try {
                String bruto = l.campos().get("faixa");
                String cidr = Faixas.normalizar(bruto);
                if (cidr == null) {
                    throw new IllegalArgumentException("faixa inválida \"" + bruto + "\" (use 172.19.21.0/24)");
                }
                vistas.add(cidr);   // linha recusada também conta como presente: erro de digitação não vira remoção
                String unidade = l.campos().get("unidade");
                List<Long> achadas = unidade == null ? List.of() : deptPorNome.getOrDefault(chave(unidade), List.of());
                if (achadas.size() != 1) {
                    throw new IllegalArgumentException(achadas.isEmpty() ? "a unidade \"" + unidade + "\" não existe (confira o nome em Sistema → Departamentos)"
                            : "há mais de uma unidade chamada \"" + unidade + "\"");
                }
                if (plano.gravar().stream().anyMatch(x -> x.cidr().equals(cidr))) {
                    throw new IllegalArgumentException("a faixa " + cidr + " aparece mais de uma vez no arquivo");
                }
                String obs = l.campos().get("observacao") == null || l.campos().get("observacao").isBlank() ? null
                        : l.campos().get("observacao").trim();
                Long dept = achadas.get(0);
                List<ImportacaoVOs.Mudanca> mudancas = new ArrayList<>();
                boolean existe = ids.containsKey(cidr);
                comparar(mudancas, "Unidade", existe ? nomes.get(deptAtual.get(cidr)) : null, nomes.get(dept));
                comparar(mudancas, "Observação", existe ? obsAtual.get(cidr) : null, obs);
                if (!existe) {
                    plano.previa().getNovos().add(item(null, cidr, mudancas, null));
                    plano.gravar().add(new Linha(cidr, dept, obs));
                } else if (mudancas.isEmpty()) {
                    plano.previa().setSemMudanca(plano.previa().getSemMudanca() + 1);
                } else {
                    plano.previa().getAlterados().add(item(ids.get(cidr), cidr, mudancas, null));
                    plano.gravar().add(new Linha(cidr, dept, obs));
                }
            } catch (IllegalArgumentException | ServiceException e) {
                plano.previa().setRecusados(plano.previa().getRecusados() + 1);
                if (plano.previa().getErros().size() < 100) {
                    plano.previa().getErros().add("Linha " + l.numero() + ": " + e.getMessage());
                }
            }
        }
        ids.forEach((cidr, id) -> {
            if (!vistas.contains(cidr)) {
                plano.previa().getAusentes().add(item(id, cidr, List.of(), "As máquinas desta faixa voltam a seguir a unidade da pessoa logada."));
            }
        });
        return plano;
    }

    private static void comparar(List<ImportacaoVOs.Mudanca> l, String campo, String de, String para) {
        if (!Objects.equals(de, para)) {
            l.add(new ImportacaoVOs.Mudanca(campo, de, para));
        }
    }

    private static ImportacaoVOs.Item item(Long id, String nome, List<ImportacaoVOs.Mudanca> mudancas, String aviso) {
        ImportacaoVOs.Item i = new ImportacaoVOs.Item();
        i.setId(id);
        i.setNome(nome);
        i.setMudancas(mudancas);
        i.setAviso(aviso);
        return i;
    }

    private static String chave(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    Map<Long, String> nomesDept() {
        Map<Long, String> m = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM system_dept WHERE deleted = 0",
                (RowCallbackHandler) rs -> m.put(rs.getLong("id"), rs.getString("name")));
        return m;
    }

}
