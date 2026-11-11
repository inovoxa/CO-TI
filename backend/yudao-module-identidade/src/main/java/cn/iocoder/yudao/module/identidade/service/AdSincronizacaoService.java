package cn.iocoder.yudao.module.identidade.service;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.config.AdProperties;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sincronização completa: lê todas as contas do filtro e atualiza o portal; desativa quem sumiu do AD.
 */
@Slf4j
public class AdSincronizacaoService {

    /**
     * Se mais desta fração dos vínculos "sumir" numa rodada, o mais provável é filtro ou OU mal configurados,
     * não uma demissão em massa: não desativamos ninguém e registramos o erro.
     */
    static final double LIMITE_AUSENTES = 0.2;

    private final AdConfiguracaoService configuracao;
    private final AdProvisionamentoService provisionamento;
    private final VinculoAdMapper vinculoAdMapper;
    private final AuditoriaService auditoria;
    private final ReentrantLock emAndamento = new ReentrantLock();

    public AdSincronizacaoService(AdConfiguracaoService configuracao, AdProvisionamentoService provisionamento,
                                  VinculoAdMapper vinculoAdMapper, AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.configuracao = configuracao;
        this.provisionamento = provisionamento;
        this.vinculoAdMapper = vinculoAdMapper;
    }

    @Value
    public static class Resumo {
        int lidas;
        int sincronizadas;
        int ignoradas;
        int desativadas;
        boolean desativacaoSuspensa;
    }

    @DataPermission(enable = false)
    public Resumo sincronizarTudo() {
        if (!emAndamento.tryLock()) {
            throw new IllegalStateException("Já existe uma sincronização com o AD em andamento");
        }
        try {
            AdProperties props = configuracao.atual();
            AdDiretorio diretorio = configuracao.diretorio()
                    .orElseThrow(() -> new IllegalStateException("O login pelo AD está desligado"));
            LocalDateTime inicio = LocalDateTime.now();
            AtomicInteger lidas = new AtomicInteger();
            AtomicInteger ok = new AtomicInteger();
            AtomicInteger ignoradas = new AtomicInteger();
            // se a listagem falhar no meio, a exceção sobe antes da etapa de desativação
            diretorio.listarUsuarios(conta -> {
                lidas.incrementAndGet();
                try {
                    if (provisionamento.provisionar(conta, inicio).isPresent()) {
                        ok.incrementAndGet();
                    } else {
                        ignoradas.incrementAndGet();
                    }
                } catch (Exception e) {
                    ignoradas.incrementAndGet();
                    log.error("[sincronizarTudo][falha ao sincronizar {}]", conta.getLogin(), e);
                }
            });

            int desativadas = 0;
            boolean suspensa = false;
            if (props.isDesativarAusentes()) {
                List<VinculoAdDO> ausentes = vinculoAdMapper.selectListNaoVistosDesde(inicio);
                long total = vinculoAdMapper.selectCount();
                if (!ausentes.isEmpty() && (lidas.get() == 0 || ausentes.size() > total * LIMITE_AUSENTES)) {
                    suspensa = true;
                    log.error("[sincronizarTudo][{} de {} contas sumiram do AD numa rodada; desativação suspensa. "
                            + "Confira coti.ad.base-usuarios e coti.ad.filtro-usuarios]", ausentes.size(), total);
                } else {
                    for (VinculoAdDO v : ausentes) {
                        if (Boolean.TRUE.equals(v.getAtivoNoAd())) {
                            provisionamento.desativarAusente(v);
                            desativadas++;
                        }
                    }
                }
            }
            Resumo r = new Resumo(lidas.get(), ok.get(), ignoradas.get(), desativadas, suspensa);
            log.info("[sincronizarTudo][{}]", r);
            auditoria.registrar("ad.sincronizacao", null, null, Map.of("lidas", r.getLidas(),
                    "sincronizadas", r.getSincronizadas(), "ignoradas", r.getIgnoradas(),
                    "desativadas", r.getDesativadas(), "desativacaoSuspensa", r.isDesativacaoSuspensa()));
            return r;
        } finally {
            emAndamento.unlock();
        }
    }

}
