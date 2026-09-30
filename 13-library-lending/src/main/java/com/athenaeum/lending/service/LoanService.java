package com.athenaeum.lending.service;

import com.athenaeum.lending.config.CirculationProperties;
import com.athenaeum.lending.dto.LoanResponse;
import com.athenaeum.lending.entity.BookCopy;
import com.athenaeum.lending.entity.CopyStatus;
import com.athenaeum.lending.entity.Loan;
import com.athenaeum.lending.entity.LoanStatus;
import com.athenaeum.lending.entity.Member;
import com.athenaeum.lending.entity.MemberStatus;
import com.athenaeum.lending.exception.CirculationRuleException;
import com.athenaeum.lending.exception.NoCopyAvailableException;
import com.athenaeum.lending.exception.ResourceNotFoundException;
import com.athenaeum.lending.mapper.CirculationMapper;
import com.athenaeum.lending.repository.BookCopyRepository;
import com.athenaeum.lending.repository.LoanRepository;
import com.athenaeum.lending.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The circulation desk: issuing a copy, taking it back, and extending a loan.
 *
 * Everything here is written from the point of view of the desk clerk, who is holding one
 * physical book at a time.
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    private static final BigDecimal BORROWING_BLOCKED_ABOVE = new BigDecimal("100.00");

    private final LoanRepository loanRepository;
    private final BookCopyRepository copyRepository;
    private final MemberRepository memberRepository;
    private final FineService fineService;
    private final ReservationService reservationService;
    private final CirculationMapper circulationMapper;
    private final CirculationProperties properties;

    public LoanService(LoanRepository loanRepository,
                       BookCopyRepository copyRepository,
                       MemberRepository memberRepository,
                       FineService fineService,
                       ReservationService reservationService,
                       CirculationMapper circulationMapper,
                       CirculationProperties properties) {
        this.loanRepository = loanRepository;
        this.copyRepository = copyRepository;
        this.memberRepository = memberRepository;
        this.fineService = fineService;
        this.reservationService = reservationService;
        this.circulationMapper = circulationMapper;
        this.properties = properties;
    }

    /**
     * Issues the lowest numbered copy of a title that is sitting on the shelf.
     */
    @Transactional
    public LoanResponse borrow(Long bookId, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new CirculationRuleException("Membership " + member.getMembershipNumber()
                    + " is " + member.getStatus());
        }

        int limit = properties.getCirculation().loanLimitFor(member.getTier());
        if (member.getActiveLoanCount() >= limit) {
            throw new CirculationRuleException("Borrowing limit of " + limit + " has been reached");
        }

        BigDecimal outstanding = fineService.outstandingFor(memberId);
        if (outstanding.compareTo(BORROWING_BLOCKED_ABOVE) > 0) {
            throw new CirculationRuleException("Outstanding fines of " + outstanding
                    + " must be cleared before borrowing");
        }

        BookCopy copy = copyRepository
                .findFirstByBookIdAndStatusOrderByIdAsc(bookId, CopyStatus.AVAILABLE)
                .orElseThrow(() -> new NoCopyAvailableException(bookId));

        copyRepository.updateStatus(copy.getId(), CopyStatus.ON_LOAN);
        memberRepository.updateActiveLoanCount(memberId, member.getActiveLoanCount() + 1);

        LocalDateTime now = LocalDateTime.now();
        Loan loan = new Loan();
        loan.setCopy(copy);
        loan.setMember(member);
        loan.setBorrowedAt(now);
        loan.setDueAt(now.plusDays(properties.getCirculation().loanDaysFor(member.getTier())));
        loan.setStatus(LoanStatus.ACTIVE);
        Loan saved = loanRepository.save(loan);

        log.info("Copy {} issued to {} until {}", copy.getBarcode(),
                member.getMembershipNumber(), loan.getDueAt());
        return circulationMapper.toResponse(saved);
    }

    /**
     * Takes a copy back at the desk. Late returns are charged, and anybody waiting for the
     * title is told their hold is ready.
     */
    @Transactional
    public LoanResponse returnCopy(String barcode) {
        BookCopy copy = copyRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Copy", barcode));

        Loan loan = loanRepository.findFirstByCopyIdAndStatus(copy.getId(), LoanStatus.ACTIVE)
                .orElseThrow(() -> new CirculationRuleException(
                        "Copy " + barcode + " is not currently on loan"));

        Member member = loan.getMember();
        copyRepository.updateStatus(copy.getId(), CopyStatus.AVAILABLE);
        memberRepository.updateActiveLoanCount(member.getId(),
                Math.max(0, member.getActiveLoanCount() - 1));

        loan.setReturnedAt(LocalDateTime.now());
        loan.setStatus(LoanStatus.RETURNED);

        fineService.assessOnReturn(loan.getId());
        reservationService.promoteNextInQueue(copy);

        log.info("Copy {} returned by {}", barcode, member.getMembershipNumber());
        return circulationMapper.toResponse(loan);
    }

    @Transactional
    public LoanResponse renew(Long loanId) {
        Loan loan = loanRepository.findDetailed(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId));

        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new CirculationRuleException("Loan " + loanId + " has already been returned");
        }

        int maxRenewals = properties.getCirculation().getMaxRenewals();
        if (loan.getRenewalCount() >= maxRenewals) {
            throw new CirculationRuleException("A loan may be renewed at most "
                    + maxRenewals + " times");
        }

        if (reservationService.hasWaitingHolds(loan.getCopy().getBook().getId())) {
            throw new CirculationRuleException(
                    "Another member is waiting for this title, so it cannot be renewed");
        }

        int loanDays = properties.getCirculation().loanDaysFor(loan.getMember().getTier());
        loan.setDueAt(LocalDateTime.now().plusDays(loanDays));
        loan.setRenewalCount(loan.getRenewalCount() + 1);

        log.info("Loan {} renewed until {}", loanId, loan.getDueAt());
        return circulationMapper.toResponse(loan);
    }

    @Transactional(readOnly = true)
    public LoanResponse byId(Long loanId) {
        return circulationMapper.toResponse(loanRepository.findDetailed(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan", loanId)));
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> active() {
        return circulationMapper.toLoanResponses(loanRepository.findAllActive());
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> overdue() {
        return circulationMapper.toLoanResponses(loanRepository.findOverdue(LocalDateTime.now()));
    }
}
