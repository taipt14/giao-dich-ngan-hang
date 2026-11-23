package vn.bank.account.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import vn.bank.account.domain.EntryDirection;

/** Mot dong No/Co cua posting; so tien vua cot NUMBER(19,2) va luon duong (huong tien nam o {@code direction}). */
public record PostingLineRequest(
        @NotBlank @Size(max = 20) String accountNo,
        @NotNull EntryDirection direction,

        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {}
