package vn.bank.account.api;

import java.time.Instant;
import java.util.List;
import vn.bank.account.domain.ReferenceType;

/** Journal da ghi (moi hoac da co tu truoc) va cac dong so cai theo thu tu ghi. */
public record PostingResponse(
        Long journalId,
        ReferenceType referenceType,
        String referenceId,
        String description,
        Instant createdAt,
        List<LedgerEntryResponse> entries) {}
