package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountNumberMasker;
import vn.bank.account.domain.CashDeposit;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

/**
 * Nhan vien nap tien mat tai quay. Ghi so qua {@link SubmitPostingService} (cung luong voi {@code POST
 * /internal/postings}, goi thang trong cung service thay vi qua HTTP): journal DEPOSIT voi REFERENCE_ID =
 * Idempotency-Key nen UK cua JOURNAL chong nap trung, ke ca hai request cung key den dong thoi. Khong co transaction
 * rieng: transaction ghi tien nam trong {@link PostingService#post}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateDepositService {

    private final AccountRepository accountRepository;
    private final SubmitPostingService submitPostingService;

    /**
     * Tai khoan khong ton tai → {@code ACCOUNT_NOT_FOUND}; khong ACTIVE → {@code ACCOUNT_NOT_ACTIVE}. Idempotency-Key da
     * nap: tra lai lan nap cu neu cung tai khoan va so tien, khac → {@code IDEMPOTENCY_KEY_REUSED}.
     */
    public DepositResult deposit(DepositCommand command) {
        var deposit = new CashDeposit(command.accountNo(), command.amount());
        deposit.requireCustomerAccount(getAccount(command.accountNo()));
        var posting = submitPostingService.submit(new PostingCommand(
                ReferenceType.DEPOSIT, command.idempotencyKey(), command.description(), deposit.toPostingLines()));
        if (!posting.created() && !deposit.isRecordedBy(posting.entries())) {
            throw new BusinessException(
                    AccountErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key was already used for a different deposit");
        }
        var journalId = posting.journal().getId();
        var maskedAccountNo = AccountNumberMasker.mask(command.accountNo());
        if (posting.created()) {
            log.info("Cash deposit journal {} credited account {}", journalId, maskedAccountNo);
        } else {
            log.warn("Repeated cash deposit for account {} returned existing journal {}", maskedAccountNo, journalId);
        }
        return new DepositResult(posting.journal(), deposit.creditEntryOf(posting.entries()), posting.created());
    }

    private Account getAccount(String accountNo) {
        return accountRepository
                .findById(accountNo)
                .orElseThrow(() -> new BusinessException(
                        AccountErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found: " + AccountNumberMasker.mask(accountNo)));
    }
}
