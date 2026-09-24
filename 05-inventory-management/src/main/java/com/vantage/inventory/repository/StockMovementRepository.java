package com.vantage.inventory.repository;

import com.vantage.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    Page<StockMovement> findByProductIdAndWarehouseIdOrderByCreatedAtDesc(
            Long productId, Long warehouseId, Pageable pageable);

    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    @Query("""
            select coalesce(sum(m.quantity), 0)
            from StockMovement m
            where m.product.id = :productId
              and m.warehouse.id = :warehouseId
            """)
    Integer sumQuantity(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);
}
