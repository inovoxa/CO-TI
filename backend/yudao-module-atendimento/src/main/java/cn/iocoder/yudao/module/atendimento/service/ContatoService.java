package cn.iocoder.yudao.module.atendimento.service;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoCanalDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ContatoDO;
import cn.iocoder.yudao.module.atendimento.dal.dataobject.ConversaDO;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ContatoCanalMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ContatoMapper;
import cn.iocoder.yudao.module.atendimento.dal.mysql.ConversaMapper;
import cn.iocoder.yudao.module.atendimento.dominio.Telefone;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.atendimento.enums.ErrorCodeConstants.*;

/**
 * Contatos (Chatwoot: Contact e ContactInbox). Um contato pode estar em várias caixas (o mesmo número no WhatsApp e
 * no Telegram) e pode ser uma pessoa da empresa: aí fica ligado ao usuário do portal, e os chamados dele aparecem
 * na conversa.
 */
@Service
public class ContatoService {

    /** O que o canal sabe de quem escreveu. */
    public record Origem(String nome, String telefone, String email, String identificador) {
    }

    @Resource
    private ContatoMapper contatoMapper;
    @Resource
    private ContatoCanalMapper contatoCanalMapper;
    @Resource
    private ConversaMapper conversaMapper;
    @Resource
    private AdminUserMapper adminUserMapper;
    @Resource
    private AdminUserApi adminUserApi;

    /**
     * Acha (ou cria) o contato e o endereço dele na caixa. Ordem: o endereço na caixa; depois um contato com o mesmo
     * identificador, telefone (com ou sem o nono dígito) ou e-mail; senão, um contato novo, ligado à pessoa do
     * portal com o mesmo e-mail ou celular.
     */
    public ContatoCanalDO encontrarOuCriar(Long caixaId, String origemId, Origem o) {
        ContatoCanalDO cc = contatoCanalMapper.selectOne(new LambdaQueryWrapperX<ContatoCanalDO>()
                .eq(ContatoCanalDO::getCaixaId, caixaId).eq(ContatoCanalDO::getOrigemId, origemId));
        String telefone = Telefone.normalizar(o.telefone());
        String email = o.email() == null || o.email().isBlank() ? null : o.email().trim().toLowerCase(Locale.ROOT);
        if (cc != null) {
            completar(contatoMapper.selectById(cc.getContatoId()), o.nome(), telefone, email);
            return cc;
        }
        ContatoDO c = existente(o.identificador(), telefone, email);
        if (c == null) {
            c = ContatoDO.builder().nome(nomeOuPadrao(o.nome(), telefone, email, origemId)).telefone(telefone).email(email)
                    .identificador(vazio(o.identificador())).userId(pessoaDoPortal(email, telefone)).atributos("{}")
                    .bloqueado(false).ultimaAtividadeEm(LocalDateTime.now()).build();
            contatoMapper.insert(c);
        } else {
            completar(c, o.nome(), telefone, email);
        }
        cc = ContatoCanalDO.builder().contatoId(c.getId()).caixaId(caixaId).origemId(origemId).build();
        contatoCanalMapper.insert(cc);
        return cc;
    }

    private ContatoDO existente(String identificador, String telefone, String email) {
        if (vazio(identificador) != null) {
            ContatoDO c = contatoMapper.selectOne(ContatoDO::getIdentificador, identificador.trim());
            if (c != null) {
                return c;
            }
        }
        if (telefone != null) {
            ContatoDO c = contatoMapper.selectOne(new LambdaQueryWrapperX<ContatoDO>().in(ContatoDO::getTelefone, Telefone.variantes(telefone))
                    .last("LIMIT 1"));
            if (c != null) {
                return c;
            }
        }
        return email == null ? null : contatoMapper.selectOne(new LambdaQueryWrapperX<ContatoDO>().apply("lower(email) = {0}", email).last("LIMIT 1"));
    }

    /** Preenche o que faltava (o primeiro contato pelo WhatsApp não traz e-mail; um formulário depois traz). */
    private void completar(ContatoDO c, String nome, String telefone, String email) {
        ContatoDO u = ContatoDO.builder().id(c.getId()).ultimaAtividadeEm(LocalDateTime.now()).build();
        if (telefone != null && c.getTelefone() == null && contatoMapper.selectCount(ContatoDO::getTelefone, telefone) == 0) {
            u.setTelefone(telefone);
        }
        if (email != null && c.getEmail() == null
                && contatoMapper.selectCount(new LambdaQueryWrapperX<ContatoDO>().apply("lower(email) = {0}", email)) == 0) {
            u.setEmail(email);
        }
        // nome provisório (o número) troca pelo nome que o canal informou
        if (vazio(nome) != null && (c.getNome().equals(c.getTelefone()) || c.getNome().startsWith("Contato "))) {
            u.setNome(cortar(nome.trim(), 150));
        }
        if (c.getUserId() == null) {
            u.setUserId(pessoaDoPortal(u.getEmail() != null ? u.getEmail() : c.getEmail(), u.getTelefone() != null ? u.getTelefone() : c.getTelefone()));
        }
        contatoMapper.updateById(u);
    }

    /** Pessoa ativa do portal com o mesmo e-mail ou celular. */
    private Long pessoaDoPortal(String email, String telefone) {
        if (email != null) {
            AdminUserDO u = adminUserMapper.selectOne(new LambdaQueryWrapperX<AdminUserDO>().apply("lower(email) = {0}", email)
                    .eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus()).last("LIMIT 1"));
            if (u != null) {
                return u.getId();
            }
        }
        if (telefone != null) {
            for (AdminUserDO u : adminUserMapper.selectList(new LambdaQueryWrapperX<AdminUserDO>().isNotNull(AdminUserDO::getMobile)
                    .ne(AdminUserDO::getMobile, "").eq(AdminUserDO::getStatus, CommonStatusEnum.ENABLE.getStatus())
                    .apply("right(regexp_replace(mobile, '\\D', '', 'g'), 8) = {0}", telefone.substring(telefone.length() - 8)))) {
                if (Telefone.variantes(telefone).contains(Telefone.normalizar(u.getMobile()))) {
                    return u.getId();
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tela

    public PageResult<AtendimentoVOs.Contato> pagina(AtendimentoVOs.FiltroContato f) {
        LambdaQueryWrapperX<ContatoDO> q = new LambdaQueryWrapperX<>();
        if (vazio(f.getBusca()) != null) {
            String b = f.getBusca().trim();
            String like = "%" + b.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            String tel = Telefone.normalizar(b);
            q.and(w -> {
                w.apply("nome ILIKE {0}", like).or().apply("email ILIKE {0}", like).or().apply("telefone LIKE {0}", like);
                if (tel != null) {
                    w.or().in(ContatoDO::getTelefone, Telefone.variantes(tel));
                }
            });
        }
        q.orderByDesc(ContatoDO::getUltimaAtividadeEm).orderByDesc(ContatoDO::getId);
        PageResult<ContatoDO> p = contatoMapper.selectPage(f, q);
        return new PageResult<>(converter(p.getList()), p.getTotal());
    }

    public AtendimentoVOs.Contato obter(Long id) {
        return converter(List.of(existente(id))).get(0);
    }

    public ContatoDO existente(Long id) {
        ContatoDO c = id == null ? null : contatoMapper.selectById(id);
        if (c == null) {
            throw exception(CONTATO_NAO_EXISTE);
        }
        return c;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(AtendimentoVOs.Contato req) {
        if (req.getId() != null) {
            existente(req.getId());
        }
        String telefone = null;
        if (vazio(req.getTelefone()) != null) {
            telefone = Telefone.normalizar(req.getTelefone());
            if (telefone == null) {
                throw exception(CONTATO_TELEFONE_INVALIDO);
            }
            ContatoDO mesmo = contatoMapper.selectOne(new LambdaQueryWrapperX<ContatoDO>().in(ContatoDO::getTelefone, Telefone.variantes(telefone)).last("LIMIT 1"));
            if (mesmo != null && !mesmo.getId().equals(req.getId())) {
                throw exception(CONTATO_DUPLICADO, "telefone");
            }
        }
        String email = vazio(req.getEmail()) == null ? null : req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email != null) {
            ContatoDO mesmo = contatoMapper.selectOne(new LambdaQueryWrapperX<ContatoDO>().apply("lower(email) = {0}", email).last("LIMIT 1"));
            if (mesmo != null && !mesmo.getId().equals(req.getId())) {
                throw exception(CONTATO_DUPLICADO, "e-mail");
            }
        }
        if (req.getUserId() != null) {
            adminUserApi.validateUser(req.getUserId());
        }
        ContatoDO c = ContatoDO.builder().id(req.getId()).nome(req.getNome().trim()).telefone(telefone).email(email)
                .identificador(vazio(req.getIdentificador())).userId(req.getUserId())
                .atributos(JsonUtils.toJsonString(req.getAtributos() == null ? Map.of() : req.getAtributos()))
                .observacao(vazio(req.getObservacao())).bloqueado(Boolean.TRUE.equals(req.getBloqueado())).build();
        if (req.getId() == null) {
            c.setUltimaAtividadeEm(LocalDateTime.now());
            contatoMapper.insert(c);
        } else {
            contatoMapper.updateById(c);
            contatoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ContatoDO>()
                    .eq(ContatoDO::getId, c.getId()).set(ContatoDO::getTelefone, telefone).set(ContatoDO::getEmail, email)
                    .set(ContatoDO::getIdentificador, c.getIdentificador()).set(ContatoDO::getUserId, c.getUserId())
                    .set(ContatoDO::getObservacao, c.getObservacao()));
        }
        return c.getId();
    }

    public List<AtendimentoVOs.Contato> converter(List<ContatoDO> lista) {
        Set<Long> pessoas = lista.stream().map(ContatoDO::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> nomes = adminUserApi.getUserMap(pessoas);
        Set<Long> ids = lista.stream().map(ContatoDO::getId).collect(Collectors.toSet());
        Map<Long, Long> conversas = ids.isEmpty() ? Map.of() : conversaMapper.selectList(new LambdaQueryWrapperX<ConversaDO>()
                .in(ConversaDO::getContatoId, ids)).stream().collect(Collectors.groupingBy(ConversaDO::getContatoId, Collectors.counting()));
        return lista.stream().map(c -> {
            AtendimentoVOs.Contato v = new AtendimentoVOs.Contato();
            v.setId(c.getId());
            v.setNome(c.getNome());
            v.setTelefone(c.getTelefone());
            v.setEmail(c.getEmail());
            v.setIdentificador(c.getIdentificador());
            v.setUserId(c.getUserId());
            v.setUsuario(c.getUserId() == null ? null : Optional.ofNullable(nomes.get(c.getUserId())).map(AdminUserRespDTO::getNickname).orElse("#" + c.getUserId()));
            v.setAtributos(JsonUtils.parseObject(c.getAtributos(), new TypeReference<LinkedHashMap<String, Object>>() {
            }));
            v.setObservacao(c.getObservacao());
            v.setBloqueado(c.getBloqueado());
            v.setUltimaAtividadeEm(c.getUltimaAtividadeEm());
            v.setConversas(conversas.getOrDefault(c.getId(), 0L).intValue());
            return v;
        }).toList();
    }

    private static String nomeOuPadrao(String nome, String telefone, String email, String origemId) {
        if (vazio(nome) != null) {
            return cortar(nome.trim(), 150);
        }
        return telefone != null ? telefone : email != null ? email : cortar("Contato " + origemId, 150);
    }

    static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

}
