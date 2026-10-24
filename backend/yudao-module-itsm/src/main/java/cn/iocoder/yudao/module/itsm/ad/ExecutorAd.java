package cn.iocoder.yudao.module.itsm.ad;

/**
 * Executa uma operação no Active Directory/Exchange. O contrato é o do script Executar_AD_Automatico.ps1:
 * recebe a ação e os dados (JSON) e devolve JSON {sucesso, acao_real, login, senha?, mensagem} ou
 * {sucesso: false, erro}.
 */
public interface ExecutorAd {

    /**
     * @return a saída JSON do script
     * @throws Exception falha de infraestrutura (rede, autenticação SSH, tempo esgotado): a execução é repetida
     */
    String executar(String acao, String dadosJson) throws Exception;

    /** Nome para logs e tela: simulado, ssh. */
    String modo();

}
