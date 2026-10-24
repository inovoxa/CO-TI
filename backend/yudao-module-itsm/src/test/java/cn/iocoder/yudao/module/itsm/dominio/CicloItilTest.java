package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dal.dataobject.MudancaDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ProblemaDO;
import cn.iocoder.yudao.module.itsm.dominio.CicloChamado.TransicaoInvalidaException;
import cn.iocoder.yudao.module.itsm.enums.MudancaStatus;
import cn.iocoder.yudao.module.itsm.enums.MudancaTipo;
import cn.iocoder.yudao.module.itsm.enums.ProblemaStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CicloItilTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 8, 0);

    private static ProblemaDO problema(boolean comTecnico) {
        ProblemaDO p = new ProblemaDO();
        CicloItil.abrir(p, T0, comTecnico);
        return p;
    }

    private static MudancaDO mudanca(MudancaTipo tipo) {
        MudancaDO m = new MudancaDO();
        m.setTipo(tipo.getCodigo());
        CicloItil.abrir(m, T0, true);
        return m;
    }

    private static void status(ProblemaDO p, ProblemaStatus s) {
        assertEquals(s.getCodigo(), p.getStatus(), () -> "esperado " + s + ", veio " + ProblemaStatus.de(p.getStatus()));
    }

    private static void status(MudancaDO m, MudancaStatus s) {
        assertEquals(s.getCodigo(), m.getStatus(), () -> "esperado " + s + ", veio " + MudancaStatus.de(m.getStatus()));
    }

    // ================================ problema ================================

    @Test
    void problemaAbreNovoOuEmAnalise() {
        status(problema(false), ProblemaStatus.NOVO);
        status(problema(true), ProblemaStatus.EM_ANALISE);
    }

    @Test
    void atribuirTecnicoTiraDeNovoOuAceito() {
        ProblemaDO p = problema(false);
        CicloItil.mudarStatus(p, ProblemaStatus.ACEITO.getCodigo());
        CicloItil.tecnicoAtribuido(p);
        status(p, ProblemaStatus.EM_ANALISE);
        CicloItil.mudarStatus(p, ProblemaStatus.PENDENTE.getCodigo());
        CicloItil.tecnicoAtribuido(p);
        status(p, ProblemaStatus.PENDENTE);   // não mexe em quem já está sendo tratado
    }

    @Test
    void solucionadoEFechadoSoPelasAcoes() {
        ProblemaDO p = problema(true);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(p, ProblemaStatus.SOLUCIONADO.getCodigo()));
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(p, ProblemaStatus.FECHADO.getCodigo()));
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.fechar(p, T0));
        CicloItil.solucionar(p, T0.plusDays(1));
        status(p, ProblemaStatus.SOLUCIONADO);
        assertEquals(T0.plusDays(1), p.getSolucionadoEm());
        CicloItil.fechar(p, T0.plusDays(2));
        status(p, ProblemaStatus.FECHADO);
        assertEquals(T0.plusDays(2), p.getFechadoEm());
    }

    @Test
    void observacaoSoDepoisDeSolucionadoEPodeFechar() {
        ProblemaDO p = problema(true);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(p, ProblemaStatus.EM_OBSERVACAO.getCodigo()));
        CicloItil.solucionar(p, T0);
        CicloItil.mudarStatus(p, ProblemaStatus.EM_OBSERVACAO.getCodigo());
        status(p, ProblemaStatus.EM_OBSERVACAO);
        assertNotNull(p.getSolucionadoEm(), "observar não desfaz a solução");
        CicloItil.fechar(p, T0.plusDays(7));
        status(p, ProblemaStatus.FECHADO);
    }

    @Test
    void voltarATrabalharApagaASolucao() {
        ProblemaDO p = problema(true);
        CicloItil.solucionar(p, T0);
        CicloItil.mudarStatus(p, ProblemaStatus.EM_ANALISE.getCodigo());
        assertNull(p.getSolucionadoEm());
    }

    @Test
    void fechadoSoSaiReabrindo() {
        ProblemaDO p = problema(true);
        CicloItil.solucionar(p, T0);
        CicloItil.fechar(p, T0);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(p, ProblemaStatus.EM_ANALISE.getCodigo()));
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.solucionar(p, T0));
        CicloItil.reabrir(p, true);
        status(p, ProblemaStatus.EM_ANALISE);
        assertNull(p.getSolucionadoEm());
        assertNull(p.getFechadoEm());
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.reabrir(p, true), "já está aberto");
    }

    // ================================ mudança ================================

    @Test
    void mudancaNormalSoAndaDepoisDeAprovada() {
        MudancaDO m = mudanca(MudancaTipo.NORMAL);
        status(m, MudancaStatus.EM_AVALIACAO);
        for (MudancaStatus s : new MudancaStatus[]{MudancaStatus.ACEITA, MudancaStatus.EM_TESTE, MudancaStatus.EM_QUALIFICACAO}) {
            assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(m, s.getCodigo()), s.name());
        }
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.aplicar(m, T0));
        assertFalse(CicloItil.liberada(m));

        CicloItil.aguardarAprovacao(m);
        status(m, MudancaStatus.EM_APROVACAO);
        assertEquals(Aprovacoes.AGUARDANDO, m.getValidacao());
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(m, MudancaStatus.EM_AVALIACAO.getCodigo()),
                "enquanto há resposta pendente");

        CicloItil.resultadoAprovacao(m, Aprovacoes.APROVADA, T0);
        status(m, MudancaStatus.ACEITA);
        CicloItil.mudarStatus(m, MudancaStatus.EM_TESTE.getCodigo());
        CicloItil.aplicar(m, T0.plusHours(2));
        status(m, MudancaStatus.APLICADA);
        assertEquals(T0.plusHours(2), m.getSolucionadoEm());
    }

    @Test
    void padraoEEmergencialNaoEsperamAprovacao() {
        MudancaDO padrao = mudanca(MudancaTipo.PADRAO);
        CicloItil.mudarStatus(padrao, MudancaStatus.ACEITA.getCodigo());
        CicloItil.aplicar(padrao, T0);
        status(padrao, MudancaStatus.APLICADA);

        MudancaDO emergencial = mudanca(MudancaTipo.EMERGENCIAL);
        CicloItil.aplicar(emergencial, T0);
        status(emergencial, MudancaStatus.APLICADA);
        // a aprovação vem depois: a situação continua aplicada, só a validação muda
        CicloItil.aguardarAprovacao(emergencial);
        status(emergencial, MudancaStatus.APLICADA);
        CicloItil.resultadoAprovacao(emergencial, Aprovacoes.APROVADA, T0);
        status(emergencial, MudancaStatus.APLICADA);
        assertEquals(Aprovacoes.APROVADA, emergencial.getValidacao());
    }

    @Test
    void recusaEncerraEReabrirPedeNovaRodada() {
        MudancaDO m = mudanca(MudancaTipo.NORMAL);
        CicloItil.aguardarAprovacao(m);
        CicloItil.resultadoAprovacao(m, Aprovacoes.RECUSADA, T0);
        status(m, MudancaStatus.RECUSADA);
        assertEquals(T0, m.getFechadoEm());
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(m, MudancaStatus.EM_AVALIACAO.getCodigo()));
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.aguardarAprovacao(m));

        CicloItil.reabrir(m);
        status(m, MudancaStatus.EM_AVALIACAO);
        assertEquals(Aprovacoes.NENHUMA, m.getValidacao());
        assertNull(m.getFechadoEm());
        assertFalse(CicloItil.liberada(m));
    }

    @Test
    void situacoesQueVemDeAcoes() {
        MudancaDO m = mudanca(MudancaTipo.PADRAO);
        for (MudancaStatus s : new MudancaStatus[]{MudancaStatus.EM_APROVACAO, MudancaStatus.RECUSADA,
                MudancaStatus.CANCELADA, MudancaStatus.APLICADA, MudancaStatus.FECHADA}) {
            assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(m, s.getCodigo()), s.name());
        }
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.mudarStatus(m, MudancaStatus.EM_REVISAO.getCodigo()),
                "revisão só depois de aplicada");
    }

    @Test
    void revisaoFecharECancelar() {
        MudancaDO m = mudanca(MudancaTipo.PADRAO);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.fechar(m, T0), "fechar sem aplicar");
        CicloItil.aplicar(m, T0);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.cancelar(m, T0), "já aplicada");
        CicloItil.mudarStatus(m, MudancaStatus.EM_REVISAO.getCodigo());
        assertNotNull(m.getSolucionadoEm());
        CicloItil.fechar(m, T0.plusDays(1));
        status(m, MudancaStatus.FECHADA);

        MudancaDO c = mudanca(MudancaTipo.NORMAL);
        CicloItil.cancelar(c, T0);
        status(c, MudancaStatus.CANCELADA);
        assertThrows(TransicaoInvalidaException.class, () -> CicloItil.cancelar(c, T0));
        CicloItil.reabrir(c);
        status(c, MudancaStatus.EM_AVALIACAO);
    }

}
