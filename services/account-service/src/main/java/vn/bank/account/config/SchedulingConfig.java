package vn.bank.account.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Bat {@code @Scheduled} va cau hinh lich doi soat cuoi ngay. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReconcileProperties.class)
class SchedulingConfig {}
