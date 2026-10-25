package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.dal.dataobject.VinculoAdDO;
import cn.iocoder.yudao.module.identidade.dal.mysql.VinculoAdMapper;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.dal.dataobject.*;
import cn.iocoder.yudao.module.inventario.dal.mysql.*;
import cn.iocoder.yudao.module.inventario.dominio.Normalizacao;
import cn.iocoder.yudao.module.inventario.dominio.Protocolo;
import cn.iocoder.yudao.module.inventario.dominio.Redes;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Recebe o que os agentes de inventário mandam (OCS Inventory e GLPI Agent) e mantém o CMDB.
 * <p>
 * Protocolo OCS (conferido no servidor OCS Inventory NG 2.12.6, GPL-2.0-or-later): o agente manda um PROLOG; se a
 * resposta for {@code SEND}, ele manda o INVENTORY completo. O GLPI Agent faz o mesmo em JSON ({@code contact} e
 * {@code inventory}). Uma máquina é a mesma se tiver o mesmo DEVICEID; se o agente foi reinstalado (DEVICEID novo),
 * reconhecemos pelo UUID da placa, pela série com o fabricante ou pelo MAC com o nome.
 */
@Service
@Slf4j
public class InventarioAgenteService {

    /** Mais que isto num inventário vira uma linha de resumo no histórico */
    static final int MAXIMO_HISTORICO_POR_TIPO = 100;

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private CmdbComponenteMapper cmdbComponenteMapper;
    @Resource
    private CmdbSoftwareMapper cmdbSoftwareMapper;
    @Resource
    private CmdbAtivoSoftwareMapper cmdbAtivoSoftwareMapper;
    @Resource
    private CmdbHistoricoMapper cmdbHistoricoMapper;
    @Resource
    private VinculoAdMapper vinculoAdMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private RedeService redeService;
    @Resource
    private PacoteService pacoteService;

    /** Resposta HTTP pronta para o controller. */
    public record Resposta(int status, String tipo, byte[] corpo, Map<String, String> cabecalhos) {

        static Resposta vazia(int status) {
            return new Resposta(status, "text/plain", new byte[0], Map.of());
        }
    }

    public Resposta receber(byte[] corpo, String ip, String agente, String autorizacao) {
        if (!Redes.permitido(ip, inventarioProperties.getAgenteRedes())) {
            log.warn("[inventario] agente recusado: {} fora das redes permitidas", ip);
            return Resposta.vazia(403);
        }
        if (!senhaConfere(autorizacao)) {
            return new Resposta(401, "text/plain", new byte[0], Map.of("WWW-Authenticate", "Basic realm=\"CO-TI inventario\""));
        }
        if (corpo == null || corpo.length == 0) {
            return Resposta.vazia(400);
        }
        Protocolo.Pedido p;
        try {
            p = Protocolo.ler(corpo);
        } catch (Protocolo.ProtocoloInvalido e) {
            log.info("[inventario] pedido inválido de {}: {}", ip, e.getMessage());
            return Resposta.vazia(400);
        }
        String deviceId = limpar(p.deviceId(), 255);
        if (deviceId == null) {
            return "glpi".equals(p.formato()) ? json(400, Map.of("status", "error", "message", "deviceid ausente")) : Resposta.vazia(400);
        }
        String versaoAgente = limpar(agente, 255);
        return "glpi".equals(p.formato()) ? glpi(p, deviceId, versaoAgente) : ocs(p, deviceId, versaoAgente);
    }

    // ======================================================================================= protocolos

    private Resposta ocs(Protocolo.Pedido p, String deviceId, String agente) {
        switch (p.consulta()) {
            case "PROLOG" -> {
                boolean enviar = precisaInventariar(deviceId, agente);
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("RESPONSE", enviar ? "SEND" : "STOP");
                r.put("PROLOG_FREQ", inventarioProperties.getPrologoHoras());
                // descoberta de rede: a varredura vem no próximo inventário, então só faz sentido se ele vier
                Protocolo.ComAtributos ipdiscover = enviar ? redeService.opcaoProlog(cmdbAtivoMapper.selectOne(AtivoDO::getDeviceId, deviceId)) : null;
                List<Object> opcoes = new ArrayList<>();
                if (ipdiscover != null) {
                    opcoes.add(Map.of("NAME", "IPDISCOVER", "PARAM", ipdiscover));
                }
                // pacotes pendentes vão em todo PROLOG, com ou sem inventário (como no OCS)
                Map<String, Object> download = pacoteService.opcaoProlog(cmdbAtivoMapper.selectOne(AtivoDO::getDeviceId, deviceId));
                if (download != null) {
                    opcoes.add(download);
                }
                if (!opcoes.isEmpty()) {
                    r.put("OPTION", opcoes);
                }
                return new Resposta(200, "application/x-compressed", Protocolo.replyOcs(r), Map.of());
            }
            case "INVENTORY" -> {
                Long ativoId = processar(p, deviceId, agente, "ocs");
                if (!p.lista("IPDISCOVER").isEmpty()) {
                    redeService.registrar(p, ativoId);
                }
                return new Resposta(200, "application/x-compressed", Protocolo.replyOcs(Map.of("RESPONSE", "NO_ACCOUNT_UPDATE")), Map.of());
            }
            case "DOWNLOAD" -> {
                // resultado da implantação: ID do pacote e ERR (SUCCESS, ERR_BAD_DIGEST, EXIT_CODE_n...)
                Map<String, String> req = p.unico("REQUEST");
                pacoteService.resultado(cmdbAtivoMapper.selectOne(AtivoDO::getDeviceId, deviceId), req.get("ID"), req.get("ERR"));
                return new Resposta(200, "application/x-compressed", new byte[0], Map.of());
            }
            default -> {
                // UPDATE e outros
                return new Resposta(200, "application/x-compressed", Protocolo.replyOcs(Map.of("RESPONSE", "OTHER_ERROR")), Map.of());
            }
        }
    }

    private Resposta glpi(Protocolo.Pedido p, String deviceId, String agente) {
        switch (p.consulta()) {
            case "CONTACT", "PROLOG" -> {
                boolean enviar = precisaInventariar(deviceId, agente);
                Map<String, Object> tarefas = new LinkedHashMap<>();
                if (enviar) {
                    tarefas.put("inventory", Map.of("server", "glpi", "version", "10.0.0"));
                }
                return json(200, Map.of("status", "ok", "expiration", inventarioProperties.getPrologoHoras(), "tasks", tarefas));
            }
            case "INVENTORY" -> {
                processar(p, deviceId, agente, "glpi_agent");
                return json(200, Map.of("status", "ok", "expiration", inventarioProperties.getPrologoHoras()));
            }
            default -> {
                return json(400, Map.of("status", "error", "message", "ação não suportada"));
            }
        }
    }

    private static Resposta json(int status, Object corpo) {
        return new Resposta(status, "application/json", JsonUtils.toJsonString(corpo).getBytes(StandardCharsets.UTF_8), Map.of());
    }

    /** Registra o contato e diz se é hora de mandar o inventário. */
    boolean precisaInventariar(String deviceId, String agente) {
        AtivoDO a = cmdbAtivoMapper.selectOne(AtivoDO::getDeviceId, deviceId);
        if (a == null) {
            return true;
        }
        cmdbAtivoMapper.updateById(AtivoDO.builder().id(a.getId()).ultimoContato(LocalDateTime.now()).agente(agente).build());
        int dias = inventarioProperties.getInventarioDias();
        return dias <= 0 || a.getUltimoInventario() == null || a.getUltimoInventario().isBefore(LocalDateTime.now().minusDays(dias));
    }

    private boolean senhaConfere(String autorizacao) {
        String usuario = inventarioProperties.getAgenteUsuario();
        if (usuario == null || usuario.isEmpty()) {
            return true;
        }
        if (autorizacao == null || !autorizacao.regionMatches(true, 0, "Basic ", 0, 6)) {
            return false;
        }
        byte[] esperado = (usuario + ":" + Objects.toString(inventarioProperties.getAgenteSenha(), "")).getBytes(StandardCharsets.UTF_8);
        byte[] recebido;
        try {
            recebido = Base64.getDecoder().decode(autorizacao.substring(6).trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(esperado, recebido);
    }

    // ======================================================================================= gravação

    /** Grava o inventário. Uma máquina por vez (o mesmo agente pode reenviar enquanto o primeiro ainda grava). */
    public Long processar(Protocolo.Pedido p, String deviceId, String agente, String origem) {
        return gravarResultado(Normalizacao.normalizar(p, LocalDate.now()), deviceId, agente, origem);
    }

    /**
     * Grava um resultado já normalizado (agente ou SNMP), com a mesma regra de máquina repetida e de histórico.
     * Origem "snmp": nome e tipo de um ativo que já existe ficam como a equipe deixou (o sysName costuma ser feio).
     */
    public Long gravarResultado(Normalizacao.Resultado r, String deviceId, String agente, String origem) {
        synchronized (("cmdb-inv:" + deviceId).intern()) {
            try {
                return transactionTemplate.execute(s -> gravar(r, deviceId, agente, origem));
            } catch (DuplicateKeyException e) {
                // outra instância inseriu o mesmo software/ativo ao mesmo tempo: a segunda tentativa encontra o registro
                return transactionTemplate.execute(s -> gravar(r, deviceId, agente, origem));
            }
        }
    }

    private Long gravar(Normalizacao.Resultado r, String deviceId, String agente, String origem) {
        LocalDateTime agora = LocalDateTime.now();
        Normalizacao.Resumo n = r.resumo();
        AtivoDO atual = localizar(deviceId, n);
        AtivoDO novo = montar(n);
        novo.setDeviceId(deviceId);
        novo.setAgente(agente);
        novo.setOrigem(origem);
        novo.setUltimoInventario(agora);
        novo.setUltimoContato(agora);
        vincularUsuario(novo, atual);

        if (atual == null) {
            novo.setPrimeiroInventario(agora);
            novo.setSituacao("em_uso");
            novo.setDeptManual(false);
            if (novo.getNome() == null) {
                novo.setNome(deviceId);
            }
            cmdbAtivoMapper.insert(novo);
            gravarComponentes(novo.getId(), r.componentes(), agora, false);
            gravarSoftwares(novo.getId(), r.softwares(), agora, false);
            registrar(novo.getId(), agora, "agente", "inventario", null,
                    "Primeiro inventário: " + r.componentes().size() + " componentes, " + r.softwares().size() + " softwares");
            return novo.getId();
        }

        novo.setId(atual.getId());
        if ("snmp".equals(origem)) {
            novo.setNome(null);
            novo.setTipo(null);
        }
        if (!deviceId.equals(atual.getDeviceId())) {
            registrar(atual.getId(), agora, "agente", "device_id", atual.getDeviceId(), deviceId);
        }
        // patrimônio: o agente só preenche quando ainda está vazio (a etiqueta cadastrada à mão vale mais)
        if (atual.getPatrimonio() != null && !atual.getPatrimonio().isBlank()) {
            novo.setPatrimonio(null);
        }
        for (Campo c : CAMPOS) {
            Object antes = c.ler().apply(atual), depois = c.ler().apply(novo);
            if (depois != null && !Objects.equals(texto(antes), texto(depois))) {
                registrar(atual.getId(), agora, "campo", c.nome(), texto(antes), texto(depois));
            }
        }
        cmdbAtivoMapper.updateById(novo);
        gravarComponentes(atual.getId(), r.componentes(), agora, true);
        gravarSoftwares(atual.getId(), r.softwares(), agora, true);
        return atual.getId();
    }

    /** Campos que entram no histórico quando mudam (IP e espaço livre mudam toda hora e ficam de fora). */
    private record Campo(String nome, Function<AtivoDO, Object> ler) {
    }

    private static final List<Campo> CAMPOS = List.of(
            new Campo("nome", AtivoDO::getNome), new Campo("tipo", AtivoDO::getTipo),
            new Campo("fabricante", AtivoDO::getFabricante), new Campo("modelo", AtivoDO::getModelo),
            new Campo("numero_serie", AtivoDO::getNumeroSerie), new Campo("patrimonio", AtivoDO::getPatrimonio),
            new Campo("so_nome", AtivoDO::getSoNome), new Campo("so_versao", AtivoDO::getSoVersao),
            new Campo("so_arquitetura", AtivoDO::getSoArquitetura), new Campo("cpu", AtivoDO::getCpu),
            new Campo("cpu_nucleos", AtivoDO::getCpuNucleos), new Campo("memoria_mb", AtivoDO::getMemoriaMb),
            new Campo("disco_total_mb", AtivoDO::getDiscoTotalMb), new Campo("mac", AtivoDO::getMac),
            new Campo("dominio", AtivoDO::getDominio), new Campo("usuario_login", AtivoDO::getUsuarioLogin),
            new Campo("antivirus", AtivoDO::getAntivirus), new Campo("disco_tipo", AtivoDO::getDiscoTipo), new Campo("so_sem_suporte", AtivoDO::getSoSemSuporte));

    /** Mesma máquina: DEVICEID, senão UUID, senão série + fabricante, senão MAC + nome. */
    AtivoDO localizar(String deviceId, Normalizacao.Resumo n) {
        AtivoDO a = cmdbAtivoMapper.selectPorDeviceParaAtualizar(deviceId);
        if (a != null) {
            return a;
        }
        if (n.uuid() != null && !Normalizacao.generico(n.uuid())) {
            a = primeiro(new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getUuid, n.uuid()));
            if (a != null) {
                return a;
            }
        }
        if (n.numeroSerie() != null && !Normalizacao.generico(n.numeroSerie())) {
            a = primeiro(new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getNumeroSerie, n.numeroSerie())
                    .eqIfPresent(AtivoDO::getFabricante, n.fabricante()));
            if (a != null) {
                return a;
            }
        }
        if (n.mac() != null && n.nome() != null) {
            a = cmdbAtivoMapper.selectPorMac(n.mac());
            if (a != null && n.nome().equalsIgnoreCase(a.getNome())) {
                return a;
            }
        }
        return null;
    }

    private AtivoDO primeiro(LambdaQueryWrapperX<AtivoDO> q) {
        List<AtivoDO> l = cmdbAtivoMapper.selectList(q.orderByAsc(AtivoDO::getId).last("LIMIT 1"));
        return l.isEmpty() ? null : l.get(0);
    }

    private static AtivoDO montar(Normalizacao.Resumo n) {
        return AtivoDO.builder()
                .tipo(n.tipo()).nome(limpar(n.nome(), 255)).fabricante(limpar(n.fabricante(), 255)).modelo(limpar(n.modelo(), 255))
                .numeroSerie(limpar(n.numeroSerie(), 100)).patrimonio(limpar(n.patrimonio(), 100)).uuid(limpar(n.uuid(), 64))
                .soNome(limpar(n.soNome(), 255)).soVersao(limpar(n.soVersao(), 100)).soArquitetura(limpar(n.soArquitetura(), 30))
                .soSemSuporte(n.soSemSuporte()).cpu(limpar(n.cpu(), 255)).cpuNucleos(n.cpuNucleos()).memoriaMb(n.memoriaMb())
                .discoTotalMb(n.discoTotalMb()).discoLivreMb(n.discoLivreMb()).discoTipo(n.discoTipo()).ip(limpar(n.ip(), 64)).mac(limpar(n.mac(), 32))
                .dominio(limpar(n.dominio(), 255)).usuarioLogin(limpar(n.usuarioLogin(), 255)).antivirus(n.antivirus())
                .build();
    }

    /**
     * Pessoa do portal pelo login informado pelo agente (vínculo com o AD, senão o usuário local de mesmo nome). A
     * unidade do ativo acompanha a da pessoa, a não ser que alguém tenha escolhido a unidade à mão.
     */
    private void vincularUsuario(AtivoDO novo, AtivoDO atual) {
        String login = novo.getUsuarioLogin();
        if (login == null) {
            return;
        }
        Long userId = null;
        VinculoAdDO v = vinculoAdMapper.selectOne(new LambdaQueryWrapperX<VinculoAdDO>()
                .apply("lower(login) = {0}", login.toLowerCase(Locale.ROOT)).last("LIMIT 1"));
        if (v != null) {
            userId = v.getUserId();
        } else {
            AdminUserDO u = adminUserMapper.selectByUsername(login);
            if (u != null) {
                userId = u.getId();
            }
        }
        if (userId == null) {
            return;
        }
        novo.setUsuarioId(userId);
        boolean manual = atual != null && Boolean.TRUE.equals(atual.getDeptManual());
        if (!manual) {
            AdminUserDO u = adminUserMapper.selectById(userId);
            if (u != null && u.getDeptId() != null) {
                novo.setDeptId(u.getDeptId());
            }
        }
    }

    private void gravarComponentes(Long ativoId, List<Normalizacao.Componente> recebidos, LocalDateTime agora, boolean historico) {
        Map<String, Normalizacao.Componente> novos = new LinkedHashMap<>();
        for (Normalizacao.Componente c : recebidos) {
            novos.putIfAbsent(c.tipo() + "\u0000" + limpar(c.chave(), 500), c);
        }
        Map<String, ComponenteDO> existentes = new LinkedHashMap<>();
        for (ComponenteDO c : cmdbComponenteMapper.selectList(ComponenteDO::getAtivoId, ativoId)) {
            if (existentes.putIfAbsent(c.getTipo() + "\u0000" + c.getChave(), c) != null) {
                cmdbComponenteMapper.deleteById(c.getId());   // duplicado de alguma versão antiga
            }
        }
        List<String[]> incluidos = new ArrayList<>(), removidos = new ArrayList<>();
        for (Map.Entry<String, Normalizacao.Componente> e : novos.entrySet()) {
            Normalizacao.Componente c = e.getValue();
            String descricao = Objects.requireNonNullElse(limpar(c.descricao(), 500), c.tipo());
            String dados = JsonUtils.toJsonString(c.dados() == null ? Map.of() : c.dados());
            ComponenteDO atual = existentes.remove(e.getKey());
            if (atual == null) {
                cmdbComponenteMapper.insert(ComponenteDO.builder().ativoId(ativoId).tipo(c.tipo()).chave(limpar(c.chave(), 500))
                        .descricao(descricao).dados(dados).build());
                incluidos.add(new String[]{c.tipo(), descricao});
            } else if (!descricao.equals(atual.getDescricao()) || !dados.equals(atual.getDados())) {
                cmdbComponenteMapper.updateById(ComponenteDO.builder().id(atual.getId()).descricao(descricao).dados(dados).build());
            }
        }
        for (ComponenteDO c : existentes.values()) {
            cmdbComponenteMapper.deleteById(c.getId());
            removidos.add(new String[]{c.getTipo(), c.getDescricao()});
        }
        if (historico) {
            registrarLista(ativoId, agora, "componente_incluido", incluidos);
            registrarLista(ativoId, agora, "componente_removido", removidos);
        }
    }

    private void gravarSoftwares(Long ativoId, List<Normalizacao.Software> recebidos, LocalDateTime agora, boolean historico) {
        Map<String, Normalizacao.Software> novos = new LinkedHashMap<>();
        for (Normalizacao.Software s : recebidos) {
            if (s.nome() == null || s.nome().isBlank()) {
                continue;
            }
            novos.putIfAbsent(Normalizacao.chaveSoftware(s.nome(), s.editora()) + "\u0000" + Objects.toString(limpar(s.versao(), 100), ""), s);
        }
        Map<Long, SoftwareDO> catalogo = new HashMap<>();
        Map<String, AtivoSoftwareDO> existentes = new LinkedHashMap<>();
        List<AtivoSoftwareDO> instalados = cmdbAtivoSoftwareMapper.selectList(AtivoSoftwareDO::getAtivoId, ativoId);
        if (!instalados.isEmpty()) {
            cmdbSoftwareMapper.selectByIds(instalados.stream().map(AtivoSoftwareDO::getSoftwareId).distinct().toList())
                    .forEach(s -> catalogo.put(s.getId(), s));
        }
        for (AtivoSoftwareDO x : instalados) {
            SoftwareDO s = catalogo.get(x.getSoftwareId());
            String chave = (s == null ? "#" + x.getSoftwareId() : s.getChave()) + "\u0000" + x.getVersao();
            if (existentes.putIfAbsent(chave, x) != null) {
                cmdbAtivoSoftwareMapper.deleteById(x.getId());
            }
        }
        List<String[]> instaladosAgora = new ArrayList<>(), removidos = new ArrayList<>();
        for (Map.Entry<String, Normalizacao.Software> e : novos.entrySet()) {
            Normalizacao.Software s = e.getValue();
            AtivoSoftwareDO atual = existentes.remove(e.getKey());
            String versao = Objects.toString(limpar(s.versao(), 100), "");
            if (atual == null) {
                SoftwareDO item = software(s);
                cmdbAtivoSoftwareMapper.insert(AtivoSoftwareDO.builder().ativoId(ativoId).softwareId(item.getId()).versao(versao)
                        .instaladoEm(s.instaladoEm()).arquitetura(limpar(s.arquitetura(), 20)).pasta(limpar(s.pasta(), 500)).build());
                instaladosAgora.add(new String[]{null, rotulo(item.getNome(), versao)});
            } else if (!Objects.equals(atual.getInstaladoEm(), s.instaladoEm()) || !Objects.equals(atual.getPasta(), limpar(s.pasta(), 500))) {
                cmdbAtivoSoftwareMapper.updateById(AtivoSoftwareDO.builder().id(atual.getId()).instaladoEm(s.instaladoEm())
                        .arquitetura(limpar(s.arquitetura(), 20)).pasta(limpar(s.pasta(), 500)).build());
            }
        }
        for (AtivoSoftwareDO x : existentes.values()) {
            cmdbAtivoSoftwareMapper.deleteById(x.getId());
            SoftwareDO s = catalogo.get(x.getSoftwareId());
            removidos.add(new String[]{null, rotulo(s == null ? "#" + x.getSoftwareId() : s.getNome(), x.getVersao())});
        }
        if (historico) {
            registrarLista(ativoId, agora, "software_instalado", instaladosAgora);
            registrarLista(ativoId, agora, "software_removido", removidos);
        }
    }

    /** Item do catálogo (nome + editora normalizados), criado na primeira vez que aparece. */
    private SoftwareDO software(Normalizacao.Software s) {
        String chave = Normalizacao.chaveSoftware(s.nome(), s.editora());
        SoftwareDO item = cmdbSoftwareMapper.selectOne(SoftwareDO::getChave, chave);
        if (item == null) {
            item = SoftwareDO.builder().nome(limpar(s.nome(), 255)).editora(limpar(s.editora(), 255)).chave(chave).build();
            cmdbSoftwareMapper.insert(item);
        }
        return item;
    }

    private static String rotulo(String nome, String versao) {
        return versao == null || versao.isEmpty() ? nome : nome + " " + versao;
    }

    private void registrarLista(Long ativoId, LocalDateTime quando, String tipo, List<String[]> itens) {
        int n = 0;
        for (String[] i : itens) {
            if (++n > MAXIMO_HISTORICO_POR_TIPO) {
                registrar(ativoId, quando, tipo, null, null, "… e mais " + (itens.size() - MAXIMO_HISTORICO_POR_TIPO));
                return;
            }
            registrar(ativoId, quando, tipo, i[0], null, i[1]);
        }
    }

    void registrar(Long ativoId, LocalDateTime quando, String tipo, String campo, String antes, String depois) {
        cmdbHistoricoMapper.insert(HistoricoAtivoDO.builder().ativoId(ativoId).quando(quando).tipo(tipo).campo(campo)
                .antes(antes).depois(depois).build());
    }

    private static String texto(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** Sem espaços nas pontas, sem caracteres de controle e cortado no tamanho da coluna. Vazio vira nulo. */
    static String limpar(String s, int maximo) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("[\\p{Cntrl}&&[^\t]]", "").strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > maximo ? t.substring(0, maximo) : t;
    }

}
