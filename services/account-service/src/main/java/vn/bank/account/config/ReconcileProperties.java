package vn.bank.account.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * Lich doi soat cuoi ngay (BANK-034). {@code cron} theo gio {@code Asia/Ho_Chi_Minh}, mac dinh 23:55 hang ngay;
 * {@code "-"} tat lich (van chay thu cong duoc qua API). Sai cu phap thi service dung khoi dong thay vi bo lich am
 * tham.
 */
@Validated
@ConfigurationProperties(prefix = ReconcileProperties.PREFIX)
public record ReconcileProperties(@NotBlank String cron) {

    public static final String PREFIX = "bank.account.reconcile";

    /** Placeholder cho {@code @Scheduled(cron = ...)}: annotation chi nhan hang so, doc cung key voi record nay. */
    public static final String CRON_PLACEHOLDER = "${" + PREFIX + ".cron}";

    public ReconcileProperties {
        if (cron != null && !Scheduled.CRON_DISABLED.equals(cron) && !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron expression for " + PREFIX + ".cron: " + cron);
        }
    }
}
