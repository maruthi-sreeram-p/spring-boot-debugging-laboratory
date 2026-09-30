package com.athenaeum.lending.service;

import com.athenaeum.lending.dto.AccrualSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Runs overnight, after the branches have closed.
 */
@Component
public class FineAccrualJob {

    private static final Logger log = LoggerFactory.getLogger(FineAccrualJob.class);

    private final FineService fineService;

    public FineAccrualJob(FineService fineService) {
        this.fineService = fineService;
    }

    @Scheduled(cron = "${athenaeum.fines.accrual-cron}")
    public void accrue() {
        AccrualSummary summary = fineService.runAccrual(LocalDateTime.now());
        log.info("Nightly accrual finished: {} loans examined, {} charges created, {} refreshed",
                summary.getLoansExamined(), summary.getFinesCreated(), summary.getFinesUpdated());
    }
}
