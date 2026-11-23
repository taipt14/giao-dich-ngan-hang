package vn.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.common.error.BusinessException;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.customer.domain.CustomerContactUpdate;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

@SpringBootTest
class UpdateCustomerContactServiceIT extends AbstractIntegrationTest {

    private static final long LATCH_TIMEOUT_SECONDS = 30;

    private final UpdateCustomerContactService updateCustomerContactService;
    private final RegisterCustomerService registerCustomerService;
    private final CustomerRepository customerRepository;
    private final TransactionTemplate transactionTemplate;

    UpdateCustomerContactServiceIT(
            @Autowired UpdateCustomerContactService updateCustomerContactService,
            @Autowired RegisterCustomerService registerCustomerService,
            @Autowired CustomerRepository customerRepository,
            @Autowired PlatformTransactionManager transactionManager) {
        this.updateCustomerContactService = updateCustomerContactService;
        this.registerCustomerService = registerCustomerService;
        this.customerRepository = customerRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Moi "request" chay trong transaction rieng, doc khach hang truoc roi cho request kia cung doc xong moi cap nhat:
     * ca hai deu giu VERSION cu. Request flush truoc giu khoa dong; request con lai cho khoa, sau khi request dau commit
     * thi UPDATE ... WHERE VERSION = cu khong khop dong nao nen bi tu choi.
     */
    @Test
    void should_rejectOneRequestWithConcurrentModification_when_twoUpdatesRunConcurrently() throws Exception {
        // given
        var cif = registerCustomer();
        var initialVersion = customerRepository.findById(cif).orElseThrow().getVersion();
        var bothLoaded = new CountDownLatch(2);

        // when
        List<Throwable> failures;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.runAsync(
                    () -> updateAfterBothLoaded(cif, "first@example.com", bothLoaded), executor);
            var second = CompletableFuture.runAsync(
                    () -> updateAfterBothLoaded(cif, "second@example.com", bothLoaded), executor);
            failures = collectFailures(first, second);
        }

        // then
        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst())
                .isInstanceOf(BusinessException.class)
                .hasCauseInstanceOf(OptimisticLockingFailureException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CONCURRENT_MODIFICATION);
        var saved = customerRepository.findById(cif).orElseThrow();
        assertThat(saved.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(saved.getEmail()).isIn("first@example.com", "second@example.com");
    }

    private void updateAfterBothLoaded(String cif, String email, CountDownLatch bothLoaded) {
        transactionTemplate.executeWithoutResult(status -> {
            // Nap khach hang vao persistence context cua transaction nay: service dung lai ban da nap (VERSION cu)
            customerRepository.findById(cif).orElseThrow();
            bothLoaded.countDown();
            awaitLatch(bothLoaded);
            updateCustomerContactService.updateContact(cif, new CustomerContactUpdate(null, email, null));
        });
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Other request did not load customer in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for other request", e);
        }
    }

    private static List<Throwable> collectFailures(CompletableFuture<?>... futures) {
        var failures = new ArrayList<Throwable>();
        for (var future : futures) {
            try {
                future.join();
            } catch (CompletionException e) {
                failures.add(e.getCause());
            }
        }
        return failures;
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: tu sinh CCCD, so dien thoai rieng
    private String registerCustomer() {
        var random = ThreadLocalRandom.current();
        var idNumber = "%012d".formatted(random.nextLong(1_000_000_000_000L));
        var phoneNumber = "09%08d".formatted(random.nextInt(100_000_000));
        var profile = new CustomerProfile(
                "Nguyen Van A",
                LocalDate.of(1990, 5, 15),
                idNumber,
                phoneNumber,
                "a@example.com",
                "1 Ly Thai To, Ha Noi");
        return registerCustomerService.register(profile).getCif();
    }
}
