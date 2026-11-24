package vn.bank.transfer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.bank.transfer.api.RequiredRoleInterceptor;

@Configuration
@RequiredArgsConstructor
class WebMvcConfig implements WebMvcConfigurer {

    private final RequiredRoleInterceptor requiredRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredRoleInterceptor);
    }
}
