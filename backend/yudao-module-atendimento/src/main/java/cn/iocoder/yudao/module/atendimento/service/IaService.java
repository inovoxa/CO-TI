package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.atendimento.canal.CanalSegredos;
import cn.iocoder.yudao.module.atendimento.canal.ProvedorHttp;
import cn.iocoder.yudao.module.atendimento.config.AtendimentoProperties;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaConfigDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaProvedorDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.IaUsoDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.IaMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.IaProvedorMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.IaUsoMapper;
import cn.iocoder.yudao.module.atendimento.dominio.PromptsIa;
import cn.iocoder.yudao.module.atendimento.dominio.ProvedoresIa;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Provedores de IA (vários, docs/specs/atendimento-ia.md). Cada uso tem o seu principal e uma reserva opcional: o robô do
 * atendimento ("assistente"), o copiloto e o Assistente IA do portal ("interno"), que liga e desliga à parte. Com principal
 * definido, só ele e a reserva são tentados, e um uso nunca cai na credencial do outro. Sem principal, os provedores ativos são
 * tentados pela prioridade, como antes. Cada tentativa fica em {@code ia_uso}.
 */
@Slf4j
@Service
public class IaService {

    private static final Duration LIMITE = Duration.ofSeconds(90);
    private static final Set<String> COPILOTO = Set.of("sugerir", "resumir", "reescrever");
    public static final String INTERNO = "interno";

    @Resource
    private IaMapper iaMapper;
    @Resource
    private IaProvedorMapper iaProvedorMapper;
    @Resource
    private IaUsoMapper iaUsoMapper;
    @Resource
    private CanalSegredos segredos;
    @Resource
    private ProvedorHttp http;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private AtendimentoProperties props;

    /** Chamada de ferramenta pedida pelo modelo. */
    public record Chamada(String id, String nome, Map<String, Object> argumentos, String argumentosBrutos) {
    }

    /** Resposta do modelo: texto e/ou chamadas de ferramenta, e quem respondeu. */
    public record Resposta(String conteudo, List<Chamada> chamadas, Map<String, Object> mensagemBruta, Long provedorId) {
    }

    /** IA do atendimento ligada e com pelo menos um provedor utilizável. */
    public boolean ativo() {
        IaConfigDO c = iaMapper.selectById(IaConfigDO.ID);
        return c != null && Boolean.TRUE.equals(c.getAtivo()) && !ordem(c, "assistente").isEmpty();
    }

    /** Assistente IA do portal ligado e com provedor utilizável. */
    public boolean ativoInterno() {
        IaConfigDO c = iaMapper.selectById(IaConfigDO.ID);
        return c != null && Boolean.TRUE.equals(c.getInternoAtivo()) && !ordem(c, INTERNO).isEmpty();
    }

    private boolean ativoPara(String finalidade) {
        return INTERNO.equals(finalidade) ? ativoInterno() : ativo();
    }

    private static boolean utilizavel(IaProvedorDO p) {
        ProvedoresIa.Tipo t = ProvedoresIa.TIPOS.get(p.getTipo());
        return Boolean.TRUE.equals(p.getAtivo()) && p.getModelo() != null && (p.getChave() != null || t == null || !t.exigeChave());
    }

    List<IaProvedorDO> ordem(String finalidade) {
        return ordem(iaMapper.selectById(IaConfigDO.ID), finalidade);
    }

    /** Ordem das tentativas: o principal e a reserva do uso; sem principal, todos os ativos pela prioridade. */
    private List<IaProvedorDO> ordem(IaConfigDO c, String finalidade) {
        Long principal;
        Long reserva;
        if (INTERNO.equals(finalidade)) {
            principal = c.getProvedorInternoId();
            reserva = c.getProvedorInternoReservaId();
        } else if (COPILOTO.contains(finalidade)) {
            principal = c.getProvedorCopilotoId();
            reserva = c.getProvedorCopilotoReservaId();
        } else if ("assistente".equals(finalidade) || "transcricao".equals(finalidade)) {
            principal = c.getProvedorAssistenteId();
            reserva = c.getProvedorAssistenteReservaId();
        } else {
            principal = null;
            reserva = null;
        }
        List<IaProvedorDO> todos = iaProvedorMapper.emOrdem().stream().filter(IaService::utilizavel).toList();
        if (principal == null) {
            return todos;
        }
        List<IaProvedorDO> out = new ArrayList<>();
        todos.stream().filter(p -> p.getId().equals(principal)).findFirst().ifPresent(out::add);
        todos.stream().filter(p -> p.getId().equals(reserva) && !p.getId().equals(principal)).findFirst().ifPresent(out::add);
        return out;
    }

    /**
     * Conversa com o modelo, tentando os provedores em ordem até um responder.
     *
     * @param ferramentas null = sem ferramentas
     * @throws IllegalStateException IA desligada ou todos os provedores falharam (a mensagem diz o último erro)
     */
    public Resposta conversar(List<Map<String, Object>> mensagens, List<Map<String, Object>> ferramentas, String finalidade,
                              Long conversaId, Long userId) {
        if (!ativoPara(finalidade)) {
            throw new IllegalStateException("a inteligência artificial está desligada ou sem provedor ativo");
        }
        List<IaProvedorDO> ordem = ordem(finalidade);
        IllegalStateException ultimo = null;
        for (IaProvedorDO p : ordem) {
            try {
                return chamar(p, mensagens, ferramentas, finalidade, conversaId, userId);
            } catch (IllegalStateException e) {
                ultimo = e;
                log.warn("[conversar][provedor {} ({}) falhou: {}]", p.getId(), p.getNome(), e.getMessage());
            }
        }
        throw ultimo != null ? ultimo : new IllegalStateException("nenhum provedor de IA ativo");
    }

    @SuppressWarnings("unchecked")
    private Resposta chamar(IaProvedorDO p, List<Map<String, Object>> mensagens, List<Map<String, Object>> ferramentas, String finalidade,
                            Long conversaId, Long userId) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("model", p.getModelo());
        corpo.put("messages", mensagens);
        corpo.put("temperature", p.getTemperatura() == null ? 0.3 : p.getTemperatura().doubleValue());
        corpo.put("max_tokens", p.getMaxTokens() == null ? 800 : p.getMaxTokens());
        if (Boolean.TRUE.equals(p.getSemRaciocinio())) {
            corpo.put("reasoning_effort", "none");
        }
        if (ferramentas != null && !ferramentas.isEmpty()) {
            corpo.put("tools", ferramentas);
            corpo.put("tool_choice", "auto");
        }
        long ini = System.currentTimeMillis();
        IaUsoDO uso = IaUsoDO.builder().finalidade(finalidade).provedorId(p.getId()).conversaId(conversaId).userId(userId)
                .modelo(p.getModelo()).criadoEm(LocalDateTime.now()).build();
        try {
            ProvedorHttp.Resposta r = http.postJson(p.getBaseUrl().replaceAll("/+$", "") + "/chat/completions",
                    p.getChave() == null ? null : segredos.decifrar(p.getChave()), corpo, LIMITE,
                    ProvedoresIa.cabecalhos(p.getTipo(), props.getUrlPublica()));
            if (r.json().get("usage") instanceof Map<?, ?> u) {
                uso.setTokensEntrada(inteiro(u.get("prompt_tokens")));
                uso.setTokensSaida(inteiro(u.get("completion_tokens")));
            }
            if (!r.ok()) {
                Object erro = r.json().get("error") instanceof Map<?, ?> e ? e.get("message") : r.corpo();
                throw new IllegalStateException(p.getNome() + " respondeu HTTP " + r.status() + ": " + cortar(String.valueOf(erro), 200));
            }
            if (!(r.json().get("choices") instanceof List<?> escolhas) || escolhas.isEmpty() || !(escolhas.get(0) instanceof Map<?, ?> e0)
                    || !(e0.get("message") instanceof Map<?, ?> msg)) {
                throw new IllegalStateException(p.getNome() + " respondeu sem mensagem");
            }
            List<Chamada> chamadas = new ArrayList<>();
            if (msg.get("tool_calls") instanceof List<?> tcs) {
                for (Object o : tcs) {
                    if (o instanceof Map<?, ?> tc && tc.get("function") instanceof Map<?, ?> f) {
                        String brutos = f.get("arguments") == null ? "{}" : f.get("arguments").toString();
                        Map<String, Object> args;
                        try {
                            args = JsonUtils.parseObject(brutos, new TypeReference<Map<String, Object>>() {
                            });
                        } catch (Exception semJson) {
                            args = null;
                        }
                        chamadas.add(new Chamada(String.valueOf(tc.get("id")), String.valueOf(f.get("name")),
                                args == null ? Map.of() : args, brutos));
                    }
                }
            }
            return new Resposta(msg.get("content") == null ? "" : msg.get("content").toString(), chamadas,
                    (Map<String, Object>) msg, p.getId());
        } catch (IllegalStateException e) {
            uso.setErro(cortar(e.getMessage(), 500));
            throw e;
        } catch (Exception e) {
            uso.setErro(cortar(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            throw new IllegalStateException("falha ao falar com " + p.getNome() + ": " + e.getMessage(), e);
        } finally {
            uso.setDuracaoMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - ini));
            try {
                iaUsoMapper.insert(uso);
            } catch (Exception e) {
                log.warn("[chamar][uso não registrado: {}]", e.getMessage());
            }
        }
    }

    // =====================================================================================================
    // Transcrição de áudio
    // =====================================================================================================

    /** Há provedor ativo com modelo de transcrição (e a IA está ligada)? */
    public boolean transcreve() {
        return transcreve("assistente");
    }

    /** @param uso "assistente" (atendimento) ou {@link #INTERNO} */
    public boolean transcreve(String uso) {
        return ativoPara(uso) && ordem(uso).stream().anyMatch(p -> p.getModeloAudio() != null);
    }

    /**
     * Transcreve o áudio ({@code POST {base}/audio/transcriptions}, padrão Whisper), tentando os provedores com modelo
     * de áudio na ordem do assistente.
     *
     * @return o texto, ou null se nenhum conseguiu
     */
    public String transcrever(byte[] audio, String nome, String tipo, Long conversaId) {
        return transcrever(audio, nome, tipo, conversaId, "assistente");
    }

    /** @param uso "assistente" (atendimento) ou {@link #INTERNO}: cada um transcreve com os próprios provedores */
    public String transcrever(byte[] audio, String nome, String tipo, Long conversaId, String usoIa) {
        if (!ativoPara(usoIa)) {
            return null;
        }
        for (IaProvedorDO p : ordem(usoIa)) {
            if (p.getModeloAudio() == null) {
                continue;
            }
            long ini = System.currentTimeMillis();
            IaUsoDO uso = IaUsoDO.builder().finalidade("transcricao").provedorId(p.getId()).conversaId(conversaId)
                    .modelo(p.getModeloAudio()).criadoEm(LocalDateTime.now()).build();
            try {
                Map<String, String> campos = new LinkedHashMap<>();
                campos.put("model", p.getModeloAudio());
                campos.put("language", "pt");
                campos.put("response_format", "json");
                ProvedorHttp.Resposta r = http.postArquivo(p.getBaseUrl().replaceAll("/+$", "") + "/audio/transcriptions",
                        p.getChave() == null ? null : segredos.decifrar(p.getChave()), campos, "file", audio, nome, tipo);
                if (r.ok() && r.json().get("text") != null) {
                    return r.json().get("text").toString().strip();
                }
                Object erro = r.json().get("error") instanceof Map<?, ?> e ? e.get("message") : r.corpo();
                uso.setErro(cortar(p.getNome() + " respondeu HTTP " + r.status() + ": " + erro, 500));
            } catch (Exception e) {
                uso.setErro(cortar(e.getClass().getSimpleName() + ": " + e.getMessage(), 500));
            } finally {
                uso.setDuracaoMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - ini));
                try {
                    iaUsoMapper.insert(uso);
                } catch (Exception e) {
                    log.warn("[transcrever][uso não registrado: {}]", e.getMessage());
                }
            }
        }
        return null;
    }

    // =====================================================================================================
    // Configuração
    // =====================================================================================================

    public AtendimentoVOs.IaConfig obter() {
        IaConfigDO c = iaMapper.selectById(IaConfigDO.ID);
        AtendimentoVOs.IaConfig v = new AtendimentoVOs.IaConfig();
        v.setAtivo(c.getAtivo());
        v.setProvedorAssistenteId(c.getProvedorAssistenteId());
        v.setProvedorCopilotoId(c.getProvedorCopilotoId());
        v.setProvedorAssistenteReservaId(c.getProvedorAssistenteReservaId());
        v.setProvedorCopilotoReservaId(c.getProvedorCopilotoReservaId());
        v.setInternoAtivo(c.getInternoAtivo());
        v.setProvedorInternoId(c.getProvedorInternoId());
        v.setProvedorInternoReservaId(c.getProvedorInternoReservaId());
        Map<Long, String> nomes = new HashMap<>();
        for (IaProvedorDO p : iaProvedorMapper.emOrdem()) {
            nomes.put(p.getId(), p.getNome());
            AtendimentoVOs.IaProvedor x = new AtendimentoVOs.IaProvedor();
            x.setId(p.getId());
            x.setNome(p.getNome());
            x.setTipo(p.getTipo());
            x.setBaseUrl(p.getBaseUrl());
            x.setChaveDefinida(p.getChave() != null);
            x.setModelo(p.getModelo());
            x.setModeloAudio(p.getModeloAudio());
            x.setTemperatura(p.getTemperatura());
            x.setMaxTokens(p.getMaxTokens());
            x.setSemRaciocinio(Boolean.TRUE.equals(p.getSemRaciocinio()));
            x.setAtivo(p.getAtivo());
            x.setPrioridade(p.getPrioridade());
            x.setTestadoEm(p.getTestadoEm());
            x.setErroTeste(p.getErroTeste());
            v.getProvedores().add(x);
        }
        ProvedoresIa.TIPOS.values().forEach(t -> v.getTipos().add(Map.of("codigo", t.codigo(), "rotulo", t.rotulo(), "baseUrl", t.baseUrl(),
                "exigeChave", t.exigeChave(), "exemploModelo", t.exemploModelo())));
        iaUsoMapper.resumo30Dias().forEach(r -> {
            AtendimentoVOs.IaUso u = new AtendimentoVOs.IaUso();
            u.setFinalidade(String.valueOf(r.get("finalidade")));
            Long pid = r.get("provedor_id") == null ? null : ((Number) r.get("provedor_id")).longValue();
            u.setProvedor(pid == null ? "—" : nomes.getOrDefault(pid, "#" + pid + " (excluído)"));
            u.setChamadas(((Number) r.get("chamadas")).longValue());
            u.setErros(((Number) r.get("erros")).longValue());
            u.setTokensEntrada(((Number) r.get("entrada")).longValue());
            u.setTokensSaida(((Number) r.get("saida")).longValue());
            v.getUso().add(u);
        });
        return v;
    }

    /** Chave geral e principais de cada uso. */
    public AtendimentoVOs.IaConfig salvarConfig(AtendimentoVOs.IaConfig req, Long userId) {
        for (Long id : Arrays.asList(req.getProvedorAssistenteId(), req.getProvedorCopilotoId(), req.getProvedorAssistenteReservaId(),
                req.getProvedorCopilotoReservaId(), req.getProvedorInternoId(), req.getProvedorInternoReservaId())) {
            if (id != null && iaProvedorMapper.selectById(id) == null) {
                throw exception(ACAO_INVALIDA, "Provedor de IA não encontrado");
            }
        }
        boolean ativo = Boolean.TRUE.equals(req.getAtivo());
        boolean internoAtivo = Boolean.TRUE.equals(req.getInternoAtivo());
        if ((ativo || internoAtivo) && iaProvedorMapper.emOrdem().stream().noneMatch(IaService::utilizavel)) {
            throw exception(ACAO_INVALIDA, "Para ligar a IA, cadastre ao menos um provedor ativo, com modelo e chave");
        }
        iaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaConfigDO>()
                .eq(IaConfigDO::getId, IaConfigDO.ID).set(IaConfigDO::getAtivo, ativo)
                .set(IaConfigDO::getProvedorAssistenteId, req.getProvedorAssistenteId())
                .set(IaConfigDO::getProvedorCopilotoId, req.getProvedorCopilotoId())
                .set(IaConfigDO::getProvedorAssistenteReservaId, req.getProvedorAssistenteReservaId())
                .set(IaConfigDO::getProvedorCopilotoReservaId, req.getProvedorCopilotoReservaId())
                .set(IaConfigDO::getInternoAtivo, internoAtivo)
                .set(IaConfigDO::getProvedorInternoId, req.getProvedorInternoId())
                .set(IaConfigDO::getProvedorInternoReservaId, req.getProvedorInternoReservaId()));
        Map<String, Object> auditado = new LinkedHashMap<>();
        auditado.put("ativo", ativo);
        auditado.put("assistente", String.valueOf(req.getProvedorAssistenteId()));
        auditado.put("copiloto", String.valueOf(req.getProvedorCopilotoId()));
        auditado.put("internoAtivo", internoAtivo);
        auditado.put("interno", String.valueOf(req.getProvedorInternoId()));
        auditoria.registrar("atendimento.ia_configurada", userId, null, auditado);
        return obter();
    }

    public Long salvarProvedor(AtendimentoVOs.IaProvedor req, Long userId) {
        ProvedoresIa.Tipo tipo = ProvedoresIa.TIPOS.get(req.getTipo());
        if (tipo == null) {
            throw exception(ACAO_INVALIDA, "Tipo de provedor desconhecido");
        }
        IaProvedorDO atual = req.getId() == null ? null : iaProvedorMapper.selectById(req.getId());
        if (req.getId() != null && atual == null) {
            throw exception(ACAO_INVALIDA, "Provedor de IA não encontrado");
        }
        String url = (req.getBaseUrl() == null || req.getBaseUrl().isBlank() ? tipo.baseUrl() : req.getBaseUrl()).strip().replaceAll("/+$", "");
        try {
            URI u = URI.create(url);
            if (!Set.of("http", "https").contains(String.valueOf(u.getScheme()).toLowerCase(Locale.ROOT)) || u.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw exception(ACAO_INVALIDA, "Endereço do provedor inválido (use http:// ou https://)");
        }
        if (!segredos.configurado()) {
            throw exception(CAIXA_SEM_CHAVE_MESTRA);
        }
        String chave = req.getChave() == null || req.getChave().isBlank() ? (atual == null ? null : atual.getChave())
                : segredos.cifrar(req.getChave().strip());
        if (tipo.exigeChave() && chave == null) {
            throw exception(ACAO_INVALIDA, "Informe a chave de API do " + tipo.rotulo());
        }
        boolean mudouConexao = atual == null || !Objects.equals(url, atual.getBaseUrl()) || !Objects.equals(chave, atual.getChave())
                || !Objects.equals(req.getModelo().strip(), atual.getModelo());
        IaProvedorDO p = IaProvedorDO.builder().id(req.getId()).nome(req.getNome().strip()).tipo(tipo.codigo()).baseUrl(url).chave(chave)
                .modelo(req.getModelo().strip()).modeloAudio(ContatoService.vazio(req.getModeloAudio())).temperatura(req.getTemperatura() == null ? BigDecimal.valueOf(0.3) : req.getTemperatura())
                .maxTokens(req.getMaxTokens() == null ? 800 : req.getMaxTokens())
                .semRaciocinio(Boolean.TRUE.equals(req.getSemRaciocinio())).ativo(!Boolean.FALSE.equals(req.getAtivo()))
                .prioridade(req.getPrioridade() == null ? 0 : req.getPrioridade()).build();
        if (atual == null) {
            iaProvedorMapper.insert(p);
        } else {
            iaProvedorMapper.updateById(p);
            iaProvedorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaProvedorDO>()
                    .eq(IaProvedorDO::getId, p.getId()).set(IaProvedorDO::getModeloAudio, p.getModeloAudio()));
            if (mudouConexao) {
                iaProvedorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaProvedorDO>()
                        .eq(IaProvedorDO::getId, p.getId()).set(IaProvedorDO::getTestadoEm, null).set(IaProvedorDO::getErroTeste, null));
            }
        }
        auditoria.registrar("atendimento.ia_provedor_salvo", userId, null, Map.of("provedor", p.getId(), "nome", p.getNome(),
                "tipo", p.getTipo(), "baseUrl", url, "modelo", p.getModelo(), "semRaciocinio", p.getSemRaciocinio(), "chaveTrocada", req.getChave() != null && !req.getChave().isBlank()));
        return p.getId();
    }

    public void excluirProvedor(Long id, Long userId) {
        IaProvedorDO p = iaProvedorMapper.selectById(id);
        if (p == null) {
            throw exception(ACAO_INVALIDA, "Provedor de IA não encontrado");
        }
        iaProvedorMapper.deleteById(id);
        // quem apontava para ele volta a usar o primeiro ativo
        iaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaConfigDO>()
                .eq(IaConfigDO::getId, IaConfigDO.ID).eq(IaConfigDO::getProvedorAssistenteId, id).set(IaConfigDO::getProvedorAssistenteId, null));
        iaMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaConfigDO>()
                .eq(IaConfigDO::getId, IaConfigDO.ID).eq(IaConfigDO::getProvedorCopilotoId, id).set(IaConfigDO::getProvedorCopilotoId, null));
        auditoria.registrar("atendimento.ia_provedor_excluido", userId, null, Map.of("provedor", id, "nome", p.getNome()));
    }

    /** Testa só este provedor (sem reserva), mesmo com a IA desligada. */
    public AtendimentoVOs.IaConfig testarProvedor(Long id, Long userId) {
        IaProvedorDO p = iaProvedorMapper.selectById(id);
        if (p == null) {
            throw exception(ACAO_INVALIDA, "Provedor de IA não encontrado");
        }
        String erro = null;
        try {
            Resposta r = chamar(p, List.of(PromptsIa.mensagem("system", "Responda apenas com a palavra: ok"),
                    PromptsIa.mensagem("user", "teste de conexão")), null, "teste", null, userId);
            if (r.conteudo().isBlank()) {
                erro = "o provedor respondeu sem texto";
            }
        } catch (IllegalStateException e) {
            erro = e.getMessage();
        }
        iaProvedorMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IaProvedorDO>()
                .eq(IaProvedorDO::getId, id).set(IaProvedorDO::getTestadoEm, LocalDateTime.now())
                .set(IaProvedorDO::getErroTeste, erro == null ? null : cortar(erro, 500)));
        if (erro != null) {
            throw exception(CANAL_TESTE_FALHOU, "Teste falhou: " + erro);
        }
        return obter();
    }

    private static Integer inteiro(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static String cortar(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n - 3) + "...";
    }

}
