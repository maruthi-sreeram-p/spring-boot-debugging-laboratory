package com.meridian.banking.repository;

import com.meridian.banking.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    Optional<Account> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Account> findByAccountNumberAndCustomerId(String accountNumber, Long customerId);

    List<Account> findByCustomerIdOrderByOpenedAtAsc(Long customerId);

    boolean existsByAccountNumber(String accountNumber);
}
