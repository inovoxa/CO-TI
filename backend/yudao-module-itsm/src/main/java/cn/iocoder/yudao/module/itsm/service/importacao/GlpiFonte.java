package cn.iocoder.yudao.module.itsm.service.importacao;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Leitura do banco MySQL/MariaDB de um GLPI 10/11. Só leitura: a conexão é marcada read-only e nada é escrito lá.
 *
 * <p>Cuidados com o driver, porque o servidor de origem é informado na tela:
 * <ul>
 *   <li>host, porta, banco e usuário são validados e vão em propriedades separadas, nunca concatenados com texto
 *       livre na URL;</li>
 *   <li>{@code allowLoadLocalInfile}, {@code allowUrlInLocalInfile} e {@code autoDeserialize} desligados: um servidor
 *       malicioso não consegue ler arquivos do CO-TI nem fazer o driver desserializar objetos;</li>
 *   <li>datas zeradas do MySQL ({@code 0000-00-00}) viram vazio.</li>
 * </ul>
 */
public class GlpiFonte implements AutoCloseable {

    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Pattern BANCO = Pattern.compile("^[A-Za-z0-9_$-]{1,64}$");
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z0-9_.@%-]{1,64}$");

    private final Connection conexao;
    private final Map<String, Set<String>> colunas = new HashMap<>();

    public GlpiFonte(String host, int porta, String banco, String usuario, String senha, boolean exigirTls) throws SQLException {
        if (!HOST.matcher(host).matches() || porta < 1 || porta > 65535 || !BANCO.matcher(banco).matches()
                || !USUARIO.matcher(usuario).matches()) {
            throw new IllegalArgumentException("Servidor, porta, banco ou usuário inválido");
        }
        Properties p = new Properties();
        p.setProperty("user", usuario);
        p.setProperty("password", senha == null ? "" : senha);
        p.setProperty("allowLoadLocalInfile", "false");
        p.setProperty("allowUrlInLocalInfile", "false");
        p.setProperty("allowLocalInfile", "false");
        p.setProperty("autoDeserialize", "false");
        p.setProperty("allowMultiQueries", "false");
        p.setProperty("zeroDateTimeBehavior", "CONVERT_TO_NULL");
        p.setProperty("characterEncoding", "UTF-8");
        p.setProperty("connectTimeout", "10000");
        p.setProperty("socketTimeout", "600000");
        p.setProperty("sslMode", exigirTls ? "VERIFY_IDENTITY" : "PREFERRED");
        // datas como o relógio do CO-TI as entende: a sessão do MySQL usa o mesmo deslocamento
        p.setProperty("preserveInstants", "false");
        conexao = DriverManager.getConnection("jdbc:mysql://" + host + ":" + porta + "/" + banco, p);
        conexao.setReadOnly(true);
        ZoneOffset off = ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now());
        try (PreparedStatement st = conexao.prepareStatement("SET time_zone = ?")) {
            st.setString(1, off.getId().equals("Z") ? "+00:00" : off.getId());
            st.execute();
        }
    }

    /** Versão do GLPI gravada na configuração (glpi_configs, contexto core). */
    public String versao() {
        List<Map<String, Object>> r = lista("SELECT value FROM glpi_configs WHERE context = 'core' AND name = 'version'");
        return r.isEmpty() ? null : Objects.toString(r.get(0).get("value"), null);
    }

    public boolean temTabela(String tabela) {
        return !colunas(tabela).isEmpty();
    }

    public boolean temColuna(String tabela, String coluna) {
        return colunas(tabela).contains(coluna);
    }

    private Set<String> colunas(String tabela) {
        return colunas.computeIfAbsent(tabela, t -> {
            Set<String> s = new HashSet<>();
            lista("SELECT column_name AS c FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ?", t)
                    .forEach(r -> s.add(String.valueOf(r.get("c")).toLowerCase(Locale.ROOT)));
            return s;
        });
    }

    public long contar(String sql, Object... params) {
        List<Map<String, Object>> r = lista(sql, params);
        if (r.isEmpty()) {
            return 0;
        }
        Object v = r.get(0).values().iterator().next();
        return v == null ? 0 : ((Number) v).longValue();
    }

    /** Tudo de uma vez (tabelas pequenas: usuários, grupos, categorias...). */
    public List<Map<String, Object>> lista(String sql, Object... params) {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement st = conexao.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                st.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> linha = new HashMap<>();
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        linha.put(md.getColumnLabel(c).toLowerCase(Locale.ROOT), valor(rs, c, md.getColumnType(c)));
                    }
                    out.add(linha);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha lendo o GLPI: " + e.getMessage(), e);
        }
        return out;
    }

    /**
     * Percorre uma tabela grande em lotes pela chave (id > último), sem OFFSET.
     *
     * @param sql consulta com {@code ?} para o último id e terminando em "ORDER BY id LIMIT ?" — montada pelo
     *            importador a partir de constantes, nunca de texto da tela
     */
    public void lotes(String sql, int tamanho, Consumer<Map<String, Object>> cada) {
        long ultimo = -1;
        while (true) {
            List<Map<String, Object>> lote = lista(sql, ultimo, tamanho);
            for (Map<String, Object> r : lote) {
                cada.accept(r);
                ultimo = ((Number) r.get("id")).longValue();
            }
            if (lote.size() < tamanho) {
                return;
            }
        }
    }

    private static Object valor(ResultSet rs, int c, int tipo) throws SQLException {
        return switch (tipo) {
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> rs.getObject(c, LocalDateTime.class);
            case Types.DATE -> rs.getObject(c, LocalDate.class);
            default -> rs.getObject(c);
        };
    }

    @Override
    public void close() {
        try {
            conexao.close();
        } catch (SQLException ignorado) {
            // conexão de leitura: nada a desfazer
        }
    }

}
