package cn.iocoder.yudao.module.itsm.service;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.TelefoneVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.TelefoneDO;
import cn.iocoder.yudao.module.itsm.dal.mysql.TelefoneMapper;
import cn.iocoder.yudao.module.itsm.enums.ErrorCodeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelefoneServiceTest {

    @Mock
    private TelefoneMapper telefoneMapper;
    @InjectMocks
    private TelefoneService service;

    private static TelefoneVOs.SalvarReq req(Long id, String ramal, String telefone) {
        TelefoneVOs.SalvarReq r = new TelefoneVOs.SalvarReq();
        r.setId(id);
        r.setNome("  Almoxarifado ");
        r.setSetor("   ");
        r.setRamal(ramal);
        r.setTelefone(telefone);
        return r;
    }

    @Test
    void semRamalNemTelefone_recusa() {
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(req(null, " ", null)));
        assertEquals(ErrorCodeConstants.TELEFONE_SEM_NUMERO.getCode(), e.getCode());
        verify(telefoneMapper, never()).insert(any(TelefoneDO.class));
    }

    @Test
    void novo_tiraEspacosEGuardaVazioComoNulo() {
        service.salvar(req(null, " 1234 ", null));
        ArgumentCaptor<TelefoneDO> c = ArgumentCaptor.forClass(TelefoneDO.class);
        verify(telefoneMapper).insert(c.capture());
        assertEquals("Almoxarifado", c.getValue().getNome());
        assertEquals("1234", c.getValue().getRamal());
        assertNull(c.getValue().getSetor());
    }

    @Test
    void editarQueNaoExiste_recusa() {
        when(telefoneMapper.selectById(9L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.salvar(req(9L, "1234", null)));
        assertEquals(ErrorCodeConstants.TELEFONE_NAO_EXISTE.getCode(), e.getCode());
        verify(telefoneMapper, never()).updateById(any(TelefoneDO.class));
    }

    @Test
    void excluirQueNaoExiste_recusa() {
        when(telefoneMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.excluir(9L));
        verify(telefoneMapper, never()).deleteById(9L);
    }

}
