package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Ordem da lista de monitores ao clicar no cabeçalho. Sem o dado (ex.: sem certificado) fica no fim, nos dois sentidos. */
final class OrdenacaoMonitores {

    static final String UPTIME_24H = "uptime24h", UPTIME_30D = "uptime30d";
    private static final Set<String> CAMPOS = Set.of("nome", "situacao", "resposta", UPTIME_24H, UPTIME_30D, "unidade", "certificado");

    private OrdenacaoMonitores() {
    }

    static boolean aceita(String campo) {
        return CAMPOS.contains(campo);
    }

    /**
     * @param unidades unidade → nome
     * @param uptime   monitor → disponibilidade (%) do período pedido; só é usado nos campos de disponibilidade
     */
    static Comparator<MonitorDO> comparador(String campo, boolean decrescente, Map<Long, String> unidades, Map<Long, Double> uptime) {
        Collator colacao = Collator.getInstance(Locale.of("pt", "BR"));
        colacao.setStrength(Collator.PRIMARY); // ignora maiúsculas e acentos
        Comparator<String> texto = colacao::compare;
        Comparator<MonitorDO> porNome = Comparator.comparing(MonitorDO::getNome, texto);
        return switch (campo) {
            case "nome" -> chave(MonitorDO::getNome, texto, decrescente);
            case "situacao" -> chave(MonitorDO::getOrdem, Comparator.<Integer>naturalOrder(), decrescente).thenComparing(porNome);
            case "resposta" -> chave(MonitorDO::getUltimaLatenciaMs, Comparator.<Integer>naturalOrder(), decrescente).thenComparing(porNome);
            case UPTIME_24H, UPTIME_30D -> chave(m -> uptime.get(m.getId()), Comparator.<Double>naturalOrder(), decrescente).thenComparing(porNome);
            case "unidade" -> chave(m -> m.getUnidadeId() == null ? null : unidades.get(m.getUnidadeId()), texto, decrescente).thenComparing(porNome);
            case "certificado" -> chave(MonitorDO::getCertDias, Comparator.<Integer>naturalOrder(), decrescente).thenComparing(porNome);
            default -> porNome;
        };
    }

    private static <T> Comparator<MonitorDO> chave(Function<MonitorDO, T> valor, Comparator<T> base, boolean decrescente) {
        return Comparator.comparing(valor, Comparator.nullsLast(decrescente ? base.reversed() : base));
    }

    static <T> List<T> pagina(List<T> todos, int pagina, int tamanho) {
        if (tamanho == PageParam.PAGE_SIZE_NONE) {
            return new ArrayList<>(todos);
        }
        int inicio = Math.min((pagina - 1) * tamanho, todos.size());
        return new ArrayList<>(todos.subList(inicio, Math.min(inicio + tamanho, todos.size())));
    }

}
