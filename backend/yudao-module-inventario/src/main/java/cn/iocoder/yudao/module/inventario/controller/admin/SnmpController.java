package cn.iocoder.yudao.module.inventario.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.inventario.controller.admin.vo.SnmpVOs;
import cn.iocoder.yudao.module.inventario.service.SnmpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "CO-TI - Inventário: SNMP")
@RestController
@RequestMapping("/inventario/snmp")
@Validated
public class SnmpController {

    @Resource
    private SnmpService snmpService;

    @GetMapping("/credenciais")
    @Operation(summary = "Credenciais SNMP (sem os segredos)")
    @PreAuthorize("@ss.hasPermission('inventario:snmp:update')")
    public CommonResult<List<SnmpVOs.Credencial>> credenciais() {
        return success(snmpService.credenciais());
    }

    @PostMapping("/credencial")
    @Operation(summary = "Criar ou alterar credencial (segredo vazio = mantém o guardado)")
    @PreAuthorize("@ss.hasPermission('inventario:snmp:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody SnmpVOs.Credencial req) {
        return success(snmpService.salvarCredencial(req));
    }

    @DeleteMapping("/credencial")
    @Operation(summary = "Excluir credencial")
    @PreAuthorize("@ss.hasPermission('inventario:snmp:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        snmpService.excluirCredencial(id);
        return success(true);
    }

    @GetMapping("/alvos")
    @Operation(summary = "IPs consultados e o resultado da última tentativa")
    @PreAuthorize("@ss.hasPermission('inventario:ativo:query')")
    public CommonResult<List<SnmpVOs.Alvo>> alvos() {
        return success(snmpService.alvosConsultados());
    }

    @PostMapping("/consultar")
    @Operation(summary = "Consultar um IP agora (grava o ativo se responder)")
    @PreAuthorize("@ss.hasPermission('inventario:snmp:update')")
    public CommonResult<SnmpVOs.Resultado> consultar(@RequestParam("ip") String ip) {
        return success(snmpService.consultar(ip.strip()));
    }

    @PostMapping("/varrer")
    @Operation(summary = "Varrer todos os alvos agora")
    @PreAuthorize("@ss.hasPermission('inventario:snmp:update')")
    public CommonResult<SnmpVOs.Varredura> varrer() {
        return success(snmpService.varrer());
    }

}
