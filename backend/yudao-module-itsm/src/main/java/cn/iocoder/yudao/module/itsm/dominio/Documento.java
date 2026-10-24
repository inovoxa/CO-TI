package cn.iocoder.yudao.module.itsm.dominio;

import java.util.Locale;

/**
 * CPF e CNPJ: normalização e dígitos verificadores. Aceita o CNPJ alfanumérico (Receita Federal, a partir de julho
 * de 2026): as 12 primeiras posições podem ter letras, os 2 dígitos verificadores continuam numéricos e o cálculo é o
 * mesmo, com cada caractere valendo o código ASCII menos 48.
 */
public final class Documento {

    private Documento() {
    }

    /** Sem pontuação, letras maiúsculas; vazio vira null. */
    public static String normalizar(String doc) {
        if (doc == null) {
            return null;
        }
        String s = doc.replaceAll("[.\\-/\\s]", "").toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    public static boolean valido(String normalizado) {
        if (normalizado == null) {
            return false;
        }
        if (normalizado.matches("\\d{11}")) {
            return cpf(normalizado);
        }
        if (normalizado.matches("[0-9A-Z]{12}\\d{2}")) {
            return cnpj(normalizado);
        }
        return false;
    }

    private static boolean cpf(String c) {
        if (c.chars().distinct().count() == 1) {
            return false;   // 000.000.000-00, 111..., passam na conta mas não existem
        }
        for (int n = 9; n <= 10; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) {
                soma += (c.charAt(i) - '0') * (n + 1 - i);
            }
            int dv = (soma * 10) % 11 % 10;
            if (dv != c.charAt(n) - '0') {
                return false;
            }
        }
        return true;
    }

    private static boolean cnpj(String c) {
        if (c.chars().distinct().count() == 1) {
            return false;
        }
        int[] pesos = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        for (int n = 12; n <= 13; n++) {
            int soma = 0;
            for (int i = 0; i < n; i++) {
                soma += (c.charAt(i) - '0') * pesos[i + 13 - n];
            }
            int resto = soma % 11;
            int dv = resto < 2 ? 0 : 11 - resto;
            if (dv != c.charAt(n) - '0') {
                return false;
            }
        }
        return true;
    }

}
