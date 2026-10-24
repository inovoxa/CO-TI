package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dal.dataobject.MudancaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ProblemaDO;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaTipo;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;

import java.time.LocalDateTime;
import java.util.Set;

import static cn.iocoder.yudao.module.itsm.dominio.CicloChamado.TransicaoInvalidaException;

/**
 * Ciclo de vida de problemas e mudanças. Sem banco e sem Spring, para ser testado à parte.
 *
 * <p>Problema (GLPI: Problem): o técnico move entre as situações de trabalho; Solucionado só com uma solução,
 * Fechado só pela ação de fechar e depois de solucionado; Em observação vem depois de Solucionado.
 *
 * <p>Mudança (GLPI: Change, com o tipo ITIL que o GLPI não tem):
 * <ul>
 *   <li>Em aprovação só pelo pedido de aprovação; Aceita ou Recusada pelo resultado (todos aprovam / uma recusa);</li>
 *   <li>mudança normal só é aceita, testada, qualificada ou aplicada depois de aprovada; padrão (pré-aprovada) e
 *       emergencial (aprovação pode vir depois) não esperam;</li>
 *   <li>Aplicada só registrando a aplicação (solução); Em revisão vem depois de Aplicada;</li>
 *   <li>Fechada, Recusada e Cancelada só saem reabrindo, que volta para Em avaliação e pede nova aprovação.</li>
 * </ul>
 */
public final class CicloItil {

    private CicloItil() {
    }

    // ================================ problema ================================

    private static final Set<ProblemaStatus> PROBLEMA_MANUAIS = Set.of(ProblemaStatus.NOVO, ProblemaStatus.ACEITO,
            ProblemaStatus.EM_ANALISE, ProblemaStatus.PLANEJADO, ProblemaStatus.PENDENTE, ProblemaStatus.EM_OBSERVACAO);

    public static void abrir(ProblemaDO p, LocalDateTime agora, boolean temTecnico) {
        p.setAbertoEm(agora);
        p.setStatus((temTecnico ? ProblemaStatus.EM_ANALISE : ProblemaStatus.NOVO).getCodigo());
        p.setTempoGastoSeg(0L);
    }

    /** Situação escolhida pelo técnico na tela. */
    public static void mudarStatus(ProblemaDO p, int codigo) {
        ProblemaStatus atual = ProblemaStatus.de(p.getStatus());
        ProblemaStatus novo = ProblemaStatus.de(codigo);
        if (atual == ProblemaStatus.FECHADO) {
            throw new TransicaoInvalidaException("o problema está fechado; reabra antes");
        }
        if (!PROBLEMA_MANUAIS.contains(novo)) {
            throw new TransicaoInvalidaException(novo == ProblemaStatus.SOLUCIONADO
                    ? "para solucionar, registre a solução" : "para fechar, use Fechar");
        }
        if (novo == ProblemaStatus.EM_OBSERVACAO && atual != ProblemaStatus.SOLUCIONADO) {
            throw new TransicaoInvalidaException("só um problema solucionado fica em observação");
        }
        if (novo != ProblemaStatus.EM_OBSERVACAO && (atual == ProblemaStatus.SOLUCIONADO || atual == ProblemaStatus.EM_OBSERVACAO)) {
            p.setSolucionadoEm(null);   // voltou a trabalhar: a solução anterior não valeu
        }
        p.setStatus(novo.getCodigo());
    }

    public static void tecnicoAtribuido(ProblemaDO p) {
        ProblemaStatus s = ProblemaStatus.de(p.getStatus());
        if (s == ProblemaStatus.NOVO || s == ProblemaStatus.ACEITO) {
            p.setStatus(ProblemaStatus.EM_ANALISE.getCodigo());
        }
    }

    public static void solucionar(ProblemaDO p, LocalDateTime agora) {
        ProblemaStatus s = ProblemaStatus.de(p.getStatus());
        if (s == ProblemaStatus.FECHADO) {
            throw new TransicaoInvalidaException("o problema está fechado; reabra antes");
        }
        p.setStatus(ProblemaStatus.SOLUCIONADO.getCodigo());
        p.setSolucionadoEm(agora);
    }

    public static void fechar(ProblemaDO p, LocalDateTime agora) {
        ProblemaStatus s = ProblemaStatus.de(p.getStatus());
        if (s != ProblemaStatus.SOLUCIONADO && s != ProblemaStatus.EM_OBSERVACAO) {
            throw new TransicaoInvalidaException("registre a solução antes de fechar");
        }
        p.setStatus(ProblemaStatus.FECHADO.getCodigo());
        p.setFechadoEm(agora);
    }

    public static void reabrir(ProblemaDO p, boolean temTecnico) {
        if (ProblemaStatus.de(p.getStatus()).aberto() && p.getStatus() != ProblemaStatus.EM_OBSERVACAO.getCodigo()) {
            throw new TransicaoInvalidaException("o problema já está aberto");
        }
        p.setStatus((temTecnico ? ProblemaStatus.EM_ANALISE : ProblemaStatus.NOVO).getCodigo());
        p.setSolucionadoEm(null);
        p.setFechadoEm(null);
    }

    // ================================ mudança ================================

    private static final Set<MudancaStatus> MUDANCA_MANUAIS = Set.of(MudancaStatus.NOVA, MudancaStatus.EM_AVALIACAO,
            MudancaStatus.ACEITA, MudancaStatus.PENDENTE, MudancaStatus.EM_TESTE, MudancaStatus.EM_QUALIFICACAO,
            MudancaStatus.EM_REVISAO);
    private static final Set<MudancaStatus> EXIGEM_LIBERACAO = Set.of(MudancaStatus.ACEITA, MudancaStatus.EM_TESTE,
            MudancaStatus.EM_QUALIFICACAO, MudancaStatus.APLICADA);

    public static void abrir(MudancaDO m, LocalDateTime agora, boolean temTecnico) {
        m.setAbertoEm(agora);
        m.setStatus((temTecnico ? MudancaStatus.EM_AVALIACAO : MudancaStatus.NOVA).getCodigo());
        m.setValidacao(Aprovacoes.NENHUMA);
        m.setTempoGastoSeg(0L);
    }

    public static void mudarStatus(MudancaDO m, int codigo) {
        MudancaStatus atual = MudancaStatus.de(m.getStatus());
        MudancaStatus novo = MudancaStatus.de(codigo);
        exigirNaoEncerrada(atual);
        if (!MUDANCA_MANUAIS.contains(novo)) {
            throw new TransicaoInvalidaException(switch (novo) {
                case EM_APROVACAO -> "para pedir aprovação, use Pedir aprovação";
                case RECUSADA -> "a recusa vem da resposta de um aprovador";
                case CANCELADA -> "para cancelar, use Cancelar";
                case APLICADA -> "para marcar como aplicada, registre a aplicação";
                default -> "para fechar, use Fechar";
            });
        }
        if (novo == MudancaStatus.EM_REVISAO && atual != MudancaStatus.APLICADA) {
            throw new TransicaoInvalidaException("só uma mudança aplicada entra em revisão");
        }
        if (EXIGEM_LIBERACAO.contains(novo)) {
            exigirLiberada(m);
        }
        if (atual == MudancaStatus.EM_APROVACAO && m.getValidacao() == Aprovacoes.AGUARDANDO) {
            throw new TransicaoInvalidaException("há aprovações aguardando resposta");
        }
        if (novo != MudancaStatus.EM_REVISAO && (atual == MudancaStatus.APLICADA || atual == MudancaStatus.EM_REVISAO)) {
            m.setSolucionadoEm(null);
        }
        m.setStatus(novo.getCodigo());
    }

    public static void tecnicoAtribuido(MudancaDO m) {
        if (m.getStatus() == MudancaStatus.NOVA.getCodigo()) {
            m.setStatus(MudancaStatus.EM_AVALIACAO.getCodigo());
        }
    }

    /** Pode ser aceita, testada ou aplicada agora? */
    public static boolean liberada(MudancaDO m) {
        return !MudancaTipo.de(m.getTipo()).exigeAprovacaoAntes() || m.getValidacao() == Aprovacoes.APROVADA;
    }

    public static void exigirLiberada(MudancaDO m) {
        if (!liberada(m)) {
            throw new TransicaoInvalidaException(m.getValidacao() == Aprovacoes.RECUSADA
                    ? "a mudança foi recusada na aprovação" : "a mudança normal precisa ser aprovada antes");
        }
    }

    public static void aguardarAprovacao(MudancaDO m) {
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        exigirNaoEncerrada(s);
        if (s == MudancaStatus.APLICADA || s == MudancaStatus.EM_REVISAO) {
            // emergencial aplicada e aprovada depois: a situação continua, só a validação muda
            m.setValidacao(Aprovacoes.AGUARDANDO);
            return;
        }
        m.setStatus(MudancaStatus.EM_APROVACAO.getCodigo());
        m.setValidacao(Aprovacoes.AGUARDANDO);
    }

    /** Recalculada a cada resposta (Aprovacoes.global sobre todos os pedidos da rodada). */
    public static void resultadoAprovacao(MudancaDO m, int global, LocalDateTime agora) {
        m.setValidacao(global);
        boolean emAprovacao = m.getStatus() == MudancaStatus.EM_APROVACAO.getCodigo();
        if (global == Aprovacoes.APROVADA && emAprovacao) {
            m.setStatus(MudancaStatus.ACEITA.getCodigo());
        } else if (global == Aprovacoes.RECUSADA && emAprovacao) {
            m.setStatus(MudancaStatus.RECUSADA.getCodigo());
            m.setFechadoEm(agora);
        }
    }

    public static void aplicar(MudancaDO m, LocalDateTime agora) {
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        exigirNaoEncerrada(s);
        if (s == MudancaStatus.EM_APROVACAO) {
            throw new TransicaoInvalidaException("há aprovações aguardando resposta");
        }
        exigirLiberada(m);
        m.setStatus(MudancaStatus.APLICADA.getCodigo());
        m.setSolucionadoEm(agora);
    }

    public static void cancelar(MudancaDO m, LocalDateTime agora) {
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        exigirNaoEncerrada(s);
        if (s == MudancaStatus.APLICADA || s == MudancaStatus.EM_REVISAO) {
            throw new TransicaoInvalidaException("a mudança já foi aplicada; feche ou reabra");
        }
        m.setStatus(MudancaStatus.CANCELADA.getCodigo());
        m.setFechadoEm(agora);
    }

    public static void fechar(MudancaDO m, LocalDateTime agora) {
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        if (s != MudancaStatus.APLICADA && s != MudancaStatus.EM_REVISAO) {
            throw new TransicaoInvalidaException("só uma mudança aplicada pode ser fechada (ou cancele)");
        }
        m.setStatus(MudancaStatus.FECHADA.getCodigo());
        m.setFechadoEm(agora);
    }

    public static void reabrir(MudancaDO m) {
        MudancaStatus s = MudancaStatus.de(m.getStatus());
        if (s.aberta() && s != MudancaStatus.EM_REVISAO) {
            throw new TransicaoInvalidaException("a mudança já está aberta");
        }
        if (s == MudancaStatus.RECUSADA || m.getValidacao() == Aprovacoes.RECUSADA) {
            m.setValidacao(Aprovacoes.NENHUMA);   // reformulada: nova rodada de aprovação
        }
        m.setStatus(MudancaStatus.EM_AVALIACAO.getCodigo());
        m.setSolucionadoEm(null);
        m.setFechadoEm(null);
    }

    private static void exigirNaoEncerrada(MudancaStatus s) {
        if (s.encerrada()) {
            throw new TransicaoInvalidaException("a mudança está " + s.getRotulo().toLowerCase() + "; reabra antes");
        }
    }

}
