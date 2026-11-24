package vn.bank.transfer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bat cau hinh REST client goi account-service, retry posting, key idempotency va OTP trong Redis. */
@Configuration
@EnableConfigurationProperties({
    AccountServiceProperties.class,
    IdempotencyProperties.class,
    OtpProperties.class,
    PostingProperties.class
})
class TransferPropertiesConfig {}
