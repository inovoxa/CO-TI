package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class LicencaInventarioVOs {

    private LicencaInventarioVOs() {
    }

    /** Licença do ITSM comparada com o que o inventário encontrou instalado. */
    @Data
    public static class Conformidade {
        private Long licencaId;
        /** -1 = ilimitada */
        private Integer quantidade;
        /** Usos registrados à mão na licença */
        private Integer emUso;
        /** Máquinas com algum dos softwares da licença */
        private long instalacoes;
        private boolean excedida;
        private List<AtivoVOs.SoftwareResp> softwares = new ArrayList<>();
        /** Máquinas encontradas (respeitam o escopo de unidade de quem consulta) */
        private List<AtivoVOs.Resp> ativos = new ArrayList<>();
    }

    @Data
    public static class VinculoReq {
        @NotNull
        private Long licencaId;
        @NotNull
        private Long softwareId;
    }

}
