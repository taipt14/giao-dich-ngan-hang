package vn.bank.sampleservice;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.CommonErrorCode;

/**
 * Gia lap mot service chi them dependency common-core: khong khai bao handler, khong scan package
 * {@code vn.bank.common}.
 */
@SpringBootApplication
class SampleServiceApplication {

    @RestController
    static class SampleController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(CommonErrorCode.VALIDATION_FAILED, "Rejected by rule");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal detail");
        }
    }
}
