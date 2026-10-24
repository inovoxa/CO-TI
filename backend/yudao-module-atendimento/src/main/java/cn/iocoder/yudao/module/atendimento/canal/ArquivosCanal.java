package cn.iocoder.yudao.module.atendimento.canal;

import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.atendimento.controller.admin.vo.AtendimentoVOs;
import cn.iocoder.yudao.module.atendimento.dominio.Meta;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arquivos que passam pelos canais: lê do armazenamento do CO-TI o que o atendente anexou (para mandar ao provedor)
 * e grava o que o contato mandou.
 */
@Component
public class ArquivosCanal {

    /** Endereço que o FileApi devolve: /admin-api/infra/file/{configId}/get/{caminho} */
    private static final Pattern ENDERECO = Pattern.compile("^/admin-api/infra/file/(\\d+)/get/(.+)$");

    @Resource
    private FileService fileService;
    @Resource
    private FileApi fileApi;

    /** Arquivo anexado a uma mensagem do CO-TI. */
    public record Local(byte[] dados, String nome, String tipo) {
    }

    /**
     * @throws Canal.FalhaDefinitiva o anexo não é do CO-TI ou não existe mais
     */
    public Local ler(AtendimentoVOs.Anexo a) throws Exception {
        Matcher m = a.getUrl() == null ? null : ENDERECO.matcher(a.getUrl());
        if (m == null || !m.matches()) {
            throw new Canal.FalhaDefinitiva("anexo fora do armazenamento do CO-TI");
        }
        String caminho = HttpUtils.decodeUrlPath(m.group(2));
        if (caminho.contains("..")) {
            throw new Canal.FalhaDefinitiva("anexo com caminho inválido");
        }
        byte[] dados = fileService.getFileContent(Long.parseLong(m.group(1)), caminho);
        if (dados == null || dados.length == 0) {
            throw new Canal.FalhaDefinitiva("o anexo \"" + a.getNome() + "\" não existe mais");
        }
        return new Local(dados, a.getNome() == null ? "arquivo" : a.getNome(), a.getTipo() == null ? "application/octet-stream" : a.getTipo());
    }

    /** Grava o que veio do contato e devolve o anexo para a mensagem. */
    public AtendimentoVOs.Anexo salvar(byte[] dados, String nome, String tipo, String prefixo) {
        String mime = tipo == null ? "application/octet-stream" : tipo.split(";")[0].trim();
        String n = nome == null || nome.isBlank() ? prefixo + Meta.extensao(mime) : nome;
        AtendimentoVOs.Anexo a = new AtendimentoVOs.Anexo();
        a.setNome(ProvedorHttp.nomeSeguro(n));
        a.setTipo(mime);
        a.setTamanho((long) dados.length);
        a.setUrl(fileApi.createFile(dados, a.getNome(), "atendimento", mime));
        return a;
    }

}
