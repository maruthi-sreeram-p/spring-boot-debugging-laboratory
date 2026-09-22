package com.meridian.banking.service;

import com.meridian.banking.config.BankingProperties;
import com.meridian.banking.dto.TransferRequest;
import com.meridian.banking.dto.TransferResponse;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountStatus;
import com.meridian.banking.entity.AccountTransaction;
import com.meridian.banking.entity.TransactionType;
import com.meridian.banking.exception.AccountNotOperableException;
import com.meridian.banking.exception.CurrencyMismatchException;
import com.meridian.banking.exception.DailyLimitExceededException;
import com.meridian.banking.exception.InsufficientFundsException;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.repository.AccountRepository;
import com.meridian.banking.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final ReferenceGenerator referenceGenerator;
    private final BankingProperties properties;

    public TransferService(AccountRepository accountRepository,
                           TransactionRepository transactionRepository,
                           LedgerService ledgerService,
                           ReferenceGenerator referenceGenerator,
                           BankingProperties properties) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.referenceGenerator = referenceGenerator;
        this.properties = properties;
    }

    @Transactional
    public TransferResponse transfer(Long customerId, TransferRequest request) {
        Account source = accountRepository
                .findByAccountNumberAndCustomerId(request.getFromAccountNumber(), customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getFromAccountNumber()));
        Account target = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getToAccountNumber()));

        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different");
        }
        requireOperable(source);
        requireOperable(target);
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new CurrencyMismatchException(source.getCurrency(), target.getCurrency());
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(source.getAccountNumber(), amount, source.getBalance());
        }

        source.setBalance(source.getBalance().subtract(amount));
        accountRepository.save(source);

        BigDecimal targetBalance = ledgerService.creditAccount(target.getId(), amount);

        String reference = referenceGenerator.next(properties.getTransfer().getReferencePrefix());
        record(source, target, TransactionType.TRANSFER_OUT, amount, source.getBalance(),
                reference, request.getDescription());
        record(target, source, TransactionType.TRANSFER_IN, amount, targetBalance,
                reference + "-C", request.getDescription());

        enforceDailyTransferLimit(source);

        log.info("Transfer {} moved {} from {} to {}",
                reference, amount, source.getAccountNumber(), target.getAccountNumber());

        return new TransferResponse(
                reference,
                source.getAccountNumber(),
                target.getAccountNumber(),
                amount,
                source.getBalance(),
                request.getDescription(),
                Instant.now());
    }

    /**
     * Runs once both legs have been booked, so that the running total it reads already
     * includes the transfer currently being processed.
     */
    private void enforceDailyTransferLimit(Account source) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal movedToday = transactionRepository
                .sumByTypeSince(source.getId(), TransactionType.TRANSFER_OUT, startOfDay);
        BigDecimal limit = properties.getTransfer().getDailyLimit();
        if (movedToday.compareTo(limit) > 0) {
            throw new DailyLimitExceededException(movedToday, limit);
        }
    }

    private void requireOperable(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotOperableException(account.getAccountNumber(), account.getStatus().name());
        }
    }

    private void record(Account account,
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
        transactionRepository.save(transaction);
    }
}
