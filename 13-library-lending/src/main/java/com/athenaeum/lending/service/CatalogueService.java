package com.athenaeum.lending.service;

import com.athenaeum.lending.dto.BookResponse;
import com.athenaeum.lending.dto.CopyResponse;
import com.athenaeum.lending.entity.Book;
import com.athenaeum.lending.entity.ReservationStatus;
import com.athenaeum.lending.exception.ResourceNotFoundException;
import com.athenaeum.lending.mapper.CirculationMapper;
import com.athenaeum.lending.repository.BookCopyRepository;
import com.athenaeum.lending.repository.BookRepository;
import com.athenaeum.lending.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * What the public catalogue terminals in the branches show.
 */
@Service
public class CatalogueService {

    private final BookRepository bookRepository;
    private final BookCopyRepository copyRepository;
    private final ReservationRepository reservationRepository;
    private final CirculationMapper circulationMapper;

    public CatalogueService(BookRepository bookRepository,
                            BookCopyRepository copyRepository,
                            ReservationRepository reservationRepository,
                            CirculationMapper circulationMapper) {
        this.bookRepository = bookRepository;
        this.copyRepository = copyRepository;
        this.reservationRepository = reservationRepository;
        this.circulationMapper = circulationMapper;
    }

    @Transactional(readOnly = true)
    public List<BookResponse> search(String term) {
        List<BookResponse> responses = new ArrayList<>();
        for (Book book : bookRepository.search(term)) {
            responses.add(describe(book));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public BookResponse byId(Long bookId) {
        return describe(bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId)));
    }

    @Transactional(readOnly = true)
    public List<CopyResponse> copiesOf(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException("Book", bookId);
        }
        return circulationMapper.toCopyResponses(
                copyRepository.findByBookIdOrderByBarcodeAsc(bookId));
    }

    private BookResponse describe(Book book) {
        return circulationMapper.toResponse(book,
                copyRepository.countCopies(book.getId()),
                copyRepository.countAvailable(book.getId()),
                reservationRepository.countByBookIdAndStatus(book.getId(), ReservationStatus.WAITING));
    }
}
