package cn.iocoder.yudao.module.identidade.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra segredos guardados no banco (segredo do TOTP e cofre de senhas) com AES-256-GCM.
 *
 * <p>A chave-mestra vem de {@code coti.seguranca.chave-mestra} (32 bytes em Base64, gere com
 * {@code openssl rand -base64 32}) e nunca vai para o banco. O "contexto" (ex.: {@code mfa:42}) entra como dado
 * autenticado: um segredo copiado para a linha de outro usuário não decifra.
 */
@Component
public class CifradorSegredos {

    private static final String VERSAO = "v1:";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] chave;

    public CifradorSegredos(@Value("${coti.seguranca.chave-mestra:}") String chaveBase64) {
        this.chave = chaveBase64 == null || chaveBase64.isBlank() ? null : Base64.getDecoder().decode(chaveBase64.trim());
        if (this.chave != null && this.chave.length != 32) {
            throw new IllegalStateException("coti.seguranca.chave-mestra precisa ter 32 bytes em Base64 (openssl rand -base64 32)");
        }
    }

    public boolean configurado() {
        return chave != null;
    }

    public String cifrar(String textoClaro, String contexto) {
        exigirChave();
        try {
            byte[] iv = new byte[TAMANHO_IV];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(chave, "AES"), new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            c.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));
            return VERSAO + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public String decifrar(String cifrado, String contexto) {
        exigirChave();
        if (cifrado == null || !cifrado.startsWith(VERSAO)) {
            throw new IllegalArgumentException("Formato de segredo cifrado desconhecido");
        }
        try {
            byte[] tudo = Base64.getDecoder().decode(cifrado.substring(VERSAO.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(chave, "AES"),
                    new GCMParameterSpec(TAMANHO_TAG_BITS, tudo, 0, TAMANHO_IV));
            c.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return new String(c.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // chave trocada, dado adulterado ou contexto errado
            throw new IllegalStateException("Não foi possível decifrar o segredo", e);
        }
    }

    private void exigirChave() {
        if (chave == null) {
            throw new IllegalStateException("Defina CO_TI_CHAVE_MESTRA (openssl rand -base64 32) para usar segredos cifrados");
        }
    }

}
