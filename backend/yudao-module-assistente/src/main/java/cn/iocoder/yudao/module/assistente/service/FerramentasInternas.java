package cn.iocoder.yudao.module.assistente.service;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AlertaVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.AtivoVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.service.AlertaInventarioService;
import cn.iocoder.yudao.module.inventario.service.AtivoService;
import cn.iocoder.yudao.module.inventario.service.LicencaInventarioService;
import cn.iocoder.yudao.module.inventario.service.RedeService;
import cn.iocoder.yudao.module.inventario.service.PacoteService;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.PacoteVOs;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.RedeVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.service.ConversaService;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.GovernancaVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ItilVOs;
import cn.iocoder.yudao.module.itsm.service.MudancaService;
import cn.iocoder.yudao.module.itsm.service.ProblemaService;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.KbVOs;
import cn.iocoder.yudao.module.itsm.service.ChamadoService;
import cn.iocoder.yudao.module.itsm.service.KbService;
import cn.iocoder.yudao.module.itsm.service.gov.LicencaService;
import cn.iocoder.yudao.module.system.controller.admin.monitor.vo.MonitorModuloVOs;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.monitor.fonte.MonitorFontes;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ferramentas de consulta do assistente interno. Todas são só de leitura e rodam na requisição de quem pergunta: as
 * permissões e o escopo de unidade do perfil valem como nas telas. Uma ferramenta só é oferecida ao modelo se a
 * pessoa tiver a permissão dela, e a permissão é conferida de novo na execução.
 */
@Slf4j
@Component
public class FerramentasInternas {

    /** Tamanho máximo do resultado de uma ferramenta devolvido ao modelo */
    static final int MAXIMO_RESULTADO = 14_000;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Resource
    private PermissionCommonApi permissionApi;
    @Resource
    private AtivoService ativoService;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private AlertaInventarioService alertaInventarioService;
    @Resource
    private LicencaInventarioService licencaInventarioService;
    @Resource
    private LicencaService licencaService;
    @Resource
    private RedeService redeService;
    @Resource
    private PacoteService pacoteService;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private KbService kbService;
    @Resource
    private ProblemaService problemaService;
    @Resource
    private MudancaService mudancaService;
    @Resource
    private ConversaService conversaService;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private MonitorFontes.Links fonteLinks;
    @Resource
    private MonitorFontes.Logs fonteLogs;

    /** Uma ferramenta: nome, o que faz, parâmetros (JSON Schema), permissões (qualquer uma serve; vazio = todos). */
    private record Ferramenta(String nome, String descricao, Map<String, Object> parametros, String[] permissoes,
                              Function<Contexto, Object> executar) {
    }

    /** Argumentos do modelo e a pessoa que perguntou. */
    record Contexto(Map<String, Object> args, Long userId) {
        String texto(String k) {
            Object v = args.get(k);
            return v == null || v.toString().isBlank() ? null : v.toString().trim();
        }

        Integer inteiro(String k) {
            Object v = args.get(k);
            if (v instanceof Number n) {
                return n.intValue();
            }
            try {
                return v == null ? null : Integer.valueOf(v.toString().trim().replace("#", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        int limite(int padrao, int maximo) {
            Integer l = inteiro("limite");
            return l == null || l < 1 ? padrao : Math.min(l, maximo);
        }
    }

    private final Map<String, Ferramenta> ferramentas = new LinkedHashMap<>();

    public FerramentasInternas() {
        // ---------------------------------------------------------------- inventário
        registrar("buscar_ativos", "Procura computadores e outros ativos do inventário (CMDB). Use os filtros que a pergunta"
                        + " indicar; todos são opcionais. Devolve o total e os primeiros resultados.",
                props(p("busca", "string", "Parte do nome, patrimônio, série, IP ou login do usuário"),
                        p("tipo", "string", "desktop | notebook | servidor | virtual | impressora | monitor | rede | telefone | outro"),
                        p("sistema", "string", "Nome do sistema operacional, ou parte dele (ex.: Windows 10)"),
                        p("fabricante", "string", "Fabricante (ex.: Dell)"), p("modelo", "string", "Modelo (ex.: OptiPlex 7090)"),
                        p("antivirus", "string", "ativo | desatualizado | ausente | desconhecido"),
                        p("alerta", "string", "sem_suporte | antivirus | sem_contato"),
                        p("situacao", "string", "em_uso | estoque | manutencao | baixado"),
                        p("unidade", "string", "Nome (ou parte) da unidade/departamento"),
                        p("pessoa", "string", "Nome ou login da pessoa que usa a máquina"),
                        p("software", "string", "Nome de um software instalado"),
                        p("memoria_min_gb", "integer", "Memória mínima em GB"), p("memoria_max_gb", "integer", "Memória máxima em GB"),
                        p("limite", "integer", "Quantos resultados (padrão 20, máximo 50)")),
                new String[]{"inventario:ativo:query"}, this::buscarAtivos);
        registrar("detalhar_ativo", "Detalhe de um ativo: hardware, peças, softwares instalados, alertas abertos e chamados ligados.",
                props(p("id", "integer", "Número do ativo (de buscar_ativos)"), p("nome", "string", "Nome da máquina, se não souber o número")),
                new String[]{"inventario:ativo:query"}, this::detalharAtivo);
        registrar("resumo_inventario", "Quantidades do inventário agrupadas por um campo, mais os totais de alertas.",
                props(p("agrupar_por", "string", "sistema | fabricante | modelo | tipo | antivirus | situacao | unidade | origem")),
                new String[]{"inventario:ativo:query"}, this::resumoInventario);
        registrar("buscar_softwares", "Catálogo de softwares encontrados nas máquinas, com quantas instalações e as licenças ligadas.",
                props(p("busca", "string", "Parte do nome do software ou da editora"), p("limite", "integer", "Padrão 20, máximo 50")),
                new String[]{"inventario:ativo:query"}, this::buscarSoftwares);
        registrar("alertas_inventario", "Alertas do inventário (sistema sem suporte, antivírus, disco cheio, sem comunicação, licença excedida).",
                props(p("regra", "string", "sem_suporte | antivirus | disco_cheio | sem_contato | licenca_excedida"),
                        p("situacao", "string", "abertos (padrão) | encerrados | todos"), p("limite", "integer", "Padrão 20, máximo 50")),
                new String[]{"inventario:ativo:query"}, this::alertas);
        registrar("licencas", "Licenças de software (ITSM) com quantidade comprada, usos registrados e instalações encontradas pelo inventário.",
                props(p("busca", "string", "Software, edição ou fabricante"), p("limite", "integer", "Padrão 20, máximo 50")),
                new String[]{"itsm:licenca:query"}, this::licencas);
        registrar("rede_descoberta", "Equipamentos encontrados na varredura da rede (IPDiscover): sub-redes e o que não está cadastrado.",
                props(p("subrede", "string", "Sub-rede (ex.: 10.20.5.0); vazio = resumo de todas"),
                        p("situacao", "string", "desconhecidos (padrão) | conhecidos | ignorados | todos"),
                        p("busca", "string", "IP, MAC ou nome"), p("limite", "integer", "Padrão 30, máximo 80")),
                new String[]{"inventario:ativo:query"}, this::rede);
        registrar("pacotes", "Pacotes de software implantados pelos agentes OCS e a situação em cada máquina.",
                props(p("pacote", "string", "Parte do nome do pacote; vazio = todos, só com as contagens"),
                        p("situacao", "string", "aguardando | notificado | sucesso | erro | cancelado"),
                        p("limite", "integer", "Padrão 30, máximo 80")),
                new String[]{"inventario:pacote:query"}, this::pacotes);
        // ---------------------------------------------------------------- Service Desk
        registrar("buscar_chamados", "Procura chamados do Service Desk.",
                props(p("busca", "string", "Número (#123) ou parte do título"),
                        p("fila", "string", "abertos (padrão) | novos | pendentes | sla (prazo vencido) | solucionados | fechados | todos"),
                        p("limite", "integer", "Padrão 15, máximo 40")),
                new String[]{"itsm:chamado:query"}, this::buscarChamados);
        registrar("painel_service_desk", "Quantidades da fila do Service Desk (abertos, novos, pendentes, SLA vencido...).",
                props(), new String[]{"itsm:chamado:query"}, c -> chamadoService.contagens());
        registrar("detalhar_chamado", "Detalhe de um chamado e o andamento (acompanhamentos, tarefas, solução).",
                props(p("numero", "integer", "Número do chamado")), new String[0], this::detalharChamado);
        registrar("meus_chamados", "Chamados em que a pessoa que pergunta é solicitante ou observadora.",
                props(p("somente_abertos", "boolean", "Padrão true")), new String[0], this::meusChamados);
        registrar("buscar_base_conhecimento", "Procura artigos da base de conhecimento e devolve o texto dos mais relevantes.",
                props(p("consulta", "string", "Assunto ou palavras-chave")), new String[0], this::baseConhecimento);
        registrar("buscar_problemas", "Problemas (gestão de problemas ITIL): causa de incidentes repetidos.",
                props(p("busca", "string", "Número ou parte do título"), p("fila", "string", "abertos (padrão) | encerrados | todos"),
                        p("limite", "integer", "Padrão 15, máximo 40")),
                new String[]{"itsm:problema:query"}, c -> itil(c, true));
        registrar("buscar_mudancas", "Mudanças (gestão de mudanças ITIL): o que vai ser alterado no ambiente e quando.",
                props(p("busca", "string", "Número ou parte do título"),
                        p("fila", "string", "abertos (padrão) | aprovacao (aguardando aprovação) | agenda | encerrados | todos"),
                        p("limite", "integer", "Padrão 15, máximo 40")),
                new String[]{"itsm:mudanca:query"}, c -> itil(c, false));
        registrar("buscar_conversas", "Conversas do atendimento (WhatsApp, Telegram, Instagram, e-mail, widget).",
                props(p("busca", "string", "Nome ou telefone do contato, ou texto"),
                        p("situacao", "string", "abertas (padrão) | pendentes | resolvidas"),
                        p("limite", "integer", "Padrão 15, máximo 40")),
                new String[]{"atendimento:conversa:query"}, this::conversas);
        // ---------------------------------------------------------------- pessoas e Centro de Operações
        registrar("buscar_pessoas", "Procura pessoas cadastradas (nome, login, e-mail, unidade, celular) e quantos ativos usam.",
                props(p("busca", "string", "Nome, login ou e-mail"), p("limite", "integer", "Padrão 10, máximo 30")),
                new String[]{"system:user:query"}, this::buscarPessoas);
        registrar("status_links", "Links e serviços monitorados no Centro de Operações: estado, latência e disponibilidade.",
                props(p("filtro", "string", "todos (padrão) | down | lento | ou parte do nome"), p("limite", "integer", "Padrão 30, máximo 80")),
                new String[]{"system:monitor-links:query"}, this::links);
        registrar("buscar_logs", "Eventos de log centralizados (falhas de logon, bloqueios, erros).",
                props(p("busca", "string", "Texto a procurar"), p("nivel", "string", "todos | info | aviso | erro | critico"),
                        p("limite", "integer", "Padrão 30, máximo 80")),
                new String[]{"system:monitor-logs:query"}, this::logs);
    }

    // ======================================================================================= catálogo

    /** Ferramentas que esta pessoa pode usar, no formato "tools" da API compatível com OpenAI. */
    public List<Map<String, Object>> definicoes(Long userId) {
        List<Map<String, Object>> l = new ArrayList<>();
        for (Ferramenta f : ferramentas.values()) {
            if (permitido(f, userId)) {
                l.add(Map.of("type", "function", "function", Map.of("name", f.nome(), "description", f.descricao(), "parameters", f.parametros())));
            }
        }
        return l;
    }

    /** Executa e devolve o resultado em JSON (o modelo lê). Erros viram {"erro": ...}. */
    public String executar(String nome, Map<String, Object> args, Long userId) {
        Ferramenta f = ferramentas.get(nome);
        if (f == null) {
            return JsonUtils.toJsonString(Map.of("erro", "ferramenta desconhecida: " + nome));
        }
        if (!permitido(f, userId)) {
            return JsonUtils.toJsonString(Map.of("erro", "a pessoa não tem permissão para esta consulta"));
        }
        try {
            String json = JsonUtils.toJsonString(f.executar().apply(new Contexto(args == null ? Map.of() : args, userId)));
            return json.length() > MAXIMO_RESULTADO ? json.substring(0, MAXIMO_RESULTADO) + "… (resultado cortado; refine a busca)" : json;
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            return JsonUtils.toJsonString(Map.of("erro", e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("[assistente] ferramenta {} falhou: {}", nome, e.getMessage());
            return JsonUtils.toJsonString(Map.of("erro", "falha na consulta"));
        }
    }

    private boolean permitido(Ferramenta f, Long userId) {
        return f.permissoes().length == 0 || permissionApi.hasAnyPermissions(userId, f.permissoes());
    }

    private void registrar(String nome, String descricao, Map<String, Object> parametros, String[] permissoes, Function<Contexto, Object> fn) {
        ferramentas.put(nome, new Ferramenta(nome, descricao, parametros, permissoes, fn));
    }

    @SafeVarargs
    private static Map<String, Object> props(Map.Entry<String, Object>... ps) {
        Map<String, Object> propriedades = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : ps) {
            propriedades.put(e.getKey(), e.getValue());
        }
        return Map.of("type", "object", "properties", propriedades);
    }

    private static Map.Entry<String, Object> p(String nome, String tipo, String descricao) {
        return Map.entry(nome, Map.of("type", tipo, "description", descricao));
    }

    // ======================================================================================= inventário

    private Object buscarAtivos(Contexto c) {
        AtivoVOs.PaginaReq req = new AtivoVOs.PaginaReq();
        req.setPageNo(1);
        req.setPageSize(c.limite(20, 50));
        req.setBusca(c.texto("busca"));
        req.setTipo(c.texto("tipo"));
        req.setFabricante(c.texto("fabricante"));
        req.setModelo(c.texto("modelo"));
        req.setAntivirus(c.texto("antivirus"));
        req.setAlerta(c.texto("alerta"));
        req.setSituacao(c.texto("situacao"));
        req.setMemoriaMinGb(c.inteiro("memoria_min_gb"));
        req.setMemoriaMaxGb(c.inteiro("memoria_max_gb"));
        Map<String, Object> obs = new LinkedHashMap<>();
        String sistema = c.texto("sistema");
        if (sistema != null) {
            // o modelo costuma dar só parte do nome ("Windows 10"): escolhe os nomes existentes que contêm o texto
            List<String> nomes = ativoService.opcoes().getSistemas().stream().map(m -> String.valueOf(m.get("valor")))
                    .filter(v -> semAcento(v).contains(semAcento(sistema))).toList();
            if (nomes.isEmpty()) {
                return Map.of("total", 0, "observacao", "nenhum sistema operacional com '" + sistema + "' no inventário");
            }
            if (nomes.size() == 1) {
                req.setSistema(nomes.get(0));
            } else {
                return porVariosSistemas(req, nomes);
            }
        }
        String unidade = c.texto("unidade");
        if (unidade != null) {
            List<DeptDO> d = deptMapper.selectList(new LambdaQueryWrapperX<DeptDO>().likeIfPresent(DeptDO::getName, unidade));
            if (d.isEmpty()) {
                return Map.of("total", 0, "observacao", "nenhuma unidade com '" + unidade + "'");
            }
            req.setDeptId(d.get(0).getId());
            obs.put("unidade", d.get(0).getName() + (d.size() > 1 ? " (havia " + d.size() + " unidades parecidas; usei a primeira)" : ""));
        }
        String pessoa = c.texto("pessoa");
        if (pessoa != null) {
            AdminUserDO u = pessoas(pessoa, 1).stream().findFirst().orElse(null);
            if (u == null) {
                return Map.of("total", 0, "observacao", "nenhuma pessoa com '" + pessoa + "'");
            }
            req.setUsuarioId(u.getId());
            obs.put("pessoa", u.getNickname() + " (" + u.getUsername() + ")");
        }
        String software = c.texto("software");
        if (software != null) {
            PageParam pg = new PageParam();
            pg.setPageNo(1);
            pg.setPageSize(1);
            List<AtivoVOs.SoftwareResp> s = ativoService.softwares(software, pg).getList();
            if (s.isEmpty()) {
                return Map.of("total", 0, "observacao", "nenhum software com '" + software + "' no catálogo");
            }
            req.setSoftwareId(s.get(0).getId());
            obs.put("software", s.get(0).getNome());
        }
        PageResult<AtivoVOs.Resp> p = ativoService.pagina(req);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", p.getTotal());
        if (!obs.isEmpty()) {
            r.put("filtros_resolvidos", obs);
        }
        r.put("ativos", p.getList().stream().map(FerramentasInternas::ativoResumo).toList());
        return r;
    }

    /** "Windows 10" casa com vários nomes (Pro, Enterprise...): soma as buscas. */
    private Object porVariosSistemas(AtivoVOs.PaginaReq req, List<String> nomes) {
        long total = 0;
        List<Map<String, Object>> ativos = new ArrayList<>();
        Map<String, Long> porSistema = new LinkedHashMap<>();
        for (String n : nomes) {
            req.setSistema(n);
            PageResult<AtivoVOs.Resp> p = ativoService.pagina(req);
            total += p.getTotal();
            porSistema.put(n, p.getTotal());
            p.getList().stream().limit(Math.max(0, req.getPageSize() - ativos.size())).forEach(a -> ativos.add(ativoResumo(a)));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("por_sistema", porSistema);
        r.put("ativos", ativos);
        return r;
    }

    private static Map<String, Object> ativoResumo(AtivoVOs.Resp a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("nome", a.getNome());
        m.put("tipo", a.getTipo());
        m.put("situacao", a.getSituacao());
        putSe(m, "patrimonio", a.getPatrimonio());
        putSe(m, "sistema", a.getSoNome());
        if (Boolean.TRUE.equals(a.getSoSemSuporte())) {
            m.put("sistema_sem_suporte", true);
        }
        putSe(m, "fabricante_modelo", juntar(a.getFabricante(), a.getModelo()));
        putSe(m, "memoria_gb", a.getMemoriaMb() == null ? null : Math.round(a.getMemoriaMb() / 1024.0));
        if (a.getDiscoTotalMb() != null && a.getDiscoTotalMb() > 0 && a.getDiscoLivreMb() != null) {
            m.put("disco", Math.round(a.getDiscoTotalMb() / 1024.0) + " GB, " + Math.round(100.0 * a.getDiscoLivreMb() / a.getDiscoTotalMb()) + "% livre");
        }
        putSe(m, "pessoa", a.getUsuarioNome() != null ? a.getUsuarioNome() : a.getUsuarioLogin());
        putSe(m, "unidade", a.getDeptNome());
        putSe(m, "ip", a.getIp());
        putSe(m, "paginas_impressas", a.getPaginas());
        putSe(m, "localizacao", a.getLocalizacao());
        if (Set.of("ocs", "glpi_agent").contains(a.getOrigem())) {
            m.put("antivirus", a.getAntivirus());
            putSe(m, "ultimo_contato", quando(a.getUltimoContato()));
            if (Boolean.TRUE.equals(a.getSemContato())) {
                m.put("sem_comunicacao", true);
            }
        }
        return m;
    }

    private Object detalharAtivo(Contexto c) {
        Integer id = c.inteiro("id");
        Long ativoId = id == null ? null : id.longValue();
        if (ativoId == null && c.texto("nome") != null) {
            List<AtivoDO> l = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().apply("lower(nome) = {0}", c.texto("nome").toLowerCase(Locale.ROOT)));
            if (l.isEmpty()) {
                l = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().likeIfPresent(AtivoDO::getNome, c.texto("nome")).last("LIMIT 5"));
            }
            if (l.isEmpty()) {
                return Map.of("erro", "nenhum ativo com esse nome");
            }
            if (l.size() > 1) {
                return Map.of("varios", l.stream().map(a -> Map.of("id", a.getId(), "nome", a.getNome())).toList(),
                        "observacao", "mais de um ativo com esse nome; pergunte qual ou use o id");
            }
            ativoId = l.get(0).getId();
        }
        if (ativoId == null) {
            return Map.of("erro", "informe o id ou o nome do ativo");
        }
        AtivoVOs.Detalhe d = ativoService.detalhe(ativoId);
        Map<String, Object> r = new LinkedHashMap<>(ativoResumo(d.getAtivo()));
        putSe(r, "numero_serie", d.getAtivo().getNumeroSerie());
        putSe(r, "processador", d.getAtivo().getCpu());
        putSe(r, "mac", d.getAtivo().getMac());
        putSe(r, "dominio", d.getAtivo().getDominio());
        putSe(r, "primeiro_inventario", quando(d.getAtivo().getPrimeiroInventario()));
        putSe(r, "observacao", d.getAtivo().getObservacao());
        r.put("pecas", d.getComponentes().stream().collect(Collectors.groupingBy(AtivoVOs.Componente::getTipo, LinkedHashMap::new,
                Collectors.mapping(AtivoVOs.Componente::getDescricao, Collectors.toList()))));
        r.put("softwares_total", d.getSoftwares().size());
        r.put("softwares", d.getSoftwares().stream().limit(120).map(s -> s.getVersao() == null || s.getVersao().isEmpty()
                ? s.getNome() : s.getNome() + " " + s.getVersao()).toList());
        r.put("alertas_abertos", alertaInventarioService.doAtivo(ativoId).stream().map(AlertaVOs.Resp::getTitulo).toList());
        r.put("chamados", d.getChamados().stream().map(x -> Map.of("numero", x.getChamadoId(), "titulo", x.getTitulo())).toList());
        return r;
    }

    private Object resumoInventario(Contexto c) {
        String campo = Optional.ofNullable(c.texto("agrupar_por")).orElse("tipo");
        Function<AtivoDO, Object> chave = switch (campo) {
            case "sistema" -> AtivoDO::getSoNome;
            case "fabricante" -> AtivoDO::getFabricante;
            case "modelo" -> AtivoDO::getModelo;
            case "antivirus" -> AtivoDO::getAntivirus;
            case "situacao" -> AtivoDO::getSituacao;
            case "unidade" -> AtivoDO::getDeptId;
            case "origem" -> AtivoDO::getOrigem;
            default -> AtivoDO::getTipo;
        };
        // a consulta passa pelo escopo de unidade de quem pergunta
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(new LambdaQueryWrapperX<AtivoDO>().ne(AtivoDO::getSituacao, "baixado"));
        Map<Object, Long> grupos = ativos.stream().collect(Collectors.groupingBy(a -> Optional.ofNullable(chave.apply(a)).orElse("(sem informação)"),
                Collectors.counting()));
        Map<String, String> nomesDept = new HashMap<>();
        if ("unidade".equals(campo)) {
            Set<Long> ids = grupos.keySet().stream().filter(k -> k instanceof Long).map(k -> (Long) k).collect(Collectors.toSet());
            if (!ids.isEmpty()) {
                deptMapper.selectByIds(ids).forEach(d -> nomesDept.put(String.valueOf(d.getId()), d.getName()));
            }
        }
        Map<String, Long> ordenado = new LinkedHashMap<>();
        grupos.entrySet().stream().sorted(Map.Entry.<Object, Long>comparingByValue().reversed()).limit(60)
                .forEach(e -> ordenado.put(nomesDept.getOrDefault(String.valueOf(e.getKey()), String.valueOf(e.getKey())), e.getValue()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total_nao_baixados", ativos.size());
        r.put("agrupado_por", campo);
        r.put("grupos", ordenado);
        r.put("alertas", ativoService.contagens());
        return r;
    }

    private Object buscarSoftwares(Contexto c) {
        PageParam pg = new PageParam();
        pg.setPageNo(1);
        pg.setPageSize(c.limite(20, 50));
        PageResult<AtivoVOs.SoftwareResp> p = ativoService.softwares(c.texto("busca"), pg);
        Map<Long, List<String>> licencas = licencaInventarioService.licencasDosSoftwares(p.getList().stream().map(AtivoVOs.SoftwareResp::getId).toList());
        return Map.of("total", p.getTotal(), "softwares", p.getList().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", s.getNome());
            putSe(m, "editora", s.getEditora());
            m.put("maquinas", s.getInstalacoes());
            m.put("versoes", s.getVersoes());
            putSe(m, "licencas", licencas.get(s.getId()));
            return m;
        }).toList());
    }

    private Object alertas(Contexto c) {
        AlertaVOs.PaginaReq req = new AlertaVOs.PaginaReq();
        req.setPageNo(1);
        req.setPageSize(c.limite(20, 50));
        req.setRegra(c.texto("regra"));
        req.setSituacao(c.texto("situacao"));
        PageResult<AlertaVOs.Resp> p = alertaInventarioService.pagina(req);
        return Map.of("total", p.getTotal(), "alertas", p.getList().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("regra", a.getRegraNome());
            m.put("titulo", a.getTitulo());
            putSe(m, "detalhe", a.getDetalhe());
            putSe(m, "unidade", a.getDeptNome());
            m.put("desde", quando(a.getInicio()));
            putSe(m, "encerrado_em", quando(a.getFim()));
            putSe(m, "chamado", a.getChamadoId());
            return m;
        }).toList());
    }

    private Object licencas(Contexto c) {
        GovernancaVOs.Filtro f = new GovernancaVOs.Filtro();
        f.setPageNo(1);
        f.setPageSize(c.limite(20, 50));
        f.setBusca(c.texto("busca"));
        PageResult<GovernancaVOs.Licenca> p = licencaService.pagina(f);
        Map<Long, Long> instalacoes = licencaInventarioService.instalacoesPorLicenca();
        return Map.of("total", p.getTotal(), "licencas", p.getList().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("software", juntar(l.getSoftware(), l.getNome()));
            putSe(m, "tipo", l.getTipo());
            m.put("compradas", l.getQuantidade() != null && l.getQuantidade() < 0 ? "ilimitada" : l.getQuantidade());
            m.put("usos_registrados", l.getEmUso());
            m.put("instalacoes_encontradas", instalacoes.getOrDefault(l.getId(), 0L));
            putSe(m, "expira", l.getExpira() == null ? null : l.getExpira().toString());
            m.put("situacao", l.getSituacaoRotulo());
            return m;
        }).toList());
    }

    private Object rede(Contexto c) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("subredes", redeService.subredes().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("subrede", x.getSubrede());
            putSe(m, "mascara", x.getMascara());
            m.put("equipamentos", x.getDispositivos());
            m.put("desconhecidos", x.getDesconhecidos());
            putSe(m, "varrida_por", x.getVarredores());
            putSe(m, "ultima_varredura", quando(x.getUltimaVarredura()));
            return m;
        }).toList());
        if (c.texto("subrede") != null || c.texto("busca") != null || c.texto("situacao") != null) {
            RedeVOs.PaginaReq req = new RedeVOs.PaginaReq();
            req.setPageNo(1);
            req.setPageSize(c.limite(30, 80));
            req.setSubrede(c.texto("subrede"));
            req.setSituacao(c.texto("situacao"));
            req.setBusca(c.texto("busca"));
            PageResult<RedeVOs.Dispositivo> p = redeService.pagina(req);
            r.put("total", p.getTotal());
            r.put("equipamentos", p.getList().stream().map(d -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("ip", d.getIp());
                m.put("mac", d.getMac());
                putSe(m, "nome", d.getNome());
                putSe(m, "ativo", d.getAtivoNome());
                if (Boolean.TRUE.equals(d.getIgnorado())) {
                    m.put("ignorado", true);
                }
                putSe(m, "observacao", d.getObservacao());
                m.put("ultima_vez", quando(d.getUltimaVez()));
                return m;
            }).toList());
        }
        return r;
    }

    private Object pacotes(Contexto c) {
        String nome = c.texto("pacote");
        List<PacoteVOs.Pacote> l = pacoteService.listar().stream()
                .filter(x -> nome == null || semAcento(x.getNome()).contains(semAcento(nome))).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pacotes", l.stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", x.getNome());
            m.put("acao", x.getAcao());
            putSe(m, "comando", x.getComando());
            m.put("plataforma", x.getPlataforma());
            m.put("ativo", x.getAtivo());
            m.put("maquinas_por_situacao", x.getContagens());
            return m;
        }).toList());
        if (nome != null && l.size() == 1) {
            PacoteVOs.ImplantacoesReq req = new PacoteVOs.ImplantacoesReq();
            req.setPageNo(1);
            req.setPageSize(c.limite(30, 80));
            req.setPacoteId(l.get(0).getId());
            req.setSituacao(c.texto("situacao"));
            r.put("maquinas", pacoteService.implantacoes(req).getList().stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("maquina", i.getAtivoNome());
                m.put("situacao", i.getSituacao());
                putSe(m, "resposta_do_agente", i.getCodigo());
                putSe(m, "concluido_em", quando(i.getConcluidoEm()));
                return m;
            }).toList());
        }
        return r;
    }

    // ======================================================================================= Service Desk

    private Object buscarChamados(Contexto c) {
        ChamadoVOs.PaginaReq req = new ChamadoVOs.PaginaReq();
        req.setPageNo(1);
        req.setPageSize(c.limite(15, 40));
        req.setBusca(c.texto("busca"));
        if (c.texto("fila") != null) {
            req.setFila(c.texto("fila"));
        }
        PageResult<ChamadoVOs.Resp> p = chamadoService.pagina(req, c.userId());
        return Map.of("total", p.getTotal(), "chamados", p.getList().stream().map(FerramentasInternas::chamadoResumo).toList());
    }

    private static Map<String, Object> chamadoResumo(ChamadoVOs.Resp x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero", x.getId());
        m.put("titulo", x.getTitulo());
        m.put("situacao", x.getStatusRotulo());
        putSe(m, "prioridade", x.getPrioridadeRotulo());
        putSe(m, "categoria", x.getCategoria());
        putSe(m, "unidade", x.getDept());
        m.put("aberto_em", quando(x.getAbertoEm()));
        putSe(m, "prazo_solucao", quando(x.getPrazoSolucao()));
        if (Boolean.TRUE.equals(x.getSlaVencido())) {
            m.put("sla_vencido", true);
        }
        m.put("solicitantes", x.getSolicitantes().stream().map(ChamadoVOs.Ator::getNome).toList());
        m.put("tecnicos", x.getTecnicos().stream().map(ChamadoVOs.Ator::getNome).toList());
        return m;
    }

    private Object detalharChamado(Contexto c) {
        Integer n = c.inteiro("numero");
        if (n == null) {
            return Map.of("erro", "informe o número do chamado");
        }
        Long id = n.longValue();
        // equipe técnica vê pelo escopo; os outros, só se participam do chamado
        ChamadoVOs.Resp x = permissionApi.hasAnyPermissions(c.userId(), "itsm:chamado:query")
                ? chamadoService.obter(id, c.userId()) : chamadoService.obterComoParticipante(id, c.userId());
        Map<String, Object> r = new LinkedHashMap<>(chamadoResumo(x));
        r.put("descricao", texto(x.getDescricao(), 2000));
        r.put("andamento", chamadoService.linhaDoTempo(id, c.userId()).stream().limit(30).map(i -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", i.getTipo());
            putSe(m, "autor", i.getAutor());
            m.put("quando", quando(i.getCriadoEm()));
            if (Boolean.TRUE.equals(i.getPrivado())) {
                m.put("privado", true);
            }
            m.put("texto", texto(i.getConteudo(), 600));
            return m;
        }).toList());
        return r;
    }

    private Object meusChamados(Contexto c) {
        PageParam pg = new PageParam();
        pg.setPageNo(1);
        pg.setPageSize(15);
        boolean abertos = !Boolean.FALSE.equals(c.args().get("somente_abertos")) && !"false".equals(String.valueOf(c.args().get("somente_abertos")));
        PageResult<ChamadoVOs.Resp> p = chamadoService.meus(pg, c.userId(), abertos);
        return Map.of("total", p.getTotal(), "chamados", p.getList().stream().map(FerramentasInternas::chamadoResumo).toList());
    }

    private Object baseConhecimento(Contexto c) {
        String consulta = c.texto("consulta");
        if (consulta == null) {
            return Map.of("erro", "informe o assunto");
        }
        KbVOs.BuscaReq req = new KbVOs.BuscaReq();
        req.setQ(consulta);
        req.setPageSize(4);
        List<KbVOs.ArtigoResumo> l = kbService.buscar(req, c.userId(), false).getList();
        if (l.isEmpty()) {
            l = kbService.sugestoes(consulta, c.userId());
        }
        List<Map<String, Object>> artigos = new ArrayList<>();
        int i = 0;
        for (KbVOs.ArtigoResumo a : l) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("titulo", a.getTitulo());
            putSe(m, "categoria", a.getCategoria());
            if (i++ < 2) {
                m.put("texto", texto(kbService.ler(a.getId(), c.userId(), false).getConteudo(), 3500));
            } else {
                putSe(m, "resumo", a.getResumo());
            }
            artigos.add(m);
        }
        return Map.of("artigos", artigos);
    }

    private Object itil(Contexto c, boolean problema) {
        ItilVOs.PaginaReq req = new ItilVOs.PaginaReq();
        req.setPageNo(1);
        req.setPageSize(c.limite(15, 40));
        req.setBusca(c.texto("busca"));
        if (c.texto("fila") != null) {
            req.setFila(c.texto("fila"));
        }
        PageResult<ItilVOs.Resp> p = problema ? problemaService.pagina(req, c.userId()) : mudancaService.pagina(req, c.userId());
        return Map.of("total", p.getTotal(), problema ? "problemas" : "mudancas", p.getList().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", x.getId());
            m.put("titulo", x.getTitulo());
            m.put("situacao", x.getStatusRotulo());
            putSe(m, "tipo", x.getTipoRotulo());
            putSe(m, "prioridade", x.getPrioridadeRotulo());
            putSe(m, "unidade", x.getDept());
            m.put("aberto_em", quando(x.getAbertoEm()));
            putSe(m, "inicio_previsto", quando(x.getInicioPrevisto()));
            if (Boolean.TRUE.equals(x.getAtrasado())) {
                m.put("atrasado", true);
            }
            putSe(m, "chamados_ligados", x.getTotalChamados());
            putSe(m, "causas", x.getCausas() == null ? null : texto(x.getCausas(), 400));
            return m;
        }).toList());
    }

    private Object conversas(Contexto c) {
        AtendimentoVOs.FiltroConversa f = new AtendimentoVOs.FiltroConversa();
        f.setPageNo(1);
        f.setPageSize(c.limite(15, 40));
        f.setAba("todas");
        f.setBusca(c.texto("busca"));
        f.setStatus(switch (Optional.ofNullable(c.texto("situacao")).orElse("abertas")) {
            case "resolvidas" -> 1;
            case "pendentes" -> 2;
            default -> 0;
        });
        PageResult<AtendimentoVOs.ConversaResumo> p = conversaService.pagina(f, c.userId());
        return Map.of("total", p.getTotal(), "conversas", p.getList().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", x.getId());
            m.put("contato", x.getContato());
            putSe(m, "caixa", x.getCaixa());
            m.put("situacao", x.getStatusRotulo());
            putSe(m, "atendente", x.getAgente());
            putSe(m, "chamado", x.getChamadoId());
            putSe(m, "ultima_atividade", quando(x.getUltimaAtividadeEm()));
            putSe(m, "ultima_mensagem", x.getUltimaMensagem() == null ? null : texto(x.getUltimaMensagem(), 200));
            putSe(m, "nao_lidas", x.getNaoLidas());
            return m;
        }).toList());
    }

    // ======================================================================================= pessoas e operações

    private List<AdminUserDO> pessoas(String busca, int limite) {
        String b = busca.trim();
        return adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .and(w -> w.apply("nickname ILIKE {0}", "%" + b + "%").or().apply("username ILIKE {0}", "%" + b + "%")
                        .or().apply("email ILIKE {0}", "%" + b + "%"))
                .orderByAsc(AdminUserDO::getNickname).last("LIMIT " + limite));
    }

    private Object buscarPessoas(Contexto c) {
        String busca = c.texto("busca");
        if (busca == null) {
            return Map.of("erro", "informe nome, login ou e-mail");
        }
        List<AdminUserDO> l = pessoas(busca, c.limite(10, 30));
        Set<Long> depts = l.stream().map(AdminUserDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = depts.isEmpty() ? Map.of() : deptMapper.selectByIds(depts).stream()
                .collect(Collectors.toMap(DeptDO::getId, DeptDO::getName));
        boolean veAtivos = permissionApi.hasAnyPermissions(c.userId(), "inventario:ativo:query");
        return Map.of("pessoas", l.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", u.getNickname());
            m.put("login", u.getUsername());
            putSe(m, "email", u.getEmail());
            putSe(m, "celular", u.getMobile());
            putSe(m, "unidade", u.getDeptId() == null ? null : nomes.get(u.getDeptId()));
            m.put("ativa", Integer.valueOf(0).equals(u.getStatus()));
            if (veAtivos) {
                m.put("maquinas", ativoService.doUsuario(u.getId()).stream().map(AtivoVOs.Resp::getNome).toList());
            }
            return m;
        }).toList());
    }

    private Object links(Contexto c) {
        String filtro = Optional.ofNullable(c.texto("filtro")).orElse("todos");
        boolean porNome = !Set.of("todos", "down", "lento").contains(filtro);
        MonitorModuloVOs.ListaVO<MonitorModuloVOs.LinkVO> l = fonteLinks.listarLinks(null, porNome ? "todos" : filtro, 500);
        String q = semAcento(filtro);
        List<Map<String, Object>> links = l.getLista().stream()
                .filter(x -> !porNome || semAcento(x.getNome() + " " + x.getUnidade() + " " + x.getAlvo()).contains(q))
                .limit(c.limite(30, 80)).map(x -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nome", x.getNome());
                    putSe(m, "unidade", x.getUnidade());
                    m.put("estado", x.getEstado());
                    putSe(m, "latencia_ms", x.getLatenciaMs());
                    putSe(m, "uptime_24h", x.getUptime24h());
                    putSe(m, "certificado_dias", x.getCertificadoDias());
                    return m;
                }).toList();
        return Map.of("contagens", l.getContagens(), "resumo", l.getResumo(), "links", links);
    }

    private Object logs(Contexto c) {
        MonitorModuloVOs.ListaVO<MonitorModuloVOs.LogVO> l = fonteLogs.buscarLogs(null, c.texto("nivel"), c.texto("busca"), c.limite(30, 80));
        return Map.of("total", l.getTotal(), "contagens", l.getContagens(), "resumo", l.getResumo(), "eventos", l.getLista().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quando", x.getTs() == null ? null : DATA_HORA.format(Instant.ofEpochMilli(x.getTs()).atZone(ZoneId.systemDefault())));
            m.put("origem", x.getOrigem());
            m.put("nivel", x.getNivel());
            m.put("mensagem", texto(x.getMensagem(), 400));
            return m;
        }).toList());
    }

    // ======================================================================================= apoio

    private static void putSe(Map<String, Object> m, String k, Object v) {
        if (v != null && !(v instanceof String s && s.isBlank()) && !(v instanceof Collection<?> col && col.isEmpty())) {
            m.put(k, v);
        }
    }

    private static String juntar(String a, String b) {
        if (a == null || a.isBlank()) {
            return b;
        }
        return b == null || b.isBlank() ? a : a + " " + b;
    }

    static String quando(LocalDateTime t) {
        return t == null ? null : t.format(DATA_HORA);
    }

    /** HTML do chamado/artigo em texto simples, cortado. */
    static String texto(String html, int maximo) {
        if (html == null) {
            return "";
        }
        String t = org.springframework.web.util.HtmlUtils.htmlUnescape(html.replaceAll("(?i)<br\\s*/?>|</p>|</li>|</div>", "\n")
                .replaceAll("<[^>]+>", "")).replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip();
        return t.length() > maximo ? t.substring(0, maximo) + "…" : t;
    }

    static String semAcento(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

}
