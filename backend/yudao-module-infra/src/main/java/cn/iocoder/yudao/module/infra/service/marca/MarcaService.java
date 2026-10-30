package cn.iocoder.yudao.module.infra.service.marca;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.controller.admin.marca.MarcaController.Marca;
import cn.iocoder.yudao.module.infra.dal.dataobject.config.ConfigDO;
import cn.iocoder.yudao.module.infra.dal.mysql.config.ConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nome, logo, foto e texto do login da instância, guardados em infra_config (criados pela V47). Sem valor, a interface usa os padrões do CO-TI.
 */
@Service
public class MarcaService {

    public static final String CHAVE_NOME = "coti.marca.nome";
    public static final String CHAVE_LOGO = "coti.marca.logo";
    public static final String CHAVE_FUNDO = "coti.marca.fundo";
    public static final String CHAVE_FRASE = "coti.marca.frase";
    public static final String NOME_PADRAO = "Centro de Operações de TI";

    @Resource
    private ConfigMapper configMapper;

    public Marca obter() {
        String nome = valor(CHAVE_NOME);
        return new Marca().setNome(StrUtil.blankToDefault(nome, NOME_PADRAO)).setLogo(StrUtil.nullToEmpty(valor(CHAVE_LOGO)))
                .setFundo(StrUtil.nullToEmpty(valor(CHAVE_FUNDO)))
                .setFrase(StrUtil.nullToEmpty(valor(CHAVE_FRASE)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void salvar(Marca marca) {
        gravar(CHAVE_NOME, "Nome da instância", marca.getNome().strip());
        gravar(CHAVE_LOGO, "Logo da instância", StrUtil.nullToEmpty(marca.getLogo()).strip());
        gravar(CHAVE_FUNDO, "Foto da tela de login", StrUtil.nullToEmpty(marca.getFundo()).strip());
        gravar(CHAVE_FRASE, "Texto da tela de login", StrUtil.nullToEmpty(marca.getFrase()).strip());
    }

    private String valor(String chave) {
        ConfigDO c = configMapper.selectByKey(chave);
        return c == null ? null : c.getValue();
    }

    private void gravar(String chave, String nome, String valor) {
        ConfigDO c = configMapper.selectByKey(chave);
        if (c == null) {
            c = new ConfigDO();
            c.setCategory("coti");
            c.setType(1);
            c.setName(nome);
            c.setConfigKey(chave);
            c.setValue(valor);
            c.setVisible(true);
            c.setRemark("Alterado em Sistema > Identidade visual");
            configMapper.insert(c);
            return;
        }
        ConfigDO alteracao = new ConfigDO();
        alteracao.setId(c.getId());
        alteracao.setValue(valor);
        configMapper.updateById(alteracao);
    }

}
