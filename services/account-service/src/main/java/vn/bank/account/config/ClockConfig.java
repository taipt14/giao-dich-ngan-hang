package vn.bank.account.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nguon thoi gian duy nhat cua service (muc 4.2 convention), test thay bang Clock.fixed. */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
