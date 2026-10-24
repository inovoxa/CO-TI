package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.EmailVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailEventoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailRecebidoDO;
import cn.iocoder.yudao.module.itsm.dal.dataobject.EmailSaidaDO;
import cn.iocoder.yudao.module.itsm.service.email.ColetorEmailService;
import cn.iocoder.yudao.module.itsm.service.email.EmailConfigService;
import cn.iocoder.yudao.module.itsm.service.email.EnvioEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - E-mail do Service Desk (notificações e coletor)")
@RestController
@RequestMapping("/itsm/email")
@Validated
public class EmailController {

    @Resource
    private EmailConfigService configService;
    @Resource
    private EnvioEmailService envio;
    @Resource
    private ColetorEmailService coletor;

    // -------- envio --------

    @GetMapping("/config")
    @Operation(summary = "Conta de envio (a senha não volta)")
    @PreAuthorize("@ss.hasPermission('itsm:email:query')")
    public CommonResult<EmailVOs.Config> config() {
        return success(configService.config());
    }

    @PutMapping("/config")
    @Operation(summary = "Salvar conta de envio")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Boolean> salvarConfig(@Valid @RequestBody EmailVOs.Config req) {
        configService.salvarConfig(req);
        return success(true);
    }

    @PostMapping("/testar-envio")
    @Operation(summary = "Envia um e-mail de teste agora; devolve o erro, se houver")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Map<String, Object>> testarEnvio(@Valid @RequestBody EmailVOs.TesteEnvioReq req) {
        String erro = envio.testar(req.getPara());
        return success(erro == null ? Map.of("ok", true) : Map.of("ok", false, "erro", erro));
    }

    @GetMapping("/eventos")
    @Operation(summary = "Eventos de notificação e modelos")
    @PreAuthorize("@ss.hasPermission('itsm:email:query')")
    public CommonResult<List<EmailEventoDO>> eventos() {
        return success(configService.eventos());
    }

    @PutMapping("/evento")
    @Operation(summary = "Ligar/desligar evento, destinatários e modelo")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Boolean> salvarEvento(@Valid @RequestBody EmailVOs.Evento req) {
        configService.salvarEvento(req);
        return success(true);
    }

    @GetMapping("/fila")
    @Operation(summary = "E-mails na fila de saída")
    @PreAuthorize("@ss.hasPermission('itsm:email:query')")
    public CommonResult<PageResult<EmailSaidaDO>> fila(@Valid PageParam p, @RequestParam(value = "status", required = false) String status,
                                                       @RequestParam(value = "chamadoId", required = false) Long chamadoId) {
        return success(envio.pagina(p, status, chamadoId));
    }

    @PostMapping("/reenviar")
    @Operation(summary = "Recolocar um e-mail com erro na fila")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Boolean> reenviar(@RequestParam("id") Long id) {
        envio.reenviar(id);
        return success(true);
    }

    // -------- coletor --------

    @GetMapping("/caixas")
    @Operation(summary = "Caixas de entrada coletadas")
    @PreAuthorize("@ss.hasPermission('itsm:email:query')")
    public CommonResult<List<EmailVOs.Caixa>> caixas() {
        return success(configService.caixas());
    }

    @PostMapping("/caixa")
    @Operation(summary = "Criar ou editar caixa")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Long> salvarCaixa(@Valid @RequestBody EmailVOs.Caixa req) {
        return success(configService.salvarCaixa(req));
    }

    @DeleteMapping("/caixa")
    @Operation(summary = "Excluir caixa")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Boolean> excluirCaixa(@RequestParam("id") Long id) {
        configService.excluirCaixa(id);
        return success(true);
    }

    @PostMapping("/caixa/testar")
    @Operation(summary = "Testa a conexão IMAP da caixa")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Map<String, Object>> testarCaixa(@RequestParam("id") Long id) {
        String erro = coletor.testar(configService.caixaObrigatoria(id));
        return success(erro == null ? Map.of("ok", true) : Map.of("ok", false, "erro", erro));
    }

    @PostMapping("/caixa/coletar")
    @Operation(summary = "Coleta a caixa agora")
    @PreAuthorize("@ss.hasPermission('itsm:email:update')")
    public CommonResult<Integer> coletar(@RequestParam("id") Long id) {
        return success(coletor.coletar(configService.caixaObrigatoria(id)));
    }

    @GetMapping("/recebidos")
    @Operation(summary = "E-mails lidos pelo coletor e o que virou cada um")
    @PreAuthorize("@ss.hasPermission('itsm:email:query')")
    public CommonResult<PageResult<EmailRecebidoDO>> recebidos(@Valid PageParam p,
                                                               @RequestParam(value = "caixaId", required = false) Long caixaId,
                                                               @RequestParam(value = "resultado", required = false) String resultado) {
        return success(configService.recebidos(p, caixaId, resultado));
    }

}
