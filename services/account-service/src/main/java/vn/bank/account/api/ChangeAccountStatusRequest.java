package vn.bank.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body chung cua lock/unlock/close: chi can ly do, trang thai dich lay tu URL. */
public record ChangeAccountStatusRequest(
        @NotBlank @Size(max = 255) String reason) {}
