package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.ImportacaoVOs;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.PLANILHA_INVALIDA;

/** O que as importações de monitores e de unidades têm em comum. */
final class ImportacaoPlanilha {

    private static final int MAX_LINHAS = 5000;
    private static final int MAX_ERROS = 100;

    private ImportacaoPlanilha() {
    }

    /** Arquivo cortado ou vazio faria tudo parecer ausente, e daí removível: esses são recusados inteiros. */
    static List<Planilha.Linha> ler(InputStream arquivo, Map<String, String> titulos, String obrigatorio, String coluna) {
        List<Planilha.Linha> linhas;
        try {
            linhas = Planilha.ler(arquivo, titulos, obrigatorio, MAX_LINHAS + 1);
        } catch (IOException e) {
            throw exception(PLANILHA_INVALIDA, "envie um arquivo .xlsx com a coluna " + coluna);
        }
        if (linhas.size() > MAX_LINHAS) {
            throw exception(PLANILHA_INVALIDA, "o limite é de " + MAX_LINHAS + " linhas");
        }
        if (linhas.isEmpty()) {
            throw exception(PLANILHA_INVALIDA, "o arquivo não tem nenhuma linha preenchida");
        }
        return linhas;
    }

    /** Nome como chave de busca: ignora maiúsculas e espaços a mais */
    static String chave(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static int inteiro(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(campo + " deve ser um número inteiro em vez de \"" + valor + "\"");
        }
    }

    /** Pela tela, o Spring já validou; a planilha passa pelas mesmas regras aqui. */
    static <T> void validar(Validator validator, T objeto) {
        Set<ConstraintViolation<T>> erros = validator.validate(objeto);
        if (!erros.isEmpty()) {
            ConstraintViolation<T> e = erros.iterator().next();
            throw new IllegalArgumentException(e.getPropertyPath() + ": " + e.getMessage());
        }
    }

    static void comparar(List<ImportacaoVOs.Mudanca> mudancas, String campo, Object de, Object para) {
        String a = de == null ? null : vazio(String.valueOf(de));
        String b = para == null ? null : vazio(String.valueOf(para));
        if (!Objects.equals(a, b)) {
            mudancas.add(new ImportacaoVOs.Mudanca(campo, a, b));
        }
    }

    static void recusar(ImportacaoVOs.Previa previa, Planilha.Linha linha, RuntimeException e) {
        previa.setRecusados(previa.getRecusados() + 1);
        if (previa.getErros().size() < MAX_ERROS) {
            previa.getErros().add("Linha " + linha.numero() + ": " + e.getMessage());
        }
    }

    static ImportacaoVOs.Item item(Long id, String nome, List<ImportacaoVOs.Mudanca> mudancas, String aviso) {
        ImportacaoVOs.Item i = new ImportacaoVOs.Item();
        i.setId(id);
        i.setNome(nome);
        i.setMudancas(mudancas);
        i.setAviso(aviso);
        return i;
    }

    /** Só sai o que a análise feita agora aponta como ausente: um id qualquer enviado na requisição é ignorado. */
    static Set<Long> aRemover(ImportacaoVOs.Previa previa, Collection<Long> pedidos) {
        if (pedidos == null) {
            return Set.of();
        }
        return previa.getAusentes().stream().map(ImportacaoVOs.Item::getId).filter(pedidos::contains).collect(Collectors.toSet());
    }

    static List<String> nomes(ImportacaoVOs.Previa previa, Set<Long> ids) {
        return previa.getAusentes().stream().filter(i -> ids.contains(i.getId())).map(ImportacaoVOs.Item::getNome).toList();
    }

    static ImportacaoVOs.Resultado resultado(ImportacaoVOs.Previa previa, int removidos) {
        ImportacaoVOs.Resultado r = new ImportacaoVOs.Resultado();
        r.setAdicionados(previa.getNovos().size());
        r.setAtualizados(previa.getAlterados().size());
        r.setRemovidos(removidos);
        r.setSemMudanca(previa.getSemMudanca());
        r.setRecusados(previa.getRecusados());
        return r;
    }

}
