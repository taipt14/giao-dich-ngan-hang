package vn.bank.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Bat {@link CustomerServiceProperties} cho REST client goi customer-service. */
@Configuration
@EnableConfigurationProperties(CustomerServiceProperties.class)
class CustomerServiceConfig {}
