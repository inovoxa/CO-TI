package cn.iocoder.yudao.module.infraestrutura.dominio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DestinoRegrasTest {

    @Test
    void redeInternaEPublicaPodem() {
        assertNull(DestinoRegras.motivoDeRecusa("https://10.0.5.20/avisos"));
        assertNull(DestinoRegras.motivoDeRecusa("http://192.168.1.10:8080/x"));
        assertNull(DestinoRegras.motivoDeRecusa("https://8.8.8.8/x"));
    }

    @Test
    void oProprioServidorEMetadadosNao() {
        assertNotNull(DestinoRegras.motivoDeRecusa("http://127.0.0.1/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://localhost/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://169.254.169.254/latest/meta-data"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://[::1]/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://[::ffff:169.254.169.254]/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://[::169.254.169.254]/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://0.0.0.0/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://[fe80::1]/x"));
    }

    @Test
    void enderecosInvalidos() {
        assertNotNull(DestinoRegras.motivoDeRecusa("ftp://10.0.0.1/x"));
        assertNotNull(DestinoRegras.motivoDeRecusa("file:///etc/passwd"));
        assertNotNull(DestinoRegras.motivoDeRecusa("nada"));
        assertNotNull(DestinoRegras.motivoDeRecusa(null));
        assertNotNull(DestinoRegras.motivoDeRecusa("http://usuario:senha@10.0.0.1/x"));
    }

}
