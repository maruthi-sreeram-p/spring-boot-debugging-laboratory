package com.vantage.inventory.repository;

import com.vantage.inventory.entity.PurchaseOrder;
import com.vantage.inventory.entity.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    List<PurchaseOrder> findByStatusOrderByExpectedDateAsc(PurchaseOrderStatus status);

    boolean existsByPoNumber(String poNumber);
}
