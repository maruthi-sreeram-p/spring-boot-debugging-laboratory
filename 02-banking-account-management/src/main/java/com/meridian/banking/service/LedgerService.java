package com.meridian.banking.service;

import com.meridian.banking.entity.Account;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Low level balance movements. The credit leg runs in its own transaction so that an
 * incoming payment is durable as soon as it has been applied, independently of whatever
 * bookkeeping the calling operation still has to finish.
 */
@Service
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);

    private final AccountRepository accountRepository;

    public LedgerService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BigDecimal creditAccount(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        log.debug("Credited {} to account {}, balance is now {}",
                amount, account.getAccountNumber(), account.getBalance());
        return account.getBalance();
    }
}
