package vn.bank.account.application;

import java.util.List;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;

/** Yeu cau ghi mot journal: cac dong No/Co cua nghiep vu {@code (referenceType, referenceId)}. */
public record PostingCommand(
        ReferenceType referenceType, String referenceId, String description, List<PostingLine> lines) {

    public PostingCommand {
        lines = List.copyOf(lines);
    }
}
