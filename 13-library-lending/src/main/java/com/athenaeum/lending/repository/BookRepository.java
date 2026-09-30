package com.athenaeum.lending.repository;

import com.athenaeum.lending.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findByIsbn(String isbn);

    @Query("""
            select b
            from Book b
            where (:term is null
                   or lower(b.title) like lower(concat('%', :term, '%'))
                   or lower(b.author) like lower(concat('%', :term, '%')))
            order by b.title asc
            """)
    List<Book> search(@Param("term") String term);
}
