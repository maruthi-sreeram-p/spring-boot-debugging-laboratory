package com.vantage.inventory.mapper;

import com.vantage.inventory.dto.StockLevelResponse;
import com.vantage.inventory.entity.Product;
import com.vantage.inventory.entity.StockLevel;
import com.vantage.inventory.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryMapperTest {

    private final InventoryMapper mapper = new InventoryMapper();

    @Test
    void mapsStockLevelForTheWarehouseScreens() {
        Product product = new Product();
        product.setId(3L);
        product.setSku("VS-SSD-0330");
        product.setName("NVMe SSD 1TB");
        product.setReorderLevel(60);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(1L);
        warehouse.setCode("WH-BLR");
        warehouse.setName("Bengaluru Central");

        StockLevel level = new StockLevel();
        level.setProduct(product);
        level.setWarehouse(warehouse);
        level.setQuantityOnHand(310);
        level.setQuantityReserved(95);
        level.setUpdatedAt(Instant.parse("2025-01-20T11:15:00Z"));

        StockLevelResponse response = mapper.toResponse(level);

        assertThat(response.getSku()).isEqualTo("VS-SSD-0330");
        assertThat(response.getWarehouseCode()).isEqualTo("WH-BLR");
        assertThat(response.getQuantityOnHand()).isEqualTo(310);
        assertThat(response.getQuantityReserved()).isEqualTo(95);
        assertThat(response.isBelowReorderLevel()).isFalse();
    }

    @Test
    void flagsStockBelowTheReorderLevel() {
        Product product = new Product();
        product.setId(2L);
        product.setSku("VS-PSU-0210");
        product.setName("Desktop power supply 650W");
        product.setReorderLevel(40);

        Warehouse warehouse = new Warehouse();
        warehouse.setId(2L);
        warehouse.setCode("WH-MUM");

        StockLevel level = new StockLevel();
        level.setProduct(product);
        level.setWarehouse(warehouse);
        level.setQuantityOnHand(30);
        level.setQuantityReserved(5);
        level.setUpdatedAt(Instant.now());

        assertThat(mapper.toResponse(level).isBelowReorderLevel()).isTrue();
    }
}
