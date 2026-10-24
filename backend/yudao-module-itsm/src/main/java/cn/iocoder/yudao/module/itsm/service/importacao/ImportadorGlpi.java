package cn.iocoder.yudao.module.itsm.service.importacao;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.Documento;
import cn.iocoder.yudao.module.itsm.dominio.GlpiConverte;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.dominio.Projetos;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.MudancaTipo;
import cn.iocoder.yudao.module.itsm.service.gov.LicencaService;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Importa um GLPI 10/11 para o CO-TI, lendo o banco MySQL/MariaDB dele (GLPI: as mesmas tabelas que o próprio
 * GLPI usa; ver NOTICE). Pode rodar de novo: o que já veio (itsm.importacao_mapa) é pulado.
 *
 * <p>Cada item (um chamado com tudo o que tem dentro, um artigo, um contrato...) entra na sua própria transação: um
 * registro com problema vai para o log e o resto continua. Nada dispara notificação por e-mail, porque os dados são
 * gravados direto nas tabelas, sem passar pelos serviços que publicam eventos.
 */
@Slf4j
@Component
public class ImportadorGlpi {

    static final String ORIGEM = "glpi";
    private static final int LOTE = 200;
    private static final long ARQUIVO_MAXIMO = 50L * 1024 * 1024;
    /** Contas que vêm em toda instalação do GLPI e não são pessoas */
    private static final Set<String> CONTAS_PADRAO = Set.of("glpi", "post-only", "tech", "normal", "glpi-system");

    @Resource
    private ImportacaoMapper importacaoMapper;
    @Resource
    private ImportacaoMapaMapper importacaoMapaMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private EquipeMapper equipeMapper;
    @Resource
    private EquipeMembroMapper equipeMembroMapper;
    @Resource
    private CategoriaMapper categoriaMapper;
    @Resource
    private OrigemMapper origemMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper chamadoAtorMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private HistoricoMapper historicoMapper;
    @Resource
    private AprovacaoMapper aprovacaoMapper;
    @Resource
    private SatisfacaoMapper satisfacaoMapper;
    @Resource
    private KbCategoriaMapper kbCategoriaMapper;
    @Resource
    private KbArtigoMapper kbArtigoMapper;
    @Resource
    private ProblemaMapper problemaMapper;
    @Resource
    private MudancaMapper mudancaMapper;
    @Resource
    private ItilAtorMapper itilAtorMapper;
    @Resource
    private ItilInteracaoMapper itilInteracaoMapper;
    @Resource
    private ItilHistoricoMapper itilHistoricoMapper;
    @Resource
    private ItilVinculoMapper itilVinculoMapper;
    @Resource
    private MudancaAprovacaoMapper mudancaAprovacaoMapper;
    @Resource
    private FornecedorMapper fornecedorMapper;
    @Resource
    private OrcamentoMapper orcamentoMapper;
    @Resource
    private ContratoMapper contratoMapper;
    @Resource
    private CustoMapper custoMapper;
    @Resource
    private LicencaMapper licencaMapper;
    @Resource
    private LicencaUsoMapper licencaUsoMapper;
    @Resource
    private ProjetoMapper projetoMapper;
    @Resource
    private ProjetoTarefaMapper projetoTarefaMapper;
    @Resource
    private ProjetoMembroMapper projetoMembroMapper;
    @Resource
    private ProjetoVinculoMapper projetoVinculoMapper;
    @Resource
    private FileApi fileApi;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private ItsmProperties props;
    @Resource
    private PlatformTransactionManager transactionManager;

    /** Interrompe a execução quando alguém pede para cancelar. */
    static class CanceladaException extends RuntimeException {
    }

    public void executar(ImportacaoDO imp, ImportacaoVOs.Opcoes op, GlpiFonte f, Long userId) {
        new Execucao(imp, op, f, userId).rodar();
    }

    // =====================================================================================================

    /** Estado de uma execução (mapas de ids, progresso, log). */
    private class Execucao {

        private final ImportacaoDO imp;
        private final ImportacaoVOs.Opcoes op;
        private final GlpiFonte f;
        private final Long userId;
        private final TransactionTemplate tx = new TransactionTemplate(transactionManager);
        private final Map<String, ImportacaoVOs.Etapa> progresso = new LinkedHashMap<>();
        private final StringBuilder logTexto = new StringBuilder();
        private final Map<String, Map<String, Long>> mapas = new HashMap<>();
        private final Map<Long, Long> deptPorEntidade = new HashMap<>();
        private final Map<Long, String> emailPorUsuario = new HashMap<>();
        private final Map<Long, String> urlDocumento = new HashMap<>();
        private final Path pasta;
        private Long deptPadrao;
        private String etapa;
        private long ultimoSalvo;

        Execucao(ImportacaoDO imp, ImportacaoVOs.Opcoes op, GlpiFonte f, Long userId) {
            this.imp = imp;
            this.op = op;
            this.f = f;
            this.userId = userId;
            String p = props.getImportacaoPastaArquivos();
            Path candidata = p == null || p.isBlank() ? null : Path.of(p).toAbsolutePath().normalize();
            this.pasta = Boolean.TRUE.equals(op.getArquivos()) && candidata != null && Files.isDirectory(candidata) ? candidata : null;
        }

        void rodar() {
            log("GLPI " + Objects.toString(f.versao(), "(versão desconhecida)") + " em " + imp.getHost() + "/" + imp.getBanco());
            if (Boolean.TRUE.equals(op.getArquivos()) && pasta == null) {
                log("Anexos não serão copiados: a pasta de arquivos do GLPI não está montada no servidor");
            }
            passo("entidades", this::entidades);
            passo("usuarios", this::usuarios);
            passo("equipes", this::equipes);
            passo("categorias", this::categorias);
            passo("origens", this::origens);
            if (Boolean.TRUE.equals(op.getChamados())) {
                passo("chamados", this::chamados);
            }
            if (Boolean.TRUE.equals(op.getBaseConhecimento())) {
                passo("base_conhecimento", this::baseConhecimento);
            }
            if (Boolean.TRUE.equals(op.getProblemasMudancas())) {
                passo("problemas", this::problemas);
                passo("mudancas", this::mudancas);
                passo("vinculos", this::vinculos);
            }
            if (Boolean.TRUE.equals(op.getGovernanca())) {
                passo("fornecedores", this::fornecedores);
                passo("orcamentos", this::orcamentos);
                passo("contratos", this::contratos);
                passo("licencas", this::licencas);
                passo("projetos", this::projetos);
            }
            etapa = null;
            salvar(true);
        }

        // ---------------------------------------------------------------- infraestrutura

        private void passo(String nome, Runnable r) {
            etapa = nome;
            progresso.putIfAbsent(nome, new ImportacaoVOs.Etapa());
            salvar(true);
            r.run();
            ImportacaoVOs.Etapa e = progresso.get(nome);
            log(String.format("%s: %d lidos, %d importados, %d já existiam, %d com erro", nome, e.getLidos(),
                    e.getImportados(), e.getExistentes(), e.getErros()));
        }

        private ImportacaoVOs.Etapa conta() {
            return progresso.get(etapa);
        }

        /** Um item na sua própria transação; erro vai para o log e a importação segue. */
        private void item(String descricao, Runnable r) {
            conta().setLidos(conta().getLidos() + 1);
            try {
                tx.executeWithoutResult(s -> r.run());
            } catch (CanceladaException e) {
                throw e;
            } catch (Exception e) {
                conta().setErros(conta().getErros() + 1);
                log("Erro em " + descricao + ": " + Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
                log.warn("[importacao {}][{}]", imp.getId(), descricao, e);
            }
            salvar(false);
        }

        private void importado() {
            conta().setImportados(conta().getImportados() + 1);
        }

        private void existente() {
            conta().setExistentes(conta().getExistentes() + 1);
        }

        private void log(String linha) {
            logTexto.append(agora()).append("  ").append(linha).append('\n');
            if (logTexto.length() > 200_000) {   // guarda o começo (contexto) e o fim (mais recente)
                logTexto.delete(2_000, logTexto.length() - 150_000).insert(2_000, "\n[...]\n");
            }
        }

        /** Grava o progresso no máximo a cada 2 s e confere se pediram para cancelar. */
        private void salvar(boolean agora) {
            long t = System.currentTimeMillis();
            if (!agora && t - ultimoSalvo < 2_000) {
                return;
            }
            ultimoSalvo = t;
            importacaoMapper.updateById(ImportacaoDO.builder().id(imp.getId()).etapa(etapa)
                    .progresso(JsonUtils.toJsonString(progresso)).log(logTexto.toString()).build());
            ImportacaoDO atual = importacaoMapper.selectById(imp.getId());
            if (atual != null && Boolean.TRUE.equals(atual.getCancelar())) {
                throw new CanceladaException();
            }
        }

        private Map<String, Long> mapa(String tipo) {
            return mapas.computeIfAbsent(tipo, t -> {
                Map<String, Long> m = new HashMap<>();
                importacaoMapaMapper.todos(ORIGEM, t).forEach(r -> m.put(String.valueOf(r.get("id_origem")),
                        ((Number) r.get("id_destino")).longValue()));
                return m;
            });
        }

        private Long destino(String tipo, Object idOrigem) {
            if (idOrigem == null) {
                return null;
            }
            long id = ((Number) idOrigem).longValue();
            return id <= 0 ? null : mapa(tipo).get(String.valueOf(id));
        }

        private void mapear(String tipo, Object idOrigem, Long idDestino) {
            String k = String.valueOf(((Number) idOrigem).longValue());
            importacaoMapaMapper.gravar(ORIGEM, tipo, k, idDestino);
            mapa(tipo).put(k, idDestino);
        }

        private Long usuario(Object glpiId) {
            return destino("usuario", glpiId);
        }

        /** Quem aprova: quem respondeu ou, ainda sem resposta, a pessoa indicada (GLPI 10.1+: itemtype_target). */
        private Long aprovador(Map<String, Object> v) {
            Long a = usuario(v.get("users_id_validate"));
            if (a == null && "User".equals(v.get("itemtype_target"))) {
                a = usuario(v.get("items_id_target"));
            }
            return a;
        }

        /** Filtros comuns de chamados, problemas e mudanças (lixeira e data de corte). */
        private String filtroItil() {
            StringBuilder w = new StringBuilder();
            if (!Boolean.TRUE.equals(op.getIncluirLixeira())) {
                w.append(" AND is_deleted = 0");
            }
            if (op.getDesde() != null) {
                // data tipada (LocalDate), não texto livre
                w.append(" AND date >= '").append(op.getDesde()).append("'");
            }
            return w.toString();
        }

        /** Linhas filhas de vários itens de uma vez: id do item → linhas. */
        private Map<Long, List<Map<String, Object>>> porItem(String sql, String chave, Collection<Long> ids) {
            if (ids.isEmpty()) {
                return Map.of();
            }
            String lista = ids.stream().map(String::valueOf).collect(Collectors.joining(","));   // números do próprio banco
            return f.lista(sql.replace("{ids}", lista)).stream()
                    .collect(Collectors.groupingBy(r -> ((Number) r.get(chave)).longValue(), LinkedHashMap::new, Collectors.toList()));
        }

        /** Percorre uma tabela grande em lotes, entregando o lote inteiro (para buscar os filhos de uma vez). */
        private void emLotes(String sql, java.util.function.Consumer<List<Map<String, Object>>> cada) {
            long ultimo = -1;
            while (true) {
                List<Map<String, Object>> lote = f.lista(sql, ultimo, LOTE);
                if (lote.isEmpty()) {
                    return;
                }
                cada.accept(lote);
                ultimo = num(lote.get(lote.size() - 1).get("id"));
                if (lote.size() < LOTE) {
                    return;
                }
            }
        }

        /** HTML do GLPI saneado, com os documentos trocados pelas cópias (ou por um aviso). */
        private String texto(Object bruto) {
            String h = GlpiConverte.html(bruto == null ? null : String.valueOf(bruto));
            return HtmlSeguro.limpar(GlpiConverte.trocarDocumentos(h, this::documento));
        }

        /** Copia um documento do GLPI para o armazenamento do CO-TI (uma vez); null se não deu. */
        private String documento(Long docId) {
            if (pasta == null || docId == null) {
                return null;
            }
            if (urlDocumento.containsKey(docId)) {
                return urlDocumento.get(docId);
            }
            String url = null;
            List<Map<String, Object>> d = f.lista("SELECT filepath, filename, mime FROM glpi_documents WHERE id = ?", docId);
            if (!d.isEmpty() && d.get(0).get("filepath") != null) {
                try {
                    Path arq = pasta.resolve(String.valueOf(d.get(0).get("filepath"))).normalize();
                    // o caminho vem do banco do GLPI: não pode sair da pasta montada
                    if (arq.startsWith(pasta) && Files.isRegularFile(arq) && Files.size(arq) <= ARQUIVO_MAXIMO) {
                        String nome = Objects.toString(d.get(0).get("filename"), "anexo-" + docId);
                        url = fileApi.createFile(Files.readAllBytes(arq), nome, "glpi", (String) d.get(0).get("mime"));
                    } else {
                        log("Documento " + docId + " não copiado (arquivo ausente, fora da pasta ou maior que 50 MB)");
                    }
                } catch (Exception e) {
                    log("Documento " + docId + " não copiado: " + e.getMessage());
                }
            }
            urlDocumento.put(docId, url);
            return url;
        }

        /** O documento já aparece (copiado) no texto? */
        private boolean citado(String html, long docId) {
            String url = documento(docId);
            return url != null && html != null && html.contains(url);
        }

        /** Lista de anexos não citados no texto, como links. */
        private String anexos(List<Map<String, Object>> docs) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> d : docs) {
                String url = documento(num(d.get("documents_id")));
                String nome = HtmlUtils.htmlEscape(Objects.toString(d.get("filename"), "anexo"));
                sb.append("<li>").append(url == null ? nome + " <em>(arquivo não copiado)</em>" : "<a href=\"" + url + "\">" + nome + "</a>")
                        .append("</li>");
            }
            return sb.isEmpty() ? null : "<p>Anexos trazidos do GLPI:</p><ul>" + sb + "</ul>";
        }

        private Long dept(Object entidade) {
            Long d = entidade == null ? null : deptPorEntidade.get(num(entidade));
            return d != null ? d : deptPadrao;
        }

        // ---------------------------------------------------------------- cadastros

        private void entidades() {
            List<DeptDO> depts = deptMapper.selectList();
            deptPadrao = op.getDeptPadraoId() != null ? op.getDeptPadraoId()
                    : depts.stream().filter(d -> d.getParentId() == null || d.getParentId() == 0L).map(DeptDO::getId).min(Long::compare).orElse(null);
            Map<String, Long> porNome = new HashMap<>();
            depts.forEach(d -> porNome.putIfAbsent(d.getName().trim().toLowerCase(Locale.ROOT), d.getId()));
            for (Map<String, Object> e : f.lista("SELECT id, name, completename FROM glpi_entities")) {
                conta().setLidos(conta().getLidos() + 1);
                Long d = porNome.get(Objects.toString(e.get("name"), "").trim().toLowerCase(Locale.ROOT));
                if (d != null) {
                    deptPorEntidade.put(num(e.get("id")), d);
                    importado();
                } else {
                    existente();   // sem unidade com o mesmo nome: vai para a padrão
                }
            }
        }

        private void usuarios() {
            for (Map<String, Object> e : f.lista("SELECT users_id, email FROM glpi_useremails ORDER BY is_default DESC, id")) {
                emailPorUsuario.putIfAbsent(num(e.get("users_id")), String.valueOf(e.get("email")).trim().toLowerCase(Locale.ROOT));
            }
            Map<String, Long> porLogin = new HashMap<>();
            Map<String, Long> porEmail = new HashMap<>();
            adminUserMapper.selectList().forEach(u -> {
                porLogin.put(u.getUsername().toLowerCase(Locale.ROOT), u.getId());
                if (u.getEmail() != null && !u.getEmail().isBlank()) {
                    porEmail.putIfAbsent(u.getEmail().trim().toLowerCase(Locale.ROOT), u.getId());
                }
            });
            SecureRandom aleatorio = new SecureRandom();
            for (Map<String, Object> u : f.lista("SELECT id, name, realname, firstname, phone, mobile, is_active, entities_id FROM glpi_users ORDER BY id")) {
                String loginGlpi = Objects.toString(u.get("name"), "");
                if (CONTAS_PADRAO.contains(loginGlpi.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                item("usuário " + loginGlpi, () -> {
                    if (usuario(u.get("id")) != null) {
                        existente();
                        return;
                    }
                    String login = GlpiConverte.login(loginGlpi);
                    String email = emailPorUsuario.get(num(u.get("id")));
                    Long id = login == null ? null : porLogin.get(login);
                    if (id == null && email != null) {
                        id = porEmail.get(email);
                    }
                    if (id != null) {
                        mapear("usuario", u.get("id"), id);
                        existente();
                        return;
                    }
                    if (!Boolean.TRUE.equals(op.getCriarUsuarios()) || login == null) {
                        return;   // fica só pelo e-mail nos chamados
                    }
                    byte[] b = new byte[24];
                    aleatorio.nextBytes(b);
                    String telefone = Objects.toString(u.get("mobile") != null && !String.valueOf(u.get("mobile")).isBlank() ? u.get("mobile") : u.get("phone"), null);
                    AdminUserDO novo = AdminUserDO.builder().username(login)
                            .nickname(cortar(GlpiConverte.nome((String) u.get("firstname"), (String) u.get("realname"), login), 100))
                            // senha aleatória e conta desativada: entra pelo AD ou depois que alguém ativar
                            .password(passwordEncoder.encode(Base64.getEncoder().encodeToString(b)))
                            .status(CommonStatusEnum.DISABLE.getStatus())
                            .email(email == null ? "" : cortar(email, 128)).mobile(telefone == null ? "" : cortar(telefone, 32))
                            .deptId(dept(u.get("entities_id"))).remark("Importado do GLPI (#" + u.get("id") + ")").build();
                    adminUserMapper.insert(novo);
                    porLogin.put(login, novo.getId());
                    mapear("usuario", u.get("id"), novo.getId());
                    importado();
                });
            }
        }

        private void equipes() {
            Map<String, Long> porNome = new HashMap<>();
            equipeMapper.selectList().forEach(e -> porNome.put(e.getNome().toLowerCase(Locale.ROOT), e.getId()));
            List<Map<String, Object>> membros = f.lista("SELECT groups_id, users_id, is_manager FROM glpi_groups_users");
            for (Map<String, Object> g : f.lista("SELECT id, name, completename, comment FROM glpi_groups WHERE is_assign = 1 ORDER BY id")) {
                item("grupo " + g.get("name"), () -> {
                    Long id = destino("equipe", g.get("id"));
                    if (id == null) {
                        String nome = cortar(Objects.toString(g.get("name"), "Grupo " + g.get("id")), 100);
                        id = porNome.get(nome.toLowerCase(Locale.ROOT));
                        if (id == null) {
                            EquipeDO e = EquipeDO.builder().nome(nome).descricao(cortar((String) g.get("comment"), 500)).ativo(true).build();
                            equipeMapper.insert(e);
                            id = e.getId();
                            porNome.put(nome.toLowerCase(Locale.ROOT), id);
                            importado();
                        } else {
                            existente();
                        }
                        mapear("equipe", g.get("id"), id);
                    } else {
                        existente();
                    }
                    Long equipe = id;
                    Set<Long> ja = equipeMembroMapper.selectList(EquipeMembroDO::getEquipeId, equipe).stream()
                            .map(EquipeMembroDO::getUserId).collect(Collectors.toSet());
                    membros.stream().filter(m -> num(m.get("groups_id")) == num(g.get("id"))).forEach(m -> {
                        Long u = usuario(m.get("users_id"));
                        if (u != null && ja.add(u)) {
                            equipeMembroMapper.insert(EquipeMembroDO.builder().equipeId(equipe).userId(u)
                                    .supervisor(num(m.get("is_manager")) == 1).build());
                        }
                    });
                });
            }
        }

        private void categorias() {
            for (Map<String, Object> c : f.lista("SELECT id, itilcategories_id, name, comment, code, users_id, groups_id, level,"
                    + " is_helpdeskvisible, is_incident, is_request FROM glpi_itilcategories ORDER BY level, id")) {
                item("categoria " + c.get("name"), () -> {
                    if (destino("categoria", c.get("id")) != null) {
                        existente();
                        return;
                    }
                    Long pai = destino("categoria", c.get("itilcategories_id"));
                    long paiId = pai == null ? CategoriaDO.RAIZ : pai;
                    String nome = cortar(Objects.toString(c.get("name"), "Categoria " + c.get("id")).trim(), 100);
                    CategoriaDO mesma = categoriaMapper.selectOne(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<CategoriaDO>()
                            .eq(CategoriaDO::getParentId, paiId).apply("lower(nome) = lower({0})", nome).last("LIMIT 1"));
                    if (mesma != null) {
                        mapear("categoria", c.get("id"), mesma.getId());
                        existente();
                        return;
                    }
                    CategoriaDO paiDO = pai == null ? null : categoriaMapper.selectById(pai);
                    String codigo = vazio((String) c.get("code"));
                    if (codigo != null && categoriaMapper.selectCount(CategoriaDO::getCodigo, codigo) > 0) {
                        codigo = null;
                    }
                    CategoriaDO nova = CategoriaDO.builder().parentId(paiId).nome(nome)
                            .nomeCompleto(paiDO == null ? nome : paiDO.getNomeCompleto() + " > " + nome)
                            .nivel(paiDO == null ? 1 : paiDO.getNivel() + 1).codigo(codigo).descricao(cortar((String) c.get("comment"), 1000))
                            .tecnicoId(usuario(c.get("users_id"))).equipeId(destino("equipe", c.get("groups_id")))
                            .visivelPortal(num(c.get("is_helpdeskvisible")) == 1).paraIncidente(num(c.get("is_incident")) == 1)
                            .paraRequisicao(num(c.get("is_request")) == 1).ativo(true).ordem(0).build();
                    categoriaMapper.insert(nova);
                    mapear("categoria", c.get("id"), nova.getId());
                    importado();
                });
            }
        }

        private void origens() {
            Map<String, Long> porNome = new HashMap<>();
            origemMapper.selectList().forEach(o -> porNome.put(o.getNome().toLowerCase(Locale.ROOT), o.getId()));
            for (Map<String, Object> o : f.lista("SELECT id, name, is_active FROM glpi_requesttypes ORDER BY id")) {
                item("origem " + o.get("name"), () -> {
                    if (destino("origem", o.get("id")) != null) {
                        existente();
                        return;
                    }
                    String nome = cortar(Objects.toString(o.get("name"), "Origem " + o.get("id")).trim(), 100);
                    Long id = porNome.get(nome.toLowerCase(Locale.ROOT));
                    if (id == null) {
                        OrigemDO nova = OrigemDO.builder().nome(nome).padraoPortal(false).padraoEmail(false)
                                .ativo(num(o.get("is_active")) == 1).build();
                        origemMapper.insert(nova);
                        id = nova.getId();
                        porNome.put(nome.toLowerCase(Locale.ROOT), id);
                        importado();
                    } else {
                        existente();
                    }
                    mapear("origem", o.get("id"), id);
                });
            }
        }

        // ---------------------------------------------------------------- chamados

        /**
         * Mantendo os números do GLPI: a sequência é empurrada para depois do maior número de lá, o registro entra com
         * um número provisório e é renumerado na mesma transação (assim um número provisório nunca colide com um do
         * GLPI ainda por vir).
         */
        private boolean prepararNumeros(String tabela, String sequencia, String tipo, String sqlIds) {
            if (!Boolean.TRUE.equals(op.getManterNumeros())) {
                return false;
            }
            Set<Long> daqui = new HashSet<>(importacaoMapaMapper.ids(tabela));
            Set<Long> jaVieram = new HashSet<>(mapa(tipo).values());
            List<Long> glpi = f.lista(sqlIds).stream().map(r -> num(r.get("id"))).toList();
            long ocupados = glpi.stream().filter(id -> daqui.contains(id) && !jaVieram.contains(id) && !mapa(tipo).containsKey(String.valueOf(id))).count();
            if (ocupados > 0) {
                log(tipo + ": " + ocupados + " números do GLPI já estão em uso aqui; esses registros recebem número novo");
            }
            long maior = glpi.stream().mapToLong(Long::longValue).max().orElse(0);
            importacaoMapaMapper.ajustarSequencia(sequencia, tabela, maior + 1);
            return true;
        }

        private Long numerar(String tabela, Long provisorio, Object glpiId, boolean manter) {
            if (!manter) {
                return provisorio;
            }
            long alvo = num(glpiId);
            // número já ocupado por algo daqui: fica com o provisório
            if (importacaoMapaMapper.contar(tabela, alvo) == 0) {
                importacaoMapaMapper.trocarId(tabela, provisorio, alvo);
                return alvo;
            }
            return provisorio;
        }

        private void chamados() {
            boolean manter = prepararNumeros("itsm.chamado", "itsm.chamado_seq", "chamado",
                    "SELECT id FROM glpi_tickets WHERE 1 = 1" + filtroItil());
            emLotes("SELECT * FROM glpi_tickets WHERE id > ?" + filtroItil() + " ORDER BY id LIMIT ?", lote -> {
                List<Long> ids = lote.stream().map(r -> num(r.get("id"))).toList();
                Map<Long, List<Map<String, Object>>> usuarios = porItem("SELECT * FROM glpi_tickets_users WHERE tickets_id IN ({ids})", "tickets_id", ids);
                Map<Long, List<Map<String, Object>>> grupos = porItem("SELECT * FROM glpi_groups_tickets WHERE tickets_id IN ({ids})", "tickets_id", ids);
                Map<Long, List<Map<String, Object>>> acomp = porItem("SELECT * FROM glpi_itilfollowups WHERE itemtype = 'Ticket' AND items_id IN ({ids}) ORDER BY id", "items_id", ids);
                Map<Long, List<Map<String, Object>>> tarefas = porItem("SELECT * FROM glpi_tickettasks WHERE tickets_id IN ({ids}) ORDER BY id", "tickets_id", ids);
                Map<Long, List<Map<String, Object>>> solucoes = porItem("SELECT * FROM glpi_itilsolutions WHERE itemtype = 'Ticket' AND items_id IN ({ids}) ORDER BY id", "items_id", ids);
                Map<Long, List<Map<String, Object>>> validacoes = porItem("SELECT * FROM glpi_ticketvalidations WHERE tickets_id IN ({ids}) ORDER BY id", "tickets_id", ids);
                Map<Long, List<Map<String, Object>>> pesquisas = porItem("SELECT * FROM glpi_ticketsatisfactions WHERE tickets_id IN ({ids})", "tickets_id", ids);
                Map<Long, List<Map<String, Object>>> docs = porItem("SELECT di.items_id, di.documents_id, d.filename FROM glpi_documents_items di"
                        + " JOIN glpi_documents d ON d.id = di.documents_id WHERE di.itemtype = 'Ticket' AND di.items_id IN ({ids})", "items_id", ids);
                Map<Long, List<Map<String, Object>>> docsAcomp = porItem("SELECT di.items_id, di.documents_id, d.filename FROM glpi_documents_items di"
                        + " JOIN glpi_documents d ON d.id = di.documents_id WHERE di.itemtype = 'ITILFollowup' AND di.items_id IN ({ids})", "items_id",
                        acomp.values().stream().flatMap(List::stream).map(a -> num(a.get("id"))).toList());
                for (Map<String, Object> t : lote) {
                    long id = num(t.get("id"));
                    item("chamado " + id, () -> chamado(t, manter, usuarios.getOrDefault(id, List.of()), grupos.getOrDefault(id, List.of()),
                            acomp.getOrDefault(id, List.of()), docsAcomp, tarefas.getOrDefault(id, List.of()),
                            solucoes.getOrDefault(id, List.of()), validacoes.getOrDefault(id, List.of()),
                            pesquisas.getOrDefault(id, List.of()), docs.getOrDefault(id, List.of())));
                }
            });
        }

        private void chamado(Map<String, Object> t, boolean manter, List<Map<String, Object>> usuarios, List<Map<String, Object>> grupos,
                             List<Map<String, Object>> acomp, Map<Long, List<Map<String, Object>>> docsAcomp,
                             List<Map<String, Object>> tarefas, List<Map<String, Object>> solucoes,
                             List<Map<String, Object>> validacoes, List<Map<String, Object>> pesquisas, List<Map<String, Object>> docs) {
            if (destino("chamado", t.get("id")) != null) {
                existente();
                return;
            }
            Long solicitante = usuarios.stream().filter(u -> num(u.get("type")) == AtorTipo.SOLICITANTE.getCodigo())
                    .map(u -> usuario(u.get("users_id"))).filter(Objects::nonNull).findFirst().orElse(null);
            Long deptSolicitante = solicitante == null ? null : Optional.ofNullable(adminUserMapper.selectById(solicitante)).map(AdminUserDO::getDeptId).orElse(null);
            LocalDateTime aberto = data(t.get("date"), t.get("date_creation"));
            int status = GlpiConverte.statusChamado(int0(t.get("status")));
            ChamadoDO c = ChamadoDO.builder()
                    .titulo(titulo(t.get("name"))).descricao(texto(t.get("content")))
                    .tipo(num(t.get("type")) == 2 ? 2 : 1).status(status)
                    .urgencia(GlpiConverte.escala(int0(t.get("urgency")))).impacto(GlpiConverte.escala(int0(t.get("impact"))))
                    .prioridade(GlpiConverte.prioridade(int0(t.get("priority"))))
                    .categoriaId(destino("categoria", t.get("itilcategories_id"))).origemId(destino("origem", t.get("requesttypes_id")))
                    .deptId(deptPorEntidade.containsKey(num(t.get("entities_id"))) ? dept(t.get("entities_id"))
                            : deptSolicitante != null ? deptSolicitante : deptPadrao)
                    .validacao(GlpiConverte.validacao(int0(t.get("global_validation"))))
                    .abertoEm(aberto).assumidoEm(data(t.get("takeintoaccountdate"))).solucionadoEm(data(t.get("solvedate")))
                    .fechadoEm(data(t.get("closedate"))).prazoAtendimento(data(t.get("time_to_own"))).prazoSolucao(data(t.get("time_to_resolve")))
                    .inicioEspera(status == 4 ? data(t.get("begin_waiting_date")) : null)
                    .esperaSeg(lng(t.get("waiting_duration"))).esperaSlaSeg(lng(t.get("sla_waiting_duration")))
                    .tempoAssumirSeg(positivo(t.get("takeintoaccount_delay_stat"))).tempoSolucaoSeg(positivo(t.get("solve_delay_stat")))
                    .tempoFechamentoSeg(positivo(t.get("close_delay_stat"))).tempoGastoSeg(lng(t.get("actiontime")))
                    .registradoPor(usuario(t.get("users_id_recipient"))).build();
            c.setCreateTime(aberto);
            chamadoMapper.insert(c);
            Long id = numerar("itsm.chamado", c.getId(), t.get("id"), manter);
            // participantes
            Set<String> ja = new HashSet<>();
            for (Map<String, Object> u : usuarios) {
                int tipo = num(u.get("type")) >= 1 && num(u.get("type")) <= 3 ? (int) num(u.get("type")) : 1;
                Long uid = usuario(u.get("users_id"));
                String email = uid != null ? null : vazio(Objects.toString(u.get("alternative_email"), null)) != null
                        ? String.valueOf(u.get("alternative_email")).trim().toLowerCase(Locale.ROOT) : emailPorUsuario.get(num(u.get("users_id")));
                if (uid == null && email == null) {
                    continue;
                }
                if (ja.add(tipo + ":" + (uid != null ? "u" + uid : email))) {
                    chamadoAtorMapper.insert(ChamadoAtorDO.builder().chamadoId(id).tipo(tipo).userId(uid).email(uid == null ? cortar(email, 255) : null)
                            .notificar(num(u.get("use_notification")) == 1).build());
                }
            }
            for (Map<String, Object> g : grupos) {
                Long eq = destino("equipe", g.get("groups_id"));
                if (eq != null && num(g.get("type")) == AtorTipo.TECNICO.getCodigo() && ja.add("2:e" + eq)) {
                    chamadoAtorMapper.insert(ChamadoAtorDO.builder().chamadoId(id).tipo(2).equipeId(eq).notificar(true).build());
                }
            }
            // linha do tempo
            for (Map<String, Object> a : acomp) {
                String conteudo = texto(a.get("content"));
                String extra = anexos(docsAcomp.getOrDefault(num(a.get("id")), List.of()).stream()
                        .filter(d -> !citado(conteudo, num(d.get("documents_id")))).toList());
                interacao(InteracaoDO.builder().chamadoId(id).tipo(InteracaoDO.ACOMPANHAMENTO).userId(usuario(a.get("users_id")))
                        .conteudo(extra == null ? conteudo : conteudo + extra).privado(num(a.get("is_private")) == 1)
                        .origemId(destino("origem", a.get("requesttypes_id"))).build(), data(a.get("date"), a.get("date_creation")));
            }
            for (Map<String, Object> k : tarefas) {
                interacao(InteracaoDO.builder().chamadoId(id).tipo(InteracaoDO.TAREFA).userId(usuario(k.get("users_id")))
                        .conteudo(texto(k.get("content"))).privado(num(k.get("is_private")) == 1)
                        .estadoTarefa(GlpiConverte.estadoTarefa(int0(k.get("state")))).tecnicoId(usuario(k.get("users_id_tech")))
                        .equipeId(destino("equipe", k.get("groups_id_tech"))).inicio(data(k.get("begin"))).fim(data(k.get("end")))
                        .tempoGastoSeg(lng(k.get("actiontime"))).build(), data(k.get("date"), k.get("date_creation")));
            }
            for (Map<String, Object> s : solucoes) {
                interacao(InteracaoDO.builder().chamadoId(id).tipo(InteracaoDO.SOLUCAO).userId(usuario(s.get("users_id")))
                        .conteudo(texto(s.get("content"))).privado(false)
                        .statusSolucao(GlpiConverte.validacao(int0(s.get("status")))).avaliadoPor(usuario(s.get("users_id_approval")))
                        .avaliadoEm(data(s.get("date_approval"))).build(), data(s.get("date_creation")));
            }
            String extra = anexos(docs.stream().filter(d -> !citado(c.getDescricao(), num(d.get("documents_id")))).toList());
            if (extra != null) {
                interacao(InteracaoDO.builder().chamadoId(id).tipo(InteracaoDO.ACOMPANHAMENTO).userId(solicitante)
                        .conteudo(extra).privado(false).build(), aberto);
            }
            for (Map<String, Object> v : validacoes) {
                Long aprovador = aprovador(v);
                int st = GlpiConverte.validacao(int0(v.get("status")));
                if (aprovador == null || st == 1) {
                    continue;
                }
                AprovacaoDO a = AprovacaoDO.builder().chamadoId(id).aprovadorId(aprovador).solicitadoPor(usuario(v.get("users_id")))
                        .status(st).comentarioPedido(cortar(textoPuro(v.get("comment_submission")), 2000))
                        .comentarioResposta(cortar(textoPuro(v.get("comment_validation")), 2000))
                        .pedidoEm(Optional.ofNullable(data(v.get("submission_date"))).orElse(aberto))
                        .respondidoEm(data(v.get("validation_date"))).build();
                aprovacaoMapper.insert(a);
            }
            for (Map<String, Object> p : pesquisas) {
                satisfacaoMapper.insert(SatisfacaoDO.builder().chamadoId(id)
                        .enviadaEm(Optional.ofNullable(data(p.get("date_begin"))).orElse(aberto)).respondidaEm(data(p.get("date_answered")))
                        .nota(GlpiConverte.nota(int0(p.get("satisfaction")), dbl(p.get("satisfaction_scaled_to_5"))))
                        .comentario(cortar(textoPuro(p.get("comment")), 2000)).build());
                break;   // uma pesquisa por chamado
            }
            historicoMapper.insert(HistoricoDO.builder().chamadoId(id).momento(LocalDateTime.now()).userId(userId).usuario("Importação")
                    .campo("importado").valorNovo("GLPI #" + t.get("id")).build());
            mapear("chamado", t.get("id"), id);
            importado();
        }

        private void interacao(InteracaoDO i, LocalDateTime quando) {
            i.setCreateTime(quando);
            i.setUpdateTime(quando);
            interacaoMapper.insert(i);
        }

        // ---------------------------------------------------------------- base de conhecimento

        private void baseConhecimento() {
            boolean categorias11 = f.temTabela("glpi_knowbaseitemcategories");
            if (categorias11) {
                for (Map<String, Object> c : f.lista("SELECT id, knowbaseitemcategories_id, name, comment FROM glpi_knowbaseitemcategories ORDER BY level, id")) {
                    item("categoria da base " + c.get("name"), () -> {
                        if (destino("kb_categoria", c.get("id")) != null) {
                            existente();
                            return;
                        }
                        Long pai = destino("kb_categoria", c.get("knowbaseitemcategories_id"));
                        KbCategoriaDO paiDO = pai == null ? null : kbCategoriaMapper.selectById(pai);
                        String nome = cortar(Objects.toString(c.get("name"), "Categoria").trim(), 100);
                        KbCategoriaDO k = KbCategoriaDO.builder().parentId(pai == null ? KbCategoriaDO.RAIZ : pai).nome(nome)
                                .nomeCompleto(paiDO == null ? nome : paiDO.getNomeCompleto() + " > " + nome)
                                .descricao(cortar((String) c.get("comment"), 1000)).ordem(0).build();
                        kbCategoriaMapper.insert(k);
                        mapear("kb_categoria", c.get("id"), k.getId());
                        importado();
                    });
                }
            }
            Map<Long, Long> categoriaDoArtigo = new HashMap<>();
            if (f.temTabela("glpi_knowbaseitems_knowbaseitemcategories")) {
                f.lista("SELECT knowbaseitems_id, knowbaseitemcategories_id FROM glpi_knowbaseitems_knowbaseitemcategories ORDER BY id")
                        .forEach(r -> categoriaDoArtigo.putIfAbsent(num(r.get("knowbaseitems_id")), num(r.get("knowbaseitemcategories_id"))));
            } else if (f.temColuna("glpi_knowbaseitems", "knowbaseitemcategories_id")) {
                f.lista("SELECT id, knowbaseitemcategories_id FROM glpi_knowbaseitems")
                        .forEach(r -> categoriaDoArtigo.put(num(r.get("id")), num(r.get("knowbaseitemcategories_id"))));
            }
            boolean fixado = f.temColuna("glpi_knowbaseitems", "is_pinned");
            emLotes("SELECT * FROM glpi_knowbaseitems WHERE id > ? ORDER BY id LIMIT ?", lote -> lote.forEach(a -> item("artigo " + a.get("id"), () -> {
                if (destino("kb_artigo", a.get("id")) != null) {
                    existente();
                    return;
                }
                String conteudo = texto(a.get("answer"));
                Long autor = usuario(a.get("users_id"));
                KbArtigoDO k = KbArtigoDO.builder().titulo(titulo(a.get("name"))).conteudo(conteudo)
                        .texto(HtmlSeguro.texto(conteudo)).categoriaId(destino("kb_categoria", categoriaDoArtigo.get(num(a.get("id")))))
                        .faq(num(a.get("is_faq")) == 1).publicado(true).fixado(fixado && num(a.get("is_pinned")) == 1)
                        .inicioPublicacao(data(a.get("begin_date"))).fimPublicacao(data(a.get("end_date")))
                        .autorId(autor != null ? autor : userId).revisao(1).visualizacoes((int) num(a.get("view")))
                        .util(0).naoUtil(0).build();
                k.setCreateTime(data(a.get("date_creation"), a.get("date")));
                kbArtigoMapper.insert(k);
                mapear("kb_artigo", a.get("id"), k.getId());
                importado();
            })));
        }

        // ---------------------------------------------------------------- problemas e mudanças

        private void problemas() {
            boolean manter = prepararNumeros("itsm.problema", "itsm.problema_seq", "problema", "SELECT id FROM glpi_problems WHERE 1 = 1" + filtroItil());
            emLotes("SELECT * FROM glpi_problems WHERE id > ?" + filtroItil() + " ORDER BY id LIMIT ?", lote -> {
                Filhos fl = filhos(lote, "Problem", "glpi_problems_users", "problems_id", "glpi_groups_problems", "problems_id",
                        "glpi_problemtasks", "problems_id");
                for (Map<String, Object> p : lote) {
                    item("problema " + p.get("id"), () -> {
                        if (destino("problema", p.get("id")) != null) {
                            existente();
                            return;
                        }
                        LocalDateTime aberto = data(p.get("date"), p.get("date_creation"));
                        ProblemaDO d = ProblemaDO.builder().titulo(titulo(p.get("name"))).descricao(texto(p.get("content")))
                                .status(GlpiConverte.statusProblema(int0(p.get("status"))))
                                .urgencia(GlpiConverte.escala(int0(p.get("urgency")))).impacto(GlpiConverte.escala(int0(p.get("impact"))))
                                .prioridade(GlpiConverte.prioridade(int0(p.get("priority")))).categoriaId(destino("categoria", p.get("itilcategories_id")))
                                .deptId(dept(p.get("entities_id"))).abertoEm(aberto).prazo(data(p.get("time_to_resolve")))
                                .solucionadoEm(data(p.get("solvedate"))).fechadoEm(data(p.get("closedate")))
                                .impactos(texto(p.get("impactcontent"))).causas(texto(p.get("causecontent"))).sintomas(texto(p.get("symptomcontent")))
                                .tempoGastoSeg(lng(p.get("actiontime"))).registradoPor(usuario(p.get("users_id_recipient")))
                                .idExterno("glpi:" + p.get("id")).build();
                        d.setCreateTime(aberto);
                        problemaMapper.insert(d);
                        Long id = numerar("itsm.problema", d.getId(), p.get("id"), manter);
                        itil(ItilVOs.PROBLEMA, id, num(p.get("id")), fl, aberto);
                        mapear("problema", p.get("id"), id);
                        importado();
                    });
                }
            });
        }

        private void mudancas() {
            boolean manter = prepararNumeros("itsm.mudanca", "itsm.mudanca_seq", "mudanca", "SELECT id FROM glpi_changes WHERE 1 = 1" + filtroItil());
            emLotes("SELECT * FROM glpi_changes WHERE id > ?" + filtroItil() + " ORDER BY id LIMIT ?", lote -> {
                Filhos fl = filhos(lote, "Change", "glpi_changes_users", "changes_id", "glpi_changes_groups", "changes_id",
                        "glpi_changetasks", "changes_id");
                Map<Long, List<Map<String, Object>>> validacoes = porItem("SELECT * FROM glpi_changevalidations WHERE changes_id IN ({ids}) ORDER BY id",
                        "changes_id", lote.stream().map(r -> num(r.get("id"))).toList());
                for (Map<String, Object> m : lote) {
                    item("mudança " + m.get("id"), () -> {
                        if (destino("mudanca", m.get("id")) != null) {
                            existente();
                            return;
                        }
                        LocalDateTime aberto = data(m.get("date"), m.get("date_creation"));
                        MudancaDO d = MudancaDO.builder().titulo(titulo(m.get("name"))).descricao(texto(m.get("content")))
                                .tipo(MudancaTipo.NORMAL.getCodigo()).status(GlpiConverte.statusMudanca(int0(m.get("status"))))
                                .urgencia(GlpiConverte.escala(int0(m.get("urgency")))).impacto(GlpiConverte.escala(int0(m.get("impact"))))
                                .prioridade(GlpiConverte.prioridade(int0(m.get("priority")))).categoriaId(destino("categoria", m.get("itilcategories_id")))
                                .deptId(dept(m.get("entities_id"))).abertoEm(aberto).prazo(data(m.get("time_to_resolve")))
                                .solucionadoEm(data(m.get("solvedate"))).fechadoEm(data(m.get("closedate")))
                                .analiseImpacto(texto(m.get("impactcontent"))).planoControle(texto(m.get("controlistcontent")))
                                .planoImplantacao(texto(m.get("rolloutplancontent"))).planoRetorno(texto(m.get("backoutplancontent")))
                                .checklist(texto(m.get("checklistcontent"))).validacao(GlpiConverte.validacao(int0(m.get("global_validation"))))
                                .tempoGastoSeg(lng(m.get("actiontime"))).registradoPor(usuario(m.get("users_id_recipient")))
                                .idExterno("glpi:" + m.get("id")).build();
                        d.setCreateTime(aberto);
                        mudancaMapper.insert(d);
                        Long id = numerar("itsm.mudanca", d.getId(), m.get("id"), manter);
                        itil(ItilVOs.MUDANCA, id, num(m.get("id")), fl, aberto);
                        for (Map<String, Object> v : validacoes.getOrDefault(num(m.get("id")), List.of())) {
                            Long aprovador = aprovador(v);
                            int st = GlpiConverte.validacao(int0(v.get("status")));
                            if (aprovador != null && st != 1) {
                                mudancaAprovacaoMapper.insert(MudancaAprovacaoDO.builder().mudancaId(id).aprovadorId(aprovador)
                                        .solicitadoPor(usuario(v.get("users_id"))).rodada(1).status(st)
                                        .comentarioPedido(cortar(textoPuro(v.get("comment_submission")), 2000))
                                        .comentarioResposta(cortar(textoPuro(v.get("comment_validation")), 2000))
                                        .pedidoEm(Optional.ofNullable(data(v.get("submission_date"))).orElse(aberto))
                                        .respondidoEm(data(v.get("validation_date"))).build());
                            }
                        }
                        mapear("mudanca", m.get("id"), id);
                        importado();
                    });
                }
            });
        }

        private record Filhos(Map<Long, List<Map<String, Object>>> usuarios, Map<Long, List<Map<String, Object>>> grupos,
                              Map<Long, List<Map<String, Object>>> acomp, Map<Long, List<Map<String, Object>>> tarefas,
                              Map<Long, List<Map<String, Object>>> solucoes) {
        }

        private Filhos filhos(List<Map<String, Object>> lote, String itemtype, String tUsuarios, String cUsuarios, String tGrupos,
                              String cGrupos, String tTarefas, String cTarefas) {
            List<Long> ids = lote.stream().map(r -> num(r.get("id"))).toList();
            return new Filhos(
                    porItem("SELECT * FROM " + tUsuarios + " WHERE " + cUsuarios + " IN ({ids})", cUsuarios, ids),
                    porItem("SELECT * FROM " + tGrupos + " WHERE " + cGrupos + " IN ({ids})", cGrupos, ids),
                    porItem("SELECT * FROM glpi_itilfollowups WHERE itemtype = '" + itemtype + "' AND items_id IN ({ids}) ORDER BY id", "items_id", ids),
                    porItem("SELECT * FROM " + tTarefas + " WHERE " + cTarefas + " IN ({ids}) ORDER BY id", cTarefas, ids),
                    porItem("SELECT * FROM glpi_itilsolutions WHERE itemtype = '" + itemtype + "' AND items_id IN ({ids}) ORDER BY id", "items_id", ids));
        }

        /** Participantes, linha do tempo e registro de importação de um problema ou mudança. */
        private void itil(String objeto, Long id, long glpiId, Filhos fl, LocalDateTime aberto) {
            Set<String> ja = new HashSet<>();
            for (Map<String, Object> u : fl.usuarios().getOrDefault(glpiId, List.of())) {
                Long uid = usuario(u.get("users_id"));
                int tipo = (int) Math.max(1, Math.min(3, num(u.get("type"))));
                if (uid != null && ja.add(tipo + ":u" + uid)) {
                    itilAtorMapper.insert(ItilAtorDO.builder().objeto(objeto).objetoId(id).tipo(tipo).userId(uid).build());
                }
            }
            for (Map<String, Object> g : fl.grupos().getOrDefault(glpiId, List.of())) {
                Long eq = destino("equipe", g.get("groups_id"));
                if (eq != null && num(g.get("type")) == AtorTipo.TECNICO.getCodigo() && ja.add("2:e" + eq)) {
                    itilAtorMapper.insert(ItilAtorDO.builder().objeto(objeto).objetoId(id).tipo(2).equipeId(eq).build());
                }
            }
            for (Map<String, Object> a : fl.acomp().getOrDefault(glpiId, List.of())) {
                itilInteracao(ItilInteracaoDO.builder().objeto(objeto).objetoId(id).tipo(InteracaoDO.ACOMPANHAMENTO)
                        .userId(usuario(a.get("users_id"))).conteudo(texto(a.get("content"))).build(), data(a.get("date"), a.get("date_creation")));
            }
            for (Map<String, Object> k : fl.tarefas().getOrDefault(glpiId, List.of())) {
                itilInteracao(ItilInteracaoDO.builder().objeto(objeto).objetoId(id).tipo(InteracaoDO.TAREFA)
                        .userId(usuario(k.get("users_id"))).conteudo(texto(k.get("content")))
                        .estadoTarefa(GlpiConverte.estadoTarefa(int0(k.get("state")))).tecnicoId(usuario(k.get("users_id_tech")))
                        .inicio(data(k.get("begin"))).fim(data(k.get("end"))).tempoGastoSeg(lng(k.get("actiontime"))).build(),
                        data(k.get("date"), k.get("date_creation")));
            }
            for (Map<String, Object> s : fl.solucoes().getOrDefault(glpiId, List.of())) {
                itilInteracao(ItilInteracaoDO.builder().objeto(objeto).objetoId(id).tipo(InteracaoDO.SOLUCAO)
                        .userId(usuario(s.get("users_id"))).conteudo(texto(s.get("content"))).build(), data(s.get("date_creation")));
            }
            itilHistoricoMapper.insert(ItilHistoricoDO.builder().objeto(objeto).objetoId(id).momento(LocalDateTime.now()).userId(userId)
                    .usuario("Importação").campo("importado").valorNovo("GLPI #" + glpiId).build());
        }

        private void itilInteracao(ItilInteracaoDO i, LocalDateTime quando) {
            i.setCreateTime(quando);
            i.setUpdateTime(quando);
            itilInteracaoMapper.insert(i);
        }

        /** Problema ↔ chamado, mudança ↔ chamado, mudança ↔ problema. */
        private void vinculos() {
            vincular("SELECT problems_id AS o, tickets_id AS a FROM glpi_problems_tickets", ItilVOs.PROBLEMA, "problema", ItilVOs.CHAMADO, "chamado");
            vincular("SELECT changes_id AS o, tickets_id AS a FROM glpi_changes_tickets", ItilVOs.MUDANCA, "mudanca", ItilVOs.CHAMADO, "chamado");
            vincular("SELECT changes_id AS o, problems_id AS a FROM glpi_changes_problems", ItilVOs.MUDANCA, "mudanca", ItilVOs.PROBLEMA, "problema");
        }

        private void vincular(String sql, String objeto, String tipoObjeto, String alvo, String tipoAlvo) {
            for (Map<String, Object> r : f.lista(sql)) {
                Long o = destino(tipoObjeto, r.get("o"));
                Long a = destino(tipoAlvo, r.get("a"));
                if (o == null || a == null) {
                    continue;   // um dos lados não veio (fora do filtro de data ou na lixeira)
                }
                item("vínculo " + objeto + " " + o + " → " + alvo + " " + a, () -> {
                    if (itilVinculoMapper.selectCount(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ItilVinculoDO>()
                            .eq(ItilVinculoDO::getObjeto, objeto).eq(ItilVinculoDO::getObjetoId, o)
                            .eq(ItilVinculoDO::getAlvo, alvo).eq(ItilVinculoDO::getAlvoId, a)) > 0) {
                        existente();
                        return;
                    }
                    itilVinculoMapper.insert(ItilVinculoDO.builder().objeto(objeto).objetoId(o).alvo(alvo).alvoId(a).build());
                    importado();
                });
            }
        }

        // ---------------------------------------------------------------- governança

        private void fornecedores() {
            for (Map<String, Object> s : f.lista("SELECT * FROM glpi_suppliers WHERE is_deleted = 0 ORDER BY id")) {
                item("fornecedor " + s.get("name"), () -> {
                    if (destino("fornecedor", s.get("id")) != null) {
                        existente();
                        return;
                    }
                    String doc = Documento.normalizar((String) s.get("registration_number"));
                    if (doc != null && !Documento.valido(doc)) {
                        log("Fornecedor " + s.get("name") + ": CNPJ/CPF \"" + s.get("registration_number") + "\" inválido, não importado (vai na observação)");
                    }
                    String docValido = doc != null && Documento.valido(doc) ? doc : null;
                    if (docValido != null) {
                        FornecedorDO mesmo = fornecedorMapper.selectOne(FornecedorDO::getDocumento, docValido);
                        if (mesmo != null) {
                            mapear("fornecedor", s.get("id"), mesmo.getId());
                            existente();
                            return;
                        }
                    }
                    String uf = Objects.toString(s.get("state"), "").trim();
                    String obs = Objects.toString(s.get("comment"), "");
                    if (doc != null && docValido == null) {
                        obs = (obs + "\nCNPJ/CPF no GLPI: " + s.get("registration_number")).trim();
                    }
                    FornecedorDO n = FornecedorDO.builder().nome(cortar(Objects.toString(s.get("name"), "Fornecedor " + s.get("id")), 150))
                            .documento(docValido).email(cortar(vazio((String) s.get("email")), 255)).telefone(cortar(vazio((String) s.get("phonenumber")), 40))
                            .site(cortar(vazio((String) s.get("website")), 255)).endereco(cortar(vazio((String) s.get("address")), 255))
                            .cidade(cortar(vazio((String) s.get("town")), 100)).uf(uf.matches("[A-Za-z]{2}") ? uf.toUpperCase(Locale.ROOT) : null)
                            .cep(cortar(vazio((String) s.get("postcode")), 9)).observacao(vazio(obs))
                            .ativo(!f.temColuna("glpi_suppliers", "is_active") || num(s.get("is_active")) == 1)
                            .idExterno("glpi:" + s.get("id")).build();
                    fornecedorMapper.insert(n);
                    mapear("fornecedor", s.get("id"), n.getId());
                    importado();
                });
            }
        }

        private void orcamentos() {
            for (Map<String, Object> b : f.lista("SELECT * FROM glpi_budgets WHERE is_deleted = 0 AND is_template = 0 ORDER BY id")) {
                item("orçamento " + b.get("name"), () -> {
                    if (destino("orcamento", b.get("id")) != null) {
                        existente();
                        return;
                    }
                    LocalDate ini = dia(b.get("begin_date"));
                    LocalDate fim = dia(b.get("end_date"));
                    LocalDateTime criado = data(b.get("date_creation"));
                    int ano = (ini != null ? ini : criado != null ? criado.toLocalDate() : LocalDate.now()).getYear();
                    if (ini == null) {
                        ini = LocalDate.of(ano, 1, 1);
                    }
                    if (fim == null || fim.isBefore(ini)) {
                        fim = LocalDate.of(ini.getYear(), 12, 31);
                    }
                    OrcamentoDO o = OrcamentoDO.builder().nome(cortar(Objects.toString(b.get("name"), "Orçamento " + b.get("id")), 150))
                            .deptId(dept(b.get("entities_id"))).inicio(ini).fim(fim)
                            .valor(b.get("value") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(b.get("value"))).setScale(2, java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO))
                            .observacao(vazio((String) b.get("comment"))).build();
                    orcamentoMapper.insert(o);
                    mapear("orcamento", b.get("id"), o.getId());
                    importado();
                });
            }
        }

        private void contratos() {
            Map<Long, Long> fornecedorDoContrato = new HashMap<>();
            f.lista("SELECT contracts_id, suppliers_id FROM glpi_contracts_suppliers ORDER BY id")
                    .forEach(r -> fornecedorDoContrato.putIfAbsent(num(r.get("contracts_id")), num(r.get("suppliers_id"))));
            Map<Long, List<Map<String, Object>>> custos = f.lista("SELECT * FROM glpi_contractcosts ORDER BY id").stream()
                    .collect(Collectors.groupingBy(r -> num(r.get("contracts_id"))));
            for (Map<String, Object> c : f.lista("SELECT * FROM glpi_contracts WHERE is_deleted = 0 AND is_template = 0 ORDER BY id")) {
                item("contrato " + c.get("name"), () -> {
                    if (destino("contrato", c.get("id")) != null) {
                        existente();
                        return;
                    }
                    LocalDate ini = dia(c.get("begin_date"));
                    if (ini == null) {
                        log("Contrato " + c.get("name") + " (#" + c.get("id") + ") sem data de início: não importado");
                        conta().setErros(conta().getErros() + 1);
                        return;
                    }
                    int duracao = (int) num(c.get("duration"));
                    if (duracao <= 0) {
                        log("Contrato " + c.get("name") + ": sem duração no GLPI, importado com 12 meses");
                        duracao = 12;
                    }
                    int aviso = (int) Math.max(0, num(c.get("notice")));
                    if (aviso >= duracao) {
                        aviso = 0;
                    }
                    int renovacao = (int) num(c.get("renewal"));
                    ContratoDO n = ContratoDO.builder().nome(cortar(Objects.toString(c.get("name"), "Contrato " + c.get("id")), 150))
                            .numero(cortar(vazio((String) c.get("num")), 60)).fornecedorId(destino("fornecedor", fornecedorDoContrato.get(num(c.get("id")))))
                            .deptId(dept(c.get("entities_id"))).inicio(ini).duracaoMeses(duracao).avisoPrevioMeses(aviso)
                            .renovacao(renovacao >= 0 && renovacao <= 2 ? renovacao : 0).alertaDias(30)
                            .numeroContabil(cortar(vazio((String) c.get("accounting_number")), 60)).observacao(vazio((String) c.get("comment")))
                            .encerrado(false).idExterno("glpi:" + c.get("id")).build();
                    contratoMapper.insert(n);
                    for (Map<String, Object> k : custos.getOrDefault(num(c.get("id")), List.of())) {
                        custo("contrato", n.getId(), k, ini);
                    }
                    mapear("contrato", c.get("id"), n.getId());
                    importado();
                });
            }
        }

        private void custo(String objeto, Long id, Map<String, Object> k, LocalDate padrao) {
            LocalDate ini = Optional.ofNullable(dia(k.get("begin_date"))).orElse(padrao);
            LocalDate fim = dia(k.get("end_date"));
            custoMapper.insert(CustoDO.builder().objeto(objeto).objetoId(id).nome(cortar(Objects.toString(k.get("name"), "Custo"), 150))
                    .inicio(ini).fim(fim != null && fim.isBefore(ini) ? null : fim)
                    .valor(k.get("cost") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(k.get("cost"))).setScale(2, java.math.RoundingMode.HALF_UP).max(BigDecimal.ZERO))
                    .orcamentoId(destino("orcamento", k.get("budgets_id"))).observacao(cortar(vazio((String) k.get("comment")), 1000)).build());
        }

        private void licencas() {
            boolean usuariosLicenca = f.temTabela("glpi_softwarelicenses_users");
            Map<Long, List<Map<String, Object>>> computadores = f.temTabela("glpi_items_softwarelicenses")
                    ? f.lista("SELECT isl.softwarelicenses_id, c.name FROM glpi_items_softwarelicenses isl JOIN glpi_computers c"
                    + " ON c.id = isl.items_id WHERE isl.itemtype = 'Computer' AND isl.is_deleted = 0").stream()
                    .collect(Collectors.groupingBy(r -> num(r.get("softwarelicenses_id"))))
                    : Map.of();
            Map<Long, List<Map<String, Object>>> pessoas = usuariosLicenca
                    ? f.lista("SELECT softwarelicenses_id, users_id FROM glpi_softwarelicenses_users").stream()
                    .collect(Collectors.groupingBy(r -> num(r.get("softwarelicenses_id"))))
                    : Map.of();
            for (Map<String, Object> l : f.lista("SELECT l.*, s.name AS software FROM glpi_softwarelicenses l LEFT JOIN glpi_softwares s"
                    + " ON s.id = l.softwares_id WHERE l.is_deleted = 0 AND l.is_template = 0 ORDER BY l.id")) {
                item("licença " + l.get("software"), () -> {
                    if (destino("licenca", l.get("id")) != null) {
                        existente();
                        return;
                    }
                    String serial = vazio((String) l.get("serial"));
                    String chave = null;
                    if (serial != null && cifrador.configurado()) {
                        chave = cifrador.cifrar(serial, LicencaService.CONTEXTO_CHAVE);
                    } else if (serial != null) {
                        log("Licença " + l.get("software") + ": chave não importada (servidor sem chave-mestra)");
                    }
                    List<Map<String, Object>> comps = computadores.getOrDefault(num(l.get("id")), List.of());
                    List<Map<String, Object>> users = pessoas.getOrDefault(num(l.get("id")), List.of());
                    int quantidade = (int) num(l.get("number"));
                    boolean excesso = quantidade >= 0 && comps.size() + users.size() > quantidade;
                    LicencaDO n = LicencaDO.builder().software(cortar(Objects.toString(l.get("software"), "Software"), 150))
                            .nome(cortar(vazio((String) l.get("name")), 150)).quantidade(quantidade < -1 ? -1 : quantidade)
                            .permiteExcesso(excesso).chaveCifrada(chave).expira(dia(l.get("expire"))).alertaDias(30)
                            .contratoId(null).deptId(dept(l.get("entities_id"))).responsavelId(usuario(l.get("users_id_tech")))
                            .observacao(vazio((String) l.get("comment"))).ativo(true).idExterno("glpi:" + l.get("id")).build();
                    licencaMapper.insert(n);
                    if (excesso) {
                        log("Licença " + l.get("software") + ": em uso além do comprado no GLPI (marcada como 'aceita uso além')");
                    }
                    Set<String> ja = new HashSet<>();
                    for (Map<String, Object> c : comps) {
                        String eq = cortar(Objects.toString(c.get("name"), null), 150);
                        if (eq != null && ja.add("e" + eq.toLowerCase(Locale.ROOT))) {
                            licencaUsoMapper.insert(LicencaUsoDO.builder().licencaId(n.getId()).equipamento(eq).desde(LocalDate.now()).build());
                        }
                    }
                    for (Map<String, Object> u : users) {
                        Long uid = usuario(u.get("users_id"));
                        if (uid != null && ja.add("u" + uid)) {
                            licencaUsoMapper.insert(LicencaUsoDO.builder().licencaId(n.getId()).userId(uid).desde(LocalDate.now()).build());
                        }
                    }
                    mapear("licenca", l.get("id"), n.getId());
                    importado();
                });
            }
        }

        private void projetos() {
            Map<Long, Boolean> estadoConcluido = new HashMap<>();
            f.lista("SELECT id, is_finished FROM glpi_projectstates").forEach(r -> estadoConcluido.put(num(r.get("id")), num(r.get("is_finished")) == 1));
            Map<Long, List<Map<String, Object>>> tarefas = f.lista("SELECT * FROM glpi_projecttasks WHERE is_template = 0 ORDER BY projecttasks_id, id")
                    .stream().collect(Collectors.groupingBy(r -> num(r.get("projects_id"))));
            Map<Long, List<Map<String, Object>>> equipe = f.lista("SELECT projects_id, items_id FROM glpi_projectteams WHERE itemtype = 'User'")
                    .stream().collect(Collectors.groupingBy(r -> num(r.get("projects_id"))));
            Map<Long, List<Map<String, Object>>> custos = f.lista("SELECT * FROM glpi_projectcosts ORDER BY id")
                    .stream().collect(Collectors.groupingBy(r -> num(r.get("projects_id"))));
            Map<Long, List<Map<String, Object>>> itens = f.lista("SELECT projects_id, itemtype, items_id FROM glpi_itils_projects")
                    .stream().collect(Collectors.groupingBy(r -> num(r.get("projects_id"))));
            for (Map<String, Object> p : f.lista("SELECT * FROM glpi_projects WHERE is_deleted = 0 AND is_template = 0 ORDER BY id")) {
                item("projeto " + p.get("name"), () -> {
                    if (destino("projeto", p.get("id")) != null) {
                        existente();
                        return;
                    }
                    boolean concluido = Boolean.TRUE.equals(estadoConcluido.get(num(p.get("projectstates_id"))));
                    LocalDate iniReal = dia(p.get("real_start_date"));
                    int situacao = concluido ? Projetos.CONCLUIDO : iniReal != null ? Projetos.EM_ANDAMENTO : Projetos.PLANEJADO;
                    LocalDate iniPrev = dia(p.get("plan_start_date"));
                    LocalDate fimPrev = dia(p.get("plan_end_date"));
                    if (iniPrev != null && fimPrev != null && fimPrev.isBefore(iniPrev)) {
                        fimPrev = iniPrev;
                    }
                    String codigo = cortar(vazio((String) p.get("code")), 40);
                    if (codigo != null && projetoMapper.selectCount(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ProjetoDO>()
                            .apply("lower(codigo) = lower({0})", codigo)) > 0) {
                        codigo = null;
                    }
                    ProjetoDO n = ProjetoDO.builder().nome(cortar(Objects.toString(p.get("name"), "Projeto " + p.get("id")), 150)).codigo(codigo)
                            .descricao(texto(p.get("content"))).situacao(situacao).prioridade(GlpiConverte.prioridade(int0(p.get("priority"))))
                            .deptId(dept(p.get("entities_id"))).responsavelId(usuario(p.get("users_id")))
                            .inicioPrevisto(iniPrev).fimPrevisto(fimPrev).inicioReal(iniReal).fimReal(dia(p.get("real_end_date")))
                            .percentual((int) Math.max(0, Math.min(100, num(p.get("percent_done")))))
                            .percentualAuto(num(p.get("auto_percent_done")) == 1).idExterno("glpi:" + p.get("id")).build();
                    projetoMapper.insert(n);
                    Map<Long, Long> tarefaPara = new HashMap<>();
                    List<Map<String, Object>> ts = tarefas.getOrDefault(num(p.get("id")), List.of());
                    int ordem = 0;
                    for (Map<String, Object> t : ts) {
                        LocalDate ti = dia(t.get("plan_start_date"));
                        LocalDate tf = dia(t.get("plan_end_date"));
                        int pct = (int) Math.max(0, Math.min(100, num(t.get("percent_done"))));
                        int st = Projetos.situacaoDaTarefa(pct > 0 ? Projetos.TAREFA_EM_ANDAMENTO : Projetos.TAREFA_A_FAZER, pct);
                        ProjetoTarefaDO k = ProjetoTarefaDO.builder().projetoId(n.getId()).nome(cortar(Objects.toString(t.get("name"), "Tarefa"), 200))
                                .descricao(texto(t.get("content"))).responsavelId(usuario(t.get("users_id"))).situacao(st)
                                .percentual(Projetos.percentualDaTarefa(st, pct)).inicioPrevisto(ti)
                                .fimPrevisto(ti != null && tf != null && tf.isBefore(ti) ? ti : tf)
                                .inicioReal(dia(t.get("real_start_date"))).fimReal(dia(t.get("real_end_date")))
                                .horasPrevistas(BigDecimal.valueOf(num(t.get("planned_duration")) / 3600.0).setScale(2, java.math.RoundingMode.HALF_UP))
                                .horasGastas(BigDecimal.valueOf(num(t.get("effective_duration")) / 3600.0).setScale(2, java.math.RoundingMode.HALF_UP))
                                .marco(num(t.get("is_milestone")) == 1).ordem(++ordem).build();
                        projetoTarefaMapper.insert(k);
                        tarefaPara.put(num(t.get("id")), k.getId());
                    }
                    for (Map<String, Object> t : ts) {   // tarefas superiores, depois que todas existem
                        Long pai = tarefaPara.get(num(t.get("projecttasks_id")));
                        if (pai != null) {
                            projetoTarefaMapper.updateById(ProjetoTarefaDO.builder().id(tarefaPara.get(num(t.get("id")))).tarefaPaiId(pai).build());
                        }
                    }
                    Set<Long> membros = new HashSet<>();
                    for (Map<String, Object> m : equipe.getOrDefault(num(p.get("id")), List.of())) {
                        Long uid = usuario(m.get("items_id"));
                        if (uid != null && membros.add(uid)) {
                            projetoMembroMapper.insert(ProjetoMembroDO.builder().projetoId(n.getId()).userId(uid).build());
                        }
                    }
                    for (Map<String, Object> k : custos.getOrDefault(num(p.get("id")), List.of())) {
                        custo("projeto", n.getId(), k, Optional.ofNullable(iniPrev).orElse(LocalDate.now()));
                    }
                    for (Map<String, Object> i : itens.getOrDefault(num(p.get("id")), List.of())) {
                        String tipo = switch (Objects.toString(i.get("itemtype"), "")) {
                            case "Ticket" -> "chamado";
                            case "Problem" -> "problema";
                            case "Change" -> "mudanca";
                            default -> null;
                        };
                        Long alvo = tipo == null ? null : destino(tipo, i.get("items_id"));
                        if (alvo != null) {
                            projetoVinculoMapper.insert(ProjetoVinculoDO.builder().projetoId(n.getId()).objeto(tipo).objetoId(alvo).build());
                        }
                    }
                    mapear("projeto", p.get("id"), n.getId());
                    importado();
                });
            }
        }
    }

    // =====================================================================================================
    // conversões de valores lidos pelo JDBC

    private static final java.time.format.DateTimeFormatter HORA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Data e hora para o registro da importação. */
    static String agora() {
        return HORA.format(LocalDateTime.now());
    }

    static long num(Object v) {
        return v == null ? 0 : v instanceof Number n ? n.longValue() : v instanceof Boolean b ? (b ? 1 : 0) : Long.parseLong(String.valueOf(v).trim());
    }

    private static Integer int0(Object v) {
        return v == null ? null : (int) num(v);
    }

    private static Long lng(Object v) {
        return v == null ? 0L : Math.max(0, num(v));
    }

    private static Long positivo(Object v) {
        long n = v == null ? 0 : num(v);
        return n > 0 ? n : null;
    }

    private static Double dbl(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private static LocalDateTime data(Object... candidatas) {
        for (Object v : candidatas) {
            if (v instanceof LocalDateTime d) {
                return d;
            }
            if (v instanceof LocalDate d) {
                return d.atStartOfDay();
            }
        }
        return null;
    }

    private static LocalDate dia(Object v) {
        return v instanceof LocalDate d ? d : v instanceof LocalDateTime t ? t.toLocalDate() : null;
    }

    private static String titulo(Object v) {
        String s = v == null ? "" : HtmlSeguro.texto(GlpiConverte.html(String.valueOf(v))).trim();
        return s.isEmpty() ? "(sem título)" : cortar(s, 255);
    }

    private static String textoPuro(Object v) {
        return v == null ? null : vazio(HtmlSeguro.texto(GlpiConverte.html(String.valueOf(v))));
    }

    static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
