package cn.iocoder.yudao.module.itsm.service.importacao;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.config.ItsmProperties;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.ImportacaoVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.ImportacaoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ImportacaoMapaMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.ImportacaoMapper;
import cn.iocoder.yudao.module.itsm.dominio.GlpiConverte;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Importação de um GLPI existente: verificar (só leitura, nada é gravado), iniciar (em segundo plano, uma de cada
 * vez), acompanhar e cancelar.
 *
 * <p>A senha do banco do GLPI não é gravada: vai para a tarefa em memória e some quando ela termina. Iniciar e
 * terminar ficam na trilha de auditoria.
 */
@Slf4j
@Service
public class ImportacaoService {

    @Resource
    private ImportacaoMapper importacaoMapper;
    @Resource
    private ImportacaoMapaMapper importacaoMapaMapper;
    @Resource
    private ImportadorGlpi importadorGlpi;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptMapper deptMapper;
    @Resource
    private ItsmProperties props;
    @Resource
    private AuditoriaService auditoria;

    private final ExecutorService fila = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "coti-importacao");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void parar() {
        fila.shutdownNow();
    }

    /** Uma importação que estava rodando quando o servidor parou não volta sozinha. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        importacaoMapper.selectList(ImportacaoDO::getStatus, ImportacaoDO.EXECUTANDO).forEach(i -> {
            importacaoMapper.updateById(ImportacaoDO.builder().id(i.getId()).status(ImportacaoDO.ERRO).terminadoEm(LocalDateTime.now())
                    .log(i.getLog() + ImportadorGlpi.agora() + "  Interrompida: o servidor reiniciou. Rode de novo: o que já veio é pulado.\n").build());
        });
    }

    // ------------------------------------------------------------------ verificar

    public ImportacaoVOs.Verificacao verificar(ImportacaoVOs.IniciarReq req) {
        ImportacaoVOs.Verificacao v = new ImportacaoVOs.Verificacao();
        try (GlpiFonte f = abrir(req.getConexao())) {
            v.setVersao(f.versao());
            if (v.getVersao() == null || !(v.getVersao().startsWith("10.") || v.getVersao().startsWith("11."))) {
                v.getAvisos().add("Feito para GLPI 10 e 11; esta base é " + Objects.toString(v.getVersao(), "de versão desconhecida")
                        + ". Confira o resultado com atenção.");
            }
            ImportacaoVOs.Opcoes op = req.getOpcoes();
            String filtro = (Boolean.TRUE.equals(op.getIncluirLixeira()) ? "" : " AND is_deleted = 0")
                    + (op.getDesde() == null ? "" : " AND date >= '" + op.getDesde() + "'");
            Map<String, Long> c = v.getContagens();
            c.put("usuarios", f.contar("SELECT count(*) FROM glpi_users"));
            c.put("equipes", f.contar("SELECT count(*) FROM glpi_groups WHERE is_assign = 1"));
            c.put("categorias", f.contar("SELECT count(*) FROM glpi_itilcategories"));
            c.put("chamados", f.contar("SELECT count(*) FROM glpi_tickets WHERE 1 = 1" + filtro));
            c.put("acompanhamentos", f.contar("SELECT count(*) FROM glpi_itilfollowups WHERE itemtype = 'Ticket'"));
            c.put("tarefas", f.contar("SELECT count(*) FROM glpi_tickettasks"));
            c.put("artigos", f.contar("SELECT count(*) FROM glpi_knowbaseitems"));
            c.put("problemas", f.contar("SELECT count(*) FROM glpi_problems WHERE 1 = 1" + filtro));
            c.put("mudancas", f.contar("SELECT count(*) FROM glpi_changes WHERE 1 = 1" + filtro));
            c.put("fornecedores", f.contar("SELECT count(*) FROM glpi_suppliers WHERE is_deleted = 0"));
            c.put("contratos", f.contar("SELECT count(*) FROM glpi_contracts WHERE is_deleted = 0 AND is_template = 0"));
            c.put("licencas", f.contar("SELECT count(*) FROM glpi_softwarelicenses WHERE is_deleted = 0 AND is_template = 0"));
            c.put("projetos", f.contar("SELECT count(*) FROM glpi_projects WHERE is_deleted = 0 AND is_template = 0"));
            c.put("documentos", f.contar("SELECT count(*) FROM glpi_documents WHERE is_deleted = 0"));
            usuarios(f, v);
            entidades(f, v, op);
            if (Boolean.TRUE.equals(op.getManterNumeros())) {
                v.getNumerosOcupados().put("chamados", ocupados(f, "SELECT id FROM glpi_tickets WHERE 1 = 1" + filtro, "itsm.chamado", "chamado"));
                v.getNumerosOcupados().put("problemas", ocupados(f, "SELECT id FROM glpi_problems WHERE 1 = 1" + filtro, "itsm.problema", "problema"));
                v.getNumerosOcupados().put("mudancas", ocupados(f, "SELECT id FROM glpi_changes WHERE 1 = 1" + filtro, "itsm.mudanca", "mudanca"));
                if (v.getNumerosOcupados().values().stream().anyMatch(n -> n > 0)) {
                    v.getAvisos().add("Alguns números do GLPI já estão em uso aqui: esses registros vão receber número novo.");
                }
            }
            String p = props.getImportacaoPastaArquivos();
            v.setPastaArquivos(p != null && !p.isBlank() && Files.isDirectory(Path.of(p)));
            if (Boolean.TRUE.equals(op.getArquivos()) && !v.getPastaArquivos() && c.get("documentos") > 0) {
                v.getAvisos().add("Os " + c.get("documentos") + " anexos não serão copiados: copie a pasta files/ do GLPI para importacao/glpi-files no servidor.");
            }
        } catch (IllegalArgumentException e) {
            throw exception(IMPORTACAO_CONEXAO, e.getMessage());
        } catch (java.sql.SQLException | IllegalStateException e) {
            throw exception(IMPORTACAO_CONEXAO, resumo(e));
        }
        return v;
    }

    private void usuarios(GlpiFonte f, ImportacaoVOs.Verificacao v) {
        Set<String> logins = new HashSet<>();
        Set<String> emails = new HashSet<>();
        adminUserMapper.selectList().forEach(u -> {
            logins.add(u.getUsername().toLowerCase(Locale.ROOT));
            if (u.getEmail() != null) {
                emails.add(u.getEmail().trim().toLowerCase(Locale.ROOT));
            }
        });
        Map<Long, String> emailDe = new HashMap<>();
        f.lista("SELECT users_id, email FROM glpi_useremails ORDER BY is_default DESC, id")
                .forEach(e -> emailDe.putIfAbsent(ImportadorGlpi.num(e.get("users_id")), String.valueOf(e.get("email")).trim().toLowerCase(Locale.ROOT)));
        int achados = 0, novos = 0, semLogin = 0;
        for (Map<String, Object> u : f.lista("SELECT id, name FROM glpi_users")) {
            String login = GlpiConverte.login(Objects.toString(u.get("name"), ""));
            String email = emailDe.get(ImportadorGlpi.num(u.get("id")));
            if ((login != null && logins.contains(login)) || (email != null && emails.contains(email))) {
                achados++;
            } else if (login == null) {
                semLogin++;
            } else {
                novos++;
            }
        }
        v.setUsuariosEncontrados(achados);
        v.setUsuariosNovos(novos);
        v.setUsuariosSemLogin(semLogin);
    }

    private void entidades(GlpiFonte f, ImportacaoVOs.Verificacao v, ImportacaoVOs.Opcoes op) {
        Map<String, DeptDO> porNome = new HashMap<>();
        deptMapper.selectList().forEach(d -> porNome.putIfAbsent(d.getName().trim().toLowerCase(Locale.ROOT), d));
        for (Map<String, Object> e : f.lista("SELECT id, name, completename FROM glpi_entities ORDER BY id")) {
            ImportacaoVOs.Entidade x = new ImportacaoVOs.Entidade();
            x.setId(ImportadorGlpi.num(e.get("id")));
            x.setNome(Objects.toString(e.get("completename"), Objects.toString(e.get("name"), "")));
            DeptDO d = porNome.get(Objects.toString(e.get("name"), "").trim().toLowerCase(Locale.ROOT));
            if (d != null) {
                x.setDeptId(d.getId());
                x.setDept(d.getName());
            }
            v.getEntidades().add(x);
        }
        if (v.getEntidades().stream().anyMatch(x -> x.getDeptId() == null) && op.getDeptPadraoId() == null) {
            v.getAvisos().add("Entidades sem unidade com o mesmo nome vão para a unidade principal. Escolha outra em \"Unidade padrão\", se preferir.");
        }
    }

    private int ocupados(GlpiFonte f, String sqlIds, String tabela, String tipo) {
        Set<Long> daqui = new HashSet<>(importacaoMapaMapper.ids(tabela));
        Set<String> jaVieram = new HashSet<>();
        importacaoMapaMapper.todos(ImportadorGlpi.ORIGEM, tipo).forEach(r -> jaVieram.add(String.valueOf(r.get("id_origem"))));
        return (int) f.lista(sqlIds).stream().map(r -> ImportadorGlpi.num(r.get("id")))
                .filter(id -> daqui.contains(id) && !jaVieram.contains(String.valueOf(id))).count();
    }

    // ------------------------------------------------------------------ executar

    public Long iniciar(ImportacaoVOs.IniciarReq req, Long userId) {
        ImportacaoVOs.Conexao c = req.getConexao();
        // falha cedo, antes de criar o registro (servidor errado, senha errada)
        try (GlpiFonte teste = abrir(c)) {
            teste.versao();
        } catch (IllegalArgumentException e) {
            throw exception(IMPORTACAO_CONEXAO, e.getMessage());
        } catch (java.sql.SQLException | IllegalStateException e) {
            throw exception(IMPORTACAO_CONEXAO, resumo(e));
        }
        ImportacaoDO imp = ImportacaoDO.builder().origem(ImportadorGlpi.ORIGEM).status(ImportacaoDO.EXECUTANDO).host(c.getHost())
                .porta(c.getPorta()).banco(c.getBanco()).usuario(c.getUsuario()).opcoes(JsonUtils.toJsonString(req.getOpcoes()))
                .progresso("{}").log("").cancelar(false).iniciadoEm(LocalDateTime.now()).build();
        try {
            importacaoMapper.insert(imp);
        } catch (DuplicateKeyException e) {
            throw exception(IMPORTACAO_EM_ANDAMENTO);
        }
        auditoria.registrar("importacao.iniciada", userId, null, Map.of("importacao", imp.getId(), "origem", c.getHost() + "/" + c.getBanco()));
        String senha = c.getSenha();
        fila.submit(() -> rodar(imp, req.getConexao(), senha, req.getOpcoes(), userId));
        return imp.getId();
    }

    private void rodar(ImportacaoDO imp, ImportacaoVOs.Conexao c, String senha, ImportacaoVOs.Opcoes op, Long userId) {
        String status = ImportacaoDO.CONCLUIDA;
        String fim;
        try (GlpiFonte f = new GlpiFonte(c.getHost(), c.getPorta(), c.getBanco(), c.getUsuario(), senha, Boolean.TRUE.equals(c.getExigirTls()))) {
            importacaoMapper.updateById(ImportacaoDO.builder().id(imp.getId()).versaoOrigem(f.versao()).build());
            importadorGlpi.executar(imp, op, f, userId);
            fim = "Concluída.";
        } catch (ImportadorGlpi.CanceladaException e) {
            status = ImportacaoDO.CANCELADA;
            fim = "Cancelada a pedido. O que já veio fica; rodar de novo continua de onde parou.";
        } catch (Throwable e) {
            status = ImportacaoDO.ERRO;
            fim = "Parou com erro: " + resumo(e);
            log.error("[rodar][importação {}]", imp.getId(), e);
        }
        ImportacaoDO atual = importacaoMapper.selectById(imp.getId());
        // etapa volta a vazio: o updateById ignoraria o nulo
        importacaoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ImportacaoDO>()
                .eq(ImportacaoDO::getId, imp.getId()).set(ImportacaoDO::getStatus, status).set(ImportacaoDO::getEtapa, null)
                .set(ImportacaoDO::getTerminadoEm, LocalDateTime.now())
                .set(ImportacaoDO::getLog, (atual == null ? "" : atual.getLog()) + ImportadorGlpi.agora() + "  " + fim + "\n"));
        auditoria.registrar("importacao.terminada", userId, null, Map.of("importacao", imp.getId(), "situacao", status));
    }

    public void cancelar(Long id) {
        ImportacaoDO i = existente(id);
        if (!ImportacaoDO.EXECUTANDO.equals(i.getStatus())) {
            throw exception(IMPORTACAO_NAO_EXECUTANDO);
        }
        importacaoMapper.updateById(ImportacaoDO.builder().id(id).cancelar(true).build());
    }

    public List<ImportacaoVOs.Resp> listar() {
        return converter(importacaoMapper.selectList(new LambdaQueryWrapperX<ImportacaoDO>().orderByDesc(ImportacaoDO::getId).last("LIMIT 20")), false);
    }

    public ImportacaoVOs.Resp obter(Long id) {
        return converter(List.of(existente(id)), true).get(0);
    }

    private ImportacaoDO existente(Long id) {
        ImportacaoDO i = id == null ? null : importacaoMapper.selectById(id);
        if (i == null) {
            throw exception(IMPORTACAO_NAO_EXISTE);
        }
        return i;
    }

    private List<ImportacaoVOs.Resp> converter(List<ImportacaoDO> lista, boolean comLog) {
        Set<Long> quem = new HashSet<>();
        lista.forEach(i -> {
            try {
                quem.add(Long.parseLong(i.getCreator()));
            } catch (Exception ignorado) {
                // criada sem usuário logado
            }
        });
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(quem);
        return lista.stream().map(i -> {
            ImportacaoVOs.Resp r = new ImportacaoVOs.Resp();
            r.setId(i.getId());
            r.setStatus(i.getStatus());
            r.setHost(i.getHost());
            r.setPorta(i.getPorta());
            r.setBanco(i.getBanco());
            r.setUsuario(i.getUsuario());
            r.setOpcoes(JsonUtils.parseObject(i.getOpcoes(), ImportacaoVOs.Opcoes.class));
            r.setEtapa(i.getEtapa());
            r.setProgresso(JsonUtils.parseObject(i.getProgresso(), new TypeReference<LinkedHashMap<String, ImportacaoVOs.Etapa>>() {
            }));
            r.setLog(comLog ? i.getLog() : null);
            r.setVersaoOrigem(i.getVersaoOrigem());
            r.setIniciadoEm(i.getIniciadoEm());
            r.setTerminadoEm(i.getTerminadoEm());
            try {
                r.setQuem(Optional.ofNullable(nomes.get(Long.parseLong(i.getCreator()))).map(AdminUserRespDTO::getNickname).orElse(null));
            } catch (Exception ignorado) {
                // sem autor
            }
            return r;
        }).toList();
    }

    private static GlpiFonte abrir(ImportacaoVOs.Conexao c) throws java.sql.SQLException {
        return new GlpiFonte(c.getHost().trim(), c.getPorta(), c.getBanco().trim(), c.getUsuario().trim(), c.getSenha(),
                Boolean.TRUE.equals(c.getExigirTls()));
    }

    /** Mensagem curta da causa, sem pilha (vai para a tela). */
    private static String resumo(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String m = Objects.toString(t.getMessage(), t.getClass().getSimpleName());
        return m.length() > 300 ? m.substring(0, 300) + "…" : m;
    }

}
