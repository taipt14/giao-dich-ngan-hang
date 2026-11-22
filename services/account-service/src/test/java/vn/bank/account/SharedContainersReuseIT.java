package vn.bank.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Test class thu hai ke thua {@link AbstractIntegrationTest}: cung voi {@link AccountServiceIntegrationSampleIT}
 * chung minh container khong bi khoi dong lai khi chay test class khac trong cung JVM.
 */
@SpringBootTest
class SharedContainersReuseIT extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    SharedContainersReuseIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void should_reuseContainersOfFirstTestClass_when_secondTestClassRunsInSameJvm() {
        // given
        var current = SharedContainerFingerprints.fingerprintsOf(ORACLE, KAFKA, REDIS);

        // when
        var firstSeen = SharedContainerFingerprints.firstSeenOrRecord(current);

        // then
        assertThat(current).isEqualTo(firstSeen);
        assertThat(ORACLE.isRunning() && KAFKA.isRunning() && REDIS.isRunning()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select 1 from dual", Integer.class)).isEqualTo(1);
    }
}
