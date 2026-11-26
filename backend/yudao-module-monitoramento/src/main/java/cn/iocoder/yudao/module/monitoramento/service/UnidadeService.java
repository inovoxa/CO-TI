package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.excel.core.util.Planilha;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.config.MonitoramentoProperties;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.identidade.service.AuditoriaService;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.ManutencaoDO;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonManutencaoMapper;
import cn.iocoder.yudao.module.monitoramento.dal.mysql.MonMonitorMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.monitor.MonitorUnitDO;
import cn.iocoder.yudao.module.system.dal.mysql.monitor.MonitorUnitMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.monitoramento.enums.ErrorCodeConstants.*;

/**
 * Unidades (filiais) do mapa do telão. O Kuma não tem localização: o endereço é preenchido à mão — o CEP traz rua,
 * bairro e cidade (ViaCEP), o número completa e as coordenadas vêm do OpenStreetMap (Nominatim), com ajuste no mapa.
 * Em rede sem internet, as buscas ficam desligadas e as coordenadas são marcadas no mapa ou digitadas.
 */
@Service
@Slf4j
public class UnidadeService {

    @Resource
    private cn.iocoder.yudao.module.inventario.service.UnidadeRedeService unidadeRedeService;
    @Resource
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private MonManutencaoMapper monManutencaoMapper;
    @Resource
    private TelefoneMapper telefoneMapper;
    @Resource
    private DeptApi deptApi;
    @Resource
    private AuditoriaService auditoriaService;
    @Resource
    private MonitoramentoProperties monitoramentoProperties;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** O Nominatim pede no máximo 1 consulta por segundo */
    private long ultimaGeocodificacao;

    public List<Planilha.Aba> planilha() {
        Map<Long, List<String>> faixas = unidadeRedeService.porDept();
        List<List<Object>> linhas = listar().stream().<List<Object>>map(u -> Arrays.asList(u.getNome(), u.getDeptNome(), u.getDeptSuperior(), u.getCep(),
                u.getLogradouro(), u.getNumero(), u.getComplemento(), u.getBairro(), u.getCidade(), u.getUf(), u.getLatitude(),
                u.getLongitude(), Planilha.simNao(u.getAtiva()), String.join(", ", faixas.getOrDefault(u.getDeptId(), List.of())),
                u.getMonitores())).toList();
        return List.of(new Planilha.Aba("Unidades", List.of("Unidade", "Departamento", "Departamento superior", "CEP", "Logradouro", "Número", "Complemento",
                "Bairro", "Cidade", "UF", "Latitude", "Longitude", "Ativa", "Faixas de IP", "Monitores"), linhas));
    }

    public List<UnidadeVOs.Unidade> listar() {
        List<MonitorUnitDO> lista = monitorUnitMapper.selectList(new LambdaQueryWrapperX<MonitorUnitDO>().orderByAsc(MonitorUnitDO::getName));
        Set<Long> depts = lista.stream().map(MonitorUnitDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<DeptRespDTO> dos = depts.isEmpty() ? List.of() : deptApi.getDeptList(depts);
        Map<Long, String> nomes = dos.stream().collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName));
        Set<Long> pais = dos.stream().map(DeptRespDTO::getParentId).filter(p -> p != null && p != 0L).collect(Collectors.toSet());
        // pai na raiz fica fora: a raiz tem outro nome em cada instalação e a importação já usa a raiz quando vem em branco
        Map<Long, String> superiores = pais.isEmpty() ? Map.of() : deptApi.getDeptList(pais).stream()
                .filter(p -> p.getParentId() != null && p.getParentId() != 0L)
                .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName));
        Map<Long, Long> paiDe = new HashMap<>();
        dos.forEach(d -> paiDe.put(d.getId(), d.getParentId()));
        Map<Long, Long> monitores = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().isNotNull(MonitorDO::getUnidadeId)).stream()
                .collect(Collectors.groupingBy(MonitorDO::getUnidadeId, Collectors.counting()));
        Map<Long, Long> manutencoes = monManutencaoMapper.selectList(new LambdaQueryWrapperX<ManutencaoDO>().isNotNull(ManutencaoDO::getUnidadeId))
                .stream().collect(Collectors.groupingBy(ManutencaoDO::getUnidadeId, Collectors.counting()));
        Map<Long, Long> telefones = telefoneMapper.selectList(new LambdaQueryWrapperX<TelefoneDO>().isNotNull(TelefoneDO::getUnidadeId)).stream()
                .collect(Collectors.groupingBy(TelefoneDO::getUnidadeId, Collectors.counting()));
        return lista.stream().map(u -> {
            UnidadeVOs.Unidade vo = new UnidadeVOs.Unidade();
            vo.setId(u.getId());
            vo.setNome(u.getName());
            vo.setDeptId(u.getDeptId());
            vo.setDeptNome(nomes.get(u.getDeptId()));
            Long pai = paiDe.get(u.getDeptId());
            vo.setDeptSuperior(pai == null ? null : superiores.get(pai));
            vo.setCep(u.getCep());
            vo.setLogradouro(u.getLogradouro());
            vo.setNumero(u.getNumero());
            vo.setComplemento(u.getComplemento());
            vo.setBairro(u.getBairro());
            vo.setCidade(u.getCity());
            vo.setUf(u.getUf());
            vo.setLatitude(u.getLatitude());
            vo.setLongitude(u.getLongitude());
            vo.setAtiva(u.getStatus() == null || u.getStatus() == 0);
            vo.setMonitores(monitores.getOrDefault(u.getId(), 0L).intValue());
            vo.setManutencoes(manutencoes.getOrDefault(u.getId(), 0L).intValue());
            vo.setTelefones(telefones.getOrDefault(u.getId(), 0L).intValue());
            return vo;
        }).toList();
    }

    public Long salvar(UnidadeVOs.Unidade req) {
        MonitorUnitDO u = previsto(req);
        MonitorUnitDO atual = u.getId() == null ? null : monitorUnitMapper.selectById(u.getId());
        if (u.getId() != null && atual == null) {
            throw exception(UNIDADE_NAO_EXISTE);
        }
        // a repetida que já existia continua podendo ser editada sem trocar o nome: o caminho para ela é Juntar
        String chave = chaveNome(u.getName());
        if (atual == null || !chave.equals(chaveNome(atual.getName()))) {
            monitorUnitMapper.selectList().stream()
                    .filter(o -> !o.getId().equals(u.getId()) && chave.equals(chaveNome(o.getName()))).findFirst()
                    .ifPresent(o -> {
                        throw exception(UNIDADE_NOME_REPETIDO, o.getName());
                    });
        }
        if (u.getId() == null) {
            monitorUnitMapper.insert(u);
        } else {
            monitorUnitMapper.updateById(u);
            monitorUnitMapper.update(null, new LambdaUpdateWrapper<MonitorUnitDO>().eq(MonitorUnitDO::getId, u.getId())
                    .set(MonitorUnitDO::getCep, u.getCep()).set(MonitorUnitDO::getLogradouro, u.getLogradouro())
                    .set(MonitorUnitDO::getNumero, u.getNumero()).set(MonitorUnitDO::getComplemento, u.getComplemento())
                    .set(MonitorUnitDO::getBairro, u.getBairro()).set(MonitorUnitDO::getCity, u.getCity()).set(MonitorUnitDO::getUf, u.getUf()));
        }
        return u.getId();
    }

    /** O que a prévia mostrou entra inteiro ou não entra. */
    @Transactional(rollbackFor = Exception.class)
    public void aplicarImportacao(List<UnidadeVOs.Unidade> gravar, Collection<Long> remover) {
        gravar.forEach(this::salvar);
        remover.forEach(this::excluir);
    }

    /** Como a unidade ficaria gravada, pelas regras do cadastro, sem gravar nada. */
    MonitorUnitDO previsto(UnidadeVOs.Unidade req) {
        return previsto(req, false);
    }

    /** @param deptACriar o departamento só passa a existir ao aplicar a importação; não confere agora */
    MonitorUnitDO previsto(UnidadeVOs.Unidade req, boolean deptACriar) {
        if (!deptACriar && deptApi.getDept(req.getDeptId()) == null) {
            throw exception(UNIDADE_INVALIDA, "departamento não encontrado");
        }
        String cep = cep(req.getCep());
        if (req.getCep() != null && !req.getCep().isBlank() && cep == null) {
            throw exception(UNIDADE_INVALIDA, "CEP deve ter 8 dígitos");
        }
        return MonitorUnitDO.builder().id(req.getId()).name(req.getNome().trim()).deptId(req.getDeptId())
                .cep(cep == null ? null : cep.substring(0, 5) + "-" + cep.substring(5)).logradouro(vazio(req.getLogradouro()))
                .numero(vazio(req.getNumero())).complemento(vazio(req.getComplemento())).bairro(vazio(req.getBairro()))
                .city(vazio(req.getCidade())).uf(req.getUf() == null || req.getUf().isBlank() ? null : req.getUf().trim().toUpperCase(Locale.ROOT))
                .latitude(req.getLatitude().setScale(7, RoundingMode.HALF_UP)).longitude(req.getLongitude().setScale(7, RoundingMode.HALF_UP))
                .status(req.getAtiva() == null || req.getAtiva() ? 0 : 1).build();
    }

    /**
     * Exclui a unidade. Os monitores dela ficam sem unidade (e deixam de ser link principal), as manutenções passam a
     * valer só para os monitores escolhidos nelas e os telefones ficam sem unidade (e deixam de ser de emergência, que
     * exige unidade).
     */
    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        if (monitorUnitMapper.selectById(id) == null) {
            throw exception(UNIDADE_NAO_EXISTE);
        }
        monitorUnitMapper.deleteById(id);
        monMonitorMapper.update(null, new LambdaUpdateWrapper<MonitorDO>().eq(MonitorDO::getUnidadeId, id).set(MonitorDO::getUnidadeId, null)
                .set(MonitorDO::getPapel, "servico"));
        monManutencaoMapper.update(null, new LambdaUpdateWrapper<ManutencaoDO>().eq(ManutencaoDO::getUnidadeId, id)
                .set(ManutencaoDO::getUnidadeId, null));
        telefoneMapper.update(null, new LambdaUpdateWrapper<TelefoneDO>().eq(TelefoneDO::getUnidadeId, id)
                .set(TelefoneDO::getUnidadeId, null).set(TelefoneDO::getEmergencia, false));
    }

    /**
     * Junta a unidade repetida ({@code origemId}) na que fica ({@code destinoId}): monitores, manutenções e telefones
     * passam para a que fica e a repetida é excluída. Nome, endereço e ponto no mapa da que fica não mudam.
     */
    @Transactional(rollbackFor = Exception.class)
    public UnidadeVOs.JuntarResp juntar(Long origemId, Long destinoId, Long userId) {
        if (Objects.equals(origemId, destinoId)) {
            throw exception(UNIDADE_INVALIDA, "escolha outra unidade para juntar");
        }
        MonitorUnitDO origem = monitorUnitMapper.selectById(origemId);
        MonitorUnitDO destino = monitorUnitMapper.selectById(destinoId);
        if (origem == null || destino == null) {
            throw exception(UNIDADE_NAO_EXISTE);
        }
        UnidadeVOs.JuntarResp r = new UnidadeVOs.JuntarResp();
        r.setMonitores(monMonitorMapper.update(null, new LambdaUpdateWrapper<MonitorDO>().eq(MonitorDO::getUnidadeId, origemId)
                .set(MonitorDO::getUnidadeId, destinoId)));
        r.setManutencoes(monManutencaoMapper.update(null, new LambdaUpdateWrapper<ManutencaoDO>().eq(ManutencaoDO::getUnidadeId, origemId)
                .set(ManutencaoDO::getUnidadeId, destinoId)));
        r.setTelefones(telefoneMapper.update(null, new LambdaUpdateWrapper<TelefoneDO>().eq(TelefoneDO::getUnidadeId, origemId)
                .set(TelefoneDO::getUnidadeId, destinoId)));
        monitorUnitMapper.deleteById(origemId);
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("excluida", origem.getName() + " (#" + origemId + ")");
        detalhes.put("mantida", destino.getName() + " (#" + destinoId + ")");
        detalhes.put("monitores", r.getMonitores());
        detalhes.put("manutencoes", r.getManutencoes());
        detalhes.put("telefones", r.getTelefones());
        auditoriaService.registrar("monitoramento.juntar_unidades", userId, null, detalhes);
        return r;
    }

    /** O nome como o cadastro compara: sem acento e sem diferença de maiúsculas ou de espaços. */
    static String chaveNome(String nome) {
        if (nome == null) {
            return "";
        }
        return Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // ================================================================================== CEP e mapa

    public UnidadeVOs.Endereco buscarCep(String cepInformado) {
        String cep = cep(cepInformado);
        if (cep == null) {
            throw exception(UNIDADE_INVALIDA, "CEP deve ter 8 dígitos");
        }
        String api = monitoramentoProperties.getCepApi();
        if (api == null || api.isBlank()) {
            throw exception(ENDERECO_INDISPONIVEL, "a busca de CEP está desligada nesta instalação; preencha o endereço à mão");
        }
        Map<String, Object> r = get(api.replaceAll("/+$", "") + "/" + cep + "/json/");
        if (r == null || Boolean.TRUE.equals(r.get("erro")) || "true".equals(String.valueOf(r.get("erro")))) {
            throw exception(CEP_NAO_ENCONTRADO);
        }
        UnidadeVOs.Endereco e = new UnidadeVOs.Endereco();
        e.setCep(cep.substring(0, 5) + "-" + cep.substring(5));
        e.setLogradouro(texto(r.get("logradouro")));
        e.setBairro(texto(r.get("bairro")));
        e.setCidade(texto(r.get("localidade")));
        e.setUf(texto(r.get("uf")));
        return e;
    }

    /**
     * Coordenadas do endereço. Tenta com o número; se não achar, a rua; depois o CEP; por fim a cidade (a precisão
     * volta junto, para a tela pedir o ajuste no mapa).
     */
    public UnidadeVOs.Coordenadas geocodificar(UnidadeVOs.GeocodificarReq req) {
        String api = monitoramentoProperties.getGeocodificacaoApi();
        if (api == null || api.isBlank()) {
            throw exception(ENDERECO_INDISPONIVEL, "o cálculo de coordenadas está desligado nesta instalação; marque o ponto no mapa");
        }
        String rua = vazio(req.getLogradouro());
        String numero = vazio(req.getNumero());
        String cidade = vazio(req.getCidade());
        String uf = vazio(req.getUf());
        String cep = cep(req.getCep());
        List<Map.Entry<String, Map<String, String>>> tentativas = new ArrayList<>();
        if (rua != null && numero != null && cidade != null) {
            tentativas.add(Map.entry("endereco", consulta(numero + " " + rua, cidade, uf, null)));
        }
        if (rua != null && cidade != null) {
            tentativas.add(Map.entry("rua", consulta(rua, cidade, uf, null)));
        }
        if (cep != null) {
            tentativas.add(Map.entry("cep", consulta(null, null, null, cep.substring(0, 5) + "-" + cep.substring(5))));
        }
        if (cidade != null) {
            tentativas.add(Map.entry("cidade", consulta(null, cidade, uf, null)));
        }
        if (tentativas.isEmpty()) {
            throw exception(UNIDADE_INVALIDA, "informe pelo menos a cidade ou o CEP");
        }
        for (Map.Entry<String, Map<String, String>> t : tentativas) {
            List<Map<String, Object>> achados = geocodificar(api, t.getValue());
            if (achados != null && !achados.isEmpty()) {
                Map<String, Object> a = achados.get(0);
                UnidadeVOs.Coordenadas c = new UnidadeVOs.Coordenadas();
                c.setLatitude(new BigDecimal(String.valueOf(a.get("lat"))).setScale(7, RoundingMode.HALF_UP));
                c.setLongitude(new BigDecimal(String.valueOf(a.get("lon"))).setScale(7, RoundingMode.HALF_UP));
                c.setPrecisao(t.getKey());
                c.setDescricao(texto(a.get("display_name")));
                return c;
            }
        }
        throw exception(ENDERECO_INDISPONIVEL, "endereço não encontrado no mapa; marque o ponto no mapa");
    }

    private static Map<String, String> consulta(String rua, String cidade, String uf, String cep) {
        Map<String, String> q = new LinkedHashMap<>();
        if (rua != null) {
            q.put("street", rua);
        }
        if (cidade != null) {
            q.put("city", cidade);
        }
        if (uf != null) {
            q.put("state", uf);
        }
        if (cep != null) {
            q.put("postalcode", cep);
        }
        q.put("country", "Brasil");
        q.put("format", "jsonv2");
        q.put("limit", "1");
        return q;
    }

    private synchronized List<Map<String, Object>> geocodificar(String api, Map<String, String> q) {
        long espera = 1100 - (System.currentTimeMillis() - ultimaGeocodificacao);
        if (espera > 0) {
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        ultimaGeocodificacao = System.currentTimeMillis();
        String url = api + (api.contains("?") ? "&" : "?") + q.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        String contato = monitoramentoProperties.getGeocodificacaoContato();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "CO-TI/1.0" + (contato == null || contato.isBlank() ? "" : " (" + contato + ")"))
                    .header("Accept-Language", "pt-BR").GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw exception(ENDERECO_INDISPONIVEL, "o serviço de mapas respondeu HTTP " + r.statusCode());
            }
            return JsonUtils.parseObject(r.body(), new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (java.io.IOException e) {
            throw exception(ENDERECO_INDISPONIVEL, "sem acesso ao serviço de mapas (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(ENDERECO_INDISPONIVEL, "interrompido");
        }
    }

    private Map<String, Object> get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent", "CO-TI/1.0").GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 400 || r.statusCode() == 404) {
                return null;
            }
            if (r.statusCode() != 200) {
                throw exception(ENDERECO_INDISPONIVEL, "o serviço de CEP respondeu HTTP " + r.statusCode());
            }
            return JsonUtils.parseObject(r.body(), new TypeReference<Map<String, Object>>() {
            });
        } catch (java.io.IOException e) {
            throw exception(ENDERECO_INDISPONIVEL, "sem acesso ao serviço de CEP (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(ENDERECO_INDISPONIVEL, "interrompido");
        }
    }

    /** Só os 8 dígitos; null se não tiver 8. */
    static String cep(String s) {
        if (s == null) {
            return null;
        }
        String d = s.replaceAll("\\D", "");
        return d.length() == 8 ? d : null;
    }

    private static String texto(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o).trim();
    }

    private static String vazio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

}
