package com.athenaeum.lending.mapper;

import com.athenaeum.lending.dto.BookResponse;
import com.athenaeum.lending.dto.CopyResponse;
import com.athenaeum.lending.dto.FineResponse;
import com.athenaeum.lending.dto.LoanResponse;
import com.athenaeum.lending.dto.MemberResponse;
import com.athenaeum.lending.dto.ReservationResponse;
import com.athenaeum.lending.entity.Book;
import com.athenaeum.lending.entity.BookCopy;
import com.athenaeum.lending.entity.Fine;
import com.athenaeum.lending.entity.Loan;
import com.athenaeum.lending.entity.LoanStatus;
import com.athenaeum.lending.entity.Member;
import com.athenaeum.lending.entity.Reservation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CirculationMapper {

    public BookResponse toResponse(Book book, long totalCopies, long availableCopies, long holdsWaiting) {
        return new BookResponse(book.getId(), book.getIsbn(), book.getTitle(), book.getAuthor(),
                book.getPublisher(), book.getPublishedYear(), book.getShelfMark(),
                totalCopies, availableCopies, holdsWaiting);
    }

    public CopyResponse toResponse(BookCopy copy) {
        return new CopyResponse(copy.getId(), copy.getBarcode(), copy.getBranch(),
                copy.getStatus().name(), copy.getAcquiredOn());
    }

    public List<CopyResponse> toCopyResponses(List<BookCopy> copies) {
        return copies.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public LoanResponse toResponse(Loan loan) {
        boolean overdue = loan.getStatus() == LoanStatus.ACTIVE
                && loan.getDueAt().isBefore(LocalDateTime.now());
        return new LoanResponse(
                loan.getId(),
                loan.getCopy().getId(),
                loan.getCopy().getBarcode(),
                loan.getCopy().getBook().getId(),
                loan.getCopy().getBook().getTitle(),
                loan.getMember().getId(),
                loan.getMember().getMembershipNumber(),
                loan.getBorrowedAt(),
                loan.getDueAt(),
                loan.getReturnedAt(),
                loan.getStatus().name(),
                loan.getRenewalCount(),
                overdue);
    }

    public List<LoanResponse> toLoanResponses(List<Loan> loans) {
        return loans.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getBook().getId(),
                reservation.getBook().getTitle(),
                reservation.getMember().getId(),
                reservation.getMember().getMembershipNumber(),
                reservation.getPlacedAt(),
                reservation.getStatus().name(),
                reservation.getQueuePosition(),
                reservation.getHeldCopyId(),
                reservation.getReadyUntil());
    }

    public List<ReservationResponse> toReservationResponses(List<Reservation> reservations) {
        return reservations.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public FineResponse toResponse(Fine fine) {
        return new FineResponse(fine.getId(), fine.getLoanId(), fine.getMemberId(), fine.getAmount(),
                fine.getDaysOverdue(), fine.getAssessedAt(), fine.getPaidAt(), fine.getStatus().name());
    }

    public List<FineResponse> toFineResponses(List<Fine> fines) {
        return fines.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public MemberResponse toResponse(Member member, int loanLimit, BigDecimal outstandingFines) {
        return new MemberResponse(member.getId(), member.getMembershipNumber(), member.getFullName(),
                member.getEmail(), member.getTier().name(), member.getStatus().name(),
                member.getActiveLoanCount(), loanLimit, outstandingFines, member.getJoinedOn());
    }
}
