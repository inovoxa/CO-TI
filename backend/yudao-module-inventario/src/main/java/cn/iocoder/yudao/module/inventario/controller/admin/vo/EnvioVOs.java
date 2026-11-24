package cn.iocoder.yudao.module.inventario.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Envios dos agentes (aba "Envios" da página Agentes). */
public final class EnvioVOs {

    private EnvioVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Um resultado, ou os grupos "recusados", "problemas" (recusados + erro) e "inventarios" */
        private String resultado;
        /** IP, DEVICEID, nome da máquina ou versão do agente */
        private String busca;
        private Long ativoId;
        /** Só as últimas N horas */
        private Integer horas;
    }

    @Data
    public static class Resp {
        private Long id;
        private LocalDateTime quando;
        private String ip;
        private String deviceId;
        private String formato;
        private String consulta;
        private String resultado;
        private String detalhe;
        private String agente;
        private Integer bytes;
        private Integer ms;
        private Long ativoId;
        private String ativoNome;
        /** O ativo foi mesclado em outro ou excluído depois */
        private Boolean ativoRemovido;
    }

    @Data
    public static class Recusa {
        private String ip;
        private String resultado;
        private Long vezes;
        private LocalDateTime ultima;
    }

    @Data
    public static class Resumo {
        /** Últimas 24 h */
        private Map<String, Long> porResultado = new LinkedHashMap<>();
        private Long maquinas;
        private List<Recusa> recusas = new ArrayList<>();
        /** Por quantos dias o registro fica guardado */
        private Integer dias;
    }

}
