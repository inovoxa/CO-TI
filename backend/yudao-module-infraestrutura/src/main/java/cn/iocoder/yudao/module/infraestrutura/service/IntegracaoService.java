package cn.iocoder.yudao.module.infraestrutura.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.ad.AdDiretorio;
import cn.iocoder.yudao.module.identidade.ad.AdIndisponivelException;
import cn.iocoder.yudao.module.identidade.seguranca.CifradorSegredos;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.infraestrutura.controller.admin.vo.IntegracaoVOs;
import cn.iocoder.yudao.module.infraestrutura.dal.*;
import cn.iocoder.yudao.module.infraestrutura.dominio.EntradaRegras;
import cn.iocoder.yudao.module.itsm.dal.dataobject.FormularioDO;
import cn.iocoder.yudao.module.itsm.service.FormularioService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.retencao.AvisoInfraService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Recepção dos avisos, fila e cadastro das entradas. A recepção só confere e grava: quem chama recebe a resposta na hora. */
@Slf4j
@Service
public class IntegracaoService {

    private static final ErrorCode INVALIDA = new ErrorCode(1_098_000_001, "{}");
    private static final Set<String> MODOS = Set.of("simulacao", "confirmacao", "automatico");
    private static final int[] ESPERA_MIN = {1, 5, 15, 60};
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    public enum Recepcao { RECEBIDO, DUPLICADO, RECUSADO, INVALIDO }

    public record Resultado(Recepcao recepcao, Long id) {
    }

    @Resource
    private IntegracaoEntradaMapper integracaoEntradaMapper;
    @Resource
    private IntegracaoEventoMapper integracaoEventoMapper;
    @Resource
    private IntegracaoProcessador processador;
    @Resource
    private CifradorSegredos cifrador;
    @Resource
    private AuditoriaService auditoria;
    @Resource
    private AvisoInfraService avisoInfra;
    @Resource
    private FormularioService formularioService;
    @Resource
    private AdminUserMapper adminUserMapper;

    // ---------------------------------------------------------------- recepção

    /** A mesma recusa para entrada inexistente, desativada, IP ou segredo errado: quem está de fora não aprende nada. */
    public Resultado receber(String codigo, byte[] corpo, String ip, String autorizacao, String carimbo, String assinatura) {
        IntegracaoEntradaDO e = integracaoEntradaMapper.selectByCodigo(codigo);
        if (e == null || !Boolean.TRUE.equals(e.getAtiva()) || !EntradaRegras.ipPermitido(ip, e.getIpsPermitidos()) || !autenticado(e, corpo, autorizacao, carimbo, assinatura)) {
            log.warn("[receber][aviso recusado na entrada {} vindo de {}]", codigo, ip);
            return new Resultado(Recepcao.RECUSADO, null);
        }
        String chave;
        try {
            var raiz = JSON.readTree(corpo);
            if (raiz == null || !raiz.isObject()) {
                return new Resultado(Recepcao.INVALIDO, null);
            }
            chave = EntradaRegras.ler(raiz, e.getCaminhoChave()).orElseGet(() -> EntradaRegras.hash(corpo));
        } catch (IOException ex) {
            return new Resultado(Recepcao.INVALIDO, null);
        }
        if (chave.length() > 128) {
            chave = EntradaRegras.hash(chave.getBytes(StandardCharsets.UTF_8));
        }
        IntegracaoEventoDO ev = new IntegracaoEventoDO();
        ev.setEntradaId(e.getId());
        ev.setChave(chave);
        ev.setIp(ip);
        ev.setPayloadCifrado(cifrador.cifrar(new String(corpo, StandardCharsets.UTF_8), IntegracaoProcessador.contexto(e.getId())));
        ev.setStatus("recebido");
        ev.setTentativas(0);
        ev.setRecebidoEm(LocalDateTime.now());
        try {
            integracaoEventoMapper.insert(ev);
        } catch (DuplicateKeyException ex) {
            return new Resultado(Recepcao.DUPLICADO, null);
        }
        return new Resultado(Recepcao.RECEBIDO, ev.getId());
    }

    private boolean autenticado(IntegracaoEntradaDO e, byte[] corpo, String autorizacao, String carimbo, String assinatura) {
        String segredo;
        try {
            segredo = cifrador.decifrar(e.getSegredoCifrado(), contextoSegredo(e.getCodigo()));
        } catch (RuntimeException ex) {
            log.error("[autenticado][segredo da entrada {} ilegível: a chave-mestra mudou?]", e.getCodigo());
            return false;
        }
        if ("bearer".equals(e.getAutenticacao())) {
            return e.getIpsPermitidos() != null && !e.getIpsPermitidos().isBlank()
                    && EntradaRegras.iguais("Bearer " + segredo, autorizacao);
        }
        return EntradaRegras.assinaturaValida(segredo.getBytes(StandardCharsets.UTF_8), carimbo, corpo, assinatura, Instant.now());
    }

    // ---------------------------------------------------------------- fila

    @DataPermission(enable = false)
    public int processarFila() {
        integracaoEventoMapper.devolverPresos();
        int n = 0;
        for (Long id : integracaoEventoMapper.selectIdsNaFila(20)) {
            if (integracaoEventoMapper.reivindicar(id) == 1) {
                processar(integracaoEventoMapper.selectById(id));
                n++;
            }
        }
        return n;
    }

    private void processar(IntegracaoEventoDO ev) {
        IntegracaoEntradaDO e = integracaoEntradaMapper.selectById(ev.getEntradaId());
        try {
            if (e == null || !Boolean.TRUE.equals(e.getAtiva())) {
                terminar(ev, "ignorado", "A entrada foi desativada");
                return;
            }
            processador.aplicar(e, ev, processador.avaliar(e, processador.lerCorpo(ev), ev.getId()));
        } catch (AdIndisponivelException ex) {
            // sem o AD não se decide nada: nem bloqueia nem abre chamado dizendo que a pessoa não existe
            int t = ev.getTentativas() == null ? 1 : ev.getTentativas();
            if (t > ESPERA_MIN.length) {
                falhar(e, ev, "O AD não respondeu depois de " + t + " tentativas");
                return;
            }
            IntegracaoEventoDO novo = new IntegracaoEventoDO();
            novo.setId(ev.getId());
            novo.setStatus("recebido");
            novo.setProximaTentativa(LocalDateTime.now().plusMinutes(ESPERA_MIN[t - 1]));
            novo.setResultado("O AD não respondeu; nova tentativa em " + ESPERA_MIN[t - 1] + " min");
            integracaoEventoMapper.updateById(novo);
        } catch (Exception ex) {
            // só o tipo: a mensagem de erro do banco repete a linha inteira, com dados pessoais
            log.error("[processar][evento {} da entrada {}: {}]", ev.getId(), ev.getEntradaId(), ex.getClass().getSimpleName());
            falhar(e, ev, "Falha ao processar: " + ex.getClass().getSimpleName());
        }
    }

    private void falhar(IntegracaoEntradaDO e, IntegracaoEventoDO ev, String motivo) {
        terminar(ev, "erro", motivo);
        avisoInfra.avisar("integracao-erro-" + ev.getEntradaId(), "A integração \"" + (e == null ? ev.getEntradaId() : e.getNome())
                + "\" não conseguiu tratar um aviso recebido. Veja em Infraestrutura > Integrações.");
    }

    private void terminar(IntegracaoEventoDO ev, String status, String resultado) {
        IntegracaoEventoDO novo = new IntegracaoEventoDO();
        novo.setId(ev.getId());
        novo.setStatus(status);
        novo.setResultado(resultado);
        novo.setProcessadoEm(LocalDateTime.now());
        integracaoEventoMapper.updateById(novo);
    }

    // ---------------------------------------------------------------- cadastro

    public List<IntegracaoVOs.Entrada> listar() {
        return integracaoEntradaMapper.selectList(new LambdaQueryWrapperX<IntegracaoEntradaDO>().orderByAsc(IntegracaoEntradaDO::getNome))
                .stream().map(IntegracaoService::paraTela).toList();
    }

    /** @return o segredo, só quando a entrada é nova */
    public IntegracaoVOs.Salva salvar(IntegracaoVOs.Entrada r, Long userId) {
        validar(r);
        IntegracaoEntradaDO e = r.getId() == null ? new IntegracaoEntradaDO() : obter(r.getId());
        boolean nova = r.getId() == null;
        String modoAnterior = e.getModo();
        String segredo = null;
        if (nova) {
            segredo = novoSegredo();
            e.setCodigo(r.getCodigo());
            e.setSegredoCifrado(cifrador.cifrar(segredo, contextoSegredo(r.getCodigo())));
            e.setModo("simulacao"); // toda entrada nasce em simulação: ligar de verdade é um passo à parte
        } else {
            e.setModo(r.getModo());
        }
        e.setNome(r.getNome().strip());
        e.setAtiva(r.getAtiva() == null || r.getAtiva());
        e.setAutenticacao(r.getAutenticacao());
        e.setIpsPermitidos(vazioParaNulo(r.getIpsPermitidos()));
        e.setFormularioId(r.getFormularioId());
        e.setSolicitanteId(r.getSolicitanteId());
        e.setAtributoAd(r.getAtributoAd());
        e.setCaminhoId(r.getCaminhoId().strip());
        e.setCaminhoNome(vazioParaNulo(r.getCaminhoNome()));
        e.setConferirNome(Boolean.TRUE.equals(r.getConferirNome()));
        e.setCaminhoSituacao(r.getCaminhoSituacao().strip());
        e.setValoresDesligar(r.getValoresDesligar().strip());
        e.setValoresCancelar(vazioParaNulo(r.getValoresCancelar()));
        e.setCaminhoData(vazioParaNulo(r.getCaminhoData()));
        e.setCaminhoChave(vazioParaNulo(r.getCaminhoChave()));
        e.setMapa(JsonUtils.toJsonString(r.getMapa() == null ? Map.of() : r.getMapa()));
        e.setGruposProtegidos(vazioParaNulo(r.getGruposProtegidos()));
        e.setLoginsProtegidos(vazioParaNulo(r.getLoginsProtegidos()));
        e.setLimiteDia(r.getLimiteDia());
        e.setAtualizadoEm(LocalDateTime.now());
        if (nova) {
            e.setCriadoEm(LocalDateTime.now());
            integracaoEntradaMapper.insert(e);
        } else {
            integracaoEntradaMapper.updateById(e);
        }
        auditoria.registrar(nova ? "integracao.entrada_criada" : "integracao.entrada_alterada", userId, null,
                Map.of("entrada", e.getCodigo(), "modo", e.getModo(), "modoAnterior", modoAnterior == null ? "" : modoAnterior));
        return new IntegracaoVOs.Salva(e.getId(), segredo);
    }

    public String novoSegredo(Long id, Long userId) {
        IntegracaoEntradaDO e = obter(id);
        String segredo = novoSegredo();
        e.setSegredoCifrado(cifrador.cifrar(segredo, contextoSegredo(e.getCodigo())));
        e.setAtualizadoEm(LocalDateTime.now());
        integracaoEntradaMapper.updateById(e);
        auditoria.registrar("integracao.segredo_trocado", userId, null, Map.of("entrada", e.getCodigo()));
        return segredo;
    }

    /** Mostra o que seria feito, sem efeito nenhum. */
    public IntegracaoVOs.Teste testar(Long id, String json) {
        IntegracaoEntradaDO e = obter(id);
        try {
            IntegracaoProcessador.Plano p = processador.avaliar(e, json.getBytes(StandardCharsets.UTF_8), null);
            Map<String, String> campos = new LinkedHashMap<>(p.respostas());
            return new IntegracaoVOs.Teste(p.tipo().name(), p.mensagem(), p.login(), campos, p.executarEm());
        } catch (IOException ex) {
            throw exception(INVALIDA, "O texto não é um JSON válido");
        } catch (AdIndisponivelException ex) {
            throw exception(INVALIDA, "O AD não respondeu");
        }
    }

    public PageResult<IntegracaoVOs.Evento> eventos(IntegracaoVOs.EventosReq req) {
        PageResult<IntegracaoEventoDO> r = integracaoEventoMapper.selectPage(req, new LambdaQueryWrapperX<IntegracaoEventoDO>()
                .eqIfPresent(IntegracaoEventoDO::getEntradaId, req.getEntradaId()).eqIfPresent(IntegracaoEventoDO::getStatus, req.getStatus())
                .orderByDesc(IntegracaoEventoDO::getId));
        return new PageResult<>(r.getList().stream().map(v -> new IntegracaoVOs.Evento(v.getId(), v.getEntradaId(), v.getStatus(), v.getResultado(),
                v.getLogin(), v.getChamadoId(), v.getIp(), v.getTentativas(), v.getRecebidoEm(), v.getProcessadoEm())).toList(), r.getTotal());
    }

    private void validar(IntegracaoVOs.Entrada r) {
        if (!cifrador.configurado()) {
            throw exception(INVALIDA, "Defina CO_TI_CHAVE_MESTRA no servidor: sem ela os segredos e os avisos não podem ser guardados");
        }
        if (r.getId() == null) {
            if (r.getCodigo() == null || !r.getCodigo().matches("^[a-z0-9-]{3,40}$")) {
                throw exception(INVALIDA, "Código: use de 3 a 40 letras minúsculas, números ou hífen");
            }
            if (integracaoEntradaMapper.selectByCodigo(r.getCodigo()) != null) {
                throw exception(INVALIDA, "Já existe uma entrada com este código");
            }
        } else if (!MODOS.contains(r.getModo())) {
            throw exception(INVALIDA, "Modo inválido");
        }
        if (!Set.of("hmac", "bearer").contains(r.getAutenticacao())) {
            throw exception(INVALIDA, "Autenticação inválida");
        }
        if ("bearer".equals(r.getAutenticacao()) && (r.getIpsPermitidos() == null || r.getIpsPermitidos().isBlank())) {
            throw exception(INVALIDA, "Com token fixo, informe os IPs permitidos: ele não protege o conteúdo como a assinatura");
        }
        if (!AdDiretorio.ATRIBUTOS_DE_BUSCA.contains(r.getAtributoAd())) {
            throw exception(INVALIDA, "Atributo do AD não permitido");
        }
        FormularioDO f = r.getFormularioId() == null ? null : formularioService.obter(r.getFormularioId());
        if (f == null || !"Desativar".equals(f.getAcaoAd())) {
            throw exception(INVALIDA, "Escolha o formulário de desativação de usuário");
        }
        AdminUserDO s = r.getSolicitanteId() == null ? null : adminUserMapper.selectById(r.getSolicitanteId());
        if (s == null || !CommonStatusEnum.isEnable(s.getStatus())) {
            throw exception(INVALIDA, "Escolha uma pessoa ativa como solicitante");
        }
        if (r.getLimiteDia() == null || r.getLimiteDia() < 1) {
            throw exception(INVALIDA, "O limite por dia precisa ser de pelo menos 1");
        }
    }

    private IntegracaoEntradaDO obter(Long id) {
        IntegracaoEntradaDO e = id == null ? null : integracaoEntradaMapper.selectById(id);
        if (e == null) {
            throw exception(INVALIDA, "Entrada não encontrada");
        }
        return e;
    }

    private static IntegracaoVOs.Entrada paraTela(IntegracaoEntradaDO e) {
        IntegracaoVOs.Entrada v = new IntegracaoVOs.Entrada();
        v.setId(e.getId());
        v.setCodigo(e.getCodigo());
        v.setNome(e.getNome());
        v.setAtiva(e.getAtiva());
        v.setModo(e.getModo());
        v.setAutenticacao(e.getAutenticacao());
        v.setIpsPermitidos(e.getIpsPermitidos());
        v.setFormularioId(e.getFormularioId());
        v.setSolicitanteId(e.getSolicitanteId());
        v.setAtributoAd(e.getAtributoAd());
        v.setCaminhoId(e.getCaminhoId());
        v.setCaminhoNome(e.getCaminhoNome());
        v.setConferirNome(e.getConferirNome());
        v.setCaminhoSituacao(e.getCaminhoSituacao());
        v.setValoresDesligar(e.getValoresDesligar());
        v.setValoresCancelar(e.getValoresCancelar());
        v.setCaminhoData(e.getCaminhoData());
        v.setCaminhoChave(e.getCaminhoChave());
        v.setMapa(JsonUtils.parseObject(e.getMapa(), new TypeReference<Map<String, String>>() {
        }));
        v.setGruposProtegidos(e.getGruposProtegidos());
        v.setLoginsProtegidos(e.getLoginsProtegidos());
        v.setLimiteDia(e.getLimiteDia());
        return v;
    }

    private static String novoSegredo() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String contextoSegredo(String codigo) {
        return "integracao-entrada:" + codigo;
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

}
