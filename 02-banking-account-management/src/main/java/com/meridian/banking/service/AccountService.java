package com.meridian.banking.service;

import com.meridian.banking.config.BankingProperties;
import com.meridian.banking.dto.AccountResponse;
import com.meridian.banking.dto.OpenAccountRequest;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountStatus;
import com.meridian.banking.entity.AccountType;
import com.meridian.banking.entity.Customer;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.mapper.AccountMapper;
import com.meridian.banking.repository.AccountRepository;
import com.meridian.banking.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AccountMapper accountMapper;
    private final BankingProperties properties;

    public AccountService(AccountRepository accountRepository,
                          CustomerRepository customerRepository,
                          AccountMapper accountMapper,
                          BankingProperties properties) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.accountMapper = accountMapper;
        this.properties = properties;
    }

    @Transactional
    public AccountResponse openAccount(Long customerId, OpenAccountRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        BigDecimal opening = request.getOpeningBalance().setScale(2, RoundingMode.HALF_UP);
        BigDecimal minimum = properties.getAccount().getMinimumOpeningBalance();
        if (opening.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Opening balance must be at least " + minimum);
        }

        Account account = new Account();
        account.setAccountNumber(nextAccountNumber());
        account.setCustomer(customer);
        account.setAccountType(AccountType.valueOf(request.getAccountType()));
        account.setCurrency(request.getCurrency());
        account.setBalance(opening);
        account.setStatus(AccountStatus.ACTIVE);

        Account saved = accountRepository.save(account);
        log.info("Opened {} account {} for customer {}", saved.getAccountType(), saved.getAccountNumber(), customerId);
        return accountMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts(Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerIdOrderByOpenedAtAsc(customerId);
        return accountMapper.toResponses(accounts);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId, Long customerId) {
        Account account = accountRepository.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        return accountMapper.toResponse(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse freezeAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        account.setStatus(AccountStatus.FROZEN);
        accountRepository.save(account);

        log.info("Account {} frozen by the back office", accountNumber);
        return accountMapper.toResponse(account);
    }

    private String nextAccountNumber() {
        String candidate;
        do {
            candidate = properties.getAccount().getNumberPrefix()
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(1, 9999))
                    + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(1000, 9999));
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
