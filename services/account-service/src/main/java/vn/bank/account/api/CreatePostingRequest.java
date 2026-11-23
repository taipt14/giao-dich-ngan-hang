package vn.bank.account.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import vn.bank.account.domain.ReferenceType;

/**
 * Body {@code POST /internal/postings}. Chi kiem tra dinh dang; tong No = tong Co va trang thai tai khoan kiem tra o
 * domain. Kich thuoc theo cot JOURNAL (REFERENCE_ID 64, DESCRIPTION 255 CHAR).
 */
public record CreatePostingRequest(
        @NotNull ReferenceType referenceType,
        @NotBlank @Size(max = 64) String referenceId,
        @Size(max = 255) String description,
        @NotEmpty List<@Valid @NotNull PostingLineRequest> lines) {}
