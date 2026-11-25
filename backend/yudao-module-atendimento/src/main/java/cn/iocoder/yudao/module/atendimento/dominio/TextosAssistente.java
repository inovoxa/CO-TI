package cn.iocoder.yudao.module.atendimento.dominio;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textos e opções do assistente virtual que a organização pode mudar pela tela (Atendimento → Configuração →
 * Assistente virtual). Cada item tem o padrão do código; o banco guarda só o que foi alterado.
 *
 * <p>Nos textos, {@code {variavel}} é trocada pelo valor da hora. Uma linha cuja variável ficou vazia some inteira
 * (ex.: "Solucionado em: {solucionado_em}" num chamado ainda aberto). Variável desconhecida fica como está.
 */
public final class TextosAssistente {

    private TextosAssistente() {
    }

    public enum Tipo { TEXTO, SIM_NAO, NUMERO }

    /**
     * @param grupo      aba da tela: menu, chamado, avisos ou ia
     * @param variaveis  as que o texto aceita (a tela mostra para clicar)
     * @param minimo     para números
     */
    public record Item(String chave, String grupo, String rotulo, String ajuda, Tipo tipo, String padrao, List<String> variaveis,
                       int minimo, int maximo) {
    }

    private static final Pattern VARIAVEL = Pattern.compile("\\{([a-z_]+)}");
    private static final Map<String, Item> ITENS = new LinkedHashMap<>();

    // ----- chaves usadas no código
    public static final String BOAS_VINDAS = "fluxo.boas_vindas";
    public static final String MENU = "fluxo.menu";
    public static final String NAO_IDENTIFICADO = "fluxo.nao_identificado";
    public static final String TRANSFERENCIA = "fluxo.transferencia";
    public static final String CONSULTA_VAZIA = "fluxo.consulta.vazia";
    public static final String CONSULTA_TITULO = "fluxo.consulta.titulo";
    public static final String CONSULTA_RODAPE = "fluxo.consulta.rodape";
    public static final String CONSULTA_QUANTIDADE = "param.consulta.quantidade";
    public static final String DETALHE = "fluxo.chamado.detalhe";
    public static final String MOSTRAR_ULTIMA = "param.chamado.ultima_atualizacao";
    public static final String PERMITIR_ADICIONAR = "param.chamado.adicionar";
    public static final String PERMITIR_AVALIAR = "param.chamado.avaliar_solucao";
    public static final String OPCOES_ABERTO = "fluxo.chamado.opcoes";
    public static final String OPCOES_SOLUCIONADO = "fluxo.chamado.opcoes_solucionado";
    public static final String OPCOES_SO_CONSULTA = "fluxo.chamado.opcoes_consulta";
    public static final String ENCERRADO = "fluxo.chamado.encerrado";
    public static final String PEDIR_TEXTO = "fluxo.chamado.pedir_texto";
    public static final String TEXTO_OK = "fluxo.chamado.texto_ok";
    public static final String TEXTO_REABERTO = "fluxo.chamado.texto_reaberto";
    public static final String SOLUCAO_APROVADA = "fluxo.chamado.solucao_aprovada";
    public static final String PEDIR_MOTIVO_RECUSA = "fluxo.chamado.pedir_motivo";
    public static final String SOLUCAO_RECUSADA = "fluxo.chamado.solucao_recusada";
    public static final String AVISO_ACOMPANHAMENTO = "aviso.acompanhamento";
    public static final String AVISO_SOLUCIONADO = "aviso.solucionado";
    public static final String AVISO_APROVADO = "aviso.aprovado";
    public static final String AVISO_RECUSADO = "aviso.recusado";
    public static final String AVISO_FECHADO = "aviso.fechado";
    public static final String AVISO_APROVACAO = "aviso.aprovacao_solicitada";
    public static final String IA_ORIENTACOES = "ia.orientacoes_base";

    private static final List<String> CHAMADO = List.of("chamado", "titulo");

    static {
        texto(BOAS_VINDAS, "menu", "Boas-vindas", "Primeira mensagem de uma conversa nova, antes do menu. A caixa pode ter a"
                + " própria (aba Caixas).", "Olá! Sou o assistente virtual da equipe de TI 👋", List.of());
        texto(MENU, "menu", "Menu principal", "As opções são fixas: 1 abrir, 2 consultar, 3 dúvida, 4 atendente. Mude só o texto.",
                """
                1️⃣ Abrir um chamado
                2️⃣ Consultar meus chamados
                3️⃣ Tirar uma dúvida (base de conhecimento)
                4️⃣ Falar com um atendente

                Responda com o número da opção ou descreva o que precisa.""", List.of());
        texto(NAO_IDENTIFICADO, "menu", "Celular não identificado", "Quando o número não está no cadastro de ninguém.",
                "Para abrir ou consultar pedidos pelo WhatsApp, o número deste celular precisa estar no seu cadastro"
                        + " (portal da TI ou Active Directory), e não o encontrei.\n\nPosso:\n3️⃣ Tirar uma dúvida\n4️⃣ Chamar um atendente",
                List.of());
        texto(TRANSFERENCIA, "menu", "Chamando um atendente", null,
                "Certo! Vou chamar uma pessoa da equipe para continuar o atendimento. Só um instante.", List.of());

        texto(CONSULTA_TITULO, "chamado", "Lista de chamados: título", null, "*Seus chamados mais recentes:*", List.of());
        texto(CONSULTA_RODAPE, "chamado", "Lista de chamados: rodapé", null,
                "Envie o número de um chamado (ex.: *#{exemplo}*) para ver os detalhes, ou *menu*.", List.of("exemplo"));
        numero(CONSULTA_QUANTIDADE, "chamado", "Quantos chamados listar", 5, 1, 20);
        texto(CONSULTA_VAZIA, "chamado", "Sem chamados", null, "Você não tem chamados registrados.", List.of());
        texto(DETALHE, "chamado", "Detalhe do chamado", "Linhas com variável vazia somem (ex.: solucionado em, num chamado aberto).",
                """
                *Chamado #{chamado}*
                {titulo}
                Situação: {situacao}
                Aberto em: {aberto_em}
                Solucionado em: {solucionado_em}

                💬 Última atualização, por {ultima_autor} em {ultima_data}:
                {ultima_texto}""", List.of("chamado", "titulo", "situacao", "aberto_em", "solucionado_em", "ultima_autor",
                        "ultima_data", "ultima_texto"));
        simNao(MOSTRAR_ULTIMA, "chamado", "Mostrar a última atualização da equipe no detalhe", true);
        simNao(PERMITIR_ADICIONAR, "chamado", "Deixar o contato escrever no chamado pelo canal", true);
        simNao(PERMITIR_AVALIAR, "chamado", "Deixar o contato aprovar ou recusar a solução pelo canal", true);
        texto(OPCOES_ABERTO, "chamado", "Opções de um chamado em andamento", null,
                "1️⃣ Adicionar uma informação ao chamado\n\nOu envie outro número ou *menu*.", List.of("chamado"));
        texto(OPCOES_SOLUCIONADO, "chamado", "Opções de um chamado solucionado", null,
                "1️⃣ Resolveu: aprovar a solução e encerrar\n2️⃣ Não resolveu: recusar a solução (reabre)\n\n"
                        + "Ou envie outro número ou *menu*.", List.of("chamado"));
        texto(OPCOES_SO_CONSULTA, "chamado", "Rodapé quando o contato não pode escrever no chamado", null,
                "Envie outro número ou *menu*.", List.of("chamado"));
        texto(ENCERRADO, "chamado", "Chamado encerrado", null,
                "Este chamado está encerrado. Para um assunto novo, envie *menu* e abra outro pedido.", CHAMADO);
        texto(PEDIR_TEXTO, "chamado", "Pedindo a informação", null,
                "Escreva numa única mensagem o que quer acrescentar ao chamado *#{chamado}*. Vai direto para a equipe que atende."
                        + "\n\n(Envie *menu* para desistir.)", CHAMADO);
        texto(TEXTO_OK, "chamado", "Informação registrada", null,
                "📝 Pronto! Registrei no chamado *#{chamado}*. A resposta da equipe chega por aqui.", CHAMADO);
        texto(TEXTO_REABERTO, "chamado", "Informação registrada num solucionado", "Escrever num chamado solucionado recusa a"
                + " solução e reabre, como no portal.", "📝 Registrei no chamado *#{chamado}* e ele foi reaberto: a equipe volta a"
                + " cuidar dele.", CHAMADO);
        texto(SOLUCAO_APROVADA, "chamado", "Solução aprovada", null, "👍 Obrigado! O chamado *#{chamado}* foi encerrado.", CHAMADO);
        texto(PEDIR_MOTIVO_RECUSA, "chamado", "Pedindo o motivo da recusa", null,
                "Conte numa mensagem o que ainda não está resolvido. O chamado *#{chamado}* será reaberto com a sua observação.",
                CHAMADO);
        texto(SOLUCAO_RECUSADA, "chamado", "Solução recusada", null,
                "🔁 Chamado *#{chamado}* reaberto com a sua observação. A equipe volta a cuidar dele.", CHAMADO);

        texto(AVISO_ACOMPANHAMENTO, "avisos", "Atualização da equipe", "Quando um técnico escreve no chamado (notas internas não"
                + " vão).", "📝 Chamado #{chamado} — atualização da equipe:\n{texto}\n\nPara responder, envie *#{chamado}*.",
                List.of("chamado", "titulo", "texto"));
        texto(AVISO_SOLUCIONADO, "avisos", "Chamado solucionado", null,
                "✅ Chamado #{chamado} solucionado.\n{texto}\n\nEnvie *#{chamado}* para aprovar a solução ou dizer que não resolveu.",
                List.of("chamado", "titulo", "texto"));
        texto(AVISO_APROVADO, "avisos", "Pedido aprovado", null,
                "👍 Chamado #{chamado}: o pedido foi aprovado e segue para atendimento.", CHAMADO);
        texto(AVISO_RECUSADO, "avisos", "Pedido recusado", null, "Chamado #{chamado}: o pedido foi recusado pelo aprovador.", CHAMADO);
        texto(AVISO_FECHADO, "avisos", "Chamado encerrado", null, "Chamado #{chamado} encerrado. Obrigado!", CHAMADO);
        texto(AVISO_APROVACAO, "avisos", "Pedido de aprovação (ao superior)", "Vai para o celular do cadastro de cada aprovador.",
                "🔔 Olá, {aprovador}! O pedido *#{chamado} — {titulo}*, de {solicitante}, aguarda a sua aprovação.\n\n"
                        + "Aprove ou recuse pelo portal: {link}", List.of("aprovador", "chamado", "titulo", "solicitante", "link"));

        texto(IA_ORIENTACOES, "ia", "Orientações gerais do assistente de IA", "Tom e estilo de todas as caixas com IA. As"
                + " instruções de cada caixa vêm depois; as regras de segurança valem acima das duas.",
                "Fale em português do Brasil, com frases curtas e cordiais, como numa conversa de WhatsApp. Não use títulos nem"
                        + " tabelas; listas curtas podem.", List.of());
    }

    private static void texto(String chave, String grupo, String rotulo, String ajuda, String padrao, List<String> variaveis) {
        ITENS.put(chave, new Item(chave, grupo, rotulo, ajuda, Tipo.TEXTO, padrao, variaveis, 0, 0));
    }

    private static void simNao(String chave, String grupo, String rotulo, boolean padrao) {
        ITENS.put(chave, new Item(chave, grupo, rotulo, null, Tipo.SIM_NAO, String.valueOf(padrao), List.of(), 0, 0));
    }

    private static void numero(String chave, String grupo, String rotulo, int padrao, int minimo, int maximo) {
        ITENS.put(chave, new Item(chave, grupo, rotulo, null, Tipo.NUMERO, String.valueOf(padrao), List.of(), minimo, maximo));
    }

    public static Collection<Item> itens() {
        return Collections.unmodifiableCollection(ITENS.values());
    }

    public static Item item(String chave) {
        Item i = ITENS.get(chave);
        if (i == null) {
            throw new IllegalArgumentException("texto do assistente desconhecido: " + chave);
        }
        return i;
    }

    public static boolean existe(String chave) {
        return ITENS.containsKey(chave);
    }

    /**
     * Confere um valor antes de gravar.
     *
     * @return o valor normalizado, ou {@code null} quando é igual ao padrão (volta a valer o do código)
     * @throws IllegalArgumentException com a mensagem para a tela
     */
    public static String validar(String chave, String valor) {
        Item i = item(chave);
        String v = valor == null ? "" : valor.replace("\r\n", "\n").strip();
        switch (i.tipo()) {
            case SIM_NAO -> {
                if (!v.equals("true") && !v.equals("false")) {
                    throw new IllegalArgumentException(i.rotulo() + ": use sim ou não");
                }
            }
            case NUMERO -> {
                int n;
                try {
                    n = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(i.rotulo() + ": informe um número");
                }
                if (n < i.minimo() || n > i.maximo()) {
                    throw new IllegalArgumentException(i.rotulo() + ": de " + i.minimo() + " a " + i.maximo());
                }
                v = String.valueOf(n);
            }
            default -> {
                if (v.isEmpty()) {
                    throw new IllegalArgumentException(i.rotulo() + ": o texto não pode ficar vazio (use restaurar o padrão)");
                }
                if (v.length() > 4000) {
                    throw new IllegalArgumentException(i.rotulo() + ": no máximo 4000 caracteres");
                }
            }
        }
        return v.equals(i.padrao()) ? null : v;
    }

    /** Troca as variáveis; some a linha cuja variável ficou vazia; nunca mais de uma linha em branco seguida. */
    public static String preencher(String modelo, Map<String, ?> valores) {
        if (modelo == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String linha : modelo.split("\n", -1)) {
            Matcher m = VARIAVEL.matcher(linha);
            StringBuilder l = new StringBuilder();
            boolean vazia = false;
            while (m.find()) {
                String nome = m.group(1);
                String v;
                if (valores.containsKey(nome)) {
                    Object o = valores.get(nome);
                    v = o == null ? "" : o.toString();
                    vazia |= v.isBlank();
                } else {
                    v = m.group();   // desconhecida: fica como está
                }
                m.appendReplacement(l, Matcher.quoteReplacement(v));
            }
            m.appendTail(l);
            if (!vazia) {
                out.append(l).append('\n');
            }
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

}
