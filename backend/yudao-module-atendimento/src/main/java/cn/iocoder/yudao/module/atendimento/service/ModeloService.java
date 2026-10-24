package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.canal.CanalWhatsappCloud;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.CaixaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ModeloDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ModeloMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Modelos de mensagem do WhatsApp (message templates): copiados da conta na Meta para escolher na tela e mandados
 * quando a janela de 24 h fechou. O texto que fica na conversa é o corpo com as variáveis preenchidas.
 */
@Slf4j
@Service
public class ModeloService {

    private static final Pattern VARIAVEL = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    @Resource
    private ModeloMapper modeloMapper;
    @Resource
    private CaixaService caixaService;
    @Resource
    private CanalWhatsappCloud canalWhatsapp;
    @Resource
    private ConversaService conversaService;
    @Resource
    private Acesso acesso;

    /** @return quantos modelos a conta tem agora */
    @Transactional(rollbackFor = Exception.class)
    public int sincronizar(Long caixaId) {
        CaixaDO caixa = caixaService.existente(caixaId);
        if (!"whatsapp_cloud".equals(caixa.getCanal())) {
            throw exception(CANAL_TIPO_ERRADO, "WhatsApp oficial");
        }
        List<Map<String, Object>> daMeta;
        try {
            daMeta = canalWhatsapp.modelos(caixa);
        } catch (Exception e) {
            throw exception(CANAL_TESTE_FALHOU, "Não foi possível buscar os modelos: " + e.getMessage());
        }
        Map<String, ModeloDO> atuais = new HashMap<>();
        modeloMapper.selectList(ModeloDO::getCaixaId, caixaId).forEach(m -> atuais.put(m.getNome() + "|" + m.getIdioma(), m));
        Set<String> vistos = new HashSet<>();
        for (Map<String, Object> t : daMeta) {
            String nome = String.valueOf(t.get("name"));
            String idioma = String.valueOf(t.get("language"));
            String chave = nome + "|" + idioma;
            if (!vistos.add(chave)) {
                continue;
            }
            String corpo = "";
            if (t.get("components") instanceof List<?> comps) {
                for (Object c : comps) {
                    if (c instanceof Map<?, ?> comp && "BODY".equalsIgnoreCase(String.valueOf(comp.get("type"))) && comp.get("text") != null) {
                        corpo = comp.get("text").toString();
                    }
                }
            }
            ModeloDO m = ModeloDO.builder().caixaId(caixaId).nome(ContatoService.cortar(nome, 512)).idioma(ContatoService.cortar(idioma, 20))
                    .categoria(t.get("category") == null ? null : ContatoService.cortar(t.get("category").toString(), 30))
                    .status(t.get("status") == null ? "DESCONHECIDO" : ContatoService.cortar(t.get("status").toString(), 30))
                    .corpo(corpo).variaveis(variaveis(corpo).size())
                    .componentes(JsonUtils.toJsonString(t.get("components") == null ? List.of() : t.get("components"))).build();
            ModeloDO atual = atuais.get(chave);
            if (atual == null) {
                modeloMapper.insert(m);
            } else {
                m.setId(atual.getId());
                modeloMapper.updateById(m);
            }
        }
        // apagado na Meta: some da lista
        atuais.forEach((chave, m) -> {
            if (!vistos.contains(chave)) {
                modeloMapper.deleteById(m.getId());
            }
        });
        return vistos.size();
    }

    /**
     * @param administracao tela de configuração da caixa: todos os modelos, com a situação na Meta; senão, só os
     *                      aprovados e só para quem atende a caixa
     */
    public List<AtendimentoVOs.Modelo> listar(Long caixaId, Long userId, boolean administracao) {
        if (!administracao && !acesso.veTodas(userId) && !acesso.membro(caixaId, userId)) {
            throw exception(CONVERSA_SEM_ACESSO);
        }
        LambdaQueryWrapperX<ModeloDO> q = new LambdaQueryWrapperX<ModeloDO>().eq(ModeloDO::getCaixaId, caixaId);
        if (!administracao) {
            q.eq(ModeloDO::getStatus, "APPROVED");
        }
        return modeloMapper.selectList(q.orderByAsc(ModeloDO::getNome)).stream().map(ModeloService::converter).toList();
    }

    /** Manda o modelo na conversa (fora da janela de 24 h ou para retomar o contato). */
    public Long enviar(AtendimentoVOs.ModeloReq req, Long userId) {
        ConversaDO c = conversaService.existente(req.getConversaId());
        ModeloDO m = modeloMapper.selectById(req.getModeloId());
        if (m == null || !m.getCaixaId().equals(c.getCaixaId()) || !"APPROVED".equals(m.getStatus())) {
            throw exception(MODELO_NAO_EXISTE);
        }
        List<String> nomes = variaveis(m.getCorpo());
        List<String> params = req.getParametros() == null ? List.of() : req.getParametros().stream().map(p -> p == null ? "" : p.strip()).toList();
        if (params.size() != nomes.size() || params.stream().anyMatch(String::isEmpty)) {
            throw exception(MODELO_PARAMETROS, nomes.size());
        }
        String texto = preencher(m.getCorpo(), nomes, params);
        Map<String, Object> modelo = new LinkedHashMap<>();
        modelo.put("nome", m.getNome());
        modelo.put("idioma", m.getIdioma());
        modelo.put("parametros", params);
        if (nomes.stream().anyMatch(n -> !n.matches("\\d+"))) {
            modelo.put("nomesVariaveis", nomes);
        }
        return conversaService.responderModelo(c.getId(), texto, Map.of("modelo", modelo), userId);
    }

    /** Variáveis do corpo na ordem em que aparecem, sem repetir ({{1}}, {{2}}... ou {{nome}}). */
    static List<String> variaveis(String corpo) {
        List<String> out = new ArrayList<>();
        if (corpo == null) {
            return out;
        }
        Matcher m = VARIAVEL.matcher(corpo);
        while (m.find()) {
            if (!out.contains(m.group(1))) {
                out.add(m.group(1));
            }
        }
        // posicionais: em ordem numérica, que é a ordem em que a Meta espera os parâmetros
        if (!out.isEmpty() && out.stream().allMatch(s -> s.matches("\\d+"))) {
            out.sort(Comparator.comparingInt(Integer::parseInt));
        }
        return out;
    }

    static String preencher(String corpo, List<String> nomes, List<String> params) {
        Matcher m = VARIAVEL.matcher(corpo);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int i = nomes.indexOf(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(i >= 0 && i < params.size() ? params.get(i) : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static AtendimentoVOs.Modelo converter(ModeloDO m) {
        AtendimentoVOs.Modelo v = new AtendimentoVOs.Modelo();
        v.setId(m.getId());
        v.setNome(m.getNome());
        v.setIdioma(m.getIdioma());
        v.setCategoria(m.getCategoria());
        v.setStatus(m.getStatus());
        v.setCorpo(m.getCorpo());
        List<String> nomes = variaveis(m.getCorpo());
        v.setVariaveis(nomes.size());
        v.setNomesVariaveis(nomes);
        return v;
    }

}
