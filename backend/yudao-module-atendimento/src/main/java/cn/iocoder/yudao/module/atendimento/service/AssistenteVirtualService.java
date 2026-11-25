package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AssistenteVirtualVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ModeloDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.CaixaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ModeloMapper;
import cn.iocoder.yudao.module.atendimento.dominio.PromptsIa;
import cn.iocoder.yudao.module.atendimento.dominio.TextosAssistente;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Textos e opções do assistente virtual (menu, consulta, avisos e orientações da IA). O padrão vem de
 * {@link TextosAssistente}; o banco guarda só o que a organização mudou. Leitura com cache curto: o robô consulta
 * isso a cada mensagem.
 */
@Service
public class AssistenteVirtualService {

    private static final long CACHE_MS = 60_000;

    @Resource
    private JdbcTemplate jdbc;
    @Resource
    private CaixaService caixaService;
    @Resource
    private CaixaMapper caixaMapper;
    @Resource
    private ModeloMapper modeloMapper;
    @Resource
    private IaService iaService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private AuditoriaService auditoria;

    private volatile Map<String, String> cache;
    private volatile long carregadoEm;

    // =====================================================================================================
    // Uso pelo robô
    // =====================================================================================================

    /** Texto atual (o da organização ou o padrão), sem variáveis trocadas. */
    public String texto(String chave) {
        String v = alterados().get(chave);
        return v != null ? v : TextosAssistente.item(chave).padrao();
    }

    /** Texto atual com as variáveis trocadas. */
    public String texto(String chave, Map<String, ?> valores) {
        return TextosAssistente.preencher(texto(chave), valores);
    }

    public boolean sim(String chave) {
        return Boolean.parseBoolean(texto(chave));
    }

    public int numero(String chave) {
        try {
            return Integer.parseInt(texto(chave));
        } catch (NumberFormatException e) {
            return Integer.parseInt(TextosAssistente.item(chave).padrao());
        }
    }

    private Map<String, String> alterados() {
        Map<String, String> c = cache;
        if (c == null || System.currentTimeMillis() - carregadoEm > CACHE_MS) {
            Map<String, String> novo = new HashMap<>();
            jdbc.query("SELECT chave, valor FROM atendimento.assistente_texto", rs -> {
                novo.put(rs.getString(1), rs.getString(2));
            });
            cache = c = Collections.unmodifiableMap(novo);
            carregadoEm = System.currentTimeMillis();
        }
        return c;
    }

    private void limparCache() {
        cache = null;
    }

    // =====================================================================================================
    // Tela
    // =====================================================================================================

    public AssistenteVirtualVOs.Tela tela() {
        record Linha(String valor, Long por, LocalDateTime em) {
        }
        Map<String, Linha> banco = new HashMap<>();
        jdbc.query("SELECT chave, valor, atualizado_por, atualizado_em FROM atendimento.assistente_texto", rs -> {
            Timestamp t = rs.getTimestamp(4);
            long por = rs.getLong(3);
            banco.put(rs.getString(1), new Linha(rs.getString(2), rs.wasNull() ? null : por, t == null ? null : t.toLocalDateTime()));
        });
        Set<Long> pessoas = new HashSet<>();
        banco.values().forEach(l -> pessoas.add(l.por()));
        pessoas.remove(null);
        Map<Long, AdminUserRespDTO> nomes = pessoas.isEmpty() ? Map.of() : adminUserApi.getUserMap(pessoas);
        AssistenteVirtualVOs.Tela t = new AssistenteVirtualVOs.Tela();
        for (TextosAssistente.Item i : TextosAssistente.itens()) {
            Linha l = banco.get(i.chave());
            AssistenteVirtualVOs.Texto x = new AssistenteVirtualVOs.Texto();
            x.setChave(i.chave());
            x.setGrupo(i.grupo());
            x.setRotulo(i.rotulo());
            x.setAjuda(i.ajuda());
            x.setTipo(i.tipo().name());
            x.setPadrao(i.padrao());
            x.setValor(l == null ? i.padrao() : l.valor());
            x.setAlterado(l != null);
            x.setVariaveis(i.variaveis());
            if (i.tipo() == TextosAssistente.Tipo.NUMERO) {
                x.setMinimo(i.minimo());
                x.setMaximo(i.maximo());
            }
            if (l != null) {
                x.setAtualizadoEm(l.em());
                AdminUserRespDTO u = l.por() == null ? null : nomes.get(l.por());
                x.setAtualizadoPor(u == null ? null : u.getNickname());
            }
            t.getTextos().add(x);
        }
        t.setCaixas(caixaService.listar());
        t.setRegrasFixas(PromptsIa.REGRAS.strip());
        t.setIaAtiva(iaService.ativo());
        return t;
    }

    /** Grava os textos enviados; vazio ou igual ao padrão volta ao padrão. */
    @Transactional(rollbackFor = Exception.class)
    public void salvar(Map<String, String> valores, Long userId) {
        Map<String, String> validos = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : valores.entrySet()) {
            if (!TextosAssistente.existe(e.getKey())) {
                throw exception(ASSISTENTE_TEXTO_INVALIDO, "texto desconhecido: " + e.getKey());
            }
            TextosAssistente.Item i = TextosAssistente.item(e.getKey());
            String v = e.getValue();
            if (i.tipo() == TextosAssistente.Tipo.TEXTO && (v == null || v.isBlank())) {
                validos.put(e.getKey(), null);
                continue;
            }
            try {
                validos.put(e.getKey(), TextosAssistente.validar(e.getKey(), v));
            } catch (IllegalArgumentException ex) {
                throw exception(ASSISTENTE_TEXTO_INVALIDO, ex.getMessage());
            }
        }
        Map<String, String> atuais = alteradosDoBanco();
        List<String> mudados = new ArrayList<>();
        for (Map.Entry<String, String> e : validos.entrySet()) {
            String antes = atuais.get(e.getKey());
            if (Objects.equals(antes, e.getValue())) {
                continue;
            }
            mudados.add(e.getKey());
            if (e.getValue() == null) {
                jdbc.update("DELETE FROM atendimento.assistente_texto WHERE chave = ?", e.getKey());
            } else {
                jdbc.update("INSERT INTO atendimento.assistente_texto (chave, valor, atualizado_por, atualizado_em) VALUES (?, ?, ?, now())"
                        + " ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor, atualizado_por = EXCLUDED.atualizado_por,"
                        + " atualizado_em = now()", e.getKey(), e.getValue(), userId);
            }
        }
        limparCache();
        if (!mudados.isEmpty()) {
            auditoria.registrar("atendimento.assistente_textos", userId, null, Map.of("textos", mudados));
        }
    }

    private Map<String, String> alteradosDoBanco() {
        Map<String, String> m = new HashMap<>();
        jdbc.query("SELECT chave, valor FROM atendimento.assistente_texto", rs -> {
            m.put(rs.getString(1), rs.getString(2));
        });
        return m;
    }

    /** Só os campos do robô; o resto da caixa (canal, equipe, horário) fica como está. */
    @Transactional(rollbackFor = Exception.class)
    public void salvarCaixa(AssistenteVirtualVOs.CaixaRobo req, Long userId) {
        CaixaDO c = caixaService.existente(req.getId());
        if (req.getAvisoModeloId() != null) {
            ModeloDO m = modeloMapper.selectById(req.getAvisoModeloId());
            if (m == null || !m.getCaixaId().equals(c.getId())) {
                throw exception(MODELO_NAO_EXISTE);
            }
        }
        caixaMapper.update(null, new LambdaUpdateWrapper<CaixaDO>().eq(CaixaDO::getId, c.getId())
                .set(CaixaDO::getFluxoAtivo, Boolean.TRUE.equals(req.getFluxoAtivo()))
                .set(CaixaDO::getFluxoBoasVindas, vazio(req.getFluxoBoasVindas()))
                .set(CaixaDO::getRoboAtivo, Boolean.TRUE.equals(req.getRoboAtivo()))
                .set(CaixaDO::getRoboNome, vazio(req.getRoboNome()))
                .set(CaixaDO::getRoboInstrucoes, vazio(req.getRoboInstrucoes()))
                .set(CaixaDO::getRoboMaxRespostas, req.getRoboMaxRespostas() == null ? 10 : req.getRoboMaxRespostas())
                .set(CaixaDO::getAvisosAtivo, !Boolean.FALSE.equals(req.getAvisosAtivo()))
                .set(CaixaDO::getAvisoModeloId, req.getAvisoModeloId()));
        auditoria.registrar("atendimento.assistente_caixa", userId, null, Map.of("caixa", c.getId(), "nome", c.getNome()));
    }

    // =====================================================================================================
    // Exportar e importar
    // =====================================================================================================

    public AssistenteVirtualVOs.Arquivo exportar() {
        AssistenteVirtualVOs.Arquivo a = new AssistenteVirtualVOs.Arquivo();
        TextosAssistente.itens().forEach(i -> a.getTextos().put(i.chave(), texto(i.chave())));
        return a;
    }

    public List<AssistenteVirtualVOs.LinhaImportacao> previaImportacao(AssistenteVirtualVOs.Arquivo arq) {
        if (arq == null || arq.getTextos() == null || !"co-ti/assistente-virtual".equals(arq.getTipo())) {
            throw exception(ASSISTENTE_IMPORTACAO_INVALIDA, "não é uma exportação do assistente virtual do CO-TI");
        }
        Map<String, String> atuais = alteradosDoBanco();
        List<AssistenteVirtualVOs.LinhaImportacao> linhas = new ArrayList<>();
        for (Map.Entry<String, String> e : arq.getTextos().entrySet()) {
            AssistenteVirtualVOs.LinhaImportacao l = new AssistenteVirtualVOs.LinhaImportacao();
            l.setChave(e.getKey());
            l.setNovo(e.getValue());
            if (!TextosAssistente.existe(e.getKey())) {
                l.setSituacao("ignorada");
                l.setErro("texto que esta versão não conhece");
                linhas.add(l);
                continue;
            }
            TextosAssistente.Item i = TextosAssistente.item(e.getKey());
            l.setRotulo(i.rotulo());
            String atual = atuais.getOrDefault(e.getKey(), i.padrao());
            l.setAtual(atual);
            try {
                String v = TextosAssistente.validar(e.getKey(), e.getValue());
                String efetivo = v == null ? i.padrao() : v;
                l.setSituacao(efetivo.equals(atual) ? "igual" : v == null ? "padrao" : "muda");
            } catch (IllegalArgumentException ex) {
                l.setSituacao("invalida");
                l.setErro(ex.getMessage());
            }
            linhas.add(l);
        }
        return linhas;
    }

    /** Aplica o que a prévia mostrou como "muda" ou "padrao"; inválidas e desconhecidas ficam de fora. */
    @Transactional(rollbackFor = Exception.class)
    public int importar(AssistenteVirtualVOs.Arquivo arq, Long userId) {
        Map<String, String> aplicar = new LinkedHashMap<>();
        for (AssistenteVirtualVOs.LinhaImportacao l : previaImportacao(arq)) {
            if ("muda".equals(l.getSituacao()) || "padrao".equals(l.getSituacao())) {
                aplicar.put(l.getChave(), l.getNovo());
            }
        }
        if (!aplicar.isEmpty()) {
            salvar(aplicar, userId);
        }
        return aplicar.size();
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

}
