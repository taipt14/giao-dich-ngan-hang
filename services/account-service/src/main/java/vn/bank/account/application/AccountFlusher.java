package vn.bank.account.application;

import org.springframework.dao.OptimisticLockingFailureException;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

/**
 * Flush thay doi tai khoan ngay trong method nghiep vu: xung dot optimistic lock ({@code VERSION}) lo ra o day thay vi
 * luc commit (sau khi method da tra ve), nen chuyen duoc thanh 409 {@code CONCURRENT_MODIFICATION}.
 */
final class AccountFlusher {

    private AccountFlusher() {}

    /** {@code subject} mo ta du lieu dang ghi de dua vao message loi, vi du "Account 1000****5678". */
    static void flush(AccountRepository accountRepository, String subject) {
        try {
            accountRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(
                    AccountErrorCode.CONCURRENT_MODIFICATION,
                    subject + " was modified by another request, please retry",
                    e);
        }
    }
}
