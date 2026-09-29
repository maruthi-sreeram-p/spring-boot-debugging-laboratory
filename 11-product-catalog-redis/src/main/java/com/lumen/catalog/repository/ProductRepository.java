package com.lumen.catalog.repository;

import com.lumen.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    List<Product> findByCategoryIdAndActiveTrueOrderByNameAsc(Long categoryId);

    @Query("""
            select p
            from Product p
            where p.active = true
              and (cast(:term as String) is null or lower(p.name) like lower(concat('%', cast(:term as String), '%')))
              and (cast(:brand as String) is null or lower(p.brand) = lower(cast(:brand as String)))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:minPrice is null or p.price >= :minPrice)
              and (:maxPrice is null or p.price <= :maxPrice)
            """)
    Page<Product> search(@Param("term") String term,
                         @Param("brand") String brand,
                         @Param("categoryId") Long categoryId,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         Pageable pageable);

    @Query("""
            select p
            from Product p
            where p.active = true
              and (cast(:brand as String) is null or lower(p.brand) = lower(cast(:brand as String)))
              and (:categoryId is null or p.category.id = :categoryId)
            order by p.name asc
            """)
    List<Product> browse(@Param("brand") String brand, @Param("categoryId") Long categoryId);
}
