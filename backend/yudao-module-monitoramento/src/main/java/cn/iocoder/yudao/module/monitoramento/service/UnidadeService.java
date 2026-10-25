package cn.iocoder.yudao.module.monitoramento.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.monitoramento.config.MonitoramentoProperties;
import cn.iocoder.yudao.module.monitoramento.controller.admin.vo.UnidadeVOs;
import cn.iocoder.yudao.module.monitoramento.dal.dataobject.MonitorDO;
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
    private MonitorUnitMapper monitorUnitMapper;
    @Resource
    private MonMonitorMapper monMonitorMapper;
    @Resource
    private DeptApi deptApi;
    @Resource
    private MonitoramentoProperties monitoramentoProperties;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** O Nominatim pede no máximo 1 consulta por segundo */
    private long ultimaGeocodificacao;

    public List<UnidadeVOs.Unidade> listar() {
        List<MonitorUnitDO> lista = monitorUnitMapper.selectList(new LambdaQueryWrapperX<MonitorUnitDO>().orderByAsc(MonitorUnitDO::getName));
        Set<Long> depts = lista.stream().map(MonitorUnitDO::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nomes = depts.isEmpty() ? Map.of() : deptApi.getDeptList(depts).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, DeptRespDTO::getName));
        Map<Long, Long> monitores = monMonitorMapper.selectList(new LambdaQueryWrapperX<MonitorDO>().isNotNull(MonitorDO::getUnidadeId)).stream()
                .collect(Collectors.groupingBy(MonitorDO::getUnidadeId, Collectors.counting()));
        return lista.stream().map(u -> {
            UnidadeVOs.Unidade vo = new UnidadeVOs.Unidade();
            vo.setId(u.getId());
            vo.setNome(u.getName());
            vo.setDeptId(u.getDeptId());
            vo.setDeptNome(nomes.get(u.getDeptId()));
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
            return vo;
        }).toList();
    }

    public Long salvar(UnidadeVOs.Unidade req) {
        if (deptApi.getDept(req.getDeptId()) == null) {
            throw exception(UNIDADE_INVALIDA, "departamento não encontrado");
        }
        String cep = cep(req.getCep());
        if (req.getCep() != null && !req.getCep().isBlank() && cep == null) {
            throw exception(UNIDADE_INVALIDA, "CEP deve ter 8 dígitos");
        }
        MonitorUnitDO u = MonitorUnitDO.builder().id(req.getId()).name(req.getNome().trim()).deptId(req.getDeptId())
                .cep(cep == null ? null : cep.substring(0, 5) + "-" + cep.substring(5)).logradouro(vazio(req.getLogradouro()))
                .numero(vazio(req.getNumero())).complemento(vazio(req.getComplemento())).bairro(vazio(req.getBairro()))
                .city(vazio(req.getCidade())).uf(req.getUf() == null || req.getUf().isBlank() ? null : req.getUf().trim().toUpperCase(Locale.ROOT))
                .latitude(req.getLatitude().setScale(7, RoundingMode.HALF_UP)).longitude(req.getLongitude().setScale(7, RoundingMode.HALF_UP))
                .status(req.getAtiva() == null || req.getAtiva() ? 0 : 1).build();
        if (u.getId() == null) {
            monitorUnitMapper.insert(u);
        } else {
            if (monitorUnitMapper.selectById(u.getId()) == null) {
                throw exception(UNIDADE_NAO_EXISTE);
            }
            monitorUnitMapper.updateById(u);
            monitorUnitMapper.update(null, new LambdaUpdateWrapper<MonitorUnitDO>().eq(MonitorUnitDO::getId, u.getId())
                    .set(MonitorUnitDO::getCep, u.getCep()).set(MonitorUnitDO::getLogradouro, u.getLogradouro())
                    .set(MonitorUnitDO::getNumero, u.getNumero()).set(MonitorUnitDO::getComplemento, u.getComplemento())
                    .set(MonitorUnitDO::getBairro, u.getBairro()).set(MonitorUnitDO::getCity, u.getCity()).set(MonitorUnitDO::getUf, u.getUf()));
        }
        return u.getId();
    }

    /** Exclui a unidade; os monitores dela ficam sem unidade. */
    @Transactional(rollbackFor = Exception.class)
    public void excluir(Long id) {
        if (monitorUnitMapper.selectById(id) == null) {
            throw exception(UNIDADE_NAO_EXISTE);
        }
        monitorUnitMapper.deleteById(id);
        monMonitorMapper.update(null, new LambdaUpdateWrapper<MonitorDO>().eq(MonitorDO::getUnidadeId, id).set(MonitorDO::getUnidadeId, null)
                .set(MonitorDO::getPapel, "servico"));
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
