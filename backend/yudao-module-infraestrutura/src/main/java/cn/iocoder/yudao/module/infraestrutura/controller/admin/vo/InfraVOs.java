package cn.iocoder.yudao.module.infraestrutura.controller.admin.vo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public final class InfraVOs {

    private InfraVOs() {
    }

    /** @param diasAteEncher nulo enquanto não há histórico suficiente ou os dados não crescem */
    public record Capacidade(long totalBytes, long usadoBytes, long livreBytes, int percentual, boolean pelaReserva,
                             long dadosBytes, long crescimentoPorDia, Long diasAteEncher, long diasDeHistorico, int diasMinimos,
                             List<Area> areas) {
    }

    public record Area(String chave, String rotulo, long bytes, long linhas, long crescimentoPorDia, String guarda) {
    }

    public record Rotina(String nome, String rotulo, LocalDateTime ultimaExecucao, long duracaoMs, String status, String ultimoErro,
                         long execucoes, long falhas, LocalDateTime ultimaFalha) {
    }

    @Data
    public static class SalvarRetencao {
        @NotBlank
        private String chave;
        @NotNull
        @Min(0)
        private Integer valor;
    }

}
