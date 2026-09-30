package com.aegis.identity.repository;

import com.aegis.identity.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByReference(String reference);

    boolean existsByReference(String reference);

    @Query("select d from Document d join fetch d.owner order by d.reference asc")
    List<Document> findAllWithOwner();
}
