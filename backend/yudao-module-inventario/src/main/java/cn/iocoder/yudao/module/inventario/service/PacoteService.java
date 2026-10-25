package cn.iocoder.yudao.module.inventario.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.inventario.config.InventarioProperties;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.PacoteVOs;
import cn.iocoder.yudao.module.inventario.dal.dataobject.AtivoDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.PacoteDO;
import cn.iocoder.yudao.module.inventario.dal.dataobject.PacoteImplantacaoDO;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbAtivoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbPacoteImplantacaoMapper;
import cn.iocoder.yudao.module.inventario.dal.mysql.CmdbPacoteMapper;
import cn.iocoder.yudao.module.inventario.dominio.Protocolo;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.inventario.enums.ErrorCodeConstants.*;

/**
 * Implantação de pacotes pelo agente OCS (teledeploy), no mesmo protocolo do servidor OCS:
 * <ol>
 *   <li>a resposta ao PROLOG leva a opção DOWNLOAD com a configuração e os pacotes pendentes da máquina;</li>
 *   <li>o agente baixa {@code https://<INFO_LOC>/<id>/info} (XML com ação, digest e número de fragmentos) e os
 *   fragmentos {@code <proto>://<PACK_LOC>/<id>/<id>-<n>};</li>
 *   <li>confere o digest, executa e manda {@code QUERY=DOWNLOAD} com o resultado (SUCCESS, ERR_...).</li>
 * </ol>
 * Executar comando nas máquinas é poderoso: criar e implantar exige {@code inventario:pacote:update} e tudo vai para a
 * auditoria.
 */
@Service
@Slf4j
public class PacoteService {

    static final Set<String> ACOES = Set.of("EXECUTE", "STORE", "LAUNCH");
    static final Set<String> PLATAFORMAS = Set.of("windows", "linux", "macos");

    @Resource
    private InventarioProperties inventarioProperties;
    @Resource
    private CmdbPacoteMapper cmdbPacoteMapper;
    @Resource
    private CmdbPacoteImplantacaoMapper cmdbPacoteImplantacaoMapper;
    @Resource
    private CmdbAtivoMapper cmdbAtivoMapper;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private DeptApi deptApi;

    @Value("${coti.atendimento.url-publica:}")
    private String urlPublica;

    // ======================================================================================= pacotes

    public List<PacoteVOs.Pacote> listar() {
        List<PacoteDO> l = cmdbPacoteMapper.selectList(new LambdaQueryWrapperX<PacoteDO>().orderByDesc(PacoteDO::getId));
        Map<Long, Map<String, Long>> contagens = new HashMap<>();
        for (PacoteImplantacaoDO i : cmdbPacoteImplantacaoMapper.selectList()) {
            contagens.computeIfAbsent(i.getPacoteId(), k -> new LinkedHashMap<>()).merge(i.getSituacao(), 1L, Long::sum);
        }
        return l.stream().map(p -> converter(p, contagens.getOrDefault(p.getId(), Map.of()))).toList();
    }

    public PacoteVOs.Pacote obter(Long id) {
        PacoteDO p = existente(id);
        Map<String, Long> c = new LinkedHashMap<>();
        cmdbPacoteImplantacaoMapper.selectList(PacoteImplantacaoDO::getPacoteId, id).forEach(i -> c.merge(i.getSituacao(), 1L, Long::sum));
        return converter(p, c);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(PacoteVOs.Pacote req, Long userId) {
        if (!ACOES.contains(req.getAcao())) {
            throw exception(PACOTE_INVALIDO, "ação deve ser EXECUTE, STORE ou LAUNCH");
        }
        if (!PLATAFORMAS.contains(Optional.ofNullable(req.getPlataforma()).orElse("windows"))) {
            throw exception(PACOTE_INVALIDO, "plataforma deve ser windows, linux ou macos");
        }
        String comando = vazio(req.getComando());
        String caminho = vazio(req.getCaminho());
        if ("EXECUTE".equals(req.getAcao()) && comando == null) {
            throw exception(PACOTE_INVALIDO, "informe a linha de comando");
        }
        if ("LAUNCH".equals(req.getAcao()) && comando == null) {
            throw exception(PACOTE_INVALIDO, "informe o arquivo a executar");
        }
        if ("STORE".equals(req.getAcao()) && caminho == null) {
            throw exception(PACOTE_INVALIDO, "informe a pasta de destino");
        }
        PacoteDO atual = req.getId() == null ? null : existente(req.getId());
        // o que o agente executa não muda depois de implantado: crie outro pacote
        if (atual != null && temImplantacao(atual.getId()) && (!Objects.equals(atual.getAcao(), req.getAcao())
                || !Objects.equals(atual.getComando(), comando) || !Objects.equals(atual.getCaminho(), caminho))) {
            throw exception(PACOTE_JA_IMPLANTADO);
        }
        PacoteDO p = PacoteDO.builder().id(req.getId()).nome(req.getNome().strip()).descricao(vazio(req.getDescricao()))
                .plataforma(Optional.ofNullable(req.getPlataforma()).orElse("windows")).acao(req.getAcao())
                .comando(comando).caminho(caminho).prioridade(req.getPrioridade() == null ? 5 : req.getPrioridade())
                .notificar(Boolean.TRUE.equals(req.getNotificar())).notificarTexto(vazio(req.getNotificarTexto()))
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        if (atual == null) {
            p.setFileid(novoFileid());
            p.setFragmentos(0);
            p.setUserId(userId);
            cmdbPacoteMapper.insert(p);
        } else {
            cmdbPacoteMapper.updateById(p);
        }
        auditoriaService.registrar(atual == null ? "inventario.pacote_criado" : "inventario.pacote_alterado", userId, null,
                Map.of("pacote", p.getId(), "nome", p.getNome(), "acao", p.getAcao(), "comando", Objects.toString(comando, Objects.toString(caminho, ""))));
        return p.getId();
    }

    /** Identificador no protocolo: como o OCS, os segundos do momento da criação (único). */
    private synchronized String novoFileid() {
        long s = System.currentTimeMillis() / 1000;
        while (cmdbPacoteMapper.selectCount(PacoteDO::getFileid, String.valueOf(s)) > 0) {
            s++;
        }
        return String.valueOf(s);
    }

    /**
     * Recebe o arquivo (zip para Windows, tar.gz para Linux) em fluxo, calcula MD5 e SHA-256 e quebra nos fragmentos
     * que o agente baixa.
     */
    public PacoteVOs.Pacote receberArquivo(Long id, String nome, InputStream corpo, Long userId) {
        PacoteDO p = existente(id);
        if (temImplantacao(id)) {
            throw exception(PACOTE_JA_IMPLANTADO);
        }
        long maximo = (long) inventarioProperties.getPacoteMaxMb() * 1024 * 1024;
        int tamanhoFragmento = Math.max(64, inventarioProperties.getPacoteFragmentoKb()) * 1024;
        Path pasta = pasta(p.getFileid());
        Path temporaria = pasta.resolveSibling(p.getFileid() + ".novo");
        long total = 0;
        int fragmentos = 0;
        MessageDigest md5, sha;
        try {
            md5 = MessageDigest.getInstance("MD5");
            sha = MessageDigest.getInstance("SHA-256");
            apagar(temporaria);
            Files.createDirectories(temporaria);
            byte[] buf = new byte[64 * 1024];
            OutputStream frag = null;
            long noFragmento = 0;
            try {
                int n;
                while ((n = corpo.read(buf)) > 0) {
                    total += n;
                    if (total > maximo) {
                        throw exception(PACOTE_GRANDE, inventarioProperties.getPacoteMaxMb());
                    }
                    md5.update(buf, 0, n);
                    sha.update(buf, 0, n);
                    int ofs = 0;
                    while (ofs < n) {
                        if (frag == null || noFragmento >= tamanhoFragmento) {
                            if (frag != null) {
                                frag.close();
                            }
                            fragmentos++;
                            frag = Files.newOutputStream(temporaria.resolve(p.getFileid() + "-" + fragmentos));
                            noFragmento = 0;
                        }
                        int k = (int) Math.min(n - ofs, tamanhoFragmento - noFragmento);
                        frag.write(buf, ofs, k);
                        ofs += k;
                        noFragmento += k;
                    }
                }
            } finally {
                if (frag != null) {
                    frag.close();
                }
            }
            if (total == 0) {
                apagar(temporaria);
                throw exception(PACOTE_INVALIDO, "arquivo vazio");
            }
            apagar(pasta);
            Files.move(temporaria, pasta, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            apagar(temporaria);
            throw exception(PACOTE_ARQUIVO_FALHOU, e.getMessage());
        } catch (RuntimeException e) {
            apagar(temporaria);
            throw e;
        }
        String md5Hex = HexFormat.of().formatHex(md5.digest()), shaHex = HexFormat.of().formatHex(sha.digest());
        cmdbPacoteMapper.updateById(PacoteDO.builder().id(id).arquivoNome(InventarioAgenteService.limpar(nome, 255)).tamanho(total)
                .md5(md5Hex).sha256(shaHex).fragmentos(fragmentos).build());
        auditoriaService.registrar("inventario.pacote_arquivo", userId, null, Map.of("pacote", id, "nome", p.getNome(),
                "arquivo", Objects.toString(nome, ""), "tamanho", total, "sha256", shaHex));
        return obter(id);
    }

    private Path pasta(String fileid) {
        return Paths.get(inventarioProperties.getPacotesPasta()).toAbsolutePath().normalize().resolve(fileid);
    }

    private static void apagar(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (var s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignorado) {
                    // sobra no disco não atrapalha o próximo envio
                }
            });
        } catch (IOException ignorado) {
            // idem
        }
    }

    // ======================================================================================= implantação

    /** Pede o pacote para máquinas com agente OCS (por lista ou por unidade). */
    @Transactional(rollbackFor = Exception.class)
    public PacoteVOs.Resultado implantar(PacoteVOs.ImplantarReq req, Long userId) {
        PacoteDO p = existente(req.getPacoteId());
        if (!Boolean.TRUE.equals(p.getAtivo()) || p.getFragmentos() == null || p.getFragmentos() == 0) {
            throw exception(PACOTE_SEM_ARQUIVO);
        }
        endereco();   // falha cedo se o endereço público não estiver configurado
        LambdaQueryWrapperX<AtivoDO> q = new LambdaQueryWrapperX<AtivoDO>().eq(AtivoDO::getOrigem, "ocs").eq(AtivoDO::getSituacao, "em_uso");
        if (req.getAtivoIds() != null && !req.getAtivoIds().isEmpty()) {
            q.in(AtivoDO::getId, req.getAtivoIds());
        } else if (req.getDeptId() != null) {
            Set<Long> depts = new HashSet<>(List.of(req.getDeptId()));
            deptApi.getChildDeptList(req.getDeptId()).forEach(d -> depts.add(d.getId()));
            q.in(AtivoDO::getDeptId, depts);
        } else {
            throw exception(PACOTE_INVALIDO, "escolha as máquinas ou a unidade");
        }
        // pelo escopo de quem pede: só máquinas que a pessoa enxerga
        List<AtivoDO> ativos = cmdbAtivoMapper.selectList(q);
        PacoteVOs.Resultado r = new PacoteVOs.Resultado();
        LocalDateTime agora = LocalDateTime.now();
        for (AtivoDO a : ativos) {
            if (!compativel(p.getPlataforma(), a.getSoNome())) {
                r.setIgnorados(r.getIgnorados() + 1);
                continue;
            }
            try {
                cmdbPacoteImplantacaoMapper.insert(PacoteImplantacaoDO.builder().pacoteId(p.getId()).ativoId(a.getId()).situacao("aguardando")
                        .deptId(a.getDeptId()).pedidoPor(userId).pedidoEm(agora).build());
                r.setPedidos(r.getPedidos() + 1);
            } catch (DuplicateKeyException e) {
                r.setJaPendentes(r.getJaPendentes() + 1);
            }
        }
        if (req.getAtivoIds() != null) {
            r.setIgnorados(r.getIgnorados() + (int) req.getAtivoIds().stream().distinct().count() - ativos.size());
        }
        auditoriaService.registrar("inventario.pacote_implantado", userId, null, Map.of("pacote", p.getId(), "nome", p.getNome(),
                "maquinas", ativos.stream().map(AtivoDO::getNome).limit(200).collect(Collectors.joining(", ")), "pedidos", r.getPedidos()));
        return r;
    }

    /** Windows no pacote Windows, o resto nos outros (sem sistema conhecido, deixa passar). */
    static boolean compativel(String plataforma, String so) {
        if (so == null) {
            return true;
        }
        boolean windows = so.toLowerCase(Locale.ROOT).contains("windows");
        boolean mac = so.toLowerCase(Locale.ROOT).contains("mac");
        return switch (plataforma) {
            case "windows" -> windows;
            case "macos" -> mac;
            default -> !windows && !mac;
        };
    }

    public void cancelar(Long implantacaoId, Long userId) {
        PacoteImplantacaoDO i = cmdbPacoteImplantacaoMapper.selectById(implantacaoId);
        if (i == null) {
            throw exception(PACOTE_NAO_EXISTE);
        }
        if (!Set.of("aguardando", "notificado").contains(i.getSituacao())) {
            return;
        }
        cmdbPacoteImplantacaoMapper.updateById(PacoteImplantacaoDO.builder().id(i.getId()).situacao("cancelado").concluidoEm(LocalDateTime.now()).build());
        auditoriaService.registrar("inventario.pacote_cancelado", userId, null, Map.of("implantacao", i.getId(), "pacote", i.getPacoteId(),
                "ativo", i.getAtivoId()));
    }

    public PageResult<PacoteVOs.Implantacao> implantacoes(PacoteVOs.ImplantacoesReq req) {
        LambdaQueryWrapperX<PacoteImplantacaoDO> q = new LambdaQueryWrapperX<PacoteImplantacaoDO>()
                .eqIfPresent(PacoteImplantacaoDO::getPacoteId, req.getPacoteId())
                .eqIfPresent(PacoteImplantacaoDO::getAtivoId, req.getAtivoId())
                .eqIfPresent(PacoteImplantacaoDO::getSituacao, req.getSituacao())
                .orderByDesc(PacoteImplantacaoDO::getId);
        PageResult<PacoteImplantacaoDO> p = cmdbPacoteImplantacaoMapper.selectPage(req, q);
        Set<Long> ativoIds = p.getList().stream().map(PacoteImplantacaoDO::getAtivoId).collect(Collectors.toSet());
        Map<Long, String> ativos = ativoIds.isEmpty() ? Map.of() : cmdbAtivoMapper.selectByIds(ativoIds).stream()
                .collect(Collectors.toMap(AtivoDO::getId, AtivoDO::getNome));
        Set<Long> pacoteIds = p.getList().stream().map(PacoteImplantacaoDO::getPacoteId).collect(Collectors.toSet());
        Map<Long, String> pacotes = pacoteIds.isEmpty() ? Map.of() : cmdbPacoteMapper.selectByIds(pacoteIds).stream()
                .collect(Collectors.toMap(PacoteDO::getId, PacoteDO::getNome));
        List<PacoteVOs.Implantacao> l = p.getList().stream().map(i -> {
            PacoteVOs.Implantacao vo = new PacoteVOs.Implantacao();
            vo.setId(i.getId());
            vo.setPacoteId(i.getPacoteId());
            vo.setPacoteNome(pacotes.get(i.getPacoteId()));
            vo.setAtivoId(i.getAtivoId());
            vo.setAtivoNome(ativos.getOrDefault(i.getAtivoId(), "#" + i.getAtivoId()));
            vo.setSituacao(i.getSituacao());
            vo.setCodigo(i.getCodigo());
            vo.setPedidoEm(i.getPedidoEm());
            vo.setNotificadoEm(i.getNotificadoEm());
            vo.setConcluidoEm(i.getConcluidoEm());
            return vo;
        }).toList();
        return new PageResult<>(l, p.getTotal());
    }

    // ======================================================================================= protocolo do agente

    /** Opção DOWNLOAD para a resposta ao PROLOG; null = nada pendente para esta máquina. */
    @DataPermission(enable = false)
    public Map<String, Object> opcaoProlog(AtivoDO ativo) {
        if (ativo == null || !"ocs".equals(ativo.getOrigem())) {
            return null;
        }
        List<PacoteImplantacaoDO> pendentes = cmdbPacoteImplantacaoMapper.selectList(new LambdaQueryWrapperX<PacoteImplantacaoDO>()
                .eq(PacoteImplantacaoDO::getAtivoId, ativo.getId()).in(PacoteImplantacaoDO::getSituacao, "aguardando", "notificado"));
        if (pendentes.isEmpty()) {
            return null;
        }
        Map<Long, PacoteDO> pacotes = cmdbPacoteMapper.selectByIds(pendentes.stream().map(PacoteImplantacaoDO::getPacoteId).toList()).stream()
                .filter(x -> Boolean.TRUE.equals(x.getAtivo()) && x.getFragmentos() != null && x.getFragmentos() > 0)
                .collect(Collectors.toMap(PacoteDO::getId, x -> x));
        if (pacotes.isEmpty()) {
            return null;
        }
        String endereco = endereco();
        List<Object> params = new ArrayList<>();
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("TYPE", "CONF");
        conf.put("ON", 1);
        conf.put("CYCLE_LATENCY", 60);
        conf.put("FRAG_LATENCY", 10);
        conf.put("PERIOD_LATENCY", 1);
        conf.put("PERIOD_LENGTH", 10);
        conf.put("TIMEOUT", 30);
        conf.put("EXECUTION_TIMEOUT", 120);
        params.add(new Protocolo.ComAtributos(conf, null));
        LocalDateTime agora = LocalDateTime.now();
        for (PacoteImplantacaoDO i : pendentes) {
            PacoteDO p = pacotes.get(i.getPacoteId());
            if (p == null) {
                continue;
            }
            Map<String, Object> pack = new LinkedHashMap<>();
            pack.put("TYPE", "PACK");
            pack.put("ID", p.getFileid());
            pack.put("INFO_LOC", endereco);
            pack.put("PACK_LOC", endereco);
            pack.put("CERT_PATH", "INSTALL_PATH");
            pack.put("CERT_FILE", "INSTALL_PATH");
            pack.put("SCHEDULE", "");
            pack.put("POSTCMD", "");
            pack.put("FORCE", 0);
            params.add(new Protocolo.ComAtributos(pack, null));
            if ("aguardando".equals(i.getSituacao())) {
                cmdbPacoteImplantacaoMapper.updateById(PacoteImplantacaoDO.builder().id(i.getId()).situacao("notificado").notificadoEm(agora).build());
            }
        }
        Map<String, Object> opcao = new LinkedHashMap<>();
        opcao.put("NAME", "DOWNLOAD");
        opcao.put("PARAM", params);
        return opcao;
    }

    /** Arquivo info de um pacote (o agente baixa por HTTPS). */
    @DataPermission(enable = false)
    public byte[] info(String fileid) {
        PacoteDO p = cmdbPacoteMapper.selectOne(PacoteDO::getFileid, fileid);
        if (p == null || !Boolean.TRUE.equals(p.getAtivo()) || p.getFragmentos() == null || p.getFragmentos() == 0) {
            return null;
        }
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("ID", p.getFileid());
        a.put("PRI", p.getPrioridade());
        a.put("ACT", p.getAcao());
        a.put("DIGEST", p.getMd5());
        a.put("PROTO", inventarioProperties.getPacotesProtocolo());
        a.put("FRAGS", p.getFragmentos());
        a.put("DIGEST_ALGO", "MD5");
        a.put("DIGEST_ENCODE", "Hexa");
        switch (p.getAcao()) {
            case "STORE" -> a.put("PATH", p.getCaminho());
            case "LAUNCH" -> a.put("NAME", p.getComando());
            default -> a.put("COMMAND", p.getComando());
        }
        boolean notificar = Boolean.TRUE.equals(p.getNotificar());
        a.put("NOTIFY_USER", notificar ? 1 : 0);
        a.put("NOTIFY_TEXT", notificar ? Objects.toString(p.getNotificarTexto(), "") : "");
        a.put("NOTIFY_COUNTDOWN", notificar ? 60 : "");
        a.put("NOTIFY_CAN_ABORT", 0);
        a.put("NOTIFY_CAN_DELAY", notificar ? 1 : 0);
        a.put("NEED_DONE_ACTION", 0);
        a.put("NEED_DONE_ACTION_TEXT", "");
        a.put("GARDEFOU", "rien");
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<DOWNLOAD");
        a.forEach((k, v) -> s.append(' ').append(k).append("=\"").append(Protocolo.escaparXml(String.valueOf(v))).append('"'));
        s.append(" />\n");
        return s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Fragmento n de um pacote; null = não existe. */
    @DataPermission(enable = false)
    public Path fragmento(String fileid, int n) {
        PacoteDO p = cmdbPacoteMapper.selectOne(PacoteDO::getFileid, fileid);
        if (p == null || !Boolean.TRUE.equals(p.getAtivo()) || n < 1 || p.getFragmentos() == null || n > p.getFragmentos()) {
            return null;
        }
        Path f = pasta(p.getFileid()).resolve(p.getFileid() + "-" + n);
        return Files.isRegularFile(f) ? f : null;
    }

    /** Resultado que o agente manda ({@code QUERY=DOWNLOAD}, ID e ERR). */
    @DataPermission(enable = false)
    public boolean resultado(AtivoDO ativo, String fileid, String codigo) {
        if (ativo == null || fileid == null) {
            return false;
        }
        PacoteDO p = cmdbPacoteMapper.selectOne(PacoteDO::getFileid, fileid);
        if (p == null) {
            return false;
        }
        PacoteImplantacaoDO i = cmdbPacoteImplantacaoMapper.selectOne(new LambdaQueryWrapperX<PacoteImplantacaoDO>()
                .eq(PacoteImplantacaoDO::getPacoteId, p.getId()).eq(PacoteImplantacaoDO::getAtivoId, ativo.getId())
                .in(PacoteImplantacaoDO::getSituacao, "aguardando", "notificado").last("LIMIT 1"));
        if (i == null) {
            return false;
        }
        String c = codigo == null || codigo.isBlank() ? "UNKNOWN_CODE" : InventarioAgenteService.limpar(codigo, 60);
        // SUCCESS, SUCCESS_ALREADY_SETUP; o agente Windows manda EXIT_CODE_<n> com o código do instalador
        boolean ok = c.startsWith("SUCCESS") || c.equals("EXIT_CODE_0");
        boolean intermediario = c.equals("NOTIFIED") || c.equals("DOWNLOADING");
        if (intermediario) {
            return true;
        }
        cmdbPacoteImplantacaoMapper.updateById(PacoteImplantacaoDO.builder().id(i.getId()).situacao(ok ? "sucesso" : "erro").codigo(c)
                .concluidoEm(LocalDateTime.now()).build());
        return true;
    }

    /**
     * {@code host[:porta]/caminho} (sem esquema) que vai em INFO_LOC e PACK_LOC: o configurado, ou o endereço público do
     * portal + /download.
     */
    String endereco() {
        String e = inventarioProperties.getPacotesEndereco();
        if (e == null || e.isBlank()) {
            if (urlPublica == null || urlPublica.isBlank()) {
                throw exception(PACOTE_SEM_ENDERECO);
            }
            e = urlPublica.replaceAll("/+$", "") + "/download";
        }
        return e.replaceFirst("^[a-zA-Z]+://", "").replaceAll("/+$", "");
    }

    // ======================================================================================= apoio

    private boolean temImplantacao(Long pacoteId) {
        return cmdbPacoteImplantacaoMapper.selectCount(PacoteImplantacaoDO::getPacoteId, pacoteId) > 0;
    }

    private PacoteDO existente(Long id) {
        PacoteDO p = id == null ? null : cmdbPacoteMapper.selectById(id);
        if (p == null) {
            throw exception(PACOTE_NAO_EXISTE);
        }
        return p;
    }

    private PacoteVOs.Pacote converter(PacoteDO p, Map<String, Long> contagens) {
        PacoteVOs.Pacote vo = new PacoteVOs.Pacote();
        vo.setId(p.getId());
        vo.setNome(p.getNome());
        vo.setDescricao(p.getDescricao());
        vo.setPlataforma(p.getPlataforma());
        vo.setAcao(p.getAcao());
        vo.setComando(p.getComando());
        vo.setCaminho(p.getCaminho());
        vo.setPrioridade(p.getPrioridade());
        vo.setNotificar(p.getNotificar());
        vo.setNotificarTexto(p.getNotificarTexto());
        vo.setFileid(p.getFileid());
        vo.setArquivoNome(p.getArquivoNome());
        vo.setTamanho(p.getTamanho());
        vo.setSha256(p.getSha256());
        vo.setFragmentos(p.getFragmentos());
        vo.setAtivo(p.getAtivo());
        vo.setContagens(contagens);
        return vo;
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

}
