package com.athenaeum.lending.service;

import com.athenaeum.lending.config.CirculationProperties;
import com.athenaeum.lending.dto.AccrualSummary;
import com.athenaeum.lending.dto.FineResponse;
import com.athenaeum.lending.entity.Fine;
import com.athenaeum.lending.entity.FineStatus;
import com.athenaeum.lending.entity.Loan;
import com.athenaeum.lending.exception.CirculationRuleException;
import com.athenaeum.lending.exception.ResourceNotFoundException;
import com.athenaeum.lending.mapper.CirculationMapper;
import com.athenaeum.lending.repository.FineRepository;
import com.athenaeum.lending.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Overdue charges.
 *
 * The published policy is simple: a charge accrues for each day a book is late, up to a
 * ceiling. Charges for books that are still out are refreshed by the nightly accrual run;
 * a book handed back at the desk is charged there and then.
 */
@Service
public class FineService {

    private static final Logger log = LoggerFactory.getLogger(FineService.class);

    private final FineRepository fineRepository;
    private final LoanRepository loanRepository;
    private final CirculationMapper circulationMapper;
    private final CirculationProperties properties;

    public FineService(FineRepository fineRepository,
                       LoanRepository loanRepository,
                       CirculationMapper circulationMapper,
                       CirculationProperties properties) {
        this.fineRepository = fineRepository;
        this.loanRepository = loanRepository;
        this.circulationMapper = circulationMapper;
        this.properties = properties;
    }

    /**
     * Charges a late return. This runs in a transaction of its own so that a problem while
     * pricing the charge can never roll back the return itself; a book that is physically back
     * on the shelf must always be recorded as back on the shelf.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<FineResponse> assessOnReturn(Long loanId) {
        Loan loan = loanRepository.findDetailed(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId));

        if (loan.getReturnedAt() == null) {
            log.debug("Loan {} is still out, no return charge to assess", loanId);
            return Optional.empty();
        }

        int days = daysOverdue(loan.getDueAt(), loan.getReturnedAt());
        if (days <= 0) {
            log.debug("Loan {} came back on time", loanId);
            return Optional.empty();
        }

        Fine fine = fineRepository.findByLoanId(loanId).orElseGet(Fine::new);
        fine.setLoanId(loanId);
        fine.setMemberId(loan.getMember().getId());
        fine.setDaysOverdue(days);
        fine.setAmount(amountFor(days));
        fine.setAssessedAt(LocalDateTime.now());
        fine.setStatus(FineStatus.OUTSTANDING);

        Fine saved = fineRepository.save(fine);
        log.info("Loan {} was {} days late, charge is {}", loanId, days, saved.getAmount());
        return Optional.of(circulationMapper.toResponse(saved));
    }

    /**
     * Refreshes the charge on every book that is still out and past its due date.
     */
    @Transactional
    public AccrualSummary runAccrual(LocalDateTime asOf) {
        List<Loan> overdue = loanRepository.findOverdue(asOf);
        int created = 0;
        int updated = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (Loan loan : overdue) {
            int days = daysOverdue(loan.getDueAt(), asOf);
            if (days <= 0) {
                continue;
            }

            Optional<Fine> existing = fineRepository.findByLoanId(loan.getId());
            Fine fine = existing.orElseGet(Fine::new);
            if (existing.isPresent()) {
                if (fine.getStatus() != FineStatus.OUTSTANDING) {
                    continue;
                }
                updated++;
            } else {
                created++;
            }

            fine.setLoanId(loan.getId());
            fine.setMemberId(loan.getMember().getId());
            fine.setDaysOverdue(days);
            fine.setAmount(amountFor(days));
            fine.setAssessedAt(asOf);
            fine.setStatus(FineStatus.OUTSTANDING);
            fineRepository.save(fine);
            total = total.add(fine.getAmount());
        }

        log.info("Accrual as of {} examined {} overdue loans, created {}, updated {}",
                asOf, overdue.size(), created, updated);
        return new AccrualSummary(asOf, overdue.size(), created, updated, total);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstandingFor(Long memberId) {
        return fineRepository.findByMemberIdAndStatus(memberId, FineStatus.OUTSTANDING).stream()
                .map(Fine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<FineResponse> forMember(Long memberId) {
        return circulationMapper.toFineResponses(
                fineRepository.findByMemberIdOrderByAssessedAtDesc(memberId));
    }

    @Transactional
    public FineResponse pay(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine", fineId));
        if (fine.getStatus() != FineStatus.OUTSTANDING) {
            throw new CirculationRuleException("Fine " + fineId + " is already " + fine.getStatus());
        }
        fine.setStatus(FineStatus.PAID);
        fine.setPaidAt(LocalDateTime.now());
        return circulationMapper.toResponse(fine);
    }

    @Transactional
    public FineResponse waive(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine", fineId));
        if (fine.getStatus() != FineStatus.OUTSTANDING) {
            throw new CirculationRuleException("Fine " + fineId + " is already " + fine.getStatus());
        }
        fine.setStatus(FineStatus.WAIVED);
        return circulationMapper.toResponse(fine);
    }

    private int daysOverdue(LocalDateTime dueAt, LocalDateTime asOf) {
        long late = ChronoUnit.DAYS.between(dueAt, asOf);
        return late < 0 ? 0 : (int) late;
    }

    private BigDecimal amountFor(int days) {
        BigDecimal amount = properties.getFines().getDailyRate().multiply(BigDecimal.valueOf(days));
        BigDecimal ceiling = properties.getFines().getMaximum();
        return amount.compareTo(ceiling) > 0 ? ceiling : amount;
    }
}
