package vn.bank.customer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.bank.customer.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    boolean existsByIdNumber(String idNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndCifNot(String phoneNumber, String cif);

    @Query(value = "SELECT SEQ_CIF.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextCifSequence();
}
