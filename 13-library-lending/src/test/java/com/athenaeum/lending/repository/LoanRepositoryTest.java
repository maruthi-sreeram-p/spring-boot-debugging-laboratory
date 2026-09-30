package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Book;
import com.athenaeum.lending.entity.BookCopy;
import com.athenaeum.lending.entity.CopyStatus;
import com.athenaeum.lending.entity.Loan;
import com.athenaeum.lending.entity.LoanStatus;
import com.athenaeum.lending.entity.Member;
import com.athenaeum.lending.entity.MemberTier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LoanRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private BookCopyRepository copyRepository;

    @Test
    void findsOverdueLoansAndCountsShelfStock() {
        Book book = new Book();
        book.setIsbn("9780000000001");
        book.setTitle("Test Title");
        book.setAuthor("Test Author");
        book.setPublisher("Test Press");
        book.setPublishedYear(2020);
        book.setShelfMark("TS1");
        entityManager.persist(book);

        BookCopy onShelf = copy(book, "TST-0001", CopyStatus.AVAILABLE);
        BookCopy out = copy(book, "TST-0002", CopyStatus.ON_LOAN);

        Member member = new Member();
        member.setMembershipNumber("LIB-9001");
        member.setFullName("Test Member");
        member.setEmail("test.member@athenaeum.test");
        member.setTier(MemberTier.STANDARD);
        member.setJoinedOn(LocalDate.of(2021, 1, 1));
        entityManager.persist(member);

        LocalDateTime now = LocalDateTime.now();
        entityManager.persist(loan(out, member, now.minusDays(20), now.minusDays(6)));
        entityManager.persist(loan(onShelf, member, now.minusDays(2), now.plusDays(12)));
        entityManager.flush();
        entityManager.clear();

        List<Loan> overdue = loanRepository.findOverdue(LocalDateTime.now());

        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).getCopy().getBarcode()).isEqualTo("TST-0002");
        assertThat(loanRepository.countByMemberIdAndStatus(member.getId(), LoanStatus.ACTIVE)).isEqualTo(2);
        assertThat(copyRepository.countCopies(book.getId())).isEqualTo(2);
    }

    private BookCopy copy(Book book, String barcode, CopyStatus status) {
        BookCopy copy = new BookCopy();
        copy.setBook(book);
        copy.setBarcode(barcode);
        copy.setBranch("Central");
        copy.setStatus(status);
        copy.setAcquiredOn(LocalDate.of(2021, 5, 4));
        entityManager.persist(copy);
        return copy;
    }

    private Loan loan(BookCopy copy, Member member, LocalDateTime borrowed, LocalDateTime due) {
        Loan loan = new Loan();
        loan.setCopy(copy);
        loan.setMember(member);
        loan.setBorrowedAt(borrowed);
        loan.setDueAt(due);
        loan.setStatus(LoanStatus.ACTIVE);
        return loan;
    }
}
