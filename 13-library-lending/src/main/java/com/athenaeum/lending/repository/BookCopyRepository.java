package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.BookCopy;
import com.athenaeum.lending.entity.CopyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findByBarcode(String barcode);

    List<BookCopy> findByBookIdOrderByBarcodeAsc(Long bookId);

    Optional<BookCopy> findFirstByBookIdAndStatusOrderByIdAsc(Long bookId, CopyStatus status);

    /**
     * How many copies of a title the catalogue should show as borrowable.
     */
    @Query("select count(c) from BookCopy c where c.book.id = :bookId and c.status <> 'ON_LOAN'")
    long countAvailable(@Param("bookId") Long bookId);

    @Query("select count(c) from BookCopy c where c.book.id = :bookId")
    long countCopies(@Param("bookId") Long bookId);

    /**
     * Writes the shelf status straight through, so the copy is off the shelf before the loan
     * itself is recorded.
     */
    @Modifying
    @Query("update BookCopy c set c.status = :status where c.id = :copyId")
    int updateStatus(@Param("copyId") Long copyId, @Param("status") CopyStatus status);
}
