package cn.iocoder.yudao.module.infraestrutura.controller.admin.vo;

import cn.iocoder.yudao.module.identidade.service.AdConfiguracaoService;
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

    /** @param senha vazia mantém a senha salva da conta de serviço */
    @Data
    public static class SalvarAd {
        @NotNull
        private AdConfiguracaoService.Dados dados;
        private String senha;
    }

    /** Além do que vai ser salvo, um login (e senha) de alguém para conferir que entra. */
    @Data
    public static class TestarAd {
        @NotNull
        private AdConfiguracaoService.Dados dados;
        private String senha;
        private String login;
        private String senhaUsuario;
    }

    @Data
    public static class CertificadosAd {
        @NotBlank
        private String servidores;
        private Integer porta;
        private String seguranca;
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
