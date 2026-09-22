package com.northwind.shop.repository;

import com.northwind.shop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p
            from Product p
            where p.active = true
              and (:categoryId is null or p.category.id = :categoryId)
              and (:term is null or lower(p.name) like lower(concat('%', :term, '%')))
            """)
    Page<Product> search(@Param("categoryId") Long categoryId,
                         @Param("term") String term,
                         Pageable pageable);

    List<Product> findByIdInAndActiveTrue(Iterable<Long> ids);
}
