package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.dal.IntegracaoEntradaDO;
import cn.iocoder.yudao.module.infraestrutura.dal.IntegracaoEventoDO;
import cn.iocoder.yudao.module.infraestrutura.dal.IntegracaoEventoMapper;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras.Decisao;
import cn.iocoder.yudao.module.infraestrutura.service.IdentificacaoContas.Conta;
import cn.iocoder.yudao.module.itsm.service.ExecucaoAdService;
import cn.iocoder.yudao.module.itsm.service.FormularioService;
import cn.iocoder.yudao.module.system.service.retencao.AvisoInfraService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Decide e aplica o que um aviso pede. {@link #avaliar} só consulta, e por isso também serve ao botão "Testar"; {@link #aplicar}
 * grava o chamado e o evento juntos.
 */
@Slf4j
@Service
public class IntegracaoProcessador {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CAMPO_LOGIN = "login_usuario_desativar";
    /** Servem para identificar a pessoa, mas não precisam ficar à vista no chamado. */
    private static final Set<String> FORA_DO_CHAMADO = Set.of("cpf", "data_nascimento");

    public enum Tipo { IGNORAR, CANCELAR, CHAMADO, EXECUTAR, CONFIRMAR }

    /** O que será feito. Não guarda valores do identificador: esta resposta aparece na tela. */
    public record Plano(Tipo tipo, String mensagem, String idHash, String login, Map<String, String> respostas, String observacao,
                        LocalDateTime executarEm) {
    }

    @Resource
    private IdentificacaoContas identificacao;
    @Resource
    private IntegracaoEventoMapper integracaoEventoMapper;
    @Resource
    private FormularioService formularioService;
    @Resource
    private ExecucaoAdService execucaoAdService;
    @Resource
    private AvisoInfraService avisoInfra;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private CifradorSegredos cifrador;

    /** @param eventoId evento em processamento; vazio no teste pela tela */
    public Plano avaliar(IntegracaoEntradaDO e, byte[] corpo, Long eventoId) throws IOException {
        JsonNode raiz = JSON.readTree(corpo);
        String situacao = EntradaRegras.ler(raiz, e.getCaminhoSituacao()).orElse(null);
        Optional<String> identificador = EntradaRegras.ler(raiz, e.getCaminhoId());
        String idHash = identificador.map(v -> EntradaRegras.hash((e.getId() + ":" + v).getBytes(StandardCharsets.UTF_8))).orElse(null);

        if (EntradaRegras.contem(e.getValoresCancelar(), situacao)) {
            return plano(Tipo.CANCELAR, "Cancela o que estiver pendente para esta pessoa", idHash, null);
        }
        if (!EntradaRegras.contem(e.getValoresDesligar(), situacao)) {
            return plano(Tipo.IGNORAR, situacao == null ? "O aviso não informa a situação" : "A situação não pede bloqueio", idHash, null);
        }
        Map<String, String> respostas = respostas(e, raiz);
        if (identificador.isEmpty()) {
            return chamado("O aviso não traz o identificador da pessoa", null, respostas, e);
        }
        if (eventoId != null) {
            Long anterior = integracaoEventoMapper.selectChamadoAnterior(e.getId(), idHash, eventoId);
            if (anterior != null) {
                return plano(Tipo.IGNORAR, "Já tratado no chamado #" + anterior, idHash, null);
            }
        }

        List<Conta> contas = identificacao.buscar(e.getAtributoAd(), identificador.get());
        Conta conta = contas.size() == 1 ? contas.get(0) : null;
        String nomeRh = EntradaRegras.ler(raiz, e.getCaminhoNome()).orElse(null);
        boolean nomeDiverge = conta != null && Boolean.TRUE.equals(e.getConferirNome())
                && (nomeRh == null || !EntradaRegras.mesmoNome(nomeRh, conta.nome()));
        boolean protegida = conta != null && (EntradaRegras.loginProtegido(conta.login(), e.getLoginsProtegidos())
                || identificacao.protegida(conta, e.getGruposProtegidos()));
        Decisao d = EntradaRegras.decidir(new EntradaRegras.Fatos(contas.size(), conta != null && conta.ativa(), protegida, nomeDiverge,
                "confirmacao".equals(e.getModo()), integracaoEventoMapper.contarBloqueios24h(e.getId()), e.getLimiteDia()));

        String login = conta == null ? null : conta.login();
        if (login != null) {
            respostas.put(CAMPO_LOGIN, login);
            if (nomeRh == null) {
                respostas.putIfAbsent("nome_completo", conta.nome());
            }
        }
        return switch (d) {
            case JA_DESATIVADA -> plano(Tipo.IGNORAR, "A conta " + login + " já está desativada", idHash, login);
            case NAO_IDENTIFICADA -> chamado("Nenhuma conta encontrada com este identificador", idHash, respostas, e);
            case VARIAS_CONTAS -> chamado(contas.size() + " contas têm este identificador: nenhuma foi bloqueada", idHash, respostas, e);
            case PROTEGIDA -> chamado("A conta " + login + " é protegida e não é bloqueada automaticamente", idHash, respostas, e);
            case NOME_DIVERGE -> chamado("O nome enviado não confere com o da conta " + login, idHash, respostas, e);
            case CONFIRMAR_LIMITE -> confirmar("Limite de " + e.getLimiteDia() + " bloqueios automáticos em 24 horas atingido", e, raiz, idHash, login, respostas);
            case CONFIRMAR_MODO -> confirmar("Bloqueio de " + login + " aguarda a confirmação de um técnico", e, raiz, idHash, login, respostas);
            case EXECUTAR -> new Plano(Tipo.EXECUTAR, "Bloqueia a conta " + login + " e encerra o chamado", idHash, login, respostas,
                    "Pedido automático da integração \"" + e.getNome() + "\", sem aprovação.", dataFutura(e, raiz));
        };
    }

    private Plano confirmar(String motivo, IntegracaoEntradaDO e, JsonNode raiz, String idHash, String login, Map<String, String> respostas) {
        return new Plano(Tipo.CONFIRMAR, motivo, idHash, login, respostas,
                "Pedido da integração \"" + e.getNome() + "\". " + motivo + ".", dataFutura(e, raiz));
    }

    private Plano chamado(String motivo, String idHash, Map<String, String> respostas, IntegracaoEntradaDO e) {
        respostas.putIfAbsent(CAMPO_LOGIN, "não identificado");
        return new Plano(Tipo.CHAMADO, motivo + ". Abre o chamado para a equipe, sem tocar no AD", idHash, null, respostas,
                "Pedido da integração \"" + e.getNome() + "\". " + motivo + ". O AD não foi alterado: um técnico precisa verificar.", null);
    }

    private static Plano plano(Tipo t, String mensagem, String idHash, String login) {
        return new Plano(t, mensagem, idHash, login, Map.of(), null, null);
    }

    private static LocalDateTime dataFutura(IntegracaoEntradaDO e, JsonNode raiz) {
        return EntradaRegras.ler(raiz, e.getCaminhoData()).flatMap(EntradaRegras::data).filter(d -> d.isAfter(LocalDateTime.now())).orElse(null);
    }

    private static Map<String, String> respostas(IntegracaoEntradaDO e, JsonNode raiz) {
        Map<String, String> mapa = JsonUtils.parseObject(e.getMapa(), new TypeReference<Map<String, String>>() {
        });
        Map<String, String> r = new LinkedHashMap<>();
        if (mapa != null) {
            mapa.forEach((campo, caminho) -> {
                // o login sempre vem da identificação, nunca do texto recebido
                if (!FORA_DO_CHAMADO.contains(campo) && !CAMPO_LOGIN.equals(campo)) {
                    EntradaRegras.ler(raiz, caminho).ifPresent(v -> r.put(campo, v));
                }
            });
        }
        r.putIfAbsent("motivo_desativacao", "Desligamento informado pelo RH");
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public void aplicar(IntegracaoEntradaDO e, IntegracaoEventoDO ev, Plano p) {
        IntegracaoEventoDO novo = new IntegracaoEventoDO();
        novo.setId(ev.getId());
        novo.setIdHash(p.idHash());
        novo.setLogin(p.login());
        novo.setResultado(p.mensagem());
        novo.setProcessadoEm(LocalDateTime.now());
        boolean age = p.tipo() == Tipo.CHAMADO || p.tipo() == Tipo.EXECUTAR || p.tipo() == Tipo.CONFIRMAR;
        if ("simulacao".equals(e.getModo()) && (age || p.tipo() == Tipo.CANCELAR)) {
            novo.setStatus("simulado");
            novo.setResultado("Simulação, nada foi feito: " + p.mensagem());
        } else {
            switch (p.tipo()) {
                case IGNORAR -> novo.setStatus("ignorado");
                case CANCELAR -> {
                    novo.setStatus("cancelado");
                    novo.setResultado(cancelar(e, p));
                }
                case CHAMADO -> {
                    novo.setChamadoId(formularioService.enviarDeIntegracao(e.getFormularioId(), p.respostas(), e.getSolicitanteId(), p.observacao(), null));
                    novo.setStatus("chamado");
                }
                case EXECUTAR, CONFIRMAR -> {
                    boolean confirmar = p.tipo() == Tipo.CONFIRMAR;
                    novo.setChamadoId(formularioService.enviarDeIntegracao(e.getFormularioId(), p.respostas(), e.getSolicitanteId(), p.observacao(),
                            new FormularioService.PedidoAd(p.login(), confirmar, p.executarEm())));
                    novo.setStatus(confirmar ? "confirmacao" : p.executarEm() != null ? "agendado" : "executando");
                    if (p.executarEm() != null) {
                        novo.setResultado(p.mensagem() + "; agendado para " + p.executarEm());
                    }
                    if (confirmar && p.mensagem().startsWith("Limite")) {
                        avisoInfra.avisar("integracao-limite-" + e.getId(), "A integração \"" + e.getNome() + "\" atingiu o limite de bloqueios "
                                + "automáticos em 24 horas. Os próximos esperam confirmação em Service Desk > Execuções no AD.");
                    }
                }
            }
            if (age) {
                auditoria.registrar("integracao.evento", null, p.login(), Map.of("entrada", e.getCodigo(), "evento", ev.getId(), "acao", p.tipo().name(),
                        "chamado", novo.getChamadoId()));
            }
        }
        integracaoEventoMapper.updateById(novo);
    }

    private String cancelar(IntegracaoEntradaDO e, Plano p) {
        if (p.idHash() == null) {
            return "O aviso não traz o identificador da pessoa: nada foi cancelado";
        }
        int n = 0;
        for (IntegracaoEventoDO pendente : integracaoEventoMapper.selectPendentesDaPessoa(e.getId(), p.idHash())) {
            if (execucaoAdService.cancelarDeIntegracao(pendente.getChamadoId(), "Bloqueio cancelado por novo aviso da integração \"" + e.getNome() + "\".")) {
                IntegracaoEventoDO c = new IntegracaoEventoDO();
                c.setId(pendente.getId());
                c.setStatus("cancelado");
                c.setResultado("Cancelado por um aviso posterior");
                integracaoEventoMapper.updateById(c);
                n++;
            }
        }
        return n == 0 ? "Não havia bloqueio pendente" : n + " bloqueio(s) pendente(s) cancelado(s)";
    }

    public byte[] lerCorpo(IntegracaoEventoDO ev) {
        return cifrador.decifrar(ev.getPayloadCifrado(), contexto(ev.getEntradaId())).getBytes(StandardCharsets.UTF_8);
    }

    public static String contexto(Long entradaId) {
        return "integracao-evento:" + entradaId;
    }

}
