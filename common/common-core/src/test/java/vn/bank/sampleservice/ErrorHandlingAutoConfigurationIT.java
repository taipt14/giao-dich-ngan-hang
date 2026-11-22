package vn.bank.sampleservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.bank.common.web.GlobalExceptionHandler;

@SpringBootTest(classes = SampleServiceApplication.class)
class ErrorHandlingAutoConfigurationIT {

    private final WebApplicationContext context;

    private MockMvc mockMvc;

    ErrorHandlingAutoConfigurationIT(@Autowired WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void should_registerHandlerAutomatically_when_serviceOnlyAddsCommonCoreDependency() {
        // given: SampleServiceApplication khong khai bao GlobalExceptionHandler

        // when
        var handlers = context.getBeansOfType(GlobalExceptionHandler.class);

        // then
        assertThat(handlers).hasSize(1);
    }

    @Test
    void should_returnStandardErrorResponse_when_businessExceptionInService() throws Exception {
        // given: controller nem BusinessException

        // when
        var result = mockMvc.perform(get("/business"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Rejected by rule"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void should_return500StandardErrorResponse_when_unexpectedExceptionInService() throws Exception {
        // given: controller nem IllegalStateException

        // when
        var result = mockMvc.perform(get("/unexpected"));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
