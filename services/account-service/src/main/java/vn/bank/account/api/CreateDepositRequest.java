package vn.bank.account.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Body {@code POST /api/v1/accounts/{accountNo}/deposits}. Chi kiem tra dinh dang; han muc mot lan nap kiem tra o
 * domain ({@code DEPOSIT_LIMIT_EXCEEDED}). {@code description} vua cot JOURNAL.DESCRIPTION (255 CHAR).
 */
public record CreateDepositRequest(
        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @Size(max = 255) String description) {}
