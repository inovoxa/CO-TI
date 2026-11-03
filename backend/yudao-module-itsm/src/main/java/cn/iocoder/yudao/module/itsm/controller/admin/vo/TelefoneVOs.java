package cn.iocoder.yudao.module.itsm.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

public class TelefoneVOs {

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Trecho do nome, setor, unidade ou e-mail, ou parte do número */
        @Size(max = 120)
        private String busca;
        private String setor;
    }

    @Data
    public static class SalvarReq {
        private Long id;
        @NotBlank(message = "Informe o nome")
        @Size(max = 120, message = "Nome grande demais")
        private String nome;
        @Size(max = 120, message = "Setor grande demais")
        private String setor;
        @Size(max = 120, message = "Unidade grande demais")
        private String unidade;
        @Pattern(regexp = "^\\s*$|^[0-9A-Za-z*#.\\- ]{1,20}$", message = "Ramal inválido")
        private String ramal;
        @Pattern(regexp = "^\\s*$|^[0-9()+.\\- ]{8,30}$", message = "Telefone inválido")
        private String telefone;
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
        private String unidade;
        private String ramal;
        private String telefone;
        private String email;
        private String observacao;
    }

}
