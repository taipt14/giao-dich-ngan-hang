package vn.bank.transfer.infrastructure.client;

/**
 * Cac truong can dung trong response cua {@code GET /internal/accounts/{accountNo}} (account-service); cac truong con
 * lai (so du, loai tai khoan...) bo qua. {@code cif} null voi tai khoan GL.
 */
record AccountInfoResponse(String accountNo, String cif, String status) {}
