package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Limpeza do inventário: máquinas repetidas e máquinas paradas. */
public final class LimpezaVOs {

    private LimpezaVOs() {
    }

    /** Ativos que parecem ser a mesma máquina (ou o mesmo nome reaproveitado). */
    @Data
    public static class Grupo {
        /** mesmo nome, mesma série, mesmo UUID, mesmo MAC */
        private List<String> motivos = new ArrayList<>();
        /** Do mais recente para o mais antigo; o primeiro é a sugestão do que fica */
        private List<AtivoVOs.Resp> ativos = new ArrayList<>();
        /** Quantos chamados cada ativo tem (mesma ordem) */
        private List<Long> chamados = new ArrayList<>();
    }

    @Data
    public static class MesclarReq {
        /** O que fica */
        @NotNull
        private Long manterId;
        /** Os que somem (o histórico e os chamados passam para o que fica) */
        @NotEmpty
        @Size(max = 50)
        private List<Long> ids;
    }

    @Data
    public static class IdsReq {
        @NotEmpty
        @Size(max = 5000)
        private List<Long> ids;
    }

    @Data
    public static class ParadasReq {
        @NotNull
        @Min(7)
        @Max(3650)
        private Integer dias;
    }

}
