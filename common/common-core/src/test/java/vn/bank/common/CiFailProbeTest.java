package vn.bank.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Test tam thoi de kiem AC "test fail -> workflow fail" cua BANK-007, se revert ngay
class CiFailProbeTest {

    @Test
    void should_failBuild_when_testFails() {
        assertThat(1).isEqualTo(2);
    }
}
