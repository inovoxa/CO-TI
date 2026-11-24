package cn.iocoder.yudao.module.identidade.controller.admin.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Sistema → Usuários: lista com perfis, origem, acesso e verificação em duas etapas; ficha; lote; revisão. */
public final class UsuarioGestaoVOs {

    private UsuarioGestaoVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PaginaReq extends PageParam {
        /** Nome, usuário, e-mail ou celular */
        @Size(max = 100)
        private String busca;
        /** Departamento, com os subdepartamentos */
        private Long deptId;
        /** Unidade pela máquina, com as subunidades; -1 = sem unidade */
        private Long unidadeId;
        private Long roleId;
        /** ad | local */
        private String origem;
        /** 0 ativo, 1 inativo */
        private Integer status;
        /** Sem acesso há mais de N dias (ou nunca entrou) */
        private Integer semAcessoDias;
        private Boolean semMfa;
        private Boolean privilegiado;
        /** nome, usuario, unidade, acesso, criado, situacao */
        private String ordem;
        /** asc | desc */
        private String direcao;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Perfil {
        private Long id;
        private String nome;
        private String codigo;
        /** Todas as unidades, unidade e subunidades, só os meus... */
        private String escopo;
    }

    @Data
    public static class Linha {
        private Long id;
        private String username;
        private String nickname;
        private String email;
        private String mobile;
        private Long deptId;
        private String deptNome;
        /** Unidade pela máquina que a pessoa usa (ver cmdb.unidade_do_usuario) */
        private Long unidadeId;
        private String unidadeNome;
        /** Máquina de onde veio a unidade (a de contato mais recente) */
        private String unidadeMaquina;
        private String unidadeIp;
        /** manual (dada na máquina) | faixa (pelo IP) | fora (IP fora das faixas) | sem_maquina */
        private String unidadeOrigem;
        private Integer status;
        private List<Perfil> perfis = new ArrayList<>();
        /** Algum perfil administra usuários, perfis ou permissões */
        private Boolean privilegiado;
        /** ad | local */
        private String origem;
        /** Conta do AD ainda ativa lá (só para origem ad) */
        private Boolean adAtivo;
        private LocalDateTime adSincronizacao;
        private Boolean mfa;
        private LocalDateTime ultimoAcesso;
        private String ultimoIp;
        private LocalDateTime criadoEm;
        private Long sessoes;
    }

    @Data
    public static class Sessao {
        private LocalDateTime criada;
        private LocalDateTime expira;
        private String cliente;
    }

    @Data
    public static class Acesso {
        private LocalDateTime quando;
        private String ip;
        private String navegador;
        /** 0 = entrou; outros = recusado */
        private Integer resultado;
        private String tipo;
    }

    @Data
    public static class Evento {
        private LocalDateTime momento;
        private String acao;
        private String ip;
        private String detalhes;
    }

    @Data
    public static class Ficha {
        private Linha usuario;
        private String observacao;
        private String adLogin;
        private String adDn;
        private String adGestorDn;
        private LocalDateTime mfaAtivadoEm;
        private List<Sessao> sessoes = new ArrayList<>();
        private List<Acesso> acessos = new ArrayList<>();
        private List<Evento> auditoria = new ArrayList<>();
    }

    @Data
    public static class LoteReq {
        @NotBlank
        @Pattern(regexp = "ativar|desativar|mover|perfil|tirar_perfil|mfa|encerrar")
        private String acao;
        @NotEmpty
        @Size(max = 2000)
        private List<Long> ids;
        private Long deptId;
        private Long roleId;
    }

    @Data
    public static class LoteResp {
        private int feitos;
        private int ignorados;
        private List<String> avisos = new ArrayList<>();
    }

    @Data
    public static class Revisao {
        private List<Linha> privilegiados = new ArrayList<>();
        private List<Linha> privilegiadosSemMfa = new ArrayList<>();
        private List<Linha> semAcesso = new ArrayList<>();
        private List<Linha> locais = new ArrayList<>();
        private List<Linha> desativadosNoAd = new ArrayList<>();
        private int diasSemAcesso;
        private LocalDateTime ultimaRevisao;
        private String revisadoPor;
    }

}
