package com.meridian.banking.service;

import com.meridian.banking.dto.AmountRequest;
import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountStatus;
import com.meridian.banking.entity.AccountTransaction;
import com.meridian.banking.entity.TransactionType;
import com.meridian.banking.exception.AccountNotOperableException;
import com.meridian.banking.exception.InsufficientFundsException;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.mapper.TransactionMapper;
import com.meridian.banking.repository.AccountRepository;
import com.meridian.banking.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AccountTransactionService {

    private static final Logger log = LoggerFactory.getLogger(AccountTransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final ReferenceGenerator referenceGenerator;

    public AccountTransactionService(AccountRepository accountRepository,
                                     TransactionRepository transactionRepository,
                                     TransactionMapper transactionMapper,
                                     ReferenceGenerator referenceGenerator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
        this.referenceGenerator = referenceGenerator;
    }

    @Transactional
    public TransactionResponse deposit(Long customerId, Long accountId, AmountRequest request) {
        Account account = loadOperableAccount(customerId, accountId);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        AccountTransaction transaction = record(account, null, TransactionType.DEPOSIT, amount,
                account.getBalance(), referenceGenerator.next("DEP"), request.getDescription());

        log.info("Deposited {} into {}, balance is now {}", amount, account.getAccountNumber(), account.getBalance());
        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(Long customerId, Long accountId, AmountRequest request) {
        Account account = loadOperableAccount(customerId, accountId);
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(account.getAccountNumber(), amount, account.getBalance());
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        AccountTransaction transaction = record(account, null, TransactionType.WITHDRAWAL, amount,
                account.getBalance(), referenceGenerator.next("WDR"), request.getDescription());

        log.info("Withdrew {} from {}, balance is now {}", amount, account.getAccountNumber(), account.getBalance());
        return transactionMapper.toResponse(transaction);
    }

    private Account loadOperableAccount(Long customerId, Long accountId) {
        Account account = accountRepository.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotOperableException(account.getAccountNumber(), account.getStatus().name());
        }
        return account;
    }

    private AccountTransaction record(Account account,
                                      Account counterparty,
                                      TransactionType type,
                                      BigDecimal amount,
                                      BigDecimal balanceAfter,
                                      String reference,
                                      String description) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setReference(reference);
        transaction.setAccount(account);
        transaction.setCounterpartyAccount(counterparty);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setDescription(description);
        return transactionRepository.save(transaction);
    }
}
