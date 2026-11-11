package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.monitoramento.service.ImportacaoPlanilha.*;

/**
 * Importa a planilha que o Exportar de unidades gera. A unidade é achada pelo nome, e o departamento também vem pelo nome.
 * Coluna que não veio mantém o cadastro; a coluna Monitores é só informativa.
 *
 * <p>Departamento que não existe (outra instalação, por exemplo) é criado sob o "Departamento superior" da planilha, ou sob
 * a raiz quando ele vem em branco, desde que quem importa possa criar departamentos. Superior que também não existe é
 * criado sob a raiz.
 *
 * <p>Departamento que já existe muda de lugar quando o "Departamento superior" da planilha é outro, desde que quem importa
 * possa editar departamentos. Em branco é a raiz, como o Exportar escreve; sem a coluna, nada muda de lugar.
 */
@Service
public class ImportacaoUnidadeService {

    static final String PERMISSAO_CRIAR_DEPARTAMENTO = "system:dept:create";
    static final String PERMISSAO_EDITAR_DEPARTAMENTO = "system:dept:update";
    /** Marca a unidade cujo departamento só existirá ao aplicar, para validar o resto da linha na prévia */
    private static final Long A_CRIAR = -1L;

    private static final String NOME = "nome", DEPARTAMENTO = "departamento", SUPERIOR = "superior", CEP = "cep",
            LOGRADOURO = "logradouro", NUMERO = "numero", COMPLEMENTO = "complemento", BAIRRO = "bairro", CIDADE = "cidade",
            UF = "uf", LATITUDE = "latitude", LONGITUDE = "longitude", ATIVA = "ativa";

    private static final Map<String, String> TITULOS = Map.ofEntries(Map.entry("unidade", NOME), Map.entry("departamento", DEPARTAMENTO),
            Map.entry("departamentosuperior", SUPERIOR), Map.entry("cep", CEP), Map.entry("logradouro", LOGRADOURO),
            Map.entry("numero", NUMERO), Map.entry("complemento", COMPLEMENTO), Map.entry("bairro", BAIRRO), Map.entry("cidade", CIDADE),
            Map.entry("uf", UF), Map.entry("latitude", LATITUDE), Map.entry("longitude", LONGITUDE), Map.entry("ativa", ATIVA));

    @Resource
    private UnidadeService unidadeService;
    @Resource
    private DeptService deptService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private Validator validator;

    /** Departamento a criar; {@code superior} é a chave de outro a criar ou nulo, e então vale {@code paiId}. */
    private record NovoDept(String nome, String superior, Long paiId, String onde) {
    }

    /** Departamento que vai para outro superior: {@code paiChave} é a chave de um a criar ou nulo, e então vale {@code paiId}. */
    private record Mudar(DeptDO dept, Long paiId, String paiChave, String paraNome) {
    }

    private record Plano(List<UnidadeVOs.Unidade> gravar, ImportacaoVOs.Previa previa, Map<String, NovoDept> criar,
                         Map<UnidadeVOs.Unidade, String> pendentes, Map<Long, Mudar> mudar) {
    }

    /** Árvore de agora: departamentos pelo nome e pelo id, a raiz e o que quem importa pode fazer com departamentos. */
    private record Contexto(Map<String, List<DeptDO>> porNome, Map<Long, DeptDO> porId, Long raizId, String raizNome,
                            boolean podeCriar, boolean podeEditar) {
    }

    public ImportacaoVOs.Previa analisar(InputStream arquivo) {
        return planejar(arquivo, getLoginUserId()).previa();
    }

    /** Refaz a análise sobre o cadastro de agora e aplica; quem não for indicada em {@code removerIds} não é removida. */
    @Transactional(rollbackFor = Exception.class)
    public ImportacaoVOs.Resultado aplicar(InputStream arquivo, Collection<Long> removerIds, Long userId) {
        Plano plano = planejar(arquivo, userId);
        Set<Long> remover = aRemover(plano.previa(), removerIds);
        Map<String, Long> criados = new HashMap<>();
        plano.criar().keySet().forEach(k -> criar(k, plano.criar(), criados));
        plano.pendentes().forEach((vo, k) -> vo.setDeptId(criados.get(k)));
        List<String> mudados = new ArrayList<>();
        plano.mudar().values().forEach(m -> {
            DeptSaveReqVO req = new DeptSaveReqVO();
            BeanUtils.copyProperties(m.dept(), req);
            req.setParentId(m.paiChave() == null ? m.paiId() : criar(m.paiChave(), plano.criar(), criados));
            deptService.updateDept(req);
            mudados.add(m.dept().getName() + " → " + m.paraNome());
        });
        unidadeService.aplicarImportacao(plano.gravar(), remover);
        ImportacaoVOs.Resultado r = resultado(plano.previa(), remover.size());
        auditoriaService.registrar("monitoramento.importar_unidades", userId, null, Map.of("adicionados", r.getAdicionados(),
                "atualizados", r.getAtualizados(), "removidos", nomes(plano.previa(), remover), "recusados", r.getRecusados(),
                "departamentosCriados", plano.previa().getDepartamentos(), "departamentosMudados", mudados));
        return r;
    }

    private Long criar(String chave, Map<String, NovoDept> criar, Map<String, Long> criados) {
        Long id = criados.get(chave);
        if (id != null) {
            return id;
        }
        NovoDept d = criar.get(chave);
        DeptSaveReqVO req = new DeptSaveReqVO();
        req.setName(d.nome());
        req.setParentId(d.superior() == null ? d.paiId() : criar(d.superior(), criar, criados));
        req.setSort(0);
        req.setStatus(CommonStatusEnum.ENABLE.getStatus());
        id = deptService.createDept(req);
        criados.put(chave, id);
        return id;
    }

    private Plano planejar(InputStream arquivo, Long userId) {
        List<Planilha.Linha> linhas = ler(arquivo, TITULOS, NOME, "Unidade");
        List<UnidadeVOs.Unidade> todas = unidadeService.listar();
        Map<String, List<UnidadeVOs.Unidade>> porNome = todas.stream().collect(Collectors.groupingBy(u -> chave(u.getNome())));
        List<DeptDO> depts = deptService.getDeptList(new DeptListReqVO());
        List<DeptDO> raizes = depts.stream().filter(d -> Objects.equals(d.getParentId(), DeptDO.PARENT_ID_ROOT)).toList();
        DeptDO raiz = raizes.size() == 1 ? raizes.get(0) : null;
        Contexto ctx = new Contexto(depts.stream().collect(Collectors.groupingBy(d -> chave(d.getName()))),
                depts.stream().collect(Collectors.toMap(DeptDO::getId, d -> d, (a, b) -> a)),
                raiz == null ? DeptDO.PARENT_ID_ROOT : raiz.getId(), raiz == null ? null : raiz.getName(),
                userId != null && permissionService.hasAnyPermissions(userId, PERMISSAO_CRIAR_DEPARTAMENTO),
                userId != null && permissionService.hasAnyPermissions(userId, PERMISSAO_EDITAR_DEPARTAMENTO));

        Plano plano = new Plano(new ArrayList<>(), new ImportacaoVOs.Previa(), new LinkedHashMap<>(), new IdentityHashMap<>(),
                new LinkedHashMap<>());
        // quem aparece em linha recusada também conta como presente: um erro de digitação não pode virar remoção
        Set<Long> presentes = new HashSet<>();
        Set<Long> usadas = new HashSet<>();
        Set<String> novas = new HashSet<>();
        for (Planilha.Linha l : linhas) {
            Map<String, NovoDept> antes = new LinkedHashMap<>(plano.criar());
            Map<Long, Mudar> mudarAntes = new LinkedHashMap<>(plano.mudar());
            try {
                processar(l, porNome, ctx, plano, presentes, usadas, novas);
            } catch (ServiceException | IllegalArgumentException e) {
                // linha recusada não deixa departamento para criar nem para mudar de lugar
                plano.criar().clear();
                plano.criar().putAll(antes);
                plano.mudar().clear();
                plano.mudar().putAll(mudarAntes);
                recusar(plano.previa(), l, e);
            }
        }
        plano.criar().values().forEach(d -> plano.previa().getDepartamentos().add(d.nome() + " (em " + d.onde() + ")"));
        todas.stream().filter(u -> !presentes.contains(u.getId())).forEach(u -> plano.previa().getAusentes().add(item(u.getId(), u.getNome(), List.of(),
                u.getMonitores() != null && u.getMonitores() > 0 ? u.getMonitores() + " monitor(es) ficarão sem unidade." : null)));
        return plano;
    }

    private void processar(Planilha.Linha l, Map<String, List<UnidadeVOs.Unidade>> porNome, Contexto ctx, Plano plano,
                           Set<Long> presentes, Set<Long> usadas, Set<String> novas) {
        Map<String, String> c = l.campos();
        String nome = vazio(c.get(NOME));
        if (nome == null) {
            throw new IllegalArgumentException("informe o nome da unidade");
        }
        List<UnidadeVOs.Unidade> candidatas = porNome.getOrDefault(chave(nome), List.of());
        candidatas.forEach(u -> presentes.add(u.getId()));
        if (candidatas.size() > 1) {
            throw new IllegalArgumentException("há " + candidatas.size() + " unidades com este nome");
        }
        UnidadeVOs.Unidade atual = candidatas.isEmpty() ? null : candidatas.get(0);
        if (atual != null ? !usadas.add(atual.getId()) : !novas.add(chave(nome))) {
            throw new IllegalArgumentException("a unidade \"" + nome + "\" aparece mais de uma vez no arquivo");
        }

        UnidadeVOs.Unidade vo = new UnidadeVOs.Unidade();
        if (atual != null) {
            BeanUtils.copyProperties(atual, vo);
        }
        vo.setNome(nome);
        String deptNome = departamento(vo, c, atual, ctx, plano);
        texto(c, CEP, vo::setCep);
        texto(c, LOGRADOURO, vo::setLogradouro);
        texto(c, NUMERO, vo::setNumero);
        texto(c, COMPLEMENTO, vo::setComplemento);
        texto(c, BAIRRO, vo::setBairro);
        texto(c, CIDADE, vo::setCidade);
        texto(c, UF, vo::setUf);
        coordenada(c, LATITUDE, "latitude", vo::setLatitude);
        coordenada(c, LONGITUDE, "longitude", vo::setLongitude);
        if (vazio(c.get(ATIVA)) != null) {
            vo.setAtiva(Planilha.lerSimNao(c.get(ATIVA)));
        }
        if (vo.getLatitude() == null || vo.getLongitude() == null) {
            throw new IllegalArgumentException("informe a latitude e a longitude");
        }
        validar(validator, vo);
        String pendente = A_CRIAR.equals(vo.getDeptId()) ? chave(deptNome) : null;
        MonitorUnitDO previsto = unidadeService.previsto(vo, pendente != null);

        List<ImportacaoVOs.Mudanca> mudancas = new ArrayList<>();
        UnidadeVOs.Unidade de = atual == null ? new UnidadeVOs.Unidade() : atual;
        comparar(mudancas, "Unidade", de.getNome(), previsto.getName());
        comparar(mudancas, "Departamento", de.getDeptNome(), deptNome);
        if (c.containsKey(SUPERIOR) && pendente == null) {
            superior(vo.getDeptId(), vazio(c.get(SUPERIOR)), ctx, plano, mudancas);
        }
        comparar(mudancas, "CEP", de.getCep(), previsto.getCep());
        comparar(mudancas, "Logradouro", de.getLogradouro(), previsto.getLogradouro());
        comparar(mudancas, "Número", de.getNumero(), previsto.getNumero());
        comparar(mudancas, "Complemento", de.getComplemento(), previsto.getComplemento());
        comparar(mudancas, "Bairro", de.getBairro(), previsto.getBairro());
        comparar(mudancas, "Cidade", de.getCidade(), previsto.getCity());
        comparar(mudancas, "UF", de.getUf(), previsto.getUf());
        comparar(mudancas, "Latitude", coordenada(de.getLatitude()), coordenada(previsto.getLatitude()));
        comparar(mudancas, "Longitude", coordenada(de.getLongitude()), coordenada(previsto.getLongitude()));
        comparar(mudancas, "Ativa", atual == null ? null : Planilha.simNao(atual.getAtiva()), Planilha.simNao(previsto.getStatus() == 0));

        String aviso = pendente == null ? null
                : "O departamento \"" + deptNome + "\" será criado em " + plano.criar().get(pendente).onde() + ".";
        if (atual == null) {
            plano.previa().getNovos().add(item(null, nome, mudancas, aviso));
        } else if (mudancas.isEmpty()) {
            plano.previa().setSemMudanca(plano.previa().getSemMudanca() + 1);
            return;
        } else {
            plano.previa().getAlterados().add(item(atual.getId(), atual.getNome(), mudancas, aviso));
        }
        plano.gravar().add(vo);
        if (pendente != null) {
            plano.pendentes().put(vo, pendente);
        }
    }

    /** Define o departamento e devolve o nome dele. Em branco na unidade cadastrada, mantém; o mesmo nome de agora também. */
    private static String departamento(UnidadeVOs.Unidade vo, Map<String, String> c, UnidadeVOs.Unidade atual, Contexto ctx, Plano plano) {
        String nome = vazio(c.get(DEPARTAMENTO));
        if (nome == null || (atual != null && chave(nome).equals(chave(atual.getDeptNome())))) {
            if (atual == null) {
                throw new IllegalArgumentException("informe o departamento");
            }
            return atual.getDeptNome();
        }
        List<DeptDO> achados = ctx.porNome().getOrDefault(chave(nome), List.of());
        if (achados.size() > 1) {
            throw new IllegalArgumentException("há " + achados.size() + " departamentos com o nome \"" + nome + "\"");
        }
        if (achados.size() == 1) {
            vo.setDeptId(achados.get(0).getId());
            return achados.get(0).getName();
        }
        if (!ctx.podeCriar()) {
            throw new IllegalArgumentException("departamento \"" + nome + "\" não encontrado (criá-lo exige a permissão de criar departamentos)");
        }
        aCriar(nome, vazio(c.get(SUPERIOR)), ctx, plano);
        vo.setDeptId(A_CRIAR);
        return nome;
    }

    /** Agenda o departamento (e o superior, se também faltar). O mesmo nome com outro superior na planilha é recusado. */
    private static void aCriar(String nome, String superior, Contexto ctx, Plano plano) {
        String sup = superior == null || chave(superior).equals(chave(nome)) ? null : superior;
        String chaveSup = null;
        Long paiId = ctx.raizId();
        String onde = ctx.raizNome() == null ? "na raiz" : "\"" + ctx.raizNome() + "\"";
        if (sup != null) {
            List<DeptDO> pais = ctx.porNome().getOrDefault(chave(sup), List.of());
            if (pais.size() > 1) {
                throw new IllegalArgumentException("há " + pais.size() + " departamentos com o nome \"" + sup + "\" (departamento superior)");
            }
            if (pais.size() == 1) {
                paiId = pais.get(0).getId();
            } else {
                chaveSup = chave(sup);
                plano.criar().putIfAbsent(chaveSup, new NovoDept(sup, null, ctx.raizId(), onde));
            }
            onde = "\"" + (pais.size() == 1 ? pais.get(0).getName() : sup) + "\"";
        }
        NovoDept novo = new NovoDept(nome, chaveSup, paiId, onde);
        NovoDept ja = plano.criar().putIfAbsent(chave(nome), novo);
        if (ja != null && !(Objects.equals(ja.superior(), novo.superior()) && Objects.equals(ja.paiId(), novo.paiId()))) {
            throw new IllegalArgumentException("o departamento \"" + nome + "\" aparece com departamentos superiores diferentes");
        }
    }

    /**
     * Agenda a mudança do departamento para o superior da planilha. Em branco é a raiz, como o Exportar escreve quando o pai
     * é a raiz; departamento que já está na raiz, ou que é a própria raiz, fica onde está.
     */
    private static void superior(Long deptId, String nomeSup, Contexto ctx, Plano plano, List<ImportacaoVOs.Mudanca> mudancas) {
        DeptDO dept = deptId == null ? null : ctx.porId().get(deptId);
        if (dept == null) {
            return;
        }
        Long paiAtual = dept.getParentId();
        DeptDO pai = paiAtual == null ? null : ctx.porId().get(paiAtual);
        boolean naRaiz = pai == null || pai.getParentId() == null || Objects.equals(pai.getParentId(), DeptDO.PARENT_ID_ROOT);
        String sup = nomeSup == null || chave(nomeSup).equals(chave(dept.getName())) ? null : nomeSup;
        if (sup == null && (naRaiz || ctx.raizNome() == null)) {
            return;
        }
        Long paraId = null;
        String paraChave = null;
        String paraNome;
        if (sup == null) {
            paraId = ctx.raizId();
            paraNome = ctx.raizNome();
        } else {
            List<DeptDO> pais = ctx.porNome().getOrDefault(chave(sup), List.of());
            if (pais.size() > 1) {
                throw new IllegalArgumentException("há " + pais.size() + " departamentos com o nome \"" + sup + "\" (departamento superior)");
            }
            if (pais.size() == 1) {
                paraId = pais.get(0).getId();
                paraNome = pais.get(0).getName();
            } else if (!ctx.podeCriar()) {
                throw new IllegalArgumentException("departamento superior \"" + sup + "\" não encontrado (criá-lo exige a permissão de criar departamentos)");
            } else {
                paraChave = chave(sup);
                paraNome = sup;
            }
        }
        if (paraId != null && Objects.equals(paraId, paiAtual)) {
            return;
        }
        if (!ctx.podeEditar()) {
            throw new IllegalArgumentException("levar o departamento \"" + dept.getName() + "\" para \"" + paraNome
                    + "\" exige a permissão de editar departamentos");
        }
        if (paraId != null && dentroDe(paraId, deptId, ctx)) {
            throw new IllegalArgumentException("o departamento \"" + dept.getName() + "\" não pode ficar em \"" + paraNome + "\", que está dentro dele");
        }
        Long destino = paraId;
        if (destino != null && ctx.porNome().getOrDefault(chave(dept.getName()), List.of()).stream()
                .anyMatch(d -> !d.getId().equals(deptId) && Objects.equals(d.getParentId(), destino))) {
            throw new IllegalArgumentException("já existe um departamento \"" + dept.getName() + "\" em \"" + paraNome + "\"");
        }
        Mudar novo = new Mudar(dept, paraId, paraChave, paraNome);
        Mudar ja = plano.mudar().putIfAbsent(deptId, novo);
        if (ja != null && !(Objects.equals(ja.paiId(), paraId) && Objects.equals(ja.paiChave(), paraChave))) {
            throw new IllegalArgumentException("o departamento \"" + dept.getName() + "\" aparece com departamentos superiores diferentes");
        }
        if (paraChave != null) {
            plano.criar().putIfAbsent(paraChave, new NovoDept(sup, null, ctx.raizId(),
                    ctx.raizNome() == null ? "na raiz" : "\"" + ctx.raizNome() + "\""));
        }
        comparar(mudancas, "Departamento superior", pai == null ? null : pai.getName(), paraNome);
    }

    /** Se {@code id} é {@code ancestral} ou fica dentro dele */
    private static boolean dentroDe(Long id, Long ancestral, Contexto ctx) {
        Set<Long> vistos = new HashSet<>();
        for (Long atual = id; atual != null && vistos.add(atual); ) {
            if (atual.equals(ancestral)) {
                return true;
            }
            DeptDO d = ctx.porId().get(atual);
            atual = d == null ? null : d.getParentId();
        }
        return false;
    }

    /** Coluna presente com a célula em branco apaga o dado. */
    private static void texto(Map<String, String> c, String campo, Consumer<String> definir) {
        if (c.containsKey(campo)) {
            definir.accept(vazio(c.get(campo)));
        }
    }

    /** Em branco mantém o valor: unidade sem coordenada não aparece no mapa. Aceita vírgula decimal digitada à mão. */
    private static void coordenada(Map<String, String> c, String campo, String rotulo, Consumer<BigDecimal> definir) {
        String v = vazio(c.get(campo));
        if (v == null) {
            return;
        }
        try {
            definir.accept(new BigDecimal(v.replace(',', '.')));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(rotulo + " inválida: \"" + v + "\"");
        }
    }

    private static String coordenada(BigDecimal v) {
        return v == null ? null : v.setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

}
