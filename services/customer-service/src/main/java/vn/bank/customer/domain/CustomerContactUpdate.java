package vn.bank.customer.domain;

/**
 * Thong tin lien he can cap nhat, da qua kiem tra dinh dang o tang api. Truong {@code null} nghia la giu nguyen gia tri
 * hien tai.
 */
public record CustomerContactUpdate(String phoneNumber, String email, String address) {}
