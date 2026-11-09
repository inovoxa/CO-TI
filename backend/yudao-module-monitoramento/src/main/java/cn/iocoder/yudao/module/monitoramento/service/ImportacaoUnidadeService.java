package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.system.controller.admin.dept.vo.dept.DeptListReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.monitoramento.service.ImportacaoPlanilha.*;

/**
 * Importa a planilha que o Exportar de unidades gera. A unidade é achada pelo nome, e o departamento também vem pelo nome.
 * Coluna que não veio mantém o cadastro; a coluna Monitores é só informativa.
 */
@Service
public class ImportacaoUnidadeService {

    private static final String NOME = "nome", DEPARTAMENTO = "departamento", CEP = "cep", LOGRADOURO = "logradouro",
            NUMERO = "numero", COMPLEMENTO = "complemento", BAIRRO = "bairro", CIDADE = "cidade", UF = "uf",
            LATITUDE = "latitude", LONGITUDE = "longitude", ATIVA = "ativa";

    private static final Map<String, String> TITULOS = Map.ofEntries(Map.entry("unidade", NOME), Map.entry("departamento", DEPARTAMENTO),
            Map.entry("cep", CEP), Map.entry("logradouro", LOGRADOURO), Map.entry("numero", NUMERO), Map.entry("complemento", COMPLEMENTO),
            Map.entry("bairro", BAIRRO), Map.entry("cidade", CIDADE), Map.entry("uf", UF), Map.entry("latitude", LATITUDE),
            Map.entry("longitude", LONGITUDE), Map.entry("ativa", ATIVA));

    @Resource
    private UnidadeService unidadeService;
    @Resource
    private DeptService deptService;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private Validator validator;

    private record Plano(List<UnidadeVOs.Unidade> gravar, ImportacaoVOs.Previa previa) {
    }

    public ImportacaoVOs.Previa analisar(InputStream arquivo) {
        return planejar(arquivo).previa();
    }

    /** Refaz a análise sobre o cadastro de agora e aplica; quem não for indicada em {@code removerIds} não é removida. */
    public ImportacaoVOs.Resultado aplicar(InputStream arquivo, Collection<Long> removerIds, Long userId) {
        Plano plano = planejar(arquivo);
        Set<Long> remover = aRemover(plano.previa(), removerIds);
        unidadeService.aplicarImportacao(plano.gravar(), remover);
        ImportacaoVOs.Resultado r = resultado(plano.previa(), remover.size());
        auditoriaService.registrar("monitoramento.importar_unidades", userId, null, Map.of("adicionados", r.getAdicionados(),
                "atualizados", r.getAtualizados(), "removidos", nomes(plano.previa(), remover), "recusados", r.getRecusados()));
        return r;
    }

    private Plano planejar(InputStream arquivo) {
        List<Planilha.Linha> linhas = ler(arquivo, TITULOS, NOME, "Unidade");
        List<UnidadeVOs.Unidade> todas = unidadeService.listar();
        Map<String, List<UnidadeVOs.Unidade>> porNome = todas.stream().collect(Collectors.groupingBy(u -> chave(u.getNome())));
        Map<String, List<DeptDO>> depts = deptService.getDeptList(new DeptListReqVO()).stream()
                .collect(Collectors.groupingBy(d -> chave(d.getName())));

        Plano plano = new Plano(new ArrayList<>(), new ImportacaoVOs.Previa());
        // quem aparece em linha recusada também conta como presente: um erro de digitação não pode virar remoção
        Set<Long> presentes = new HashSet<>();
        Set<Long> usadas = new HashSet<>();
        Set<String> novas = new HashSet<>();
        for (Planilha.Linha l : linhas) {
            try {
                processar(l, porNome, depts, plano, presentes, usadas, novas);
            } catch (ServiceException | IllegalArgumentException e) {
                recusar(plano.previa(), l, e);
            }
        }
        todas.stream().filter(u -> !presentes.contains(u.getId())).forEach(u -> plano.previa().getAusentes().add(item(u.getId(), u.getNome(), List.of(),
                u.getMonitores() != null && u.getMonitores() > 0 ? u.getMonitores() + " monitor(es) ficarão sem unidade." : null)));
        return plano;
    }

    private void processar(Planilha.Linha l, Map<String, List<UnidadeVOs.Unidade>> porNome, Map<String, List<DeptDO>> depts, Plano plano,
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
        String deptNome = departamento(vo, c, atual, depts);
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
        MonitorUnitDO previsto = unidadeService.previsto(vo);

        List<ImportacaoVOs.Mudanca> mudancas = new ArrayList<>();
        UnidadeVOs.Unidade de = atual == null ? new UnidadeVOs.Unidade() : atual;
        comparar(mudancas, "Unidade", de.getNome(), previsto.getName());
        comparar(mudancas, "Departamento", de.getDeptNome(), deptNome);
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

        if (atual == null) {
            plano.previa().getNovos().add(item(null, nome, mudancas, null));
            plano.gravar().add(vo);
        } else if (mudancas.isEmpty()) {
            plano.previa().setSemMudanca(plano.previa().getSemMudanca() + 1);
        } else {
            plano.previa().getAlterados().add(item(atual.getId(), atual.getNome(), mudancas, null));
            plano.gravar().add(vo);
        }
    }

    /** Define o departamento e devolve o nome dele. Em branco na unidade cadastrada, mantém; o mesmo nome de agora também. */
    private static String departamento(UnidadeVOs.Unidade vo, Map<String, String> c, UnidadeVOs.Unidade atual, Map<String, List<DeptDO>> depts) {
        String nome = vazio(c.get(DEPARTAMENTO));
        if (nome == null || (atual != null && chave(nome).equals(chave(atual.getDeptNome())))) {
            if (atual == null) {
                throw new IllegalArgumentException("informe o departamento");
            }
            return atual.getDeptNome();
        }
        List<DeptDO> achados = depts.getOrDefault(chave(nome), List.of());
        if (achados.isEmpty()) {
            throw new IllegalArgumentException("departamento \"" + nome + "\" não encontrado");
        }
        if (achados.size() > 1) {
            throw new IllegalArgumentException("há " + achados.size() + " departamentos com o nome \"" + nome + "\"");
        }
        vo.setDeptId(achados.get(0).getId());
        return achados.get(0).getName();
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
