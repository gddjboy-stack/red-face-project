package com.redface.web;

import com.redface.config.PhotoStorageProperties;
import com.redface.auth.CurrentUserArgumentResolver;
import java.nio.file.Path;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * C9 Web MVC 配置，注册当前用户参数解析器与 Admin 鉴权拦截器。
 *
 * <p>C20-5 追加大屏展示端拦截器：与 Admin 拦截器各管一条路径前缀，互不认对方的凭证。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final DisplayAuthInterceptor displayAuthInterceptor;
    private final PhotoStorageProperties photoStorageProperties;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                     AdminAuthInterceptor adminAuthInterceptor,
                     DisplayAuthInterceptor displayAuthInterceptor,
                     PhotoStorageProperties photoStorageProperties) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.displayAuthInterceptor = displayAuthInterceptor;
        this.photoStorageProperties = photoStorageProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }



    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Path.of(photoStorageProperties.getUploadDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(photoStorageProperties.normalizedResourcePattern())
                .addResourceLocations(uploadDir.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅拦截场控后台接口；用户端 /api/live、/api/popularity、/api/me 等不受影响。
        // /actuator/health 不在 /api/admin 路径下，天然放行供探针匿名访问。
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");
        // C20-5 大屏展示端：独立拦截器，fail-closed 且仅允许 GET/HEAD。
        // 换票端点在 /api/display-auth/**，不落在此模式内，因此这里无需任何 exclude 例外。
        registry.addInterceptor(displayAuthInterceptor)
                .addPathPatterns("/api/display/**");
    }
}
