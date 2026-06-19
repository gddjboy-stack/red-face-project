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
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final PhotoStorageProperties photoStorageProperties;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver,
                     AdminAuthInterceptor adminAuthInterceptor,
                     PhotoStorageProperties photoStorageProperties) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
        this.adminAuthInterceptor = adminAuthInterceptor;
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
    }
}
