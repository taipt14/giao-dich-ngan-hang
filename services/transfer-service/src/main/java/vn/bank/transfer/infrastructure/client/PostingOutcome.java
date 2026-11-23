package vn.bank.transfer.infrastructure.client;

/**
 * Ket qua chac chan cua mot lan posting: da ghi (journal moi hoac da co tu truoc), hoac bi account-service tu choi kem
 * ma loi ({@code rejectionCode}) va khong ghi gi. Loi ky thuat (ket qua chua biet) khong nam o day ma la exception.
 */
public record PostingOutcome(boolean posted, String rejectionCode) {

    public static PostingOutcome success() {
        return new PostingOutcome(true, null);
    }

    public static PostingOutcome rejected(String rejectionCode) {
        return new PostingOutcome(false, rejectionCode);
    }
}
