package cn.iocoder.yudao.module.itsm.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.itsm.controller.admin.vo.KbVOs;
import cn.iocoder.yudao.module.itsm.dal.dataobject.KbCategoriaDO;
import cn.iocoder.yudao.module.itsm.service.KbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * Base de conhecimento. Leitura e busca valem para qualquer usuário logado (o serviço filtra: FAQ para todos,
 * internos com itsm:kb:query); escrita exige itsm:kb:update.
 */
@Tag(name = "CO-TI - Base de conhecimento")
@RestController
@RequestMapping("/itsm/kb")
@Validated
public class KbController {

    @Resource
    private KbService kbService;

    // -------- leitura (todos) --------

    @GetMapping("/categorias")
    @Operation(summary = "Categorias da base")
    public CommonResult<List<KbCategoriaDO>> categorias() {
        return success(kbService.categorias());
    }

    @GetMapping("/buscar")
    @Operation(summary = "Buscar artigos (sem acento, por prefixo); gestao=true mostra rascunhos para quem escreve")
    public CommonResult<PageResult<KbVOs.ArtigoResumo>> buscar(@Valid KbVOs.BuscaReq req,
                                                               @RequestParam(value = "gestao", defaultValue = "false") boolean gestao) {
        return success(kbService.buscar(req, getLoginUserId(), gestao));
    }

    @GetMapping("/sugestoes")
    @Operation(summary = "Até 5 artigos parecidos com o texto (para quem está abrindo um chamado)")
    public CommonResult<List<KbVOs.ArtigoResumo>> sugestoes(@RequestParam("texto") String texto) {
        return success(kbService.sugestoes(texto.length() > 300 ? texto.substring(0, 300) : texto, getLoginUserId()));
    }

    @GetMapping("/get")
    @Operation(summary = "Ler artigo (conta uma visualização)")
    public CommonResult<KbVOs.Artigo> ler(@RequestParam("id") Long id,
                                          @RequestParam(value = "contar", defaultValue = "true") boolean contar) {
        return success(kbService.ler(id, getLoginUserId(), contar));
    }

    @PostMapping("/votar")
    @Operation(summary = "Este artigo ajudou?")
    public CommonResult<Boolean> votar(@Valid @RequestBody KbVOs.VotoReq req) {
        kbService.votar(req.getArtigoId(), req.getUtil(), getLoginUserId());
        return success(true);
    }

    // -------- escrita --------

    @PostMapping("/categoria/save")
    @Operation(summary = "Criar ou editar categoria da base")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Long> salvarCategoria(@Valid @RequestBody KbVOs.CategoriaSalvar req) {
        return success(kbService.salvarCategoria(req));
    }

    @DeleteMapping("/categoria/delete")
    @Operation(summary = "Excluir categoria vazia")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Boolean> excluirCategoria(@RequestParam("id") Long id) {
        kbService.excluirCategoria(id);
        return success(true);
    }

    @PostMapping("/save")
    @Operation(summary = "Criar ou editar artigo (guarda a versão anterior)")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Long> salvar(@Valid @RequestBody KbVOs.ArtigoSalvar req) {
        return success(kbService.salvar(req, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "Excluir artigo")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Boolean> excluir(@RequestParam("id") Long id) {
        kbService.excluir(id);
        return success(true);
    }

    @GetMapping("/revisoes")
    @Operation(summary = "Versões anteriores do artigo")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<List<KbVOs.Revisao>> revisoes(@RequestParam("id") Long id) {
        return success(kbService.revisoes(id));
    }

    @PostMapping("/restaurar")
    @Operation(summary = "Voltar o artigo para uma revisão")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Boolean> restaurar(@RequestParam("revisaoId") Long revisaoId) {
        kbService.restaurar(revisaoId, getLoginUserId());
        return success(true);
    }

    // -------- chamados --------

    @PostMapping("/usar-no-chamado")
    @Operation(summary = "Registrar o artigo como solução ou acompanhamento do chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:atender')")
    public CommonResult<Boolean> usarNoChamado(@Valid @RequestBody KbVOs.UsarNoChamadoReq req) {
        kbService.usarNoChamado(req, getLoginUserId());
        return success(true);
    }

    @PostMapping("/criar-do-chamado")
    @Operation(summary = "Rascunho de artigo a partir da solução do chamado")
    @PreAuthorize("@ss.hasPermission('itsm:kb:update')")
    public CommonResult<Long> criarDoChamado(@RequestParam("chamadoId") Long chamadoId) {
        return success(kbService.criarDoChamado(chamadoId, getLoginUserId()));
    }

    @GetMapping("/do-chamado")
    @Operation(summary = "Artigos usados no chamado")
    @PreAuthorize("@ss.hasPermission('itsm:chamado:query')")
    public CommonResult<List<KbVOs.ArtigoResumo>> doChamado(@RequestParam("id") Long id) {
        return success(kbService.doChamado(id, getLoginUserId()));
    }

}
