package vn.bank.transfer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bat cau hinh REST client goi account-service va key idempotency Redis. */
@Configuration
@EnableConfigurationProperties({AccountServiceProperties.class, IdempotencyProperties.class})
class TransferPropertiesConfig {}
