package cn.iocoder.yudao.module.chat.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.chat.controller.admin.vo.ChatVOs;
import cn.iocoder.yudao.module.chat.dal.dataobject.*;
import cn.iocoder.yudao.module.chat.dal.mysql.ChatMappers;
import cn.iocoder.yudao.module.chat.dominio.ChatRegras;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.chat.enums.ErrorCodeConstants.*;

/**
 * Chat interno. O acesso é sempre pela participação na conversa, conferida no servidor em toda leitura e envio; quem não
 * participa recebe "não encontrada". O escopo de unidade fica desligado porque qualquer servidor fala com qualquer outro.
 */
@Slf4j
@Service
@DataPermission(enable = false)
public class ChatService {

    private static final int LIMITE_PAGINA = 100;
    private static final Duration ONLINE = Duration.ofSeconds(90);

    @Resource
    private ChatMappers.Conversa chatConversaMapper;
    @Resource
    private ChatMappers.Participante chatParticipanteMapper;
    @Resource
    private ChatMappers.Mensagem chatMensagemMapper;
    @Resource
    private ChatMappers.Historico chatHistoricoMapper;
    @Resource
    private ChatMappers.Anexo chatAnexoMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private DeptApi deptApi;
    @Resource
    private StringRedisTemplate redis;
    @Resource
    private ApplicationEventPublisher eventos;

    @Value("${coti.chat.pasta-arquivos:arquivos/chat}")
    private String pastaArquivos;

    // ------------------------------------------------------------------ pessoas e presença

    public List<ChatVOs.Pessoa> pessoas(String busca, Long userId) {
        String t = busca == null ? "" : busca.trim();
        String padrao = "%" + escaparLike(t) + "%";
        List<AdminUserDO> l = adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>()
                .eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus()).ne(AdminUserDO::getId, userId)
                .and(w -> w.apply("nickname ILIKE {0}", padrao).or().apply("username ILIKE {0}", padrao))
                .orderByAsc(AdminUserDO::getNickname).last("LIMIT 20"));
        Map<Long, DeptRespDTO> unidades = deptApi.getDeptMap(l.stream().map(AdminUserDO::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Set<Long> online = online(l.stream().map(AdminUserDO::getId).toList());
        // só o necessário para reconhecer a pessoa: e-mail e celular ficam de fora
        return l.stream().map(u -> {
            ChatVOs.Pessoa p = new ChatVOs.Pessoa();
            p.setId(u.getId());
            p.setNome(u.getNickname());
            p.setAvatar(u.getAvatar());
            DeptRespDTO d = unidades.get(u.getDeptId());
            p.setUnidade(d == null ? null : d.getName());
            p.setOnline(online.contains(u.getId()));
            return p;
        }).toList();
    }

    /** A tela avisa a cada minuto enquanto está aberta; sem aviso por 90 s, a pessoa fica fora. */
    public void presenca(Long userId) {
        try {
            redis.opsForValue().set("chat:online:" + userId, "1", ONLINE);
        } catch (RuntimeException e) {
            log.warn("[presenca][{}: {}]", userId, e.getMessage());
        }
    }

    private Set<Long> online(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        try {
            List<Long> lista = new ArrayList<>(ids);
            List<String> v = redis.opsForValue().multiGet(lista.stream().map(i -> "chat:online:" + i).toList());
            Set<Long> r = new HashSet<>();
            for (int i = 0; v != null && i < lista.size(); i++) {
                if (v.get(i) != null) {
                    r.add(lista.get(i));
                }
            }
            return r;
        } catch (RuntimeException e) {
            // presença é um detalhe: sem o Redis, a conversa continua
            log.warn("[online][{}]", e.getMessage());
            return Set.of();
        }
    }

    // ------------------------------------------------------------------ conversas

    public List<ChatVOs.ConversaResp> conversas(Long userId) {
        List<ChatParticipanteDO> meus = chatParticipanteMapper.selectList(ChatParticipanteDO::getUserId, userId);
        if (meus.isEmpty()) {
            return List.of();
        }
        List<ChatConversaDO> cs = chatConversaMapper.selectByIds(meus.stream().map(ChatParticipanteDO::getConversaId).toList());
        return converter(cs, meus, userId, false).stream()
                .sorted(Comparator.comparing((ChatVOs.ConversaResp c) -> c.getUltimaMensagemEm() == null ? LocalDateTime.MIN : c.getUltimaMensagemEm()).reversed())
                .toList();
    }

    public ChatVOs.ConversaResp conversa(Long conversaId, Long userId) {
        ChatParticipanteDO p = participacao(conversaId, userId);
        return converter(List.of(chatConversaMapper.selectById(conversaId)), List.of(p), userId, true).get(0);
    }

    public long totalNaoLidas(Long userId) {
        return chatParticipanteMapper.totalNaoLidas(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long abrirDireta(Long outroId, Long userId) {
        if (outroId.equals(userId)) {
            throw exception(GRUPO_INVALIDO, "Escolha outra pessoa");
        }
        pessoaAtiva(outroId);
        String chave = ChatRegras.parChave(userId, outroId);
        ChatConversaDO c = chatConversaMapper.selectOne(ChatConversaDO::getParChave, chave);
        if (c != null) {
            return c.getId();
        }
        c = ChatConversaDO.builder().tipo(ChatConversaDO.DIRETA).parChave(chave).ultimaSeq(0L).criadoPor(userId).build();
        try {
            chatConversaMapper.insert(c);
        } catch (DuplicateKeyException e) {
            // os dois abriram ao mesmo tempo: fica a do outro; o erro desfaz esta transação e a tela tenta de novo
            throw exception(GRUPO_INVALIDO, "Tente de novo");
        }
        incluir(c.getId(), userId, ChatParticipanteDO.MEMBRO, 0L);
        incluir(c.getId(), outroId, ChatParticipanteDO.MEMBRO, 0L);
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long criarGrupo(ChatVOs.GrupoReq req, Long userId) {
        Set<Long> membros = new LinkedHashSet<>(req.getMembros());
        membros.remove(userId);
        if (membros.isEmpty()) {
            throw exception(GRUPO_INVALIDO, "Escolha ao menos uma outra pessoa");
        }
        if (membros.size() + 1 > ChatRegras.MAX_MEMBROS) {
            throw exception(GRUPO_INVALIDO, "O grupo pode ter até " + ChatRegras.MAX_MEMBROS + " pessoas");
        }
        membros.forEach(this::pessoaAtiva);
        ChatConversaDO c = ChatConversaDO.builder().tipo(ChatConversaDO.GRUPO).nome(req.getNome().trim()).ultimaSeq(0L).criadoPor(userId).build();
        chatConversaMapper.insert(c);
        incluir(c.getId(), userId, ChatParticipanteDO.ADMIN, 0L);
        membros.forEach(m -> incluir(c.getId(), m, ChatParticipanteDO.MEMBRO, 0L));
        sistema(c.getId(), userId, nome(userId) + " criou o grupo");
        avisarConversa(c.getId(), null);
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void renomear(ChatVOs.RenomearReq req, Long userId) {
        ChatConversaDO c = grupoComoAdmin(req.getConversaId(), userId);
        chatConversaMapper.updateById(ChatConversaDO.builder().id(c.getId()).nome(req.getNome().trim()).build());
        sistema(c.getId(), userId, nome(userId) + " renomeou o grupo para " + req.getNome().trim());
        avisarConversa(c.getId(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void adicionar(ChatVOs.MembrosReq req, Long userId) {
        ChatConversaDO c = grupoComoAdmin(req.getConversaId(), userId);
        Set<Long> atuais = membrosIds(c.getId());
        Set<Long> novos = new LinkedHashSet<>(req.getUserIds());
        novos.removeAll(atuais);
        if (atuais.size() + novos.size() > ChatRegras.MAX_MEMBROS) {
            throw exception(GRUPO_INVALIDO, "O grupo pode ter até " + ChatRegras.MAX_MEMBROS + " pessoas");
        }
        for (Long m : novos) {
            pessoaAtiva(m);
            // quem entra depois não lê o que foi dito antes
            incluir(c.getId(), m, ChatParticipanteDO.MEMBRO, chatConversaMapper.selectById(c.getId()).getUltimaSeq());
            sistema(c.getId(), userId, nome(userId) + " adicionou " + nome(m));
        }
        avisarConversa(c.getId(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void remover(Long conversaId, Long alvo, Long userId) {
        grupoComoAdmin(conversaId, userId);
        if (alvo.equals(userId)) {
            throw exception(GRUPO_INVALIDO, "Para sair, use a opção de sair do grupo");
        }
        ChatParticipanteDO p = participacao(conversaId, alvo);
        chatParticipanteMapper.deleteById(p.getId());
        sistema(conversaId, userId, nome(userId) + " removeu " + nome(alvo));
        avisarConversa(conversaId, List.of(alvo));
    }

    @Transactional(rollbackFor = Exception.class)
    public void sair(Long conversaId, Long userId) {
        ChatParticipanteDO eu = participacao(conversaId, userId);
        ChatConversaDO c = chatConversaMapper.selectById(conversaId);
        if (!ChatConversaDO.GRUPO.equals(c.getTipo())) {
            throw exception(GRUPO_INVALIDO, "Só é possível sair de grupos");
        }
        chatParticipanteMapper.deleteById(eu.getId());
        List<ChatParticipanteDO> resto = chatParticipanteMapper.selectList(ChatParticipanteDO::getConversaId, conversaId);
        // grupo não fica sem administrador: assume quem está há mais tempo
        if (!resto.isEmpty() && resto.stream().noneMatch(p -> ChatParticipanteDO.ADMIN.equals(p.getPapel()))) {
            ChatParticipanteDO novo = resto.stream().min(Comparator.comparing(ChatParticipanteDO::getId)).get();
            chatParticipanteMapper.updateById(ChatParticipanteDO.builder().id(novo.getId()).papel(ChatParticipanteDO.ADMIN).build());
        }
        if (!resto.isEmpty()) {
            sistema(conversaId, userId, nome(userId) + " saiu do grupo");
        }
        avisarConversa(conversaId, List.of(userId));
    }

    // ------------------------------------------------------------------ mensagens

    public List<ChatVOs.MensagemResp> mensagens(Long conversaId, Long antesDe, Long depoisDe, Integer limite, Long userId) {
        ChatParticipanteDO p = participacao(conversaId, userId);
        int max = limite == null ? 50 : Math.max(1, Math.min(limite, LIMITE_PAGINA));
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMensagemDO> q = new LambdaQueryWrapperX<ChatMensagemDO>().eq(ChatMensagemDO::getConversaId, conversaId)
                .gt(ChatMensagemDO::getSeq, p.getDesdeSeq());
        List<ChatMensagemDO> l;
        if (depoisDe != null) {
            // o que faltou depois de uma queda, do mais antigo para o mais novo
            l = chatMensagemMapper.selectList(q.gt(ChatMensagemDO::getSeq, depoisDe).orderByAsc(ChatMensagemDO::getSeq).last("LIMIT " + max));
        } else {
            if (antesDe != null) {
                q.lt(ChatMensagemDO::getSeq, antesDe);
            }
            l = new ArrayList<>(chatMensagemMapper.selectList(q.orderByDesc(ChatMensagemDO::getSeq).last("LIMIT " + max)));
            Collections.reverse(l);
        }
        return converterMensagens(l);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatVOs.MensagemResp enviar(ChatVOs.EnviarReq req, Long userId) {
        ChatParticipanteDO eu = participacao(req.getConversaId(), userId);
        String texto = req.getConteudo() == null ? "" : req.getConteudo().strip();
        if (texto.length() > ChatRegras.MAX_TEXTO) {
            throw exception(MENSAGEM_INVALIDA, "Mensagem longa demais (máximo " + ChatRegras.MAX_TEXTO + " caracteres)");
        }
        if (texto.isEmpty() && req.getAnexoIds().isEmpty()) {
            throw exception(MENSAGEM_INVALIDA, "Escreva uma mensagem ou anexe um arquivo");
        }
        // a trava vem antes da checagem: duas tentativas da mesma mensagem não passam juntas
        ChatConversaDO c = chatConversaMapper.selectByIdParaAtualizar(req.getConversaId());
        ChatMensagemDO repetida = chatMensagemMapper.selectOne(new LambdaQueryWrapperX<ChatMensagemDO>().eq(ChatMensagemDO::getConversaId, c.getId())
                .eq(ChatMensagemDO::getRemetenteId, userId).eq(ChatMensagemDO::getClienteId, req.getClienteId()));
        if (repetida != null) {
            return converterMensagens(List.of(repetida)).get(0);
        }
        Long resposta = req.getRespostaId();
        if (resposta != null) {
            ChatMensagemDO r = chatMensagemMapper.selectById(resposta);
            if (r == null || !r.getConversaId().equals(c.getId()) || r.getSeq() <= eu.getDesdeSeq()) {
                throw exception(MENSAGEM_NAO_EXISTE);
            }
        }
        Set<Long> membros = membrosIds(c.getId());
        ChatMensagemDO m = gravar(c, userId, ChatMensagemDO.TEXTO, texto, resposta, mencoesValidas(req.getMencoes(), membros), req.getClienteId());
        vincularAnexos(req.getAnexoIds(), c.getId(), m.getId(), userId);
        chatParticipanteMapper.marcarLida(c.getId(), userId, m.getSeq());
        ChatVOs.MensagemResp resp = converterMensagens(List.of(m)).get(0);
        eventos.publishEvent(new ChatTempoReal.Evento("chat-mensagem", resp, membros));
        return resp;
    }

    @Transactional(rollbackFor = Exception.class)
    public void editar(ChatVOs.EditarReq req, Long userId) {
        ChatMensagemDO m = mensagemDoAutor(req.getMensagemId(), userId);
        String texto = req.getConteudo().strip();
        if (texto.isEmpty() || texto.length() > ChatRegras.MAX_TEXTO) {
            throw exception(MENSAGEM_INVALIDA, "Mensagem vazia ou longa demais");
        }
        chatHistoricoMapper.insert(ChatMensagemHistoricoDO.builder().mensagemId(m.getId()).acao("editada").conteudo(m.getConteudo())
                .userId(userId).quando(LocalDateTime.now()).build());
        Set<Long> membros = membrosIds(m.getConversaId());
        chatMensagemMapper.updateById(ChatMensagemDO.builder().id(m.getId()).conteudo(texto).editadaEm(LocalDateTime.now())
                .mencoes(Optional.ofNullable(ChatRegras.juntar(mencoesValidas(req.getMencoes(), membros))).orElse("")).build());
        eventos.publishEvent(new ChatTempoReal.Evento("chat-atualizada",
                converterMensagens(List.of(chatMensagemMapper.selectById(m.getId()))).get(0), membros));
    }

    /** Some para todos, mas o texto fica no histórico. O administrador do grupo também pode apagar. */
    @Transactional(rollbackFor = Exception.class)
    public void apagar(Long mensagemId, Long userId) {
        ChatMensagemDO m = chatMensagemMapper.selectById(mensagemId);
        if (m == null || !ChatMensagemDO.TEXTO.equals(m.getTipo()) || m.getApagadaEm() != null) {
            throw exception(MENSAGEM_NAO_EXISTE);
        }
        ChatParticipanteDO eu = participacao(m.getConversaId(), userId);
        if (m.getSeq() <= eu.getDesdeSeq() || !(m.getRemetenteId().equals(userId) || ChatParticipanteDO.ADMIN.equals(eu.getPapel()))) {
            throw exception(MENSAGEM_NAO_EXISTE);
        }
        chatHistoricoMapper.insert(ChatMensagemHistoricoDO.builder().mensagemId(m.getId()).acao("apagada").conteudo(m.getConteudo())
                .userId(userId).quando(LocalDateTime.now()).build());
        chatMensagemMapper.updateById(ChatMensagemDO.builder().id(m.getId()).apagadaEm(LocalDateTime.now()).build());
        eventos.publishEvent(new ChatTempoReal.Evento("chat-atualizada",
                converterMensagens(List.of(chatMensagemMapper.selectById(m.getId()))).get(0), membrosIds(m.getConversaId())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void marcarLida(ChatVOs.LidaReq req, Long userId) {
        participacao(req.getConversaId(), userId);
        ChatConversaDO c = chatConversaMapper.selectById(req.getConversaId());
        // não deixa marcar além do que existe, para não esconder a próxima mensagem
        long seq = Math.min(req.getSeq(), c.getUltimaSeq());
        chatParticipanteMapper.marcarLida(c.getId(), userId, seq);
        // as outras abertas da mesma pessoa zeram o contador
        eventos.publishEvent(new ChatTempoReal.Evento("chat-lida", Map.of("conversaId", c.getId(), "seq", seq), List.of(userId)));
    }

    public List<ChatVOs.Achado> buscar(String texto, Long conversaId, Long userId) {
        String t = texto == null ? "" : texto.trim();
        if (t.length() < 2) {
            return List.of();
        }
        List<ChatMensagemDO> l = chatMensagemMapper.buscar(userId, conversaId, t, "%" + escaparLike(t) + "%");
        Map<Long, ChatVOs.MensagemResp> resp = converterMensagens(l).stream().collect(Collectors.toMap(ChatVOs.MensagemResp::getId, Function.identity()));
        Map<Long, ChatVOs.ConversaResp> conv = l.isEmpty() ? Map.of() : conversas(userId).stream()
                .collect(Collectors.toMap(ChatVOs.ConversaResp::getId, Function.identity()));
        return l.stream().map(m -> {
            ChatVOs.Achado a = new ChatVOs.Achado();
            a.setMensagem(resp.get(m.getId()));
            ChatVOs.ConversaResp c = conv.get(m.getConversaId());
            a.setConversa(c == null ? "" : c.getNome());
            return a;
        }).toList();
    }

    // ------------------------------------------------------------------ anexos

    @Transactional(rollbackFor = Exception.class)
    public ChatVOs.AnexoResp anexar(Long conversaId, MultipartFile arquivo, Long userId) throws IOException {
        participacao(conversaId, userId);
        String nome = ChatRegras.nomeSeguro(arquivo.getOriginalFilename());
        if (arquivo.isEmpty() || nome.isEmpty()) {
            throw exception(ANEXO_INVALIDO, "Arquivo vazio");
        }
        if (arquivo.getSize() > ChatRegras.MAX_ANEXO) {
            throw exception(ANEXO_INVALIDO, "Arquivo grande demais (máximo 25 MB)");
        }
        if (!ChatRegras.extensaoPermitida(nome)) {
            throw exception(ANEXO_INVALIDO, "Esse tipo de arquivo não é aceito no chat");
        }
        byte[] conteudo = arquivo.getBytes();
        if (!ChatRegras.conteudoCombina(nome, conteudo)) {
            throw exception(ANEXO_INVALIDO, "O conteúdo do arquivo não combina com a extensão");
        }
        LocalDate hoje = LocalDate.now();
        String caminho = hoje.getYear() + "/" + String.format("%02d", hoje.getMonthValue()) + "/" + UUID.randomUUID();
        Path destino = Path.of(pastaArquivos).resolve(caminho);
        Files.createDirectories(destino.getParent());
        Files.write(destino, conteudo);
        ChatAnexoDO a = ChatAnexoDO.builder().conversaId(conversaId).nome(nome).tipoMime(ChatRegras.mime(nome)).tamanho(arquivo.getSize())
                .caminho(caminho).imagem(ChatRegras.imagem(nome)).enviadoPor(userId).build();
        chatAnexoMapper.insert(a);
        return anexoResp(a);
    }

    public record Baixa(String nome, String tipoMime, Path arquivo) {
    }

    public Baixa baixar(Long anexoId, Long userId) {
        ChatAnexoDO a = chatAnexoMapper.selectById(anexoId);
        if (a == null) {
            throw exception(ANEXO_NAO_EXISTE);
        }
        ChatParticipanteDO p;
        try {
            p = participacao(a.getConversaId(), userId);
        } catch (RuntimeException e) {
            throw exception(ANEXO_NAO_EXISTE);
        }
        if (a.getMensagemId() == null) {
            if (!a.getEnviadoPor().equals(userId)) {
                throw exception(ANEXO_NAO_EXISTE);
            }
        } else {
            ChatMensagemDO m = chatMensagemMapper.selectById(a.getMensagemId());
            if (m == null || m.getApagadaEm() != null || m.getSeq() <= p.getDesdeSeq()) {
                throw exception(ANEXO_NAO_EXISTE);
            }
        }
        Path arquivo = Path.of(pastaArquivos).resolve(a.getCaminho());
        if (!Files.exists(arquivo)) {
            throw exception(ANEXO_NAO_EXISTE);
        }
        return new Baixa(a.getNome(), a.getTipoMime(), arquivo);
    }

    // ------------------------------------------------------------------ internos

    private ChatParticipanteDO participacao(Long conversaId, Long userId) {
        ChatParticipanteDO p = chatParticipanteMapper.selectOne(ChatParticipanteDO::getConversaId, conversaId, ChatParticipanteDO::getUserId, userId);
        if (p == null) {
            throw exception(CONVERSA_NAO_EXISTE);
        }
        return p;
    }

    private ChatConversaDO grupoComoAdmin(Long conversaId, Long userId) {
        ChatParticipanteDO p = participacao(conversaId, userId);
        ChatConversaDO c = chatConversaMapper.selectById(conversaId);
        if (!ChatConversaDO.GRUPO.equals(c.getTipo()) || !ChatParticipanteDO.ADMIN.equals(p.getPapel())) {
            throw exception(SO_ADMIN_DO_GRUPO);
        }
        return c;
    }

    private ChatMensagemDO mensagemDoAutor(Long id, Long userId) {
        ChatMensagemDO m = chatMensagemMapper.selectById(id);
        if (m == null || !m.getRemetenteId().equals(userId) || m.getApagadaEm() != null || !ChatMensagemDO.TEXTO.equals(m.getTipo())) {
            throw exception(MENSAGEM_NAO_EXISTE);
        }
        participacao(m.getConversaId(), userId);
        return m;
    }

    private void incluir(Long conversaId, Long userId, String papel, Long desde) {
        chatParticipanteMapper.insert(ChatParticipanteDO.builder().conversaId(conversaId).userId(userId).papel(papel)
                .ultimaLidaSeq(desde).desdeSeq(desde).build());
    }

    private Set<Long> membrosIds(Long conversaId) {
        return chatParticipanteMapper.selectList(ChatParticipanteDO::getConversaId, conversaId).stream().map(ChatParticipanteDO::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<Long> mencoesValidas(List<Long> pedidas, Set<Long> membros) {
        return pedidas == null ? List.of() : pedidas.stream().filter(membros::contains).distinct().toList();
    }

    /** Número sequencial sob a trava da conversa. Quem chama precisa estar numa transação. */
    private ChatMensagemDO gravar(ChatConversaDO c, Long remetente, String tipo, String texto, Long resposta, List<Long> mencoes, String clienteId) {
        long seq = c.getUltimaSeq() + 1;
        LocalDateTime agora = LocalDateTime.now();
        ChatMensagemDO m = ChatMensagemDO.builder().conversaId(c.getId()).seq(seq).remetenteId(remetente).tipo(tipo).conteudo(texto)
                .respostaId(resposta).mencoes(ChatRegras.juntar(mencoes)).clienteId(clienteId).build();
        chatMensagemMapper.insert(m);
        chatConversaMapper.updateById(ChatConversaDO.builder().id(c.getId()).ultimaSeq(seq).ultimaMensagemEm(agora).build());
        c.setUltimaSeq(seq);
        c.setUltimaMensagemEm(agora);
        return m;
    }

    private void sistema(Long conversaId, Long autor, String texto) {
        ChatConversaDO c = chatConversaMapper.selectByIdParaAtualizar(conversaId);
        ChatMensagemDO m = gravar(c, autor, ChatMensagemDO.SISTEMA, texto, null, List.of(), null);
        chatParticipanteMapper.marcarLida(conversaId, autor, m.getSeq());
        eventos.publishEvent(new ChatTempoReal.Evento("chat-mensagem", converterMensagens(List.of(m)).get(0), membrosIds(conversaId)));
    }

    private void vincularAnexos(List<Long> ids, Long conversaId, Long mensagemId, Long userId) {
        for (Long id : new LinkedHashSet<>(ids)) {
            ChatAnexoDO a = chatAnexoMapper.selectById(id);
            // só o que a própria pessoa subiu nesta conversa e ainda não foi usado
            if (a == null || !a.getEnviadoPor().equals(userId) || !a.getConversaId().equals(conversaId) || a.getMensagemId() != null) {
                throw exception(ANEXO_NAO_EXISTE);
            }
            chatAnexoMapper.updateById(ChatAnexoDO.builder().id(id).mensagemId(mensagemId).build());
        }
    }

    private void avisarConversa(Long conversaId, List<Long> tambem) {
        Set<Long> destinos = membrosIds(conversaId);
        if (tambem != null) {
            destinos.addAll(tambem);
        }
        eventos.publishEvent(new ChatTempoReal.Evento("chat-conversa", Map.of("conversaId", conversaId), destinos));
    }

    private void pessoaAtiva(Long id) {
        AdminUserDO u = adminUserMapper.selectById(id);
        if (u == null || !CommonStatusEnum.isEnable(u.getStatus())) {
            throw exception(GRUPO_INVALIDO, "Pessoa não encontrada ou desativada");
        }
    }

    private String nome(Long id) {
        AdminUserDO u = adminUserMapper.selectById(id);
        return u == null ? "Alguém" : u.getNickname();
    }

    private Map<Long, AdminUserDO> usuarios(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : adminUserMapper.selectByIds(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(AdminUserDO::getId, Function.identity()));
    }

    private List<ChatVOs.ConversaResp> converter(List<ChatConversaDO> cs, List<ChatParticipanteDO> meus, Long userId, boolean comMembros) {
        Map<Long, ChatParticipanteDO> minhas = meus.stream().collect(Collectors.toMap(ChatParticipanteDO::getConversaId, Function.identity()));
        List<Long> ids = cs.stream().map(ChatConversaDO::getId).toList();
        List<ChatParticipanteDO> todos = ids.isEmpty() ? List.of() : chatParticipanteMapper.selectList(new LambdaQueryWrapperX<ChatParticipanteDO>().in(ChatParticipanteDO::getConversaId, ids));
        Map<Long, List<ChatParticipanteDO>> porConversa = todos.stream().collect(Collectors.groupingBy(ChatParticipanteDO::getConversaId));
        // uma consulta só, em vez de uma por conversa: quem tem muitas conversas não paga caro para abrir a lista
        Map<Long, ChatMensagemDO> ultimas = new HashMap<>();
        List<ChatConversaDO> comMensagem = cs.stream().filter(c -> c.getUltimaSeq() > 0).toList();
        if (!comMensagem.isEmpty()) {
            chatMensagemMapper.selectList(new LambdaQueryWrapperX<ChatMensagemDO>().in(ChatMensagemDO::getConversaId, comMensagem.stream().map(ChatConversaDO::getId).toList())
                            .in(ChatMensagemDO::getSeq, comMensagem.stream().map(ChatConversaDO::getUltimaSeq).collect(Collectors.toSet())))
                    .forEach(m -> comMensagem.stream().filter(c -> c.getId().equals(m.getConversaId()) && c.getUltimaSeq().equals(m.getSeq()))
                            .findFirst().ifPresent(c -> ultimas.put(c.getId(), m)));
        }
        Set<Long> pessoas = todos.stream().map(ChatParticipanteDO::getUserId).collect(Collectors.toSet());
        Map<Long, AdminUserDO> us = usuarios(pessoas);
        Set<Long> online = online(pessoas);
        return cs.stream().map(c -> {
            ChatVOs.ConversaResp r = new ChatVOs.ConversaResp();
            ChatParticipanteDO eu = minhas.get(c.getId());
            r.setId(c.getId());
            r.setTipo(c.getTipo());
            r.setNome(c.getNome());
            r.setPapel(eu.getPapel());
            long base = Math.max(eu.getUltimaLidaSeq(), eu.getDesdeSeq());
            r.setNaoLidas(Math.max(0, c.getUltimaSeq() - base));
            r.setUltimaSeq(c.getUltimaSeq());
            r.setUltimaLidaSeq(eu.getUltimaLidaSeq());
            r.setDesdeSeq(eu.getDesdeSeq());
            r.setUltimaMensagemEm(c.getUltimaMensagemEm() != null ? c.getUltimaMensagemEm() : c.getCreateTime());
            List<ChatParticipanteDO> deles = porConversa.getOrDefault(c.getId(), List.of());
            if (ChatConversaDO.DIRETA.equals(c.getTipo())) {
                deles.stream().filter(p -> !p.getUserId().equals(userId)).findFirst().ifPresent(o -> {
                    AdminUserDO u = us.get(o.getUserId());
                    r.setOutroId(o.getUserId());
                    r.setNome(u == null ? "Pessoa removida" : u.getNickname());
                    r.setAvatar(u == null ? null : u.getAvatar());
                    r.setOnline(online.contains(o.getUserId()));
                });
            }
            ChatMensagemDO ult = ultimas.get(c.getId());
            // não mostra na lista a última mensagem de antes de a pessoa entrar
            if (ult != null && ult.getSeq() > eu.getDesdeSeq()) {
                r.setUltimoTexto(ult.getApagadaEm() != null ? "Mensagem apagada" : ult.getConteudo().isEmpty() ? "Anexo" : resumo(ult.getConteudo(), 80));
            }
            if (comMembros) {
                r.setMembros(deles.stream().map(p -> {
                    ChatVOs.Membro m = new ChatVOs.Membro();
                    AdminUserDO u = us.get(p.getUserId());
                    m.setId(p.getUserId());
                    m.setNome(u == null ? "Pessoa removida" : u.getNickname());
                    m.setAvatar(u == null ? null : u.getAvatar());
                    m.setPapel(p.getPapel());
                    m.setOnline(online.contains(p.getUserId()));
                    return m;
                }).sorted(Comparator.comparing(ChatVOs.Membro::getNome)).toList());
            }
            return r;
        }).toList();
    }

    private List<ChatVOs.MensagemResp> converterMensagens(List<ChatMensagemDO> l) {
        if (l.isEmpty()) {
            return List.of();
        }
        List<Long> respostas = l.stream().map(ChatMensagemDO::getRespostaId).filter(Objects::nonNull).toList();
        Map<Long, ChatMensagemDO> citadas = respostas.isEmpty() ? Map.of() : chatMensagemMapper.selectByIds(respostas).stream()
                .collect(Collectors.toMap(ChatMensagemDO::getId, Function.identity()));
        Set<Long> pessoas = new HashSet<>();
        l.forEach(m -> {
            pessoas.add(m.getRemetenteId());
            pessoas.addAll(ChatRegras.separar(m.getMencoes()));
        });
        citadas.values().forEach(m -> pessoas.add(m.getRemetenteId()));
        Map<Long, AdminUserDO> us = usuarios(pessoas);
        Map<Long, List<ChatAnexoDO>> anexos = chatAnexoMapper.selectList(new LambdaQueryWrapperX<ChatAnexoDO>().in(ChatAnexoDO::getMensagemId, l.stream().map(ChatMensagemDO::getId).toList()))
                .stream().collect(Collectors.groupingBy(ChatAnexoDO::getMensagemId));
        return l.stream().map(m -> {
            ChatVOs.MensagemResp r = new ChatVOs.MensagemResp();
            boolean apagada = m.getApagadaEm() != null;
            AdminUserDO u = us.get(m.getRemetenteId());
            r.setId(m.getId());
            r.setConversaId(m.getConversaId());
            r.setSeq(m.getSeq());
            r.setRemetenteId(m.getRemetenteId());
            r.setRemetente(u == null ? "Pessoa removida" : u.getNickname());
            r.setAvatar(u == null ? null : u.getAvatar());
            r.setTipo(m.getTipo());
            r.setApagada(apagada);
            r.setEditada(m.getEditadaEm() != null);
            r.setClienteId(m.getClienteId());
            r.setCriadaEm(m.getCreateTime() != null ? m.getCreateTime() : LocalDateTime.now());
            // o texto apagado nunca sai do servidor
            r.setConteudo(apagada ? null : m.getConteudo());
            r.setMencoes(apagada ? List.of() : ChatRegras.separar(m.getMencoes()).stream().map(id -> {
                ChatVOs.Pessoa p = new ChatVOs.Pessoa();
                AdminUserDO x = us.get(id);
                p.setId(id);
                p.setNome(x == null ? "" : x.getNickname());
                return p;
            }).toList());
            r.setAnexos(apagada ? List.of() : anexos.getOrDefault(m.getId(), List.of()).stream().map(ChatService::anexoResp).toList());
            ChatMensagemDO c = m.getRespostaId() == null || apagada ? null : citadas.get(m.getRespostaId());
            if (c != null) {
                ChatVOs.Referencia ref = new ChatVOs.Referencia();
                AdminUserDO x = us.get(c.getRemetenteId());
                ref.setId(c.getId());
                ref.setRemetente(x == null ? "" : x.getNickname());
                ref.setTrecho(c.getApagadaEm() != null ? "Mensagem apagada" : c.getConteudo().isEmpty() ? "Anexo" : resumo(c.getConteudo(), 120));
                r.setResposta(ref);
            }
            return r;
        }).toList();
    }

    private static ChatVOs.AnexoResp anexoResp(ChatAnexoDO a) {
        ChatVOs.AnexoResp r = new ChatVOs.AnexoResp();
        r.setId(a.getId());
        r.setNome(a.getNome());
        r.setTamanho(a.getTamanho());
        r.setImagem(a.getImagem());
        return r;
    }

    private static String resumo(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }

    private static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

}
