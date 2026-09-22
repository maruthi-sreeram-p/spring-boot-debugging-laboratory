package com.meridian.banking.repository;

import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountStatus;
import com.meridian.banking.entity.AccountType;
import com.meridian.banking.entity.Customer;
import com.meridian.banking.entity.CustomerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void scopesAccountLookupToTheOwningCustomer() {
        Customer owner = persistCustomer("hana.suzuki@example.com", "NID-1111-2222");
        Customer stranger = persistCustomer("liam.byrne@example.com", "NID-3333-4444");

        Account account = new Account();
        account.setAccountNumber("MB-9999-0001");
        account.setCustomer(owner);
        account.setAccountType(AccountType.SAVINGS);
        account.setCurrency("INR");
        account.setBalance(new BigDecimal("1200.00"));
        account.setStatus(AccountStatus.ACTIVE);
        entityManager.persistAndFlush(account);
        entityManager.clear();

        assertThat(accountRepository.findByIdAndCustomerId(account.getId(), owner.getId())).isPresent();
        assertThat(accountRepository.findByIdAndCustomerId(account.getId(), stranger.getId())).isEmpty();
        assertThat(accountRepository.findByAccountNumber("MB-9999-0001")).isPresent();
    }

    private Customer persistCustomer(String email, String nationalId) {
        Customer customer = new Customer();
        customer.setEmail(email);
        customer.setPasswordHash("$2a$10$notarealhash");
        customer.setFullName(email.substring(0, email.indexOf('@')));
        customer.setNationalId(nationalId);
        customer.setStatus(CustomerStatus.ACTIVE);
        return entityManager.persistAndFlush(customer);
    }
}
