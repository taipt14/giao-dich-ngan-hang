package vn.bank.account;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.testcontainers.containers.GenericContainer;

/**
 * Luu "dau van tay" (container id + thoi diem Docker start) cua container dung chung tai lan dau mot test class doc
 * no trong JVM. Test class doc sau so sanh voi gia tri nay: container bi tao lai thi doi id, bi restart thi doi thoi
 * diem start. Khong phu thuoc thu tu chay: class nao chay truoc thi ghi, class sau so sanh.
 */
final class SharedContainerFingerprints {

    private static final AtomicReference<List<String>> FIRST_SEEN = new AtomicReference<>();

    private SharedContainerFingerprints() {
    }

    /** Tra ve dau van tay ghi lan dau trong JVM (ghi {@code current} neu chua co). */
    static List<String> firstSeenOrRecord(List<String> current) {
        FIRST_SEEN.compareAndSet(null, current);
        return FIRST_SEEN.get();
    }

    static List<String> fingerprintsOf(GenericContainer<?>... containers) {
        return Arrays.stream(containers).map(SharedContainerFingerprints::fingerprintOf).toList();
    }

    private static String fingerprintOf(GenericContainer<?> container) {
        // Doc lai tu Docker thay vi cache cua Testcontainers de thay duoc lan restart neu co
        var startedAt = container.getDockerClient()
                .inspectContainerCmd(container.getContainerId())
                .exec()
                .getState()
                .getStartedAt();
        return container.getContainerId() + "@" + startedAt;
    }
}
