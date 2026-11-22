package vn.bank.common.web;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/** Tu dang ky {@link GlobalExceptionHandler} cho moi service servlet co dependency common-core. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class ErrorHandlingAutoConfiguration {

    // Dung Clock cua service neu co (de test duoc), khong thi dung gio he thong UTC
    @Bean
    @ConditionalOnMissingBean
    GlobalExceptionHandler globalExceptionHandler(ObjectProvider<Clock> clock) {
        return new GlobalExceptionHandler(clock.getIfAvailable(Clock::systemUTC));
    }
}
