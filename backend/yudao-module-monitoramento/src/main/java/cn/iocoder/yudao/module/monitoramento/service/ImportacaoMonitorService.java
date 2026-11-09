package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.MonitorVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.monitoramento.service.ImportacaoPlanilha.*;

/**
 * Importa a planilha que o Exportar de monitores gera. O monitor é achado pelo nome. Coluna que não veio mantém o cadastro,
 * e as colunas só de leitura (disponibilidade, latência etc.) são ignoradas.
 *
 * <p>A configuração que não vai na planilha (cabeçalhos, palavra-chave, canais de aviso) fica como está.
 */
@Service
public class ImportacaoMonitorService {

    private static final String NOME = "nome", TIPO = "tipo", ALVO = "alvo", PORTA = "porta", UNIDADE = "unidade",
            PAPEL = "papel", SITUACAO = "situacao", INTERVALO = "intervalo", DESCRICAO = "descricao";

    private static final Map<String, String> TITULOS = Map.of("nome", NOME, "tipo", TIPO, "endereco", ALVO, "porta", PORTA,
            "unidade", UNIDADE, "papel", PAPEL, "situacao", SITUACAO, "intervalos", INTERVALO, "intervalo", INTERVALO,
            "descricao", DESCRICAO);

    @Resource
    private MonitorService monitorService;
    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private Validator validator;

    private record Plano(List<MonitorVOs.Monitor> gravar, ImportacaoVOs.Previa previa) {
    }

    public ImportacaoVOs.Previa analisar(InputStream arquivo) {
        return planejar(arquivo).previa();
    }

    /** Refaz a análise sobre o cadastro de agora e aplica; quem não for indicado em {@code removerIds} não é removido. */
    public ImportacaoVOs.Resultado aplicar(InputStream arquivo, Collection<Long> removerIds, Long userId) {
        Plano plano = planejar(arquivo);
        Set<Long> remover = aRemover(plano.previa(), removerIds);
        monitorService.aplicarImportacao(plano.gravar(), remover);
        ImportacaoVOs.Resultado r = resultado(plano.previa(), remover.size());
        auditoriaService.registrar("monitoramento.importar_monitores", userId, null, Map.of("adicionados", r.getAdicionados(),
                "atualizados", r.getAtualizados(), "removidos", nomes(plano.previa(), remover), "recusados", r.getRecusados()));
        return r;
    }

    private Plano planejar(InputStream arquivo) {
        List<Planilha.Linha> linhas = ler(arquivo, TITULOS, NOME, "Nome");
        List<MonitorDO> todos = monitorService.todos();
        Map<String, List<MonitorDO>> porNome = todos.stream().collect(Collectors.groupingBy(m -> chave(m.getNome())));
        List<MonitorUnitDO> unidades = monitorUnitMapper.selectList();
        Map<String, List<MonitorUnitDO>> unidadesPorNome = unidades.stream().collect(Collectors.groupingBy(u -> chave(u.getName())));
        Map<Long, String> nomesUnidades = new HashMap<>();
        unidades.forEach(u -> nomesUnidades.put(u.getId(), u.getName()));

        Plano plano = new Plano(new ArrayList<>(), new ImportacaoVOs.Previa());
        // quem aparece em linha recusada também conta como presente: um erro de digitação não pode virar remoção
        Set<Long> presentes = new HashSet<>();
        Set<Long> usados = new HashSet<>();
        Set<String> novos = new HashSet<>();
        for (Planilha.Linha l : linhas) {
            try {
                processar(l, porNome, unidadesPorNome, nomesUnidades, plano, presentes, usados, novos);
            } catch (ServiceException | IllegalArgumentException e) {
                recusar(plano.previa(), l, e);
            }
        }
        todos.stream().filter(m -> !presentes.contains(m.getId())).forEach(m -> plano.previa().getAusentes()
                .add(item(m.getId(), m.getNome(), List.of(), "O histórico de verificações deste monitor será apagado.")));
        return plano;
    }

    private void processar(Planilha.Linha l, Map<String, List<MonitorDO>> porNome, Map<String, List<MonitorUnitDO>> unidades,
                           Map<Long, String> nomesUnidades, Plano plano, Set<Long> presentes, Set<Long> usados, Set<String> novos) {
        Map<String, String> c = l.campos();
        String nome = vazio(c.get(NOME));
        if (nome == null) {
            throw new IllegalArgumentException("informe o nome");
        }
        MonitorDO atual = localizar(porNome.getOrDefault(chave(nome), List.of()), c, presentes);
        if (atual != null ? !usados.add(atual.getId()) : !novos.add(chave(nome))) {
            throw new IllegalArgumentException("o monitor \"" + nome + "\" aparece mais de uma vez no arquivo");
        }
        MonitorVOs.Monitor vo = atual == null ? new MonitorVOs.Monitor() : monitorService.semCanais(atual);
        preencher(vo, nome, c, atual, unidades);
        validar(validator, vo);
        MonitorDO previsto = monitorService.previsto(vo, atual);

        List<ImportacaoVOs.Mudanca> mudancas = new ArrayList<>();
        MonitorDO de = atual == null ? new MonitorDO() : atual;
        comparar(mudancas, "Nome", de.getNome(), previsto.getNome());
        comparar(mudancas, "Tipo", de.getTipo(), previsto.getTipo());
        comparar(mudancas, "Endereço", de.getAlvo(), previsto.getAlvo());
        comparar(mudancas, "Porta", de.getPorta(), previsto.getPorta());
        comparar(mudancas, "Unidade", nomesUnidades.get(de.getUnidadeId()), nomesUnidades.get(previsto.getUnidadeId()));
        comparar(mudancas, "Papel", de.getPapel(), previsto.getPapel());
        comparar(mudancas, "Situação", atual == null ? null : situacao(atual.getAtivo()), situacao(previsto.getAtivo()));
        comparar(mudancas, "Intervalo (s)", de.getIntervaloS(), previsto.getIntervaloS());
        comparar(mudancas, "Descrição", de.getDescricao(), previsto.getDescricao());

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

    /** Com nome repetido no cadastro, o endereço desempata. */
    private static MonitorDO localizar(List<MonitorDO> candidatos, Map<String, String> c, Set<Long> presentes) {
        candidatos.forEach(m -> presentes.add(m.getId()));
        if (candidatos.size() <= 1) {
            return candidatos.isEmpty() ? null : candidatos.get(0);
        }
        List<MonitorDO> mesmoAlvo = candidatos.stream().filter(m -> Objects.equals(vazio(m.getAlvo()), vazio(c.get(ALVO)))).toList();
        if (mesmoAlvo.size() != 1) {
            throw new IllegalArgumentException("há " + candidatos.size() + " monitores com este nome e o endereço não define qual é");
        }
        return mesmoAlvo.get(0);
    }

    /** Tipo, papel, situação e intervalo em branco mantêm o valor; os demais campos em branco apagam o dado. */
    private void preencher(MonitorVOs.Monitor vo, String nome, Map<String, String> c, MonitorDO atual, Map<String, List<MonitorUnitDO>> unidades) {
        vo.setNome(nome);
        if (vazio(c.get(TIPO)) != null) {
            vo.setTipo(Planilha.titulo(c.get(TIPO)));
        }
        if (vo.getTipo() == null) {
            throw new IllegalArgumentException("informe o tipo");
        }
        if (c.containsKey(ALVO)) {
            vo.setAlvo(vazio(c.get(ALVO)));
        }
        if (c.containsKey(PORTA)) {
            vo.setPorta(vazio(c.get(PORTA)) == null ? null : inteiro(c.get(PORTA), "porta"));
        }
        if (c.containsKey(UNIDADE)) {
            Long unidade = unidade(c.get(UNIDADE), atual, unidades);
            if (atual != null && unidade != null && !unidade.equals(atual.getUnidadeId())) {
                // o setor responsável acompanha a nova unidade
                vo.setDeptId(null);
            }
            vo.setUnidadeId(unidade);
        }
        if (vazio(c.get(PAPEL)) != null) {
            vo.setPapel(Planilha.titulo(c.get(PAPEL)));
        }
        if (vazio(c.get(SITUACAO)) != null) {
            vo.setAtivo(!"pausado".equals(Planilha.titulo(c.get(SITUACAO))));
        }
        if (vazio(c.get(INTERVALO)) != null) {
            vo.setIntervaloS(inteiro(c.get(INTERVALO), "intervalo"));
        }
        if (c.containsKey(DESCRICAO)) {
            vo.setDescricao(vazio(c.get(DESCRICAO)));
        }
    }

    private static Long unidade(String valor, MonitorDO atual, Map<String, List<MonitorUnitDO>> unidades) {
        String nome = vazio(valor);
        if (nome == null) {
            return null;
        }
        List<MonitorUnitDO> achadas = unidades.getOrDefault(chave(nome), List.of());
        if (achadas.isEmpty()) {
            throw new IllegalArgumentException("unidade \"" + nome + "\" não encontrada");
        }
        if (atual != null && achadas.stream().anyMatch(u -> u.getId().equals(atual.getUnidadeId()))) {
            return atual.getUnidadeId();
        }
        if (achadas.size() > 1) {
            throw new IllegalArgumentException("há " + achadas.size() + " unidades com o nome \"" + nome + "\"");
        }
        return achadas.get(0).getId();
    }

    private static String situacao(Boolean ativo) {
        return Boolean.TRUE.equals(ativo) ? "ativo" : "pausado";
    }

}
