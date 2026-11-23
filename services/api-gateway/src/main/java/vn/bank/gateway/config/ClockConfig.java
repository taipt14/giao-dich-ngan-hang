package vn.bank.gateway.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nguon thoi gian duy nhat cua gateway (muc 4.2 convention), test thay bang Clock.fixed. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
