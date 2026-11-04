package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.*;
import cn.iocoder.yudao.module.itsm.dal.mysql.*;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.dominio.CicloChamado;
import cn.iocoder.yudao.module.itsm.dominio.MotorRegras;
import cn.iocoder.yudao.module.itsm.dominio.HtmlSeguro;
import cn.iocoder.yudao.module.itsm.dominio.Prioridade;
import cn.iocoder.yudao.module.itsm.enums.AtorTipo;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;
import cn.iocoder.yudao.module.itsm.enums.ChamadoTipo;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Chamados: consulta, abertura, participantes, linha do tempo e ciclo de vida.
 *
 * <p>Dois tipos de acesso:
 * <ul>
 *   <li>equipe técnica: permissões {@code itsm:chamado:*} e o escopo de unidade do perfil (plugin de
 *       permissão de dados sobre {@code dept_id});</li>
 *   <li>participante (solicitante ou observador): só os chamados em que está, sem itens privados. Esses métodos
 *       desligam o escopo de unidade e conferem a participação explicitamente.</li>
 * </ul>
 */
@Slf4j
@Service
public class ChamadoService {

    public static final String P_QUERY = "itsm:chamado:query";
    public static final String P_CREATE = "itsm:chamado:create";
    public static final String P_UPDATE = "itsm:chamado:update";
    public static final String P_ATRIBUIR = "itsm:chamado:atribuir";
    public static final String P_ATENDER = "itsm:chamado:atender";
    public static final String P_PRIVADO = "itsm:chamado:ver-privado";
    public static final String P_FECHAR = "itsm:chamado:fechar";
    public static final String P_REABRIR = "itsm:chamado:reabrir";
    public static final String P_DELETE = "itsm:chamado:delete";
    public static final String P_PEDIR_APROVACAO = "itsm:chamado:pedir-aprovacao";

    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoAtorMapper atorMapper;
    @Resource
    private InteracaoMapper interacaoMapper;
    @Resource
    private HistoricoMapper historicoMapper;
    @Resource
    private CadastroService cadastros;
    @Resource
    private ChamadoEscopo escopo;
    @Resource
    private SlaService slaService;
    @Resource
    private SatisfacaoMapper satisfacaoMapper;
    @Resource
    private SlaNivelExecucaoMapper nivelExecucaoMapper;
    @Resource
    private SlaMapper slaMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private ItsmProperties props;
    @Resource
    private ApplicationEventPublisher eventos;
    @Resource
    private RegraService regraService;
    @Resource
    private ExecucaoAdMapper execucaoAdMapper;
    @Resource
    @Lazy
    private AprovacaoService aprovacaoService;
    @Resource
    @Lazy
    private ExecucaoAdService execucaoAdService;

    // =====================================================================================================
    // Consulta
    // =====================================================================================================

    public PageResult<ChamadoVOs.Resp> pagina(ChamadoVOs.PaginaReq req, Long userId) {
        PageResult<ChamadoDO> p = chamadoMapper.selectPage(req, userId, cadastros.equipesDoUsuario(userId));
        return new PageResult<>(montar(p.getList(), userId, false), p.getTotal());
    }

    public Map<String, Long> contagens() {
        Map<String, Long> c = chamadoMapper.selectContagens();
        return c == null ? Map.of() : c;
    }

    /** Detalhe para a equipe técnica (respeita o escopo de unidade). */
    public ChamadoVOs.Resp obter(Long id, Long userId) {
        return montar(List.of(chamadoVisivel(id)), userId, true).get(0);
    }

    /** Chamados em que o usuário é solicitante ou observador (autoatendimento). */
    @DataPermission(enable = false)
    public PageResult<ChamadoVOs.Resp> meus(PageParam pagina, Long userId, boolean somenteAbertos) {
        LambdaQueryWrapperX<ChamadoDO> q = new LambdaQueryWrapperX<>();
        q.apply("EXISTS (SELECT 1 FROM itsm.chamado_ator a WHERE a.chamado_id = chamado.id AND a.deleted = 0"
                + " AND a.tipo IN (1, 3) AND a.user_id = {0})", userId);
        if (somenteAbertos) {
            q.lt(ChamadoDO::getStatus, ChamadoStatus.SOLUCIONADO.getCodigo());
        }
        q.orderByDesc(ChamadoDO::getAbertoEm);
        PageResult<ChamadoDO> p = chamadoMapper.selectPage(pagina, q);
        return new PageResult<>(montar(p.getList(), userId, false), p.getTotal());
    }

    /** Detalhe para participante (solicitante/observador) ou técnico. */
    @DataPermission(enable = false)
    public ChamadoVOs.Resp obterComoParticipante(Long id, Long userId) {
        ChamadoDO c = chamadoExistente(id);
        exigirParticipanteOuTecnico(c, userId);
        return montar(List.of(c), userId, true).get(0);
    }

    @DataPermission(enable = false)
    public List<ChamadoVOs.Interacao> linhaDoTempo(Long id, Long userId) {
        ChamadoDO c = chamadoExistente(id);
        boolean tecnico = podeVerComoTecnico(c, userId);
        if (!tecnico) {
            exigirParticipante(c, userId);
        }
        boolean verPrivado = tecnico && tem(userId, P_PRIVADO);
        List<InteracaoDO> itens = interacaoMapper.selectList(new LambdaQueryWrapperX<InteracaoDO>()
                .eq(InteracaoDO::getChamadoId, id)
                .eqIfPresent(InteracaoDO::getPrivado, verPrivado ? null : Boolean.FALSE)
                .orderByAsc(InteracaoDO::getCreateTime).orderByAsc(InteracaoDO::getId));
        Set<Long> pessoas = new HashSet<>();
        itens.forEach(i -> {
            pessoas.add(i.getUserId());
            pessoas.add(i.getTecnicoId());
            pessoas.add(i.getAvaliadoPor());
        });
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        return itens.stream().map(i -> {
            ChamadoVOs.Interacao v = new ChamadoVOs.Interacao();
            v.setId(i.getId());
            v.setTipo(i.getTipo());
            v.setUserId(i.getUserId());
            v.setAutor(nome(nomes, i.getUserId()));
            v.setConteudo(i.getConteudo());
            v.setPrivado(i.getPrivado());
            v.setCriadoEm(i.getCreateTime());
            v.setEstadoTarefa(i.getEstadoTarefa());
            v.setTecnicoId(i.getTecnicoId());
            v.setTecnico(nome(nomes, i.getTecnicoId()));
            v.setInicio(i.getInicio());
            v.setFim(i.getFim());
            v.setTempoGastoSeg(i.getTempoGastoSeg());
            v.setStatusSolucao(i.getStatusSolucao());
            v.setAvaliadoPorNome(nome(nomes, i.getAvaliadoPor()));
            v.setAvaliadoEm(i.getAvaliadoEm());
            return v;
        }).toList();
    }

    public List<HistoricoDO> historico(Long id) {
        chamadoVisivel(id);
        return historicoMapper.selectList(new LambdaQueryWrapperX<HistoricoDO>()
                .eq(HistoricoDO::getChamadoId, id).orderByDesc(HistoricoDO::getId));
    }

    // =====================================================================================================
    // Abertura e edição
    // =====================================================================================================

    @Transactional(rollbackFor = Exception.class)
    public Long criar(ChamadoVOs.CriarReq req, Long userId) {
        return criar(req, userId, null);
    }

    /**
     * Abre o chamado. Antes de gravar, as regras ativas são avaliadas sobre os dados do pedido (GLPI: RuleTicket
     * na criação) e podem mudar categoria, escala, tipo e participantes; os pedidos de aprovação das regras são
     * feitos logo depois de gravar.
     *
     * @param formularioId formulário do catálogo que originou o pedido (fato "formulario_id" das regras)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long criar(ChamadoVOs.CriarReq req, Long userId, Long formularioId) {
        return criar(req, userId, formularioId, null);
    }

    /**
     * Abertura por e-mail (GLPI: MailCollector): remetente com conta no portal vira o solicitante; sem conta
     * (caixa que aceita desconhecidos), o endereço entra como solicitante.
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Long criarDeEmail(String titulo, String descricaoHtml, Long userId, String email, Long categoriaId) {
        ChamadoVOs.CriarReq req = new ChamadoVOs.CriarReq();
        req.setTitulo(titulo);
        req.setDescricao(descricaoHtml);
        req.setTipo(ChamadoTipo.INCIDENTE.getCodigo());
        req.setCategoriaId(categoriaId);
        req.setOrigemId(cadastros.origemPadrao(true));
        return criar(req, userId, null, userId == null ? email : null);
    }

    /**
     * Chamado aberto por um atendente a partir de uma conversa do atendimento. O solicitante é a pessoa do portal
     * ligada ao contato ({@code req.solicitanteIds}) ou, sem conta, o e-mail do contato; o atendente fica como quem
     * registrou. Sem nenhum dos dois, o atendente é o solicitante.
     */
    @Transactional(rollbackFor = Exception.class)
    public Long criarDeConversa(ChamadoVOs.CriarReq req, Long atendenteId, String emailContato) {
        return criar(req, atendenteId, null, req.getSolicitanteIds().isEmpty() ? emailContato : null);
    }

    private Long criar(ChamadoVOs.CriarReq req, Long userId, Long formularioId, String emailSolicitante) {
        // sem solicitante informado, quem abre é o solicitante; a não ser que ele venha por e-mail
        List<Long> solicitantes = !req.getSolicitanteIds().isEmpty() ? req.getSolicitanteIds()
                : userId != null && emailSolicitante == null ? List.of(userId) : List.of();
        validarPessoas(solicitantes, req.getObservadorIds(), req.getTecnicoIds());
        req.getEquipeIds().forEach(cadastros::obterEquipe);
        if (req.getCategoriaId() != null) {
            cadastros.obterCategoria(req.getCategoriaId());
        }
        if (req.getOrigemId() != null) {
            cadastros.obterOrigem(req.getOrigemId());
        }

        LocalDateTime agora = LocalDateTime.now();
        ChamadoDO c = ChamadoDO.builder()
                .titulo(req.getTitulo().trim())
                .descricao(HtmlSeguro.limpar(req.getDescricao()))
                .tipo(req.getTipo())
                .urgencia(req.getUrgencia())
                .impacto(req.getImpacto())
                .prioridade(req.getPrioridade() != null ? req.getPrioridade() : prioridade(req.getUrgencia(), req.getImpacto()))
                .categoriaId(req.getCategoriaId())
                .origemId(req.getOrigemId() != null ? req.getOrigemId() : cadastros.origemPadrao(false))
                .deptId(req.getDeptId() != null ? req.getDeptId() : solicitantes.isEmpty() ? null : deptDe(solicitantes.get(0)))
                .registradoPor(userId)
                .build();
        List<Long> tecnicos = new ArrayList<>(req.getTecnicoIds());
        List<Long> equipes = new ArrayList<>(req.getEquipeIds());
        List<Long> observadores = new ArrayList<>(req.getObservadorIds());
        Long primeiro = solicitantes.isEmpty() ? null : solicitantes.get(0);
        ResultadoRegras regras = aplicarRegras(c, primeiro, formularioId, req.getPrioridade() != null,
                tecnicos, equipes, observadores);

        CategoriaDO categoria = c.getCategoriaId() == null ? null : cadastros.obterCategoria(c.getCategoriaId());
        // atribuição pela categoria (GLPI: técnico/grupo responsável da categoria) quando ninguém foi escolhido
        if (categoria != null && tecnicos.isEmpty() && equipes.isEmpty()) {
            // sem responsável próprio, vale o da categoria acima (a área do catálogo)
            for (CategoriaDO cat = categoria; cat != null && tecnicos.isEmpty() && equipes.isEmpty();
                 cat = cat.getParentId() == null || cat.getParentId() == 0 ? null : cadastros.obterCategoria(cat.getParentId())) {
                Optional.ofNullable(cat.getTecnicoId()).ifPresent(tecnicos::add);
                Optional.ofNullable(cat.getEquipeId()).ifPresent(equipes::add);
            }
        }
        CicloChamado.abrir(c, agora, !tecnicos.isEmpty() || !equipes.isEmpty());
        slaService.aplicarNaAbertura(c);
        chamadoMapper.insert(c);

        solicitantes.stream().distinct().forEach(u -> inserirAtor(c.getId(), AtorTipo.SOLICITANTE, u, null));
        if (emailSolicitante != null) {
            atorMapper.insert(ChamadoAtorDO.builder().chamadoId(c.getId()).tipo(AtorTipo.SOLICITANTE.getCodigo())
                    .email(emailSolicitante).notificar(true).build());
        }
        observadores.stream().distinct().forEach(u -> inserirAtor(c.getId(), AtorTipo.OBSERVADOR, u, null));
        tecnicos.stream().distinct().forEach(u -> inserirAtor(c.getId(), AtorTipo.TECNICO, u, null));
        equipes.stream().distinct().forEach(e -> inserirAtor(c.getId(), AtorTipo.TECNICO, null, e));
        registrar(c.getId(), userId, "criado", null, ChamadoStatus.de(c.getStatus()).getRotulo());
        regras.nomes().forEach(n -> registrar(c.getId(), null, "regra", null, n));
        pedirAprovacoesDasRegras(c.getId(), primeiro, regras);
        publicar(c.getId(), "criado", userId);
        return c.getId();
    }

    private record ResultadoRegras(Set<String> nomes, boolean gestor, Set<Long> aprovadores) {
    }

    /** Avalia as regras sobre o chamado ainda não gravado e aplica as ações nos campos e nas listas. */
    private ResultadoRegras aplicarRegras(ChamadoDO c, Long solicitante, Long formularioId, boolean prioridadeFixa,
                                          List<Long> tecnicos, List<Long> equipes, List<Long> observadores) {
        Map<String, Object> fatos = new HashMap<>();
        fatos.put("titulo", c.getTitulo());
        fatos.put("descricao", HtmlSeguro.texto(c.getDescricao()));
        fatos.put("categoria_id", c.getCategoriaId());
        fatos.put("tipo", c.getTipo());
        fatos.put("urgencia", c.getUrgencia());
        fatos.put("impacto", c.getImpacto());
        fatos.put("prioridade", c.getPrioridade());
        fatos.put("origem_id", c.getOrigemId());
        fatos.put("dept_id", c.getDeptId());
        fatos.put("solicitante_id", solicitante);
        fatos.put("formulario_id", formularioId);
        Set<String> nomes = new LinkedHashSet<>();
        Set<Long> aprovadores = new LinkedHashSet<>();
        boolean gestor = false;
        boolean escalaMudou = false;
        for (MotorRegras.Disparo d : regraService.avaliar(fatos)) {
            String v = d.acao().valor() == null ? "" : d.acao().valor().trim();
            try {
                switch (d.acao().acao()) {
                    case "atribuir_equipe" -> equipes.add(cadastros.obterEquipe(Long.valueOf(v)).getId());
                    case "atribuir_tecnico" -> tecnicos.add(pessoaAtiva(Long.valueOf(v)));
                    case "adicionar_observador" -> observadores.add(pessoaAtiva(Long.valueOf(v)));
                    case "definir_categoria" -> c.setCategoriaId(cadastros.obterCategoria(Long.valueOf(v)).getId());
                    case "definir_urgencia" -> {
                        c.setUrgencia(escala(v));
                        escalaMudou = true;
                    }
                    case "definir_impacto" -> {
                        c.setImpacto(escala(v));
                        escalaMudou = true;
                    }
                    case "definir_prioridade" -> {
                        c.setPrioridade(escala(v));
                        prioridadeFixa = true;
                    }
                    case "definir_tipo" -> c.setTipo(ChamadoTipo.de(Integer.parseInt(v)).getCodigo());
                    case "pedir_aprovacao_gestor" -> gestor = true;
                    case "pedir_aprovacao_usuario" -> aprovadores.add(pessoaAtiva(Long.valueOf(v)));
                    default -> throw new IllegalArgumentException("ação desconhecida");
                }
                nomes.add(d.regraNome());
            } catch (Exception e) {
                // valor que deixou de existir (equipe excluída, pessoa desativada): ignora a ação, não impede a abertura
                log.warn("[aplicarRegras][regra {} ação {}={} ignorada: {}]", d.regraId(), d.acao().acao(), v, e.getMessage());
            }
        }
        if (escalaMudou && !prioridadeFixa) {
            c.setPrioridade(prioridade(c.getUrgencia(), c.getImpacto()));
        }
        return new ResultadoRegras(nomes, gestor, aprovadores);
    }

    private void pedirAprovacoesDasRegras(Long chamadoId, Long solicitante, ResultadoRegras r) {
        Set<Long> aprovadores = new LinkedHashSet<>(r.aprovadores());
        if (r.gestor()) {
            Optional<Long> g = solicitante == null ? Optional.empty() : aprovacaoService.gestorDe(solicitante);
            if (g.isPresent()) {
                aprovadores.add(g.get());
            } else {
                acompanhamentoSistema(chamadoId, null, "Uma regra pediu a aprovação do gestor, mas o solicitante não tem "
                        + "gestor (atributo manager) no AD. Peça a aprovação manualmente.", true);
            }
        }
        aprovadores.remove(solicitante);
        // quem perdeu o papel Aprovador depois de a regra ser escrita é pulado, sem impedir a abertura
        for (Iterator<Long> it = aprovadores.iterator(); it.hasNext(); ) {
            Long a = it.next();
            if (!aprovacaoService.podeAprovar(a)) {
                it.remove();
                acompanhamentoSistema(chamadoId, null, "Uma regra pediu a aprovação de " + nomePessoa(a) + ", que não "
                        + "tem o papel Aprovador. Peça a aprovação a outra pessoa.", true);
            }
        }
        if (!aprovadores.isEmpty()) {
            aprovacaoService.solicitar(chamadoId, aprovadores, "Pedido por regra: " + String.join(", ", r.nomes()), null);
        }
    }

    /** Pessoa citada numa regra: a regra roda no contexto de quem abriu, então o escopo de unidade não vale aqui. */
    private Long pessoaAtiva(Long id) {
        DataPermissionUtils.executeIgnore(() -> adminUserApi.validateUser(id));
        return id;
    }

    private static int escala(String v) {
        int n = Integer.parseInt(v);
        if (n < 1 || n > 6) {
            throw new IllegalArgumentException("fora da escala");
        }
        return n;
    }

    /** Abertura pelo próprio usuário (portal, WhatsApp): ele é o solicitante; impacto médio. */
    @Transactional(rollbackFor = Exception.class)
    public Long abrirPortal(ChamadoVOs.AbrirReq req, Long userId) {
        if (req.getCategoriaId() != null) {
            CategoriaDO cat = cadastros.obterCategoria(req.getCategoriaId());
            if (!Boolean.TRUE.equals(cat.getVisivelPortal()) || !Boolean.TRUE.equals(cat.getAtivo())) {
                throw exception(CATEGORIA_NAO_EXISTE);
            }
        }
        ChamadoVOs.CriarReq c = new ChamadoVOs.CriarReq();
        c.setTitulo(req.getTitulo());
        c.setDescricao(req.getDescricao());
        c.setTipo(req.getTipo());
        c.setUrgencia(req.getUrgencia());
        c.setImpacto(3);
        c.setCategoriaId(req.getCategoriaId());
        return criar(c, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void atualizar(ChamadoVOs.AtualizarReq req, Long userId) {
        chamadoVisivel(req.getId());
        ChamadoDO c = travar(req.getId());
        exigirNaoFechado(c);
        if (req.getCategoriaId() != null) {
            cadastros.obterCategoria(req.getCategoriaId());
        }
        if (req.getOrigemId() != null) {
            cadastros.obterOrigem(req.getOrigemId());
        }
        ChamadoDO antes = copia(c);
        c.setTitulo(req.getTitulo().trim());
        if (req.getDescricao() != null) {
            c.setDescricao(HtmlSeguro.limpar(req.getDescricao()));
        }
        Optional.ofNullable(req.getTipo()).ifPresent(c::setTipo);
        Optional.ofNullable(req.getUrgencia()).ifPresent(c::setUrgencia);
        Optional.ofNullable(req.getImpacto()).ifPresent(c::setImpacto);
        boolean escalaMudou = !Objects.equals(antes.getUrgencia(), c.getUrgencia()) || !Objects.equals(antes.getImpacto(), c.getImpacto());
        if (req.getPrioridade() != null) {
            c.setPrioridade(req.getPrioridade());
        } else if (escalaMudou) {
            c.setPrioridade(prioridade(c.getUrgencia(), c.getImpacto()));
        }
        c.setCategoriaId(req.getCategoriaId());
        Optional.ofNullable(req.getOrigemId()).ifPresent(c::setOrigemId);
        Optional.ofNullable(req.getDeptId()).ifPresent(c::setDeptId);
        String slaAntes = nomeSla(c.getSlaSolucaoId());
        if (!Objects.equals(antes.getCategoriaId(), c.getCategoriaId()) || !Objects.equals(antes.getPrioridade(), c.getPrioridade())) {
            slaService.recalcular(c);
        }
        gravar(c);
        diferenca(c.getId(), userId, "sla", slaAntes, nomeSla(c.getSlaSolucaoId()));

        diferenca(c.getId(), userId, "titulo", antes.getTitulo(), c.getTitulo());
        diferenca(c.getId(), userId, "tipo", rotuloTipo(antes.getTipo()), rotuloTipo(c.getTipo()));
        diferenca(c.getId(), userId, "urgencia", Prioridade.rotulo(antes.getUrgencia()), Prioridade.rotulo(c.getUrgencia()));
        diferenca(c.getId(), userId, "impacto", Prioridade.rotulo(antes.getImpacto()), Prioridade.rotulo(c.getImpacto()));
        diferenca(c.getId(), userId, "prioridade", Prioridade.rotulo(antes.getPrioridade()), Prioridade.rotulo(c.getPrioridade()));
        diferenca(c.getId(), userId, "categoria", nomeCategoria(antes.getCategoriaId()), nomeCategoria(c.getCategoriaId()));
        diferenca(c.getId(), userId, "unidade", nomeDept(antes.getDeptId()), nomeDept(c.getDeptId()));
        if (!Objects.equals(antes.getDescricao(), c.getDescricao())) {
            registrar(c.getId(), userId, "descricao", null, "alterada");
        }
        publicar(c.getId(), "atualizado", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id, Long userId) {
        chamadoVisivel(id);
        chamadoMapper.deleteById(id);
        registrar(id, userId, "excluido", null, "sim");
        publicar(id, "excluido", userId);
    }

    // =====================================================================================================
    // Participantes
    // =====================================================================================================

    @Transactional(rollbackFor = Exception.class)
    public void adicionarAtor(ChamadoVOs.AtorReq req, Long userId) {
        AtorTipo tipo = AtorTipo.de(req.getTipo());
        if ((req.getUserId() == null) == (req.getEquipeId() == null)
                || (req.getEquipeId() != null && tipo != AtorTipo.TECNICO)) {
            throw exception(CHAMADO_ATOR_INVALIDO);
        }
        chamadoVisivel(req.getChamadoId());
        ChamadoDO c = travar(req.getChamadoId());
        exigirNaoFechado(c);
        if (req.getUserId() != null) {
            adminUserApi.validateUser(req.getUserId());
        } else {
            cadastros.obterEquipe(req.getEquipeId());
        }
        boolean existe = atorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId()).stream()
                .anyMatch(a -> a.getTipo() == tipo.getCodigo() && Objects.equals(a.getUserId(), req.getUserId())
                        && Objects.equals(a.getEquipeId(), req.getEquipeId()));
        if (existe) {
            throw exception(CHAMADO_ATOR_DUPLICADO);
        }
        Long atorId = inserirAtor(c.getId(), tipo, req.getUserId(), req.getEquipeId());
        if (tipo == AtorTipo.TECNICO) {
            mudarStatus(c, userId, () -> CicloChamado.tecnicoAtribuido(c, LocalDateTime.now()));
        }
        registrar(c.getId(), userId, tipo.name().toLowerCase() + "+",
                null, req.getUserId() != null ? nomePessoa(req.getUserId()) : cadastros.obterEquipe(req.getEquipeId()).getNome());
        publicar(c.getId(), "ator", userId, atorId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removerAtor(Long atorId, Long userId) {
        ChamadoAtorDO a = atorMapper.selectById(atorId);
        if (a == null) {
            throw exception(CHAMADO_ATOR_NAO_EXISTE);
        }
        chamadoVisivel(a.getChamadoId());
        ChamadoDO c = travar(a.getChamadoId());
        exigirNaoFechado(c);
        List<ChamadoAtorDO> todos = atorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId());
        if (a.getTipo() == AtorTipo.SOLICITANTE.getCodigo()
                && todos.stream().filter(x -> x.getTipo() == AtorTipo.SOLICITANTE.getCodigo()).count() <= 1) {
            throw exception(CHAMADO_ULTIMO_SOLICITANTE);
        }
        atorMapper.deleteById(atorId);
        if (a.getTipo() == AtorTipo.TECNICO.getCodigo()
                && todos.stream().noneMatch(x -> !x.getId().equals(atorId) && x.getTipo() == AtorTipo.TECNICO.getCodigo())) {
            mudarStatus(c, userId, () -> CicloChamado.semTecnicos(c));
        }
        registrar(c.getId(), userId, AtorTipo.de(a.getTipo()).name().toLowerCase() + "-",
                a.getUserId() != null ? nomePessoa(a.getUserId()) : a.getEquipeId() != null
                        ? cadastros.obterEquipe(a.getEquipeId()).getNome() : a.getEmail(), null);
        publicar(c.getId(), "ator", userId);
    }

    /** O técnico logado assume o chamado (entra como técnico). */
    @Transactional(rollbackFor = Exception.class)
    public void assumir(Long id, Long userId) {
        ChamadoVOs.AtorReq r = new ChamadoVOs.AtorReq();
        r.setChamadoId(id);
        r.setTipo(AtorTipo.TECNICO.getCodigo());
        r.setUserId(userId);
        adicionarAtor(r, userId);
    }

    // =====================================================================================================
    // Linha do tempo e ciclo de vida
    // =====================================================================================================

    /**
     * Acompanhamento, tarefa ou solução.
     *
     * @param comoTecnico quem chama tem permissão de atender (controller técnico); no portal é false
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Long adicionarInteracao(ChamadoVOs.InteracaoReq req, Long userId, boolean comoTecnico) {
        ChamadoDO c = chamadoExistente(req.getChamadoId());
        boolean tecnico = comoTecnico && podeVerComoTecnico(c, userId) && tem(userId, P_ATENDER);
        if (!tecnico) {
            exigirParticipante(c, userId);
            if (!InteracaoDO.ACOMPANHAMENTO.equals(req.getTipo())) {
                throw exception(CHAMADO_SO_TECNICO);
            }
        }
        c = travar(c.getId());
        exigirNaoFechado(c);
        LocalDateTime agora = LocalDateTime.now();
        InteracaoDO i = InteracaoDO.builder()
                .chamadoId(c.getId())
                .tipo(req.getTipo())
                .userId(userId)
                .conteudo(HtmlSeguro.limpar(req.getConteudo()))
                .privado(tecnico && Boolean.TRUE.equals(req.getPrivado()))
                .origemId(req.getOrigemId())
                .build();
        final ChamadoDO ch = c;
        switch (req.getTipo()) {
            case InteracaoDO.TAREFA -> {
                if (req.getInicio() != null && req.getFim() != null && !req.getFim().isAfter(req.getInicio())) {
                    throw exception(CHAMADO_TAREFA_DATAS);
                }
                i.setEstadoTarefa(req.getEstadoTarefa() != null ? req.getEstadoTarefa() : InteracaoDO.TAREFA_A_FAZER);
                i.setTecnicoId(req.getTecnicoId() != null ? req.getTecnicoId() : userId);
                i.setInicio(req.getInicio());
                i.setFim(req.getFim());
                i.setTempoGastoSeg(req.getTempoGastoSeg() == null ? 0 : req.getTempoGastoSeg());
                interacaoMapper.insert(i);
                c.setTempoGastoSeg((c.getTempoGastoSeg() == null ? 0 : c.getTempoGastoSeg()) + i.getTempoGastoSeg());
                gravar(c);
                mudarStatus(c, userId, () -> {
                    CicloChamado.assumir(ch, agora);
                    if (i.getInicio() != null && i.getEstadoTarefa() == InteracaoDO.TAREFA_A_FAZER) {
                        CicloChamado.planejar(ch);
                    }
                });
            }
            case InteracaoDO.SOLUCAO -> {
                i.setStatusSolucao(InteracaoDO.SOLUCAO_AGUARDANDO);
                interacaoMapper.insert(i);
                mudarStatus(c, userId, () -> CicloChamado.solucionar(ch, agora));
            }
            default -> {
                interacaoMapper.insert(i);
                if (tecnico) {
                    mudarStatus(c, userId, () -> CicloChamado.assumir(ch, agora));
                } else if (ChamadoStatus.de(c.getStatus()) == ChamadoStatus.SOLUCIONADO && ehSolicitante(c, userId)) {
                    // GLPI: acompanhamento do solicitante num chamado solucionado = solução recusada, reabre
                    recusarSolucaoPendente(c, userId);
                }
            }
        }
        publicar(c.getId(), req.getTipo(), userId, i.getId());
        return i.getId();
    }

    /** Solicitante aprova (fecha) ou recusa (reabre) a solução. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void avaliarSolucao(ChamadoVOs.AvaliarSolucaoReq req, Long userId) {
        ChamadoDO c = chamadoExistente(req.getChamadoId());
        if (!ehSolicitante(c, userId) && !(podeVerComoTecnico(c, userId) && tem(userId, P_FECHAR))) {
            throw exception(CHAMADO_SEM_ACESSO);
        }
        c = travar(c.getId());
        InteracaoDO solucao = solucaoPendente(c.getId());
        if (ChamadoStatus.de(c.getStatus()) != ChamadoStatus.SOLUCIONADO || solucao == null) {
            throw exception(CHAMADO_SEM_SOLUCAO_PENDENTE);
        }
        if (req.getComentario() != null && !req.getComentario().isBlank()) {
            interacaoMapper.insert(InteracaoDO.builder().chamadoId(c.getId()).tipo(InteracaoDO.ACOMPANHAMENTO)
                    .userId(userId).conteudo(HtmlSeguro.limpar(req.getComentario())).privado(false).build());
        }
        if (Boolean.TRUE.equals(req.getAprovar())) {
            marcarSolucao(solucao, InteracaoDO.SOLUCAO_APROVADA, userId);
            final ChamadoDO ch = c;
            mudarStatus(c, userId, () -> CicloChamado.fechar(ch, LocalDateTime.now()));
            publicar(c.getId(), "solucao-aprovada", userId);
        } else {
            recusarSolucaoPendente(c, userId);
            publicar(c.getId(), "solucao-recusada", userId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void colocarPendente(ChamadoVOs.PendenteReq req, Long userId) {
        chamadoVisivel(req.getChamadoId());
        ChamadoDO c = travar(req.getChamadoId());
        mudarStatus(c, userId, () -> CicloChamado.colocarPendente(c, LocalDateTime.now()));
        if (req.getMotivo() != null && !req.getMotivo().isBlank()) {
            interacaoMapper.insert(InteracaoDO.builder().chamadoId(c.getId()).tipo(InteracaoDO.ACOMPANHAMENTO)
                    .userId(userId).conteudo(HtmlSeguro.limpar(req.getMotivo())).privado(false).build());
        }
        publicar(c.getId(), "pendente", userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void retomar(Long id, Long userId) {
        chamadoVisivel(id);
        ChamadoDO c = travar(id);
        boolean temTecnico = temTecnico(id);
        mudarStatus(c, userId, () -> CicloChamado.retomar(c, LocalDateTime.now(), temTecnico));
        publicar(id, "retomado", userId);
    }

    /** Fechamento direto (sem esperar a aprovação do solicitante). */
    @Transactional(rollbackFor = Exception.class)
    public void fechar(Long id, Long userId) {
        chamadoVisivel(id);
        ChamadoDO c = travar(id);
        InteracaoDO pendente = solucaoPendente(id);
        if (pendente != null) {
            marcarSolucao(pendente, InteracaoDO.SOLUCAO_APROVADA, userId);
        }
        mudarStatus(c, userId, () -> CicloChamado.fechar(c, LocalDateTime.now()));
        publicar(id, "fechado", userId);
    }

    /** Reabre um chamado solucionado (técnico) ou fechado (permissão de reabrir). */
    @Transactional(rollbackFor = Exception.class)
    public void reabrir(Long id, Long userId) {
        chamadoVisivel(id);
        ChamadoDO c = travar(id);
        if (ChamadoStatus.de(c.getStatus()) == ChamadoStatus.FECHADO && !tem(userId, P_REABRIR)) {
            throw exception(CHAMADO_FECHADO);
        }
        InteracaoDO pendente = solucaoPendente(id);
        if (pendente != null) {
            marcarSolucao(pendente, InteracaoDO.SOLUCAO_RECUSADA, userId);
        }
        boolean temTecnico = temTecnico(id);
        mudarStatus(c, userId, () -> CicloChamado.reabrir(c, temTecnico));
        publicar(id, "reaberto", userId);
    }

    // =====================================================================================================
    // Ações do sistema (escalonamento e fechamento automático) e pesquisa de satisfação
    // =====================================================================================================

    /**
     * Executa um nível de escalonamento num chamado. Idempotente: o registro de execução tem índice único,
     * então duas instâncias do backend não escalonam o mesmo nível duas vezes.
     *
     * @return true se executou agora
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public boolean escalonar(Long chamadoId, SlaNivelDO nivel, String qualPrazo) {
        ChamadoDO c = chamadoMapper.selectByIdParaAtualizar(chamadoId);
        if (c == null || !ChamadoStatus.de(c.getStatus()).aberto() || c.getStatus() == ChamadoStatus.PENDENTE.getCodigo()) {
            return false;
        }
        boolean jaFeito = nivelExecucaoMapper.selectCount(new LambdaQueryWrapperX<SlaNivelExecucaoDO>()
                .eq(SlaNivelExecucaoDO::getChamadoId, chamadoId).eq(SlaNivelExecucaoDO::getNivelId, nivel.getId())) > 0;
        if (jaFeito) {
            return false;
        }
        LocalDateTime agora = LocalDateTime.now();
        nivelExecucaoMapper.insert(SlaNivelExecucaoDO.builder().chamadoId(chamadoId).nivelId(nivel.getId()).executadoEm(agora).build());
        List<ChamadoAtorDO> atores = atorMapper.selectList(ChamadoAtorDO::getChamadoId, chamadoId);
        List<String> feito = new ArrayList<>();
        if (nivel.getAtribuirEquipeId() != null && atores.stream().noneMatch(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo()
                && nivel.getAtribuirEquipeId().equals(a.getEquipeId()))) {
            inserirAtor(chamadoId, AtorTipo.TECNICO, null, nivel.getAtribuirEquipeId());
            String eq = cadastros.obterEquipe(nivel.getAtribuirEquipeId()).getNome();
            registrar(chamadoId, null, "tecnico+", null, eq);
            feito.add("equipe " + eq + " incluída");
        }
        if (nivel.getAtribuirTecnicoId() != null && atores.stream().noneMatch(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo()
                && nivel.getAtribuirTecnicoId().equals(a.getUserId()))) {
            inserirAtor(chamadoId, AtorTipo.TECNICO, nivel.getAtribuirTecnicoId(), null);
            String nome = nomePessoa(nivel.getAtribuirTecnicoId());
            registrar(chamadoId, null, "tecnico+", null, nome);
            feito.add(nome + " incluído");
        }
        if (!feito.isEmpty()) {
            mudarStatus(c, null, () -> CicloChamado.tecnicoAtribuido(c, agora));
        }
        if (nivel.getNovaPrioridade() != null && !nivel.getNovaPrioridade().equals(c.getPrioridade())) {
            registrar(chamadoId, null, "prioridade", Prioridade.rotulo(c.getPrioridade()), Prioridade.rotulo(nivel.getNovaPrioridade()));
            c.setPrioridade(nivel.getNovaPrioridade());
            gravar(c);
            feito.add("prioridade " + Prioridade.rotulo(nivel.getNovaPrioridade()).toLowerCase());
        }
        interacaoMapper.insert(InteracaoDO.builder().chamadoId(chamadoId).tipo(InteracaoDO.ACOMPANHAMENTO).privado(true)
                .conteudo(HtmlSeguro.limpar("<p><b>Escalonamento automático: " + HtmlSeguro.texto(nivel.getNome()) + "</b> (prazo de "
                        + qualPrazo + ")" + (feito.isEmpty() ? "" : ": " + String.join(", ", feito)) + ".</p>")).build());
        registrar(chamadoId, null, "escalonamento", null, nivel.getNome());
        publicar(chamadoId, "escalonado", null);
        return true;
    }

    /** Fecha um chamado solucionado sem resposta do solicitante (GLPI: autoclose). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public boolean fecharAutomatico(Long chamadoId) {
        ChamadoDO c = chamadoMapper.selectByIdParaAtualizar(chamadoId);
        if (c == null || c.getStatus() != ChamadoStatus.SOLUCIONADO.getCodigo()) {
            return false;
        }
        InteracaoDO pendente = solucaoPendente(chamadoId);
        if (pendente != null) {
            marcarSolucao(pendente, InteracaoDO.SOLUCAO_APROVADA, null);
        }
        mudarStatus(c, null, () -> CicloChamado.fechar(c, LocalDateTime.now()));
        publicar(chamadoId, "fechado", null);
        return true;
    }

    /** Resposta do solicitante à pesquisa de satisfação (uma vez só). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void responderPesquisa(Long chamadoId, int nota, String comentario, Long userId) {
        ChamadoDO c = chamadoExistente(chamadoId);
        if (!ehSolicitante(c, userId)) {
            throw exception(CHAMADO_SEM_ACESSO);
        }
        SatisfacaoDO s = satisfacaoMapper.selectOne(SatisfacaoDO::getChamadoId, chamadoId);
        if (s == null || s.getRespondidaEm() != null) {
            throw exception(SATISFACAO_NAO_DISPONIVEL);
        }
        s.setNota(nota);
        s.setComentario(comentario == null ? null : HtmlSeguro.texto(comentario));
        s.setRespondidaEm(LocalDateTime.now());
        satisfacaoMapper.updateById(s);
        registrar(chamadoId, userId, "satisfacao", null, nota + "/5");
        publicar(chamadoId, "satisfacao", userId);
    }

    // =====================================================================================================
    // Aprovações, automação no AD e ações do sistema
    // =====================================================================================================

    /** Técnico pede aprovação a uma ou mais pessoas. */
    @Transactional(rollbackFor = Exception.class)
    public void pedirAprovacao(Long chamadoId, List<Long> aprovadores, String comentario, Long userId) {
        ChamadoDO c = chamadoVisivel(chamadoId);
        exigirNaoFechado(c);
        aprovacaoService.solicitar(chamadoId, aprovadores, comentario == null ? null : comentario.trim(), userId);
    }

    /** Aprovações do chamado, para quem pode vê-lo (técnico no escopo ou participante). */
    @DataPermission(enable = false)
    public List<AprovacaoDO> aprovacoes(Long chamadoId, Long userId) {
        exigirParticipanteOuTecnico(chamadoExistente(chamadoId), userId);
        return aprovacaoService.doChamado(chamadoId);
    }

    /** Senha provisória da operação no AD: só o solicitante, uma única vez. */
    @DataPermission(enable = false)
    public String revelarSenha(Long chamadoId, Long userId) {
        if (!ehSolicitante(chamadoExistente(chamadoId), userId)) {
            throw exception(CHAMADO_SEM_ACESSO);
        }
        return execucaoAdService.revelarSenha(chamadoId, userId);
    }

    /**
     * Resposta por e-mail a um chamado. Só entra se o remetente participa (pessoa ou endereço) ou é técnico
     * atribuído (pessoa ou membro de equipe). Num chamado solucionado, a resposta do solicitante recusa a solução.
     *
     * @return false quando o remetente não participa ou o chamado está fechado (o coletor trata como novo pedido)
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public boolean acompanhamentoDeEmail(Long chamadoId, Long userId, String email, String conteudoHtml) {
        ChamadoDO c = chamadoMapper.selectById(chamadoId);
        if (c == null || ChamadoStatus.de(c.getStatus()) == ChamadoStatus.FECHADO) {
            return false;
        }
        List<ChamadoAtorDO> atores = atorMapper.selectList(ChamadoAtorDO::getChamadoId, chamadoId);
        boolean participa = atores.stream().anyMatch(a -> a.getTipo() != AtorTipo.TECNICO.getCodigo()
                && ((userId != null && userId.equals(a.getUserId())) || (a.getEmail() != null && a.getEmail().equalsIgnoreCase(email))));
        Set<Long> equipesDoRemetente = userId == null ? Set.of() : new HashSet<>(cadastros.equipesDoUsuario(userId));
        boolean tecnico = userId != null && tem(userId, P_ATENDER) && atores.stream().anyMatch(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo()
                && (userId.equals(a.getUserId()) || (a.getEquipeId() != null && equipesDoRemetente.contains(a.getEquipeId()))));
        if (!participa && !tecnico) {
            return false;
        }
        Long origem = cadastros.origemPadrao(true);
        if (userId != null) {
            ChamadoVOs.InteracaoReq r = new ChamadoVOs.InteracaoReq();
            r.setChamadoId(chamadoId);
            r.setTipo(InteracaoDO.ACOMPANHAMENTO);
            r.setConteudo(conteudoHtml);
            r.setPrivado(false);
            r.setOrigemId(origem);
            adicionarInteracao(r, userId, tecnico);
            return true;
        }
        // só o endereço (solicitante sem conta)
        ChamadoDO ch = travar(chamadoId);
        InteracaoDO i = InteracaoDO.builder().chamadoId(chamadoId).tipo(InteracaoDO.ACOMPANHAMENTO)
                .conteudo("<p><i>Por e-mail: " + HtmlUtils.htmlEscape(email) + "</i></p>" + HtmlSeguro.limpar(conteudoHtml))
                .privado(false).origemId(origem).build();
        interacaoMapper.insert(i);
        if (ChamadoStatus.de(ch.getStatus()) == ChamadoStatus.SOLUCIONADO) {
            recusarSolucaoPendente(ch, null);
        }
        publicar(chamadoId, InteracaoDO.ACOMPANHAMENTO, null, i.getId());
        return true;
    }

    /** Histórico gravado por outro serviço do ITSM (aprovações, automações). userId vazio = "Sistema". */
    public void registrarSistema(Long chamadoId, Long userId, String campo, String de, String para) {
        registrar(chamadoId, userId, campo, de, para);
    }

    /** Avisa a tela e as notificações (evento de domínio). */
    public void notificar(Long chamadoId, String acao, Long userId) {
        publicar(chamadoId, acao, userId);
    }

    /** Acompanhamento em texto puro (escapado) escrito por um serviço; userId vazio = sistema. */
    @DataPermission(enable = false)
    public void acompanhamentoSistema(Long chamadoId, Long userId, String texto, boolean privado) {
        InteracaoDO i = InteracaoDO.builder().chamadoId(chamadoId).tipo(InteracaoDO.ACOMPANHAMENTO).userId(userId)
                .conteudo("<p>" + HtmlUtils.htmlEscape(texto) + "</p>").privado(privado).build();
        interacaoMapper.insert(i);
        publicar(chamadoId, InteracaoDO.ACOMPANHAMENTO, userId, i.getId());
    }

    /** Solução registrada pela automação (ex.: operação no AD concluída); o solicitante aprova ou recusa. */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void solucionarSistema(Long chamadoId, String texto) {
        ChamadoDO c = travar(chamadoId);
        if (!ChamadoStatus.de(c.getStatus()).aberto()) {
            acompanhamentoSistema(chamadoId, null, texto, false);
            return;
        }
        InteracaoDO i = InteracaoDO.builder().chamadoId(chamadoId).tipo(InteracaoDO.SOLUCAO)
                .conteudo("<p>" + HtmlUtils.htmlEscape(texto) + "</p>").privado(false)
                .statusSolucao(InteracaoDO.SOLUCAO_AGUARDANDO).build();
        interacaoMapper.insert(i);
        mudarStatus(c, null, () -> CicloChamado.solucionar(c, LocalDateTime.now()));
        publicar(chamadoId, "solucionado", null, i.getId());
    }

    /** Pedido recusado na aprovação: registra a decisão como solução e fecha (não há o que executar). */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void encerrarPorRecusa(Long chamadoId, String comentario) {
        ChamadoDO c = travar(chamadoId);
        if (ChamadoStatus.de(c.getStatus()) == ChamadoStatus.FECHADO) {
            return;
        }
        InteracaoDO pendente = solucaoPendente(chamadoId);
        if (pendente != null) {
            marcarSolucao(pendente, InteracaoDO.SOLUCAO_RECUSADA, null);
        }
        String texto = "Pedido recusado na aprovação" + (comentario == null || comentario.isBlank() ? "." : ": " + comentario.trim());
        interacaoMapper.insert(InteracaoDO.builder().chamadoId(chamadoId).tipo(InteracaoDO.SOLUCAO)
                .conteudo("<p>" + HtmlUtils.htmlEscape(texto) + "</p>").privado(false)
                .statusSolucao(InteracaoDO.SOLUCAO_APROVADA).avaliadoEm(LocalDateTime.now()).build());
        mudarStatus(c, null, () -> CicloChamado.fechar(c, LocalDateTime.now()));
        publicar(chamadoId, "fechado-recusa", null);
    }

    private void criarPesquisa(ChamadoDO c, LocalDateTime agora) {
        if (!props.isPesquisaSatisfacao() || satisfacaoMapper.selectCount(SatisfacaoDO::getChamadoId, c.getId()) > 0) {
            return;
        }
        satisfacaoMapper.insert(SatisfacaoDO.builder().chamadoId(c.getId()).enviadaEm(agora).build());
    }

    /**
     * Grava o chamado inteiro. Os campos que podem voltar a vazio (categoria, SLA, prazos, início da espera)
     * vão explícitos: o updateById do MyBatis-Plus ignora nulos.
     */
    private void gravar(ChamadoDO c) {
        // 1) campos preenchidos; 2) os que podem ter voltado a vazio (num UPDATE só, as colunas se repetiriam)
        chamadoMapper.updateById(c);
        chamadoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChamadoDO>()
                .eq(ChamadoDO::getId, c.getId())
                .set(ChamadoDO::getCategoriaId, c.getCategoriaId())
                .set(ChamadoDO::getSlaAtendimentoId, c.getSlaAtendimentoId())
                .set(ChamadoDO::getSlaSolucaoId, c.getSlaSolucaoId())
                .set(ChamadoDO::getPrazoAtendimento, c.getPrazoAtendimento())
                .set(ChamadoDO::getPrazoSolucao, c.getPrazoSolucao())
                .set(ChamadoDO::getInicioEspera, c.getInicioEspera())
                .set(ChamadoDO::getSolucionadoEm, c.getSolucionadoEm())
                .set(ChamadoDO::getFechadoEm, c.getFechadoEm())
                .set(ChamadoDO::getTempoSolucaoSeg, c.getTempoSolucaoSeg())
                .set(ChamadoDO::getTempoFechamentoSeg, c.getTempoFechamentoSeg()));
    }

    private String nomeSla(Long id) {
        if (id == null) {
            return null;
        }
        SlaDO s = slaMapper.selectById(id);
        return s == null ? "#" + id : s.getNome();
    }

    // =====================================================================================================
    // Internos
    // =====================================================================================================

    private void recusarSolucaoPendente(ChamadoDO c, Long userId) {
        InteracaoDO solucao = solucaoPendente(c.getId());
        if (solucao != null) {
            marcarSolucao(solucao, InteracaoDO.SOLUCAO_RECUSADA, userId);
        }
        boolean temTecnico = temTecnico(c.getId());
        mudarStatus(c, userId, () -> CicloChamado.reabrir(c, temTecnico));
    }

    private void marcarSolucao(InteracaoDO solucao, int status, Long userId) {
        solucao.setStatusSolucao(status);
        solucao.setAvaliadoPor(userId);
        solucao.setAvaliadoEm(LocalDateTime.now());
        interacaoMapper.updateById(solucao);
    }

    private InteracaoDO solucaoPendente(Long chamadoId) {
        return interacaoMapper.selectOne(new LambdaQueryWrapperX<InteracaoDO>()
                .eq(InteracaoDO::getChamadoId, chamadoId).eq(InteracaoDO::getTipo, InteracaoDO.SOLUCAO)
                .eq(InteracaoDO::getStatusSolucao, InteracaoDO.SOLUCAO_AGUARDANDO)
                .orderByDesc(InteracaoDO::getId).last("LIMIT 1"));
    }

    /** Aplica a transição, grava o chamado e registra a mudança de situação no histórico. */
    private void mudarStatus(ChamadoDO c, Long userId, Runnable transicao) {
        Integer antes = c.getStatus();
        LocalDateTime inicioEspera = c.getInicioEspera();
        try {
            transicao.run();
        } catch (CicloChamado.TransicaoInvalidaException e) {
            throw exception(CHAMADO_TRANSICAO_INVALIDA, e.getMessage());
        }
        LocalDateTime agora = LocalDateTime.now();
        if (antes == ChamadoStatus.PENDENTE.getCodigo() && c.getStatus() != ChamadoStatus.PENDENTE.getCodigo()) {
            slaService.aposPendencia(c, inicioEspera, agora);
        }
        gravar(c);
        if (c.getStatus() == ChamadoStatus.FECHADO.getCodigo() && !Objects.equals(antes, c.getStatus())) {
            criarPesquisa(c, agora);
        }
        if (!Objects.equals(antes, c.getStatus())) {
            registrar(c.getId(), userId, "status", ChamadoStatus.de(antes).getRotulo(), ChamadoStatus.de(c.getStatus()).getRotulo());
        }
    }

    private ChamadoDO chamadoVisivel(Long id) {
        // selectById passa pelo filtro de escopo de unidade: fora do escopo, "não encontrado"
        ChamadoDO c = id == null ? null : chamadoMapper.selectById(id);
        if (c == null) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        return c;
    }

    private ChamadoDO chamadoExistente(Long id) {
        ChamadoDO c = id == null ? null : chamadoMapper.selectById(id);
        if (c == null) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        return c;
    }

    private ChamadoDO travar(Long id) {
        ChamadoDO c = chamadoMapper.selectByIdParaAtualizar(id);
        if (c == null) {
            throw exception(CHAMADO_NAO_EXISTE);
        }
        return c;
    }

    private void exigirNaoFechado(ChamadoDO c) {
        if (ChamadoStatus.de(c.getStatus()) == ChamadoStatus.FECHADO) {
            throw exception(CHAMADO_FECHADO);
        }
    }

    /** Técnico com permissão de consulta e o chamado dentro do escopo de unidade do perfil. */
    private boolean podeVerComoTecnico(ChamadoDO c, Long userId) {
        return tem(userId, P_QUERY) && escopo.visivel(c.getId());
    }

    private void exigirParticipanteOuTecnico(ChamadoDO c, Long userId) {
        if (!podeVerComoTecnico(c, userId)) {
            exigirParticipante(c, userId);
        }
    }

    private void exigirParticipante(ChamadoDO c, Long userId) {
        boolean participa = atorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId()).stream()
                .anyMatch(a -> userId.equals(a.getUserId()) && a.getTipo() != AtorTipo.TECNICO.getCodigo());
        if (!participa) {
            throw exception(CHAMADO_SEM_ACESSO);
        }
    }

    private boolean ehSolicitante(ChamadoDO c, Long userId) {
        return atorMapper.selectList(ChamadoAtorDO::getChamadoId, c.getId()).stream()
                .anyMatch(a -> userId.equals(a.getUserId()) && a.getTipo() == AtorTipo.SOLICITANTE.getCodigo());
    }

    private boolean temTecnico(Long chamadoId) {
        return atorMapper.selectList(ChamadoAtorDO::getChamadoId, chamadoId).stream()
                .anyMatch(a -> a.getTipo() == AtorTipo.TECNICO.getCodigo());
    }

    private boolean tem(Long userId, String permissao) {
        return permissionApi.hasAnyPermissions(userId, permissao);
    }

    private Long inserirAtor(Long chamadoId, AtorTipo tipo, Long userId, Long equipeId) {
        ChamadoAtorDO a = ChamadoAtorDO.builder().chamadoId(chamadoId).tipo(tipo.getCodigo())
                .userId(userId).equipeId(equipeId).notificar(true).build();
        atorMapper.insert(a);
        return a.getId();
    }

    private void validarPessoas(List<Long>... listas) {
        Set<Long> todos = new HashSet<>();
        for (List<Long> l : listas) {
            todos.addAll(l);
        }
        if (!todos.isEmpty()) {
            adminUserApi.validateUserList(todos);
        }
    }

    private int prioridade(int urgencia, int impacto) {
        return Prioridade.calcular(urgencia, impacto, props.getMatrizPrioridade().isEmpty() ? null : props.getMatrizPrioridade());
    }

    private Long deptDe(Long userId) {
        AdminUserRespDTO u = adminUserApi.getUser(userId);
        return u == null ? null : u.getDeptId();
    }

    private void registrar(Long chamadoId, Long userId, String campo, String de, String para) {
        historicoMapper.insert(HistoricoDO.builder().chamadoId(chamadoId).momento(LocalDateTime.now())
                .userId(userId).usuario(userId == null ? "Sistema" : nomePessoa(userId)).campo(campo)
                .valorAntigo(limitar(de)).valorNovo(limitar(para)).build());
    }

    private void diferenca(Long chamadoId, Long userId, String campo, String de, String para) {
        if (!Objects.equals(de, para)) {
            registrar(chamadoId, userId, campo, de, para);
        }
    }

    private void publicar(Long chamadoId, String acao, Long userId) {
        publicar(chamadoId, acao, userId, null);
    }

    private void publicar(Long chamadoId, String acao, Long userId, Long refId) {
        eventos.publishEvent(new ChamadoEvento(chamadoId, acao, userId, refId));
    }

    private static String limitar(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "...";
    }

    private static ChamadoDO copia(ChamadoDO c) {
        return ChamadoDO.builder().titulo(c.getTitulo()).descricao(c.getDescricao()).tipo(c.getTipo())
                .urgencia(c.getUrgencia()).impacto(c.getImpacto()).prioridade(c.getPrioridade())
                .categoriaId(c.getCategoriaId()).deptId(c.getDeptId()).build();
    }

    private String nomePessoa(Long userId) {
        AdminUserRespDTO u = userId == null ? null : adminUserApi.getUser(userId);
        return u == null ? (userId == null ? null : "#" + userId) : u.getNickname();
    }

    private String nomeCategoria(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return cadastros.obterCategoria(id).getNomeCompleto();
        } catch (Exception e) {
            return "#" + id;
        }
    }

    private String nomeDept(Long id) {
        DeptRespDTO d = id == null ? null : deptApi.getDept(id);
        return d == null ? (id == null ? null : "#" + id) : d.getName();
    }

    private static String rotuloTipo(Integer tipo) {
        return tipo == null ? null : ChamadoTipo.de(tipo).getRotulo();
    }

    private static String nome(Map<Long, AdminUserRespDTO> m, Long id) {
        if (id == null) {
            return null;
        }
        AdminUserRespDTO u = m.get(id);
        return u == null ? "#" + id : u.getNickname();
    }

    /** Converte em lote (nomes de participantes, categoria, unidade), sem calcular ações. */
    public List<ChamadoVOs.Resp> converter(List<ChamadoDO> chamados) {
        return montar(chamados, null, false);
    }

    // ------------------------------------------------------------------ montagem da resposta

    private List<ChamadoVOs.Resp> montar(List<ChamadoDO> chamados, Long userId, boolean comAcoes) {
        if (chamados.isEmpty()) {
            return List.of();
        }
        List<Long> ids = chamados.stream().map(ChamadoDO::getId).toList();
        List<ChamadoAtorDO> atores = atorMapper.selectList(ChamadoAtorDO::getChamadoId, ids);
        Map<Long, AdminUserRespDTO> pessoas = adminUserApi.getUserMap(atores.stream().map(ChamadoAtorDO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> equipes = cadastros.listarEquipes(false).stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e.getNome()));
        Map<Long, String> categorias = cadastros.listarCategorias(false, false).stream()
                .collect(Collectors.toMap(CategoriaDO::getId, CategoriaDO::getNomeCompleto));
        Map<Long, String> origens = cadastros.listarOrigens(false).stream()
                .collect(Collectors.toMap(OrigemDO::getId, OrigemDO::getNome));
        Map<Long, DeptRespDTO> depts = deptApi.getDeptMap(chamados.stream().map(ChamadoDO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, List<ChamadoAtorDO>> porChamado = atores.stream().collect(Collectors.groupingBy(ChamadoAtorDO::getChamadoId));
        Map<Long, String> slas = slaMapper.selectList().stream().collect(Collectors.toMap(SlaDO::getId, SlaDO::getNome));
        Map<Long, SatisfacaoDO> pesquisas = satisfacaoMapper.selectList(SatisfacaoDO::getChamadoId, ids).stream()
                .collect(Collectors.toMap(SatisfacaoDO::getChamadoId, x -> x, (a, b) -> a));
        Map<Long, ExecucaoAdDO> execucoes = execucaoAdMapper.selectList(ExecucaoAdDO::getChamadoId, ids).stream()
                .collect(Collectors.toMap(ExecucaoAdDO::getChamadoId, x -> x, (a, b) -> a));
        LocalDateTime agora = LocalDateTime.now();

        return chamados.stream().map(c -> {
            ChamadoVOs.Resp r = new ChamadoVOs.Resp();
            r.setId(c.getId());
            r.setTitulo(c.getTitulo());
            r.setDescricao(comAcoes ? c.getDescricao() : null);
            r.setTipo(c.getTipo());
            r.setTipoRotulo(ChamadoTipo.de(c.getTipo()).getRotulo());
            ChamadoStatus st = ChamadoStatus.de(c.getStatus());
            r.setStatus(c.getStatus());
            r.setStatusRotulo(st.getRotulo());
            r.setUrgencia(c.getUrgencia());
            r.setImpacto(c.getImpacto());
            r.setPrioridade(c.getPrioridade());
            r.setPrioridadeRotulo(Prioridade.rotulo(c.getPrioridade()));
            r.setCategoriaId(c.getCategoriaId());
            r.setCategoria(categorias.get(c.getCategoriaId()));
            r.setOrigemId(c.getOrigemId());
            r.setOrigem(origens.get(c.getOrigemId()));
            r.setDeptId(c.getDeptId());
            DeptRespDTO d = depts.get(c.getDeptId());
            r.setDept(d == null ? null : d.getName());
            r.setAbertoEm(c.getAbertoEm());
            r.setAssumidoEm(c.getAssumidoEm());
            r.setSolucionadoEm(c.getSolucionadoEm());
            r.setFechadoEm(c.getFechadoEm());
            r.setPrazoAtendimento(c.getPrazoAtendimento());
            r.setPrazoSolucao(c.getPrazoSolucao());
            // pendente está com o relógio parado: não conta como vencido
            r.setSlaVencido(st.aberto() && st != ChamadoStatus.PENDENTE && c.getPrazoSolucao() != null
                    && c.getPrazoSolucao().isBefore(agora));
            r.setAtendimentoVencido(c.getAssumidoEm() == null && st.aberto() && c.getPrazoAtendimento() != null
                    && c.getPrazoAtendimento().isBefore(agora));
            r.setSlaAtendimentoId(c.getSlaAtendimentoId());
            r.setSlaAtendimento(slas.get(c.getSlaAtendimentoId()));
            r.setSlaSolucaoId(c.getSlaSolucaoId());
            r.setSlaSolucao(slas.get(c.getSlaSolucaoId()));
            SatisfacaoDO pesq = pesquisas.get(c.getId());
            if (pesq != null) {
                r.setSatisfacaoPendente(pesq.getRespondidaEm() == null);
                r.setSatisfacaoNota(pesq.getNota());
                r.setSatisfacaoComentario(pesq.getComentario());
            }
            r.setEsperaSeg(c.getEsperaSeg());
            r.setTempoAssumirSeg(c.getTempoAssumirSeg());
            r.setTempoSolucaoSeg(c.getTempoSolucaoSeg());
            r.setTempoGastoSeg(c.getTempoGastoSeg());
            r.setOrigemAutomatica(c.getOrigemAutomatica());
            int validacao = c.getValidacao() == null ? Aprovacoes.NENHUMA : c.getValidacao();
            r.setValidacao(validacao);
            r.setValidacaoRotulo(Aprovacoes.rotulo(validacao));
            ExecucaoAdDO exec = execucoes.get(c.getId());
            if (exec != null) {
                r.setExecucaoAdStatus(exec.getStatus());
                r.setExecucaoAdAcao(exec.getAcao());
                r.setSenhaDisponivel(exec.getSenhaCifrada() != null);
            }
            Function<ChamadoAtorDO, ChamadoVOs.Ator> conv = a -> {
                ChamadoVOs.Ator x = new ChamadoVOs.Ator();
                x.setId(a.getId());
                x.setTipo(a.getTipo());
                x.setUserId(a.getUserId());
                x.setEquipeId(a.getEquipeId());
                x.setEmail(a.getEmail());
                x.setNome(a.getUserId() != null ? nome(pessoas, a.getUserId())
                        : a.getEquipeId() != null ? equipes.getOrDefault(a.getEquipeId(), "#" + a.getEquipeId()) : a.getEmail());
                return x;
            };
            for (ChamadoAtorDO a : porChamado.getOrDefault(c.getId(), List.of())) {
                switch (AtorTipo.de(a.getTipo())) {
                    case SOLICITANTE -> r.getSolicitantes().add(conv.apply(a));
                    case TECNICO -> r.getTecnicos().add(conv.apply(a));
                    case OBSERVADOR -> r.getObservadores().add(conv.apply(a));
                }
            }
            if (comAcoes) {
                r.setAcoes(acoes(c, st, porChamado.getOrDefault(c.getId(), List.of()), userId));
                if (Boolean.TRUE.equals(r.getSenhaDisponivel()) && r.getSolicitantes().stream()
                        .anyMatch(x -> userId.equals(x.getUserId()))) {
                    r.getAcoes().add("ver-senha");
                }
            }
            return r;
        }).toList();
    }

    /** O que o usuário logado pode fazer agora; a tela mostra só esses botões (o backend confere de novo). */
    private List<String> acoes(ChamadoDO c, ChamadoStatus st, List<ChamadoAtorDO> atores, Long userId) {
        List<String> a = new ArrayList<>();
        boolean tecnico = podeVerComoTecnico(c, userId);
        boolean solicitante = atores.stream().anyMatch(x -> userId.equals(x.getUserId()) && x.getTipo() == AtorTipo.SOLICITANTE.getCodigo());
        boolean souTecnico = atores.stream().anyMatch(x -> userId.equals(x.getUserId()) && x.getTipo() == AtorTipo.TECNICO.getCodigo());
        if (st != ChamadoStatus.FECHADO) {
            a.add("acompanhar");
        }
        if (tecnico && st != ChamadoStatus.FECHADO) {
            if (tem(userId, P_UPDATE)) a.add("editar");
            if (tem(userId, P_ATRIBUIR)) a.add("atribuir");
            if (tem(userId, P_ATENDER)) {
                if (st.aberto() && !souTecnico) a.add("assumir");
                if (st.aberto()) a.add("tarefa");
                if (st.aberto()) a.add("solucionar");
                if (st.aberto() && st != ChamadoStatus.PENDENTE) a.add("pendente");
                if (st == ChamadoStatus.PENDENTE) a.add("retomar");
                if (st == ChamadoStatus.SOLUCIONADO) a.add("reabrir");
            }
            if (tem(userId, P_FECHAR)) a.add("fechar");
        }
        if (st == ChamadoStatus.SOLUCIONADO && (solicitante || (tecnico && tem(userId, P_FECHAR)))) {
            a.add("avaliar-solucao");
        }
        if (tecnico && st == ChamadoStatus.FECHADO && tem(userId, P_REABRIR)) a.add("reabrir");
        if (solicitante && st == ChamadoStatus.FECHADO
                && satisfacaoMapper.selectCount(new LambdaQueryWrapperX<SatisfacaoDO>().eq(SatisfacaoDO::getChamadoId, c.getId())
                .isNull(SatisfacaoDO::getRespondidaEm)) > 0) {
            a.add("avaliar-atendimento");
        }
        if (tecnico && st != ChamadoStatus.FECHADO && tem(userId, P_PEDIR_APROVACAO)) a.add("pedir-aprovacao");
        if (tecnico && tem(userId, P_DELETE)) a.add("excluir");
        return a;
    }

}
