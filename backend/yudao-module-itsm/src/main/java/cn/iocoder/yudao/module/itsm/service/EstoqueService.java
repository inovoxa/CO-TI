package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.EstoqueVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueItemDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueMovimentoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EstoqueTipoDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.ChamadoMapper;
import cn.iocoder.yudao.module.itsm.dal.mysql.EstoqueMappers;
import cn.iocoder.yudao.module.itsm.dominio.Estoque;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants.*;

/**
 * Estoque da GTI. A situação e o saldo só mudam por movimento, e cada movimento fica no histórico; o cadastro corrige
 * os dados do item, sem mexer nisso. Item não se apaga: sai por baixa.
 */
@Service
public class EstoqueService {

    @Resource
    private EstoqueMappers.Tipo tipoMapper;
    @Resource
    private EstoqueMappers.Item itemMapper;
    @Resource
    private EstoqueMappers.Movimento movimentoMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private ChamadoMapper chamadoMapper;

    // ------------------------------------------------------------------ tipos

    public List<EstoqueVOs.Tipo> tipos() {
        return tipoMapper.selectList(new LambdaQueryWrapperX<EstoqueTipoDO>().orderByAsc(EstoqueTipoDO::getNome)).stream().map(t -> {
            EstoqueVOs.Tipo r = new EstoqueVOs.Tipo();
            r.setId(t.getId());
            r.setNome(t.getNome());
            r.setControle(t.getControle());
            r.setAtivo(t.getAtivo());
            return r;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvarTipo(EstoqueVOs.Tipo req) {
        EstoqueTipoDO t = EstoqueTipoDO.builder().id(req.getId()).nome(req.getNome().trim())
                .ativo(!Boolean.FALSE.equals(req.getAtivo())).build();
        try {
            if (req.getId() == null) {
                if (req.getControle() == null) {
                    throw exception(ESTOQUE_INVALIDO, "Escolha como o tipo é controlado");
                }
                t.setControle(req.getControle());
                tipoMapper.insert(t);
            } else {
                obterTipo(req.getId());
                // o controle não muda depois de criado: os itens existentes dependem dele
                tipoMapper.updateById(t);
            }
        } catch (DuplicateKeyException e) {
            throw exception(ESTOQUE_INVALIDO, "Já existe um tipo com esse nome");
        }
        return t.getId();
    }

    // ------------------------------------------------------------------ itens

    public PageResult<EstoqueVOs.ItemResp> pagina(EstoqueVOs.PaginaReq req) {
        PageResult<EstoqueItemDO> p = itemMapper.selectPage(req, filtro(req));
        Map<Long, EstoqueTipoDO> tipos = mapaTipos();
        return new PageResult<>(p.getList().stream().map(i -> converter(i, tipos)).toList(), p.getTotal());
    }

    /** Tudo o que o filtro alcança, para o relatório. */
    public List<EstoqueVOs.ItemResp> relatorio(EstoqueVOs.PaginaReq req) {
        Map<Long, EstoqueTipoDO> tipos = mapaTipos();
        return itemMapper.selectList(filtro(req)).stream().map(i -> converter(i, tipos)).toList();
    }

    public List<Planilha.Aba> planilha(EstoqueVOs.PaginaReq req) {
        List<List<Object>> linhas = relatorio(req).stream().<List<Object>>map(i -> Arrays.asList(i.getTipo(), i.getDescricao(),
                i.getIdentificador(), i.getLinha(), i.getPatrimonio(), "novo".equals(i.getCondicao()) ? "Novo" : "Usado",
                rotuloSituacao(i.getSituacao()), i.getQuantidade(), i.getResponsavel(), i.getDestino(), i.getDataCompra(),
                i.getFornecedor(), i.getNotaFiscal(), i.getValor(), i.getGarantiaAte(), i.getUsadoPor(), i.getObservacao())).toList();
        return List.of(new Planilha.Aba("Estoque", List.of("Tipo", "Descrição", "IMEI/ICCID/Série", "Linha", "Patrimônio", "Condição",
                "Situação", "Quantidade", "Com quem está", "Setor/Unidade", "Compra", "Fornecedor", "Nota fiscal", "Valor (R$)",
                "Garantia até", "Usado por", "Observação"), linhas));
    }

    private static String rotuloSituacao(String s) {
        return switch (s) {
            case Estoque.DISPONIVEL -> "Disponível";
            case Estoque.EM_USO -> "Em uso";
            case Estoque.MANUTENCAO -> "Em manutenção";
            default -> "Baixado";
        };
    }

    private LambdaQueryWrapperX<EstoqueItemDO> filtro(EstoqueVOs.PaginaReq req) {
        LambdaQueryWrapperX<EstoqueItemDO> q = new LambdaQueryWrapperX<EstoqueItemDO>()
                .eqIfPresent(EstoqueItemDO::getTipoId, req.getTipoId())
                .eqIfPresent(EstoqueItemDO::getSituacao, vazioNulo(req.getSituacao()))
                .eqIfPresent(EstoqueItemDO::getCondicao, vazioNulo(req.getCondicao()));
        String busca = vazioNulo(req.getBusca());
        if (busca != null) {
            q.and(w -> w.like(EstoqueItemDO::getDescricao, busca).or().like(EstoqueItemDO::getIdentificador, busca)
                    .or().like(EstoqueItemDO::getLinha, busca).or().like(EstoqueItemDO::getPatrimonio, busca)
                    .or().like(EstoqueItemDO::getResponsavel, busca).or().like(EstoqueItemDO::getNotaFiscal, busca));
        }
        if (Boolean.TRUE.equals(req.getBaixo())) {
            q.gt(EstoqueItemDO::getMinimo, 0).ne(EstoqueItemDO::getSituacao, Estoque.BAIXADO)
                    .apply("quantidade <= minimo");
        }
        if (req.getGarantiaDias() != null) {
            q.between(EstoqueItemDO::getGarantiaAte, LocalDate.now(), LocalDate.now().plusDays(req.getGarantiaDias()))
                    .ne(EstoqueItemDO::getSituacao, Estoque.BAIXADO);
        }
        return q.orderByDesc(EstoqueItemDO::getId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long salvar(EstoqueVOs.ItemSalvar req, Long userId) {
        EstoqueTipoDO tipo = obterTipo(req.getTipoId());
        boolean porUnidade = Estoque.UNIDADE.equals(tipo.getControle());
        String identificador = vazioNulo(req.getIdentificador());
        boolean novo = "novo".equals(req.getCondicao());
        EstoqueItemDO i = EstoqueItemDO.builder().id(req.getId()).tipoId(tipo.getId()).descricao(req.getDescricao().trim())
                .identificador(identificador).linha(vazioNulo(req.getLinha())).patrimonio(vazioNulo(req.getPatrimonio()))
                .condicao(req.getCondicao()).minimo(porUnidade || req.getMinimo() == null ? 0 : req.getMinimo())
                .dataCompra(req.getDataCompra()).fornecedor(vazioNulo(req.getFornecedor())).notaFiscal(vazioNulo(req.getNotaFiscal()))
                .valor(req.getValor()).garantiaAte(req.getGarantiaAte())
                // quem usava só faz sentido em item usado
                .usadoPor(novo ? null : vazioNulo(req.getUsadoPor())).observacao(vazioNulo(req.getObservacao())).build();
        try {
            if (req.getId() != null) {
                EstoqueItemDO atual = obterItem(req.getId());
                if (!atual.getTipoId().equals(tipo.getId())) {
                    throw exception(ESTOQUE_INVALIDO, "O tipo não muda depois do cadastro");
                }
                i.setResponsavel(atual.getResponsavel());
                i.setDestino(atual.getDestino());
                itemMapper.updateById(i);
                return i.getId();
            }
            int saldo = porUnidade ? 1 : Optional.ofNullable(req.getQuantidade()).orElse(0);
            i.setSituacao(Estoque.DISPONIVEL);
            i.setQuantidade(saldo);
            itemMapper.insert(i);
            if (saldo > 0) {
                registrar(i.getId(), Estoque.ENTRADA, saldo, null, null, novo ? "Cadastro: item novo" : "Cadastro: item usado", userId, null);
            }
            return i.getId();
        } catch (DuplicateKeyException e) {
            throw exception(ESTOQUE_INVALIDO, "Já existe um item desse tipo com esse identificador");
        }
    }

    // ------------------------------------------------------------------ movimentos

    @Transactional(rollbackFor = Exception.class)
    public void movimentar(EstoqueVOs.MovimentarReq req, Long userId) {
        EstoqueItemDO i = itemMapper.selectByIdParaAtualizar(req.getItemId());
        if (i == null) {
            throw exception(ESTOQUE_ITEM_NAO_EXISTE);
        }
        if (req.getChamadoId() != null && chamadoMapper.selectById(req.getChamadoId()) == null) {
            throw exception(ESTOQUE_INVALIDO, "Chamado não encontrado");
        }
        String controle = obterTipo(i.getTipoId()).getControle();
        String pessoa = vazioNulo(req.getPessoa());
        String movimento = req.getTipo();
        String erro = Estoque.erro(controle, i.getSituacao(), i.getQuantidade(), movimento, req.getQuantidade(), pessoa, req.getMotivo());
        if (erro != null) {
            throw exception(ESTOQUE_INVALIDO, erro);
        }
        Estoque.Novo novo = Estoque.aplicar(controle, i.getSituacao(), i.getQuantidade(), movimento, req.getQuantidade());
        boolean porUnidade = Estoque.UNIDADE.equals(controle);
        // devolução sem nome: quem devolve é quem estava com o item
        String quem = pessoa == null && porUnidade && Estoque.DEVOLUCAO.equals(movimento) ? i.getResponsavel() : pessoa;
        LambdaUpdateWrapper<EstoqueItemDO> u = new LambdaUpdateWrapper<EstoqueItemDO>().eq(EstoqueItemDO::getId, i.getId())
                .set(EstoqueItemDO::getSituacao, novo.situacao()).set(EstoqueItemDO::getQuantidade, novo.quantidade());
        if (porUnidade) {
            boolean comAlguem = Estoque.EM_USO.equals(novo.situacao());
            u.set(EstoqueItemDO::getResponsavel, comAlguem ? pessoa : null)
                    .set(EstoqueItemDO::getDestino, comAlguem ? vazioNulo(req.getDestino()) : null);
            if (Estoque.DEVOLUCAO.equals(movimento)) {
                // voltou de alguém: daqui em diante é usado, e fica registrado por quem passou
                u.set(EstoqueItemDO::getCondicao, "usado").set(EstoqueItemDO::getUsadoPor, quem);
            }
        }
        itemMapper.update(null, u);
        registrar(i.getId(), movimento, req.getQuantidade(), quem, vazioNulo(req.getDestino()), vazioNulo(req.getMotivo()), userId,
                req.getChamadoId());
    }

    /** Movimentos ligados a um chamado. */
    public List<EstoqueVOs.MovimentoResp> doChamado(Long chamadoId) {
        return converterMovimentos(movimentoMapper.selectList(new LambdaQueryWrapperX<EstoqueMovimentoDO>()
                .eq(EstoqueMovimentoDO::getChamadoId, chamadoId)
                .orderByDesc(EstoqueMovimentoDO::getQuando).orderByDesc(EstoqueMovimentoDO::getId)));
    }

    public List<EstoqueVOs.MovimentoResp> historico(Long itemId) {
        obterItem(itemId);
        return converterMovimentos(movimentoMapper.selectList(new LambdaQueryWrapperX<EstoqueMovimentoDO>()
                .eq(EstoqueMovimentoDO::getItemId, itemId)
                .orderByDesc(EstoqueMovimentoDO::getQuando).orderByDesc(EstoqueMovimentoDO::getId)));
    }

    private List<EstoqueVOs.MovimentoResp> converterMovimentos(List<EstoqueMovimentoDO> l) {
        Map<Long, EstoqueItemDO> itens = l.isEmpty() ? Map.of() : itemMapper.selectByIds(l.stream().map(EstoqueMovimentoDO::getItemId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(EstoqueItemDO::getId, Function.identity()));
        Map<Long, EstoqueTipoDO> tipos = mapaTipos();
        Map<Long, AdminUserRespDTO> pessoas = adminUserApi.getUserMap(l.stream().map(EstoqueMovimentoDO::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return l.stream().map(m -> {
            EstoqueVOs.MovimentoResp r = new EstoqueVOs.MovimentoResp();
            r.setId(m.getId());
            r.setItemId(m.getItemId());
            r.setChamadoId(m.getChamadoId());
            EstoqueItemDO i = itens.get(m.getItemId());
            if (i != null) {
                r.setItem(i.getDescricao());
                r.setIdentificador(i.getIdentificador());
                r.setLinha(i.getLinha());
                r.setPatrimonio(i.getPatrimonio());
                EstoqueTipoDO t = tipos.get(i.getTipoId());
                r.setTipoItem(t == null ? null : t.getNome());
            }
            r.setTipo(m.getTipo());
            r.setQuantidade(m.getQuantidade());
            r.setPessoa(m.getPessoa());
            r.setDestino(m.getDestino());
            r.setMotivo(m.getMotivo());
            AdminUserRespDTO u = pessoas.get(m.getUserId());
            r.setRegistradoPor(u == null ? null : u.getNickname());
            r.setQuando(m.getQuando());
            return r;
        }).toList();
    }

    /** Saldo por tipo, sem itens baixados. */
    public List<EstoqueVOs.Resumo> resumo() {
        Map<Long, EstoqueVOs.Resumo> porTipo = new LinkedHashMap<>();
        for (EstoqueTipoDO t : tipoMapper.selectList(new LambdaQueryWrapperX<EstoqueTipoDO>().orderByAsc(EstoqueTipoDO::getNome))) {
            EstoqueVOs.Resumo r = new EstoqueVOs.Resumo();
            r.setTipoId(t.getId());
            r.setTipo(t.getNome());
            porTipo.put(t.getId(), r);
        }
        for (EstoqueItemDO i : itemMapper.selectList(new LambdaQueryWrapperX<EstoqueItemDO>().ne(EstoqueItemDO::getSituacao, Estoque.BAIXADO))) {
            EstoqueVOs.Resumo r = porTipo.get(i.getTipoId());
            if (r == null) {
                continue;
            }
            switch (i.getSituacao()) {
                case Estoque.DISPONIVEL -> r.setDisponivel(r.getDisponivel() + i.getQuantidade());
                case Estoque.EM_USO -> r.setEmUso(r.getEmUso() + 1);
                case Estoque.MANUTENCAO -> r.setManutencao(r.getManutencao() + 1);
                default -> {
                }
            }
            if (baixo(i)) {
                r.setBaixos(r.getBaixos() + 1);
            }
        }
        return new ArrayList<>(porTipo.values());
    }

    // ------------------------------------------------------------------ internos

    private void registrar(Long itemId, String tipo, int quantidade, String pessoa, String destino, String motivo, Long userId,
                           Long chamadoId) {
        movimentoMapper.insert(EstoqueMovimentoDO.builder().itemId(itemId).tipo(tipo).quantidade(quantidade).pessoa(pessoa)
                .destino(destino).motivo(motivo).chamadoId(chamadoId).userId(userId).quando(LocalDateTime.now()).build());
    }

    private EstoqueTipoDO obterTipo(Long id) {
        EstoqueTipoDO t = tipoMapper.selectById(id);
        if (t == null) {
            throw exception(ESTOQUE_INVALIDO, "Tipo não encontrado");
        }
        return t;
    }

    private EstoqueItemDO obterItem(Long id) {
        EstoqueItemDO i = itemMapper.selectById(id);
        if (i == null) {
            throw exception(ESTOQUE_ITEM_NAO_EXISTE);
        }
        return i;
    }

    private Map<Long, EstoqueTipoDO> mapaTipos() {
        return tipoMapper.selectList().stream().collect(Collectors.toMap(EstoqueTipoDO::getId, Function.identity()));
    }

    private static boolean baixo(EstoqueItemDO i) {
        return i.getMinimo() > 0 && !Estoque.BAIXADO.equals(i.getSituacao()) && i.getQuantidade() <= i.getMinimo();
    }

    private static EstoqueVOs.ItemResp converter(EstoqueItemDO i, Map<Long, EstoqueTipoDO> tipos) {
        EstoqueVOs.ItemResp r = new EstoqueVOs.ItemResp();
        r.setId(i.getId());
        r.setTipoId(i.getTipoId());
        EstoqueTipoDO t = tipos.get(i.getTipoId());
        r.setTipo(t == null ? null : t.getNome());
        r.setControle(t == null ? null : t.getControle());
        r.setDescricao(i.getDescricao());
        r.setIdentificador(i.getIdentificador());
        r.setLinha(i.getLinha());
        r.setPatrimonio(i.getPatrimonio());
        r.setCondicao(i.getCondicao());
        r.setSituacao(i.getSituacao());
        r.setQuantidade(i.getQuantidade());
        r.setMinimo(i.getMinimo());
        r.setBaixo(baixo(i));
        r.setDataCompra(i.getDataCompra());
        r.setFornecedor(i.getFornecedor());
        r.setNotaFiscal(i.getNotaFiscal());
        r.setValor(i.getValor());
        r.setGarantiaAte(i.getGarantiaAte());
        r.setUsadoPor(i.getUsadoPor());
        r.setResponsavel(i.getResponsavel());
        r.setDestino(i.getDestino());
        r.setObservacao(i.getObservacao());
        return r;
    }

    private static String vazioNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
