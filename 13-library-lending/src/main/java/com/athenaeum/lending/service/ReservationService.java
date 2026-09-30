package com.athenaeum.lending.service;

import com.athenaeum.lending.config.CirculationProperties;
import com.athenaeum.lending.dto.ReservationResponse;
import com.athenaeum.lending.entity.Book;
import com.athenaeum.lending.entity.BookCopy;
import com.athenaeum.lending.entity.Member;
import com.athenaeum.lending.entity.MemberStatus;
import com.athenaeum.lending.entity.Reservation;
import com.athenaeum.lending.entity.ReservationStatus;
import com.athenaeum.lending.exception.CirculationRuleException;
import com.athenaeum.lending.exception.ResourceNotFoundException;
import com.athenaeum.lending.mapper.CirculationMapper;
import com.athenaeum.lending.repository.BookRepository;
import com.athenaeum.lending.repository.MemberRepository;
import com.athenaeum.lending.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Holds. A member who wants a title that is entirely out joins a queue; when a copy comes
 * back, the member at the head of the queue is told it is waiting for them on the hold shelf.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final MemberRepository memberRepository;
    private final CirculationMapper circulationMapper;
    private final CirculationProperties properties;

    public ReservationService(ReservationRepository reservationRepository,
                              BookRepository bookRepository,
                              MemberRepository memberRepository,
                              CirculationMapper circulationMapper,
                              CirculationProperties properties) {
        this.reservationRepository = reservationRepository;
        this.bookRepository = bookRepository;
        this.memberRepository = memberRepository;
        this.circulationMapper = circulationMapper;
        this.properties = properties;
    }

    @Transactional
    public ReservationResponse place(Long bookId, Long memberId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new CirculationRuleException("Membership " + member.getMembershipNumber()
                    + " is " + member.getStatus());
        }
        if (reservationRepository.existsByBookIdAndMemberIdAndStatus(
                bookId, memberId, ReservationStatus.WAITING)) {
            throw new CirculationRuleException("A hold is already in place for this title");
        }

        long waiting = reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.WAITING);

        Reservation reservation = new Reservation();
        reservation.setBook(book);
        reservation.setMember(member);
        reservation.setPlacedAt(LocalDateTime.now());
        reservation.setStatus(ReservationStatus.WAITING);
        reservation.setQueuePosition((int) waiting + 1);

        Reservation saved = reservationRepository.save(reservation);
        log.info("Hold {} placed on book {} by {} at position {}", saved.getId(), bookId,
                member.getMembershipNumber(), saved.getQueuePosition());
        return circulationMapper.toResponse(saved);
    }

    @Transactional
    public ReservationResponse cancel(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", reservationId));
        if (reservation.getStatus() == ReservationStatus.FULFILLED) {
            throw new CirculationRuleException("Hold " + reservationId + " has already been collected");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        renumber(reservation.getBook().getId());
        log.info("Hold {} cancelled", reservationId);
        return circulationMapper.toResponse(reservation);
    }

    /**
     * Called when a copy comes back at the desk. If somebody is waiting, the copy becomes
     * theirs to collect for the length of the hold shelf window.
     */
    @Transactional
    public void promoteNextInQueue(BookCopy copy) {
        Long bookId = copy.getBook().getId();
        reservationRepository
                .findFirstByBookIdAndStatusOrderByQueuePositionAsc(bookId, ReservationStatus.WAITING)
                .ifPresent(next -> {
                    next.setStatus(ReservationStatus.READY);
                    next.setHeldCopyId(copy.getId());
                    next.setReadyUntil(LocalDateTime.now()
                            .plusHours(properties.getCirculation().getHoldShelfHours()));
                    log.info("Hold {} is ready for {} on copy {}", next.getId(),
                            next.getMember().getMembershipNumber(), copy.getBarcode());
                });
    }

    @Transactional(readOnly = true)
    public boolean hasWaitingHolds(Long bookId) {
        return reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.WAITING) > 0;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> queueFor(Long bookId) {
        return circulationMapper.toReservationResponses(
                reservationRepository.findByBookIdAndStatusOrderByQueuePositionAsc(
                        bookId, ReservationStatus.WAITING));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> forMember(Long memberId) {
        return circulationMapper.toReservationResponses(
                reservationRepository.findByMemberIdOrderByPlacedAtDesc(memberId));
    }

    private void renumber(Long bookId) {
        List<Reservation> queue = reservationRepository
                .findByBookIdAndStatusOrderByQueuePositionAsc(bookId, ReservationStatus.WAITING);
        int position = 1;
        for (Reservation reservation : queue) {
            reservation.setQueuePosition(position++);
        }
    }
}
