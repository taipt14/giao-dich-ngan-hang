package vn.bank.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;
import vn.bank.common.error.TechnicalException;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final String SECRET_DETAIL = "db password leaked";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var handler = new GlobalExceptionHandler(Clock.fixed(NOW, ZoneOffset.UTC));
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(handler)
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void should_returnStatusAndCodeOfErrorCode_when_businessExceptionThrown() throws Exception {
        // given
        MDC.put(GlobalExceptionHandler.TRACE_ID_MDC_KEY, "trace-123");

        // when
        var result = mockMvc.perform(get("/business"));

        // then
        result.andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message").value("Account is LOCKED"))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-21T03:00:00Z"))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void should_returnStatusOfErrorCode_when_technicalExceptionThrown() throws Exception {
        // given: controller nem TechnicalException boc IOException

        // when
        var result = mockMvc.perform(get("/technical"));

        // then
        result.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("NAPAS_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Napas timeout"))
                .andExpect(content().string(not(containsString("IOException"))));
    }

    @Test
    void should_return400WithFieldDetails_when_requestBodyInvalid() throws Exception {
        // given
        var body = """
                {"name": " ", "amount": -1}
                """;

        // when
        var result = mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.VALIDATION_FAILED_MESSAGE))
                .andExpect(jsonPath("$.details[*].field").value(containsInAnyOrder("name", "amount")));
    }

    @Test
    void should_return400WithParameterDetails_when_requestParamInvalid() throws Exception {
        // given: size toi da 100

        // when
        var result = mockMvc.perform(get("/params").param("size", "101"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void should_return400BadRequest_when_requiredHeaderMissing() throws Exception {
        // given: khong gui header Idempotency-Key

        // when
        var result = mockMvc.perform(get("/header"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("Idempotency-Key")));
    }

    @Test
    void should_return400BadRequest_when_requestBodyMalformed() throws Exception {
        // given
        var body = "{not-json";

        // when
        var result = mockMvc.perform(post("/validated").contentType(MediaType.APPLICATION_JSON).content(body));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.MALFORMED_REQUEST_MESSAGE));
    }

    @Test
    void should_return400BadRequest_when_requestParamHasWrongType() throws Exception {
        // given: size phai la so

        // when
        var result = mockMvc.perform(get("/params").param("size", "abc"));

        // then
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void should_return500WithoutInternalDetails_when_unexpectedExceptionThrown() throws Exception {
        // given: controller nem IllegalStateException chua thong tin noi bo

        // when
        var response = mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // then
        assertThat(response)
                .doesNotContain(SECRET_DETAIL)
                .doesNotContain("IllegalStateException")
                .doesNotContain("at vn.bank");
    }

    @Test
    void should_returnNullTraceId_when_mdcHasNoTraceId() throws Exception {
        // given: MDC rong

        // when
        var result = mockMvc.perform(get("/business"));

        // then
        result.andExpect(jsonPath("$.traceId").doesNotExist());
    }

    enum TestErrorCode implements ErrorCode {
        ACCOUNT_NOT_ACTIVE(422),
        NAPAS_UNAVAILABLE(503);

        private final int httpStatus;

        TestErrorCode(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        @Override
        public String code() {
            return name();
        }

        @Override
        public int httpStatus() {
            return httpStatus;
        }
    }

    record SampleRequest(@NotBlank String name, @Positive BigDecimal amount) {}

    @RestController
    static class FailingController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestErrorCode.ACCOUNT_NOT_ACTIVE, "Account is LOCKED");
        }

        @GetMapping("/technical")
        void technical() {
            throw new TechnicalException(
                    TestErrorCode.NAPAS_UNAVAILABLE, "Napas timeout", new IOException("connection reset"));
        }

        // Body rong: endpoint chi de kich hoat validate request body
        @PostMapping("/validated")
        void validated(@Valid @RequestBody SampleRequest request) {}

        @GetMapping("/params")
        void params(@RequestParam @Max(100) int size) {}

        @GetMapping("/header")
        void header(@RequestHeader("Idempotency-Key") String idempotencyKey) {}

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException(SECRET_DETAIL);
        }
    }
}
