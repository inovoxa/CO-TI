package cn.iocoder.yudao.module.logs.dominio;

import java.util.Map;
import java.util.Set;

/**
 * Eventos do Windows que interessam aos painéis: o nome em português (campo {@code acao}) e o fluxo de cada um.
 * Contas = auditoria de logon e de contas (normalmente dos controladores de domínio); arquivos = auditoria de acesso a
 * objetos e compartilhamentos; o resto é da máquina.
 */
public final class EventosWindows {

    private EventosWindows() {
    }

    public static final Map<Integer, String> NOMES = Map.ofEntries(
            // logon e contas
            Map.entry(4624, "Logon"),
            Map.entry(4625, "Falha de logon"),
            Map.entry(4634, "Logoff"),
            Map.entry(4647, "Logoff"),
            Map.entry(4648, "Logon com outra credencial"),
            Map.entry(4720, "Conta criada"),
            Map.entry(4722, "Conta habilitada"),
            Map.entry(4723, "Senha alterada"),
            Map.entry(4724, "Senha redefinida"),
            Map.entry(4725, "Conta desabilitada"),
            Map.entry(4726, "Conta excluída"),
            Map.entry(4728, "Adicionado a grupo"),
            Map.entry(4729, "Removido de grupo"),
            Map.entry(4732, "Adicionado a grupo"),
            Map.entry(4733, "Removido de grupo"),
            Map.entry(4738, "Conta modificada"),
            Map.entry(4740, "Conta bloqueada"),
            Map.entry(4741, "Computador criado"),
            Map.entry(4742, "Computador alterado"),
            Map.entry(4743, "Computador excluído"),
            Map.entry(4756, "Adicionado a grupo"),
            Map.entry(4757, "Removido de grupo"),
            Map.entry(4767, "Conta desbloqueada"),
            Map.entry(4768, "Ticket Kerberos emitido"),
            Map.entry(4771, "Falha no Kerberos"),
            Map.entry(4776, "Validação de credencial"),
            Map.entry(4781, "Conta renomeada"),
            // arquivos e compartilhamentos
            Map.entry(4656, "Pedido de acesso a arquivo"),
            Map.entry(4658, "Acesso a arquivo encerrado"),
            Map.entry(4660, "Arquivo apagado"),
            Map.entry(4663, "Acesso a arquivo"),
            Map.entry(4670, "Permissões alteradas"),
            Map.entry(5140, "Pasta compartilhada acessada"),
            Map.entry(5142, "Compartilhamento criado"),
            Map.entry(5143, "Compartilhamento alterado"),
            Map.entry(5144, "Compartilhamento excluído"),
            Map.entry(5145, "Acesso a arquivo compartilhado"),
            // máquina
            Map.entry(41, "Reiniciou sem desligar"),
            Map.entry(104, "Log do Windows apagado"),
            Map.entry(1000, "Programa travou"),
            Map.entry(1001, "Relatório de erro"),
            Map.entry(1074, "Desligamento ou reinício"),
            Map.entry(1102, "Log de auditoria apagado"),
            Map.entry(4697, "Serviço instalado"),
            Map.entry(6005, "Windows iniciado"),
            Map.entry(6006, "Windows desligado"),
            Map.entry(6008, "Desligamento inesperado"),
            Map.entry(7034, "Serviço parou inesperadamente"),
            Map.entry(7036, "Serviço mudou de estado"),
            Map.entry(7045, "Serviço instalado"));

    private static final Set<Integer> CONTAS = Set.of(4624, 4625, 4634, 4647, 4648, 4720, 4722, 4723, 4724, 4725, 4726,
            4728, 4729, 4732, 4733, 4738, 4740, 4741, 4742, 4743, 4756, 4757, 4767, 4768, 4771, 4776, 4781);
    private static final Set<Integer> ARQUIVOS = Set.of(4656, 4658, 4660, 4663, 4670, 5140, 5142, 5143, 5144, 5145);

    public static String fluxo(int eventId) {
        if (CONTAS.contains(eventId)) {
            return Fluxos.CONTAS;
        }
        return ARQUIVOS.contains(eventId) ? Fluxos.ARQUIVOS : Fluxos.MAQUINAS;
    }

    /** 4663 com permissão de apagar (DELETE = 0x10000) é um arquivo apagado. */
    public static String nome(int eventId, String mascaraAcesso, String listaAcesso) {
        if (eventId == 4663 && (contem(mascaraAcesso, "0x10000") || contem(listaAcesso, "%%1537")
                || contem(listaAcesso, "DELETE"))) {
            return "Arquivo apagado";
        }
        return NOMES.get(eventId);
    }

    private static boolean contem(String s, String trecho) {
        return s != null && s.toUpperCase().contains(trecho.toUpperCase());
    }

}
