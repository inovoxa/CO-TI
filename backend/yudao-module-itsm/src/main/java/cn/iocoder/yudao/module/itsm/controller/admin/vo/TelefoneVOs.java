package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

public class TelefoneVOs {

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Trecho do nome, setor, unidade ou e-mail, ou parte do número */
        @Size(max = 120)
        private String busca;
        private String setor;
        private Long unidadeId;
        /** true = só contatos de emergência */
        private Boolean emergencia;
    }

    @Data
    public static class SalvarReq {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 120, message = "Nome grande demais")
        private String nome;
        @Size(max = 120, message = "Setor grande demais")
        private String setor;
        private Long unidadeId;
        @Pattern(regexp = "^\\s*$|^[0-9A-Za-z*#.\\- ]{1,20}$", message = "Ramal inválido")
        private String ramal;
        @Pattern(regexp = "^\\s*$|^[0-9()+.\\- ]{8,30}$", message = "Telefone inválido")
        private String telefone;
        @Pattern(regexp = "^\\s*$|^[0-9()+.\\- ]{10,30}$", message = "Celular inválido: use o DDD")
        private String celular;
        private Boolean whatsapp;
        private Boolean celularPublico;
        private Boolean emergencia;
        @Email(message = "E-mail inválido")
        @Size(max = 128, message = "E-mail grande demais")
        private String email;
        @Size(max = 255, message = "Observação grande demais")
        private String observacao;
    }

    @Data
    public static class Resp {
        private Long id;
        private String nome;
        private String setor;
        private Long unidadeId;
        private String unidade;
        private String ramal;
        private String telefone;
        /** Vazio para quem não pode ver: nesse caso, celularOculto indica que existe um número cadastrado */
        private String celular;
        private Boolean celularOculto;
        private Boolean whatsapp;
        private Boolean celularPublico;
        private Boolean emergencia;
        private String email;
        private String observacao;
        /** O link da unidade está fora agora: o ramal pode não funcionar */
        private Boolean linkFora;
    }

    @Data
    public static class Unidade {
        private Long id;
        private String nome;
        private Boolean linkFora;
    }

    /** Contatos de uma unidade, com os de emergência primeiro */
    @Data
    public static class ContatosUnidade {
        private Long unidadeId;
        private String unidade;
        private Boolean linkFora;
        private List<Resp> contatos = new ArrayList<>();
    }

    @Data
    public static class ImportacaoResp {
        private int criados;
        private int atualizados;
        /** Quantas linhas foram recusadas (a lista de erros pode vir cortada) */
        private int recusados;
        private List<String> erros = new ArrayList<>();
    }

}
