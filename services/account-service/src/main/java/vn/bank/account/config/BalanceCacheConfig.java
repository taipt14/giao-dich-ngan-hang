package vn.bank.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bat {@link BalanceCacheProperties} cho cache so du Redis. */
@Configuration
@EnableConfigurationProperties(BalanceCacheProperties.class)
class BalanceCacheConfig {}
