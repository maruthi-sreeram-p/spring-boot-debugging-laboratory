package com.vantage.inventory.repository;

import com.vantage.inventory.entity.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {

    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);

    List<StockLevel> findByProductIdOrderByWarehouseIdAsc(Long productId);

    List<StockLevel> findByWarehouseIdOrderByProductIdAsc(Long warehouseId);
}
