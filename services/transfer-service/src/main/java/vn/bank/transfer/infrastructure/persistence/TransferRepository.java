package vn.bank.transfer.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferStatus;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    // Tham so null nghia la khong loc theo dieu kien do (BANK-047); dung IX_TRANSFER_CIF_CREATED_AT
    String CUSTOMER_FILTER = """
            from Transfer t
            where t.cif = :cif
              and (:status is null or t.status = :status)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt < :to)""";

    /** Lenh da tao bang Idempotency-Key cua khach hang (duy nhat theo UK_TRANSFER_CIF_IDEM_KEY). */
    Optional<Transfer> findByCifAndIdempotencyKey(String cif, String idempotencyKey);

    /**
     * Tong so tien cac lenh tu {@code sourceAccount} dang o {@code statuses} va da giu cho han muc ngay (xac thuc OTP:
     * OTP_PENDING → PROCESSING) trong [{@code from}, {@code to}). Dung de tinh lai han muc khi Redis mat key (BANK-043).
     */
    @Query("""
            select coalesce(sum(t.amount), 0) from Transfer t join t.statusHistory h
            where t.sourceAccount = :sourceAccount and t.status in :statuses
              and h.previousStatus = vn.bank.transfer.domain.TransferStatus.OTP_PENDING
              and h.status = vn.bank.transfer.domain.TransferStatus.PROCESSING
              and h.changedAt >= :from and h.changedAt < :to""")
    BigDecimal sumAmountReservedBetween(
            @Param("sourceAccount") String sourceAccount,
            @Param("statuses") Collection<TransferStatus> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Thoi diem lenh giu cho han muc ngay (xac thuc OTP: OTP_PENDING → PROCESSING); rong neu chua tung giu. */
    @Query("""
            select h.changedAt from Transfer t join t.statusHistory h
            where t.transferId = :transferId
              and h.previousStatus = vn.bank.transfer.domain.TransferStatus.OTP_PENDING
              and h.status = vn.bank.transfer.domain.TransferStatus.PROCESSING""")
    Optional<Instant> findLimitReservedAt(@Param("transferId") String transferId);

    /**
     * Lenh cua khach hang {@code cif}, moi nhat truoc (cung CREATED_AT thi theo TRANSFER_ID giam dan de thu tu on dinh
     * giua cac trang). {@code status}, {@code from} (bao gom), {@code to} (khong bao gom) null → khong loc.
     */
    @Query(
            value = "select t " + CUSTOMER_FILTER + " order by t.createdAt desc, t.transferId desc",
            countQuery = "select count(t) " + CUSTOMER_FILTER)
    Page<Transfer> findPageOfCustomer(
            @Param("cif") String cif,
            @Param("status") TransferStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /** Lenh kem lich su trang thai nap trong cung mot truy van (tranh lazy loading sau khi transaction dong). */
    @Query("select t from Transfer t left join fetch t.statusHistory where t.transferId = :transferId")
    Optional<Transfer> findWithStatusHistoryById(@Param("transferId") String transferId);
}
