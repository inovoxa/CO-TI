package cn.iocoder.yudao.module.identidade.ad;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Propriedades de uma conta lidas na hora do AD, como nas abas do "Usuários e Computadores do Active Directory"
 * (Geral, Endereço, Conta, Perfil, Telefones, Organização, Membro de). Só leitura; nada de senha ou hash.
 */
@Data
public class AdPropriedades {

    /** Controlador de domínio que respondeu: o último logon (lastLogon) é desse DC, não do domínio todo. */
    private String servidor;
    private String dn;
    private String guid;

    // ----- Geral
    private String nome;
    private String nomeProprio;
    private String sobrenome;
    private String descricao;
    private String escritorio;
    private String email;
    private String telefone;
    private String paginaWeb;

    // ----- Endereço
    private String endereco;
    private String cidade;
    private String estado;
    private String cep;
    private String pais;

    // ----- Conta
    private String login;
    private String upn;
    private boolean ativa;
    private boolean bloqueada;
    private LocalDateTime bloqueadaEm;
    private boolean senhaNuncaExpira;
    private boolean senhaExpirada;
    /** pwdLastSet = 0: troca a senha no próximo logon */
    private boolean trocarSenhaNoLogon;
    private boolean exigeCartaoInteligente;
    private LocalDateTime senhaAlteradaEm;
    private LocalDateTime senhaExpiraEm;
    /** Vazio = nunca expira */
    private LocalDateTime contaExpiraEm;
    /** lastLogon: último logon com sucesso neste DC (não replica) */
    private LocalDateTime ultimoLogon;
    /** lastLogonTimestamp: replica entre os DCs, com atraso de até 14 dias */
    private LocalDateTime ultimoLogonReplicado;
    /** badPasswordTime: última senha errada (neste DC) */
    private LocalDateTime ultimaSenhaErrada;
    private Integer senhasErradas;
    private Integer quantidadeLogons;
    private LocalDateTime criadaEm;
    private LocalDateTime alteradaEm;

    // ----- Perfil
    private String caminhoPerfil;
    private String scriptLogon;
    private String pastaBase;
    private String unidadePastaBase;

    // ----- Telefones
    private String celular;
    private String telefoneResidencial;
    private String telefoneIp;

    // ----- Organização
    private String cargo;
    private String departamento;
    private String empresa;
    private String gestorDn;
    private String gestorNome;
    private List<String> subordinados = new ArrayList<>();
    private String matricula;
    private String numeroFuncionario;

    // ----- Membro de
    private String grupoPrincipal;
    private List<Grupo> grupos = new ArrayList<>();

    @Data
    public static class Grupo {
        private String nome;
        private String dn;
        /** false = vem por outro grupo (aninhado) */
        private boolean direto;
    }

}
