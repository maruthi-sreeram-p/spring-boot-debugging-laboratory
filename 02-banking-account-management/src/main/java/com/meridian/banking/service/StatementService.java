package com.meridian.banking.service;

import com.meridian.banking.dto.PagedResponse;
import com.meridian.banking.dto.TransactionResponse;
import com.meridian.banking.entity.Account;
import com.meridian.banking.entity.AccountTransaction;
import com.meridian.banking.exception.ResourceNotFoundException;
import com.meridian.banking.mapper.TransactionMapper;
import com.meridian.banking.repository.AccountRepository;
import com.meridian.banking.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class StatementService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public StatementService(AccountRepository accountRepository,
                            TransactionRepository transactionRepository,
                            TransactionMapper transactionMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> statement(Long customerId,
                                                        Long accountId,
                                                        LocalDate from,
                                                        LocalDate to,
                                                        Pageable pageable) {
        Account account = accountRepository.findByIdAndCustomerId(accountId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        Page<AccountTransaction> page;
        if (from == null || to == null) {
            page = transactionRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable);
        } else {
            Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant toInstant = to.atStartOfDay(ZoneOffset.UTC).toInstant();
            page = transactionRepository.findByAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    account.getId(), fromInstant, toInstant, pageable);
        }

        return PagedResponse.of(page, transactionMapper.toResponses(page.getContent()));
    }
}
