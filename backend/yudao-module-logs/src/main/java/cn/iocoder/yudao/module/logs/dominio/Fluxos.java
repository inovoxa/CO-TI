package cn.iocoder.yudao.module.logs.dominio;

import java.util.List;

/** Fluxos (os "streams" do Graylog): cada mensagem cai em um. */
public final class Fluxos {

    private Fluxos() {
    }

    public static final String FIREWALL = "firewall";
    public static final String CONTAS = "contas";
    public static final String MAQUINAS = "maquinas";
    public static final String ARQUIVOS = "arquivos";
    public static final String GERAL = "geral";

    public static final List<String> TODOS = List.of(FIREWALL, CONTAS, MAQUINAS, ARQUIVOS, GERAL);

}
