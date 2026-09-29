package com.lumen.catalog.repository;

import com.lumen.catalog.entity.PriceHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceHistoryRepository extends JpaRepository<PriceHistoryEntry, Long> {

    List<PriceHistoryEntry> findByProductIdOrderByChangedAtDesc(Long productId);
}
