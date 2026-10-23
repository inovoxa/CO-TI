package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.enums.ChamadoStatus;

import java.time.Duration;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.itsm.enums.ChamadoStatus.*;

/**
 * Ciclo de vida do chamado: situação e relógios. Sem banco e sem Spring, para ser testado à parte.
 *
 * <p>Regras portadas do GLPI 12 (CommonITILObject / Ticket):
 * <ul>
 *   <li>abre como Novo; com técnico (pessoa ou equipe) já atribuído, abre Em atendimento;</li>
 *   <li>atribuir técnico a um chamado Novo o coloca Em atendimento; tirar o último técnico o devolve a Novo;</li>
 *   <li>tarefa planejada (com início) põe Em atendimento (planejado); sem tarefas planejadas, volta;</li>
 *   <li>Pendente congela o relógio: o tempo parado é somado em espera e descontado dos tempos de solução;</li>
 *   <li>solução põe Solucionado; aprovada (ou fechamento automático) fecha; recusada reabre;</li>
 *   <li>a primeira ação de um técnico marca o "assumido em" (GLPI: takeintoaccountdate).</li>
 * </ul>
 */
public final class CicloChamado {

    private CicloChamado() {
    }

    public static class TransicaoInvalidaException extends RuntimeException {
        public TransicaoInvalidaException(String mensagem) {
            super(mensagem);
        }
    }

    public static void abrir(ChamadoDO c, LocalDateTime agora, boolean temTecnico) {
        c.setAbertoEm(agora);
        c.setStatus((temTecnico ? ATRIBUIDO : NOVO).getCodigo());
        c.setEsperaSeg(0L);
        c.setEsperaSlaSeg(0L);
        c.setTempoGastoSeg(0L);
        if (temTecnico) {
            assumir(c, agora);
        }
    }

    /** Primeira ação técnica (atribuir, acompanhamento de técnico, tarefa, solução). */
    public static void assumir(ChamadoDO c, LocalDateTime agora) {
        if (c.getAssumidoEm() == null) {
            c.setAssumidoEm(agora);
            c.setTempoAssumirSeg(segundos(c.getAbertoEm(), agora));
        }
    }

    public static void tecnicoAtribuido(ChamadoDO c, LocalDateTime agora) {
        exigirAberto(c, "atribuir técnico");
        if (status(c) == NOVO) {
            c.setStatus(ATRIBUIDO.getCodigo());
        }
        assumir(c, agora);
    }

    public static void semTecnicos(ChamadoDO c) {
        if (status(c).emAtendimento()) {
            c.setStatus(NOVO.getCodigo());
        }
    }

    public static void planejar(ChamadoDO c) {
        if (status(c) == ATRIBUIDO) {
            c.setStatus(PLANEJADO.getCodigo());
        }
    }

    public static void semPlanejamento(ChamadoDO c) {
        if (status(c) == PLANEJADO) {
            c.setStatus(ATRIBUIDO.getCodigo());
        }
    }

    public static void colocarPendente(ChamadoDO c, LocalDateTime agora) {
        ChamadoStatus s = status(c);
        if (!s.aberto()) {
            throw new TransicaoInvalidaException("Só um chamado em aberto pode ficar pendente");
        }
        if (s == PENDENTE) {
            return;
        }
        c.setStatus(PENDENTE.getCodigo());
        c.setInicioEspera(agora);
    }

    /** Sai de Pendente e volta a Em atendimento (com técnico) ou Novo. */
    public static void retomar(ChamadoDO c, LocalDateTime agora, boolean temTecnico) {
        if (status(c) != PENDENTE) {
            throw new TransicaoInvalidaException("O chamado não está pendente");
        }
        encerrarEspera(c, agora);
        c.setStatus((temTecnico ? ATRIBUIDO : NOVO).getCodigo());
    }

    public static void solucionar(ChamadoDO c, LocalDateTime agora) {
        exigirAberto(c, "solucionar");
        encerrarEspera(c, agora);
        assumir(c, agora);
        c.setStatus(SOLUCIONADO.getCodigo());
        c.setSolucionadoEm(agora);
        c.setTempoSolucaoSeg(tempoUtil(c, agora));
    }

    /** Aprovação da solução, fechamento automático ou fechamento direto por quem tem permissão. */
    public static void fechar(ChamadoDO c, LocalDateTime agora) {
        ChamadoStatus s = status(c);
        if (s == FECHADO) {
            throw new TransicaoInvalidaException("O chamado já está fechado");
        }
        if (s != SOLUCIONADO) {
            // fechamento direto: conta como solucionado agora
            solucionar(c, agora);
        }
        c.setStatus(FECHADO.getCodigo());
        c.setFechadoEm(agora);
        c.setTempoFechamentoSeg(tempoUtil(c, agora));
    }

    /** Solução recusada pelo solicitante, ou reabertura de chamado fechado (com permissão). */
    public static void reabrir(ChamadoDO c, boolean temTecnico) {
        ChamadoStatus s = status(c);
        if (s.aberto()) {
            throw new TransicaoInvalidaException("O chamado já está em aberto");
        }
        c.setStatus((temTecnico ? ATRIBUIDO : NOVO).getCodigo());
        c.setSolucionadoEm(null);
        c.setFechadoEm(null);
        c.setTempoSolucaoSeg(null);
        c.setTempoFechamentoSeg(null);
    }

    /** Tempo desde a abertura, sem o tempo em pendente (o SLA da F1b desconta só o expediente). */
    static long tempoUtil(ChamadoDO c, LocalDateTime agora) {
        long espera = c.getEsperaSeg() == null ? 0 : c.getEsperaSeg();
        return Math.max(0, segundos(c.getAbertoEm(), agora) - espera);
    }

    private static void encerrarEspera(ChamadoDO c, LocalDateTime agora) {
        if (c.getInicioEspera() != null) {
            long espera = c.getEsperaSeg() == null ? 0 : c.getEsperaSeg();
            c.setEsperaSeg(espera + segundos(c.getInicioEspera(), agora));
            c.setInicioEspera(null);
        }
    }

    private static void exigirAberto(ChamadoDO c, String acao) {
        if (!status(c).aberto()) {
            throw new TransicaoInvalidaException("Não é possível " + acao + " num chamado " + status(c).getRotulo().toLowerCase());
        }
    }

    private static ChamadoStatus status(ChamadoDO c) {
        return ChamadoStatus.de(c.getStatus());
    }

    static long segundos(LocalDateTime de, LocalDateTime ate) {
        return de == null || ate == null ? 0 : Math.max(0, Duration.between(de, ate).getSeconds());
    }

}
