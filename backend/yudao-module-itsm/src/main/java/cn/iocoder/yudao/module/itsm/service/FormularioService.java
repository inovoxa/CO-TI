package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CadastroVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.CatalogoVOs;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ChamadoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.CategoriaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FormularioDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FormularioRespostaDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.FormularioMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.FormularioRespostaMapper;
import cn.iocoder.yudao.module.itsm.dominio.Aprovacoes;
import cn.iocoder.yudao.module.itsm.dominio.Formulario;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Catálogo de serviços (GLPI: Glpi\Form + destino "chamado"): cadastro dos formulários, importação no formato do
 * formularios_definicoes.json e envio, que abre o chamado, pede a aprovação e agenda a operação no AD.
 */
@Service
public class FormularioService {

    @Resource
    private FormularioMapper formularioMapper;
    @Resource
    private FormularioRespostaMapper respostaMapper;
    @Resource
    private ChamadoMapper chamadoMapper;
    @Resource
    private ChamadoService chamadoService;
    @Resource
    private AprovacaoService aprovacaoService;
    @Resource
    private ExecucaoAdService execucaoAdService;
    @Resource
    private CadastroService cadastros;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private VinculoAdMapper vinculoAdMapper;

    // ------------------------------------------------------------------ cadastro

    public List<CatalogoVOs.FormularioResp> listar() {
        List<FormularioDO> l = formularioMapper.selectList(new LambdaQueryWrapperX<FormularioDO>()
                .orderByAsc(FormularioDO::getOrdem).orderByAsc(FormularioDO::getGrupo).orderByAsc(FormularioDO::getNome));
        Map<Long, String> categorias = cadastros.listarCategorias(false, false).stream()
                .collect(Collectors.toMap(CategoriaDO::getId, CategoriaDO::getNomeCompleto));
        Map<Long, AdminUserRespDTO> pessoas = adminUserApi.getUserMap(l.stream().map(FormularioDO::getAprovadorId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return l.stream().map(f -> {
            CatalogoVOs.FormularioResp r = new CatalogoVOs.FormularioResp();
            r.setId(f.getId());
            r.setCodigo(f.getCodigo());
            r.setNome(f.getNome());
            r.setDescricao(f.getDescricao());
            r.setGrupo(f.getGrupo());
            r.setIcone(f.getIcone());
            r.setCategoriaId(f.getCategoriaId());
            r.setCategoria(categorias.get(f.getCategoriaId()));
            r.setTipo(f.getTipo());
            r.setUrgencia(f.getUrgencia());
            r.setImpacto(f.getImpacto());
            r.setTituloModelo(f.getTituloModelo());
            r.setDefinicao(definicao(f));
            r.setAprovacao(f.getAprovacao());
            r.setAprovadorCampo(f.getAprovadorCampo());
            r.setAprovadorId(f.getAprovadorId());
            AdminUserRespDTO u = pessoas.get(f.getAprovadorId());
            r.setAprovador(u == null ? null : u.getNickname());
            r.setAcaoAd(f.getAcaoAd());
            r.setAtivo(f.getAtivo());
            r.setOrdem(f.getOrdem());
            return r;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(CatalogoVOs.FormularioSalvar req) {
        List<String> erros = new ArrayList<>(Formulario.validarDefinicao(req.getDefinicao()));
        Set<String> perguntas = req.getDefinicao() == null || req.getDefinicao().secoes() == null ? Set.of()
                : req.getDefinicao().perguntas().stream().map(Formulario.Pergunta::name).collect(Collectors.toSet());
        switch (req.getAprovacao()) {
            case "campo" -> {
                if (req.getAprovadorCampo() == null || !perguntas.contains(req.getAprovadorCampo())) {
                    erros.add("Escolha a pergunta que traz o e-mail do aprovador");
                }
            }
            case "fixo" -> {
                if (req.getAprovadorId() == null) {
                    erros.add("Escolha o aprovador");
                } else {
                    adminUserApi.validateUser(req.getAprovadorId());
                }
            }
            default -> {
            }
        }
        if (req.getTituloModelo() != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([a-z][a-z0-9_]*)}").matcher(req.getTituloModelo());
            while (m.find()) {
                if (!perguntas.contains(m.group(1))) {
                    erros.add("O título usa {" + m.group(1) + "}, que não é uma pergunta do formulário");
                }
            }
        }
        if (!erros.isEmpty()) {
            throw exception(FORMULARIO_INVALIDO, String.join("; ", erros));
        }
        if (req.getCategoriaId() != null) {
            cadastros.obterCategoria(req.getCategoriaId());
        }
        String codigo = req.getCodigo() == null || req.getCodigo().isBlank() ? null : req.getCodigo().trim();
        if (codigo != null) {
            FormularioDO outro = formularioMapper.selectOne(FormularioDO::getCodigo, codigo);
            if (outro != null && !outro.getId().equals(req.getId())) {
                throw exception(FORMULARIO_CODIGO_DUPLICADO);
            }
        }
        FormularioDO f = FormularioDO.builder().id(req.getId()).codigo(codigo).nome(req.getNome().trim())
                .descricao(req.getDescricao()).grupo(req.getGrupo().trim()).icone(req.getIcone())
                .categoriaId(req.getCategoriaId()).tipo(req.getTipo()).urgencia(req.getUrgencia()).impacto(req.getImpacto())
                .tituloModelo(req.getTituloModelo()).definicao(JsonUtils.toJsonString(req.getDefinicao()))
                .aprovacao(req.getAprovacao()).aprovadorCampo("campo".equals(req.getAprovacao()) ? req.getAprovadorCampo() : null)
                .aprovadorId("fixo".equals(req.getAprovacao()) ? req.getAprovadorId() : null)
                .acaoAd(req.getAcaoAd() == null || req.getAcaoAd().isBlank() ? null : req.getAcaoAd())
                .ativo(req.getAtivo()).ordem(req.getOrdem()).build();
        if (req.getId() == null) {
            formularioMapper.insert(f);
        } else {
            obter(req.getId());
            formularioMapper.updateById(f);
            // campos que podem voltar a vazio
            formularioMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FormularioDO>()
                    .eq(FormularioDO::getId, f.getId())
                    .set(FormularioDO::getCodigo, f.getCodigo()).set(FormularioDO::getCategoriaId, f.getCategoriaId())
                    .set(FormularioDO::getAprovadorCampo, f.getAprovadorCampo()).set(FormularioDO::getAprovadorId, f.getAprovadorId())
                    .set(FormularioDO::getAcaoAd, f.getAcaoAd()).set(FormularioDO::getTituloModelo, f.getTituloModelo()));
        }
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        obter(id);
        if (respostaMapper.selectCount(FormularioRespostaDO::getFormularioId, id) > 0) {
            throw exception(FORMULARIO_EM_USO);
        }
        formularioMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ catálogo (portal)

    public List<CatalogoVOs.FormularioCatalogo> catalogo() {
        // pela ordem: a tela agrupa na ordem em que os grupos aparecem (o 1º formulário de cada grupo manda)
        return formularioMapper.selectList(new LambdaQueryWrapperX<FormularioDO>().eq(FormularioDO::getAtivo, true)
                        .orderByAsc(FormularioDO::getOrdem).orderByAsc(FormularioDO::getGrupo).orderByAsc(FormularioDO::getNome))
                .stream().map(f -> {
                    CatalogoVOs.FormularioCatalogo c = new CatalogoVOs.FormularioCatalogo();
                    c.setId(f.getId());
                    c.setCodigo(f.getCodigo());
                    c.setNome(f.getNome());
                    c.setDescricao(f.getDescricao());
                    c.setGrupo(f.getGrupo());
                    c.setIcone(f.getIcone());
                    c.setDefinicao(definicao(f));
                    c.setPrecisaAprovacao(!"nenhuma".equals(f.getAprovacao()));
                    return c;
                }).toList();
    }

    /**
     * Envio do formulário: valida, abre o chamado (passa pelas regras), guarda as respostas, pede a aprovação
     * configurada e agenda a operação no AD (que só roda depois de aprovada).
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Long enviar(CatalogoVOs.EnviarReq req, Long userId) {
        return enviar(req, userId, null, Map.of());
    }

    /**
     * Envio com uma observação para o técnico e o aprovador (no topo da descrição e no pedido de aprovação) e dados
     * extras para a operação no AD (ex.: {@code entrega_senha=email} no pedido feito pela tela de login).
     */
    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Long enviar(CatalogoVOs.EnviarReq req, Long userId, String observacao, Map<String, String> extrasAd) {
        FormularioDO f = obter(req.getFormularioId());
        if (!Boolean.TRUE.equals(f.getAtivo())) {
            throw exception(FORMULARIO_NAO_EXISTE);
        }
        Formulario.Definicao d = definicao(f);
        Formulario.Resultado r = Formulario.validar(d, req.getRespostas());
        if (!r.valido()) {
            Map<String, String> rotulos = d.perguntas().stream()
                    .collect(Collectors.toMap(Formulario.Pergunta::name, Formulario.Pergunta::label, (a, b) -> a));
            throw exception(FORMULARIO_RESPOSTAS_INVALIDAS, r.erros().entrySet().stream()
                    .map(e -> rotulos.getOrDefault(e.getKey(), e.getKey()) + " (" + e.getValue() + ")")
                    .collect(Collectors.joining("; ")));
        }
        Map<String, String> respostas = r.respostas();
        // quem aprova é resolvido antes de abrir: sem aprovador, o pedido nem é criado
        Long aprovador = switch (f.getAprovacao()) {
            case "gestor" -> aprovacaoService.gestorDe(userId).orElseThrow(() -> exception(APROVACAO_SEM_APROVADOR));
            case "campo" -> aprovacaoService.usuarioPorEmail(respostas.get(f.getAprovadorCampo()))
                    .orElseThrow(() -> exception(APROVACAO_SEM_APROVADOR));
            case "fixo" -> f.getAprovadorId();
            default -> null;
        };
        if (aprovador != null && aprovador.equals(userId)) {
            throw exception(APROVACAO_PROPRIO_SOLICITANTE);
        }

        ChamadoVOs.CriarReq c = new ChamadoVOs.CriarReq();
        c.setTitulo(Formulario.titulo(f.getTituloModelo(), f.getNome(), respostas));
        c.setDescricao((observacao == null ? "" : "<p><b>⚠️ " + Formulario.escaparHtml(observacao) + "</b></p>")
                + "<p><b>Pedido pelo catálogo: " + Formulario.escaparHtml(f.getNome()) + "</b></p>"
                + Formulario.descricaoHtml(d, respostas));
        c.setTipo(f.getTipo());
        c.setUrgencia(f.getUrgencia());
        c.setImpacto(f.getImpacto());
        c.setCategoriaId(f.getCategoriaId());
        Long chamadoId = chamadoService.criar(c, userId, f.getId());

        respostaMapper.insert(FormularioRespostaDO.builder().formularioId(f.getId()).chamadoId(chamadoId).userId(userId)
                .respostas(JsonUtils.toJsonString(respostas)).criadoEm(LocalDateTime.now()).build());
        if (aprovador != null) {
            aprovacaoService.solicitar(chamadoId, List.of(aprovador), "Pedido pelo formulário \"" + f.getNome() + "\""
                    + (observacao == null ? "" : ". " + observacao), null);
        }
        if (f.getAcaoAd() != null) {
            ChamadoDO ch = chamadoMapper.selectById(chamadoId);
            boolean aguarda = ch.getValidacao() != null && ch.getValidacao() == Aprovacoes.AGUARDANDO;
            Map<String, String> dados = new LinkedHashMap<>(respostas);
            dados.put("chamado_id", String.valueOf(chamadoId));
            dados.putAll(extrasAd);
            // conta do AD de quem pediu, quando o formulário não pergunta (AcessoPasta concede a pasta a esse login)
            VinculoAdDO v = vinculoAdMapper.selectByUserId(userId);
            if (v != null && v.getLogin() != null) {
                dados.putIfAbsent("login_solicitante", v.getLogin());
            }
            execucaoAdService.criar(chamadoId, f.getAcaoAd(), dados, aguarda);
            if ("desligado".equals(execucaoAdService.modo())) {
                chamadoService.acompanhamentoSistema(chamadoId, null, "A automação no AD está desligada neste servidor: "
                        + "execute a operação \"" + f.getAcaoAd() + "\" manualmente e registre a solução.", true);
            }
        }
        return chamadoId;
    }

    /** Respostas do formulário que abriu o chamado (para a equipe técnica). */
    public Map<String, String> respostasDoChamado(Long chamadoId) {
        FormularioRespostaDO r = respostaMapper.selectOne(FormularioRespostaDO::getChamadoId, chamadoId);
        return r == null ? Map.of() : JsonUtils.parseObject(r.getRespostas(), new TypeReference<Map<String, String>>() {
        });
    }

    // ------------------------------------------------------------------ importação

    /**
     * Importa formulários no formato do formularios_definicoes.json ({formularios:[{menu, nome, descricao, icon,
     * categoria_form, ticket:{type, urgency, impact}, secoes}]}) com as extensões do CO-TI: codigo, titulo_modelo,
     * aprovacao, aprovador_campo, acao_ad e categoria. Atualiza pelo código (menu); cria os que não existem.
     *
     * @param criarCategorias cria "grupo > formulário" quando a categoria não existe
     */
    @Transactional(rollbackFor = Exception.class)
    public CatalogoVOs.ImportacaoResp importar(String json, boolean criarCategorias) {
        Map<String, Object> raiz;
        try {
            raiz = JsonUtils.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw exception(FORMULARIO_INVALIDO, "Arquivo JSON inválido");
        }
        Object lista = raiz == null ? null : raiz.get("formularios");
        if (!(lista instanceof List<?> itens) || itens.isEmpty()) {
            throw exception(FORMULARIO_INVALIDO, "O arquivo não tem a lista \"formularios\"");
        }
        CatalogoVOs.ImportacaoResp resp = new CatalogoVOs.ImportacaoResp();
        int ordem = 0;
        for (Object o : itens) {
            ordem++;
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            String nome = texto(m.get("nome"));
            try {
                CatalogoVOs.FormularioSalvar s = new CatalogoVOs.FormularioSalvar();
                String codigo = texto(m.get("codigo")) != null ? texto(m.get("codigo")) : texto(m.get("menu"));
                s.setCodigo(codigo);
                s.setNome(nome);
                s.setDescricao(texto(m.get("descricao")));
                s.setGrupo(Optional.ofNullable(texto(m.get("grupo"))).orElse(Optional.ofNullable(texto(m.get("categoria_form"))).orElse("Geral")));
                s.setIcone(texto(m.get("icon")));
                Map<?, ?> ticket = m.get("ticket") instanceof Map<?, ?> t ? t : Map.of();
                s.setTipo(inteiro(ticket.get("type"), 2));
                s.setUrgencia(inteiro(ticket.get("urgency"), 3));
                s.setImpacto(inteiro(ticket.get("impact"), 3));
                s.setTituloModelo(texto(m.get("titulo_modelo")));
                s.setAprovacao(Optional.ofNullable(texto(m.get("aprovacao"))).orElse("nenhuma"));
                s.setAprovadorCampo(texto(m.get("aprovador_campo")));
                s.setAcaoAd(texto(m.get("acao_ad")));
                s.setOrdem(m.get("ordem") != null ? inteiro(m.get("ordem"), ordem) : ordem);
                s.setDefinicao(JsonUtils.parseObject(JsonUtils.toJsonString(Map.of("secoes", m.get("secoes"))),
                        Formulario.Definicao.class));
                s.setCategoriaId(categoria(Optional.ofNullable(texto(m.get("categoria"))).orElse(nome), s.getGrupo(),
                        criarCategorias, resp));
                FormularioDO existente = codigo == null ? null : formularioMapper.selectOne(FormularioDO::getCodigo, codigo);
                if (existente != null) {
                    s.setId(existente.getId());
                    s.setAtivo(existente.getAtivo());
                }
                salvar(s);
                if (existente != null) {
                    resp.setAtualizados(resp.getAtualizados() + 1);
                } else {
                    resp.setCriados(resp.getCriados() + 1);
                }
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
                resp.getAvisos().add((nome == null ? "#" + ordem : nome) + ": " + e.getMessage());
            } catch (RuntimeException e) {
                resp.getAvisos().add((nome == null ? "#" + ordem : nome) + ": definição ilegível (" + e.getClass().getSimpleName() + ")");
            }
        }
        return resp;
    }

    private Long categoria(String nome, String grupo, boolean criar, CatalogoVOs.ImportacaoResp resp) {
        List<CategoriaDO> todas = cadastros.listarCategorias(false, false);
        String completo = grupo + " > " + nome;
        Optional<CategoriaDO> achou = todas.stream().filter(c -> c.getNomeCompleto().equalsIgnoreCase(completo)).findFirst();
        if (achou.isEmpty()) {
            achou = todas.stream().filter(c -> c.getNome().equalsIgnoreCase(nome)).findFirst();
        }
        if (achou.isPresent()) {
            return achou.get().getId();
        }
        if (!criar) {
            resp.getAvisos().add(nome + ": categoria não encontrada; o formulário ficou sem categoria");
            return null;
        }
        Long pai = todas.stream().filter(c -> (c.getParentId() == null || c.getParentId() == 0)
                && c.getNome().equalsIgnoreCase(grupo)).map(CategoriaDO::getId).findFirst().orElseGet(() -> {
            CadastroVOs.CategoriaSalvar g = new CadastroVOs.CategoriaSalvar();
            g.setNome(limitar(grupo, 100));
            return cadastros.salvarCategoria(g);
        });
        CadastroVOs.CategoriaSalvar c = new CadastroVOs.CategoriaSalvar();
        c.setParentId(pai);
        c.setNome(limitar(nome, 100));
        return cadastros.salvarCategoria(c);
    }

    // ------------------------------------------------------------------ internos

    public FormularioDO obter(Long id) {
        FormularioDO f = id == null ? null : formularioMapper.selectById(id);
        if (f == null) {
            throw exception(FORMULARIO_NAO_EXISTE);
        }
        return f;
    }

    private static Formulario.Definicao definicao(FormularioDO f) {
        return JsonUtils.parseObject(f.getDefinicao(), Formulario.Definicao.class);
    }

    private static String texto(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o).trim();
    }

    private static int inteiro(Object o, int padrao) {
        try {
            return o == null ? padrao : Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return padrao;
        }
    }

    private static String limitar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

}
