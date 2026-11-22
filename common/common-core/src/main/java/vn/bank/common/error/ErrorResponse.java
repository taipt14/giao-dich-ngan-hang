package vn.bank.common.error;

import java.time.Instant;
import java.util.List;

/** Format loi chuan cua moi service. {@code details} khong bao gio null. */
public record ErrorResponse(String code, String message, String traceId, Instant timestamp, List<ErrorDetail> details) {

    public ErrorResponse {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
