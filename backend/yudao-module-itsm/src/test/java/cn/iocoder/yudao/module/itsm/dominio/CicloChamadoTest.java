package cn.iocoder.yudao.module.itsm.dominio;

import cn.iocoder.yudao.module.itsm.dal.dataobject.ChamadoDO;
import cn.iocoder.yudao.module.itsm.dominio.CicloChamado.TransicaoInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.itsm.enums.ChamadoStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class CicloChamadoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 26, 8, 0);

    private static ChamadoDO novo(boolean comTecnico) {
        ChamadoDO c = new ChamadoDO();
        CicloChamado.abrir(c, T0, comTecnico);
        return c;
    }

    @Test
    void abreNovoSemTecnicoEEmAtendimentoComTecnico() {
        ChamadoDO sem = novo(false);
        assertEquals(NOVO.getCodigo(), sem.getStatus());
        assertNull(sem.getAssumidoEm());

        ChamadoDO com = novo(true);
        assertEquals(ATRIBUIDO.getCodigo(), com.getStatus());
        assertEquals(T0, com.getAssumidoEm());
        assertEquals(0L, com.getTempoAssumirSeg());
    }

    @Test
    void atribuirTecnicoMarcaAssumidoUmaVezSo() {
        ChamadoDO c = novo(false);
        CicloChamado.tecnicoAtribuido(c, T0.plusMinutes(12));
        assertEquals(ATRIBUIDO.getCodigo(), c.getStatus());
        assertEquals(12 * 60L, c.getTempoAssumirSeg());
        CicloChamado.tecnicoAtribuido(c, T0.plusHours(3));
        assertEquals(12 * 60L, c.getTempoAssumirSeg(), "o primeiro atendimento não muda");
    }

    @Test
    void semTecnicoVoltaANovoEPlanejamentoIdaEVolta() {
        ChamadoDO c = novo(true);
        CicloChamado.planejar(c);
        assertEquals(PLANEJADO.getCodigo(), c.getStatus());
        CicloChamado.semPlanejamento(c);
        assertEquals(ATRIBUIDO.getCodigo(), c.getStatus());
        CicloChamado.semTecnicos(c);
        assertEquals(NOVO.getCodigo(), c.getStatus());
    }

    @Test
    void pendenteNaoContaNoTempoDeSolucao() {
        ChamadoDO c = novo(true);
        CicloChamado.colocarPendente(c, T0.plusHours(1));
        assertEquals(PENDENTE.getCodigo(), c.getStatus());
        CicloChamado.retomar(c, T0.plusHours(5), true);   // 4 h pendente
        assertEquals(ATRIBUIDO.getCodigo(), c.getStatus());
        assertEquals(4 * 3600L, c.getEsperaSeg());
        CicloChamado.solucionar(c, T0.plusHours(6));
        assertEquals(SOLUCIONADO.getCodigo(), c.getStatus());
        assertEquals(2 * 3600L, c.getTempoSolucaoSeg(), "6 h corridas menos 4 h pendente");
    }

    @Test
    void solucionarDiretoDePendenteEncerraAEspera() {
        ChamadoDO c = novo(true);
        CicloChamado.colocarPendente(c, T0.plusMinutes(30));
        CicloChamado.solucionar(c, T0.plusMinutes(90));
        assertNull(c.getInicioEspera());
        assertEquals(3600L, c.getEsperaSeg());
        assertEquals(1800L, c.getTempoSolucaoSeg());
    }

    @Test
    void aprovarFechaERecusarReabre() {
        ChamadoDO c = novo(true);
        CicloChamado.solucionar(c, T0.plusHours(2));
        CicloChamado.reabrir(c, true);
        assertEquals(ATRIBUIDO.getCodigo(), c.getStatus());
        assertNull(c.getSolucionadoEm());

        CicloChamado.solucionar(c, T0.plusHours(3));
        CicloChamado.fechar(c, T0.plusHours(4));
        assertEquals(FECHADO.getCodigo(), c.getStatus());
        assertEquals(T0.plusHours(3), c.getSolucionadoEm());
        assertEquals(4 * 3600L, c.getTempoFechamentoSeg());
    }

    @Test
    void fechamentoDiretoContaComoSolucao() {
        ChamadoDO c = novo(false);
        CicloChamado.fechar(c, T0.plusMinutes(5));
        assertEquals(FECHADO.getCodigo(), c.getStatus());
        assertEquals(T0.plusMinutes(5), c.getSolucionadoEm());
        assertEquals(300L, c.getTempoSolucaoSeg());
        assertEquals(T0.plusMinutes(5), c.getAssumidoEm());
    }

    @Test
    void transicoesInvalidas() {
        ChamadoDO c = novo(true);
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.retomar(c, T0, true));
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.reabrir(c, true));
        CicloChamado.fechar(c, T0.plusHours(1));
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.fechar(c, T0.plusHours(2)));
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.colocarPendente(c, T0.plusHours(2)));
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.tecnicoAtribuido(c, T0.plusHours(2)));
        assertThrows(TransicaoInvalidaException.class, () -> CicloChamado.solucionar(c, T0.plusHours(2)));
    }

    @Test
    void reabrirSemTecnicoVoltaANovo() {
        ChamadoDO c = novo(false);
        CicloChamado.fechar(c, T0.plusHours(1));
        CicloChamado.reabrir(c, false);
        assertEquals(NOVO.getCodigo(), c.getStatus());
        assertNull(c.getFechadoEm());
    }

}
