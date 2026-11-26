package cn.iocoder.yudao.module.identidade.config;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.regex.Pattern;

/**
 * Exportar e importar planilhas (e os modelos de importação) é só do perfil Administrador, em todas as telas. As telas
 * escondem os botões; aqui a API recusa, para que o botão escondido não vire caminho por fora.
 *
 * <p>Vale para os endpoints cujo caminho tem um trecho {@code export}, {@code exportar}, {@code import}, {@code importar}
 * (ou começando por eles com hífen, como {@code export-excel}) ou {@code get-import-template}.
 */
@Configuration
public class PlanilhasSoAdministrador implements WebMvcConfigurer, HandlerInterceptor {

    static final String PERFIL = "super_admin";
    private static final Pattern PLANILHA = Pattern.compile("(^|/)((export|exportar|import|importar)(-[^/]*)?|get-import-template)(/|$)");

    @Resource
    private PermissionCommonApi permissionApi;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/admin-api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!ehPlanilha(request.getRequestURI())) {
            return true;
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId != null && permissionApi.hasAnyRoles(userId, PERFIL)) {
            return true;
        }
        ServletUtils.writeJSON(response, CommonResult.error(GlobalErrorCodeConstants.FORBIDDEN.getCode(),
                "Exportar e importar planilhas é só do perfil Administrador"));
        return false;
    }

    static boolean ehPlanilha(String uri) {
        return uri != null && PLANILHA.matcher(uri).find();
    }

}
