package cn.iocoder.yudao.module.identidade.ad;

/** O AD não respondeu (rede, certificado, conta de serviço). É diferente de senha errada. */
public class AdIndisponivelException extends RuntimeException {

    public AdIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

}
