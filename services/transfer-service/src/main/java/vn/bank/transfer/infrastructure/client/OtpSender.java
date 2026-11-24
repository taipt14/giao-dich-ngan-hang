package vn.bank.transfer.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.bank.transfer.config.OtpProperties;

/**
 * Gui OTP cho khach hang. Chua tich hop SMS (ngoai pham vi BANK-042): o moi truong dev
 * ({@code bank.transfer.otp.dev-log-enabled=true}) ghi OTP dang ro ra log, danh dau [DEV]; moi truong khac chi ghi
 * nhan da gui, khong bao gio ghi OTP (muc 13 convention).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtpSender {

    private final OtpProperties properties;

    public void send(String transferId, String otp) {
        if (properties.devLogEnabled()) {
            log.info("[DEV] OTP for transfer {}: {} (DEV only, not sent by SMS)", transferId, otp);
            return;
        }
        log.info("OTP sent for transfer {}", transferId);
    }
}
