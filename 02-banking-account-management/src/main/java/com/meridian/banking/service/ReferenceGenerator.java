package com.meridian.banking.service;

import com.meridian.banking.repository.TransactionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces the human-facing reference printed on statements, e.g. TRF-20250304-004182.
 */
@Component
public class ReferenceGenerator {

    private final TransactionRepository transactionRepository;

    public ReferenceGenerator(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public String next(String prefix) {
        String candidate;
        do {
            candidate = prefix + "-"
                    + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1, 999_999));
        } while (transactionRepository.existsByReference(candidate));
        return candidate;
    }
}
