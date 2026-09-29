package com.riverstone.orderevents.repository;

import com.riverstone.orderevents.entity.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    List<StockItem> findAllByOrderBySkuAsc();
}
