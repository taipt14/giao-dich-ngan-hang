package vn.bank.customer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.bank.customer.api.RequiredRoleInterceptor;

@Configuration
@RequiredArgsConstructor
class WebMvcConfig implements WebMvcConfigurer {

    private final RequiredRoleInterceptor requiredRoleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requiredRoleInterceptor);
    }
}
