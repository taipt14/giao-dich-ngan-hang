package vn.bank.transfer.infrastructure.client;

import java.util.List;

/** Body {@code POST /internal/postings} cua account-service. */
record CreatePostingRequest(
        String referenceType, String referenceId, String description, List<PostingLineRequest> lines) {}
