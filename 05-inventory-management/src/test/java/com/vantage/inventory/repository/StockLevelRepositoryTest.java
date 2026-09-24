package com.vantage.inventory.repository;

import com.vantage.inventory.entity.Product;
import com.vantage.inventory.entity.StockLevel;
import com.vantage.inventory.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StockLevelRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Test
    void findsOneStockLevelPerProductAndWarehouse() {
        Product product = new Product();
        product.setSku("VS-TST-0001");
        product.setName("Test product");
        product.setUnit("EACH");
        product.setReorderLevel(10);
        entityManager.persist(product);

        Warehouse first = persistWarehouse("WH-T01", "Test warehouse one");
        Warehouse second = persistWarehouse("WH-T02", "Test warehouse two");

        persistLevel(product, first, 100, 10);
        persistLevel(product, second, 55, 5);
        entityManager.flush();
        entityManager.clear();

        assertThat(stockLevelRepository.findByProductIdAndWarehouseId(product.getId(), first.getId()))
                .get()
                .extracting(StockLevel::getQuantityOnHand)
                .isEqualTo(100);
        assertThat(stockLevelRepository.findByProductIdOrderByWarehouseIdAsc(product.getId())).hasSize(2);
    }

    private Warehouse persistWarehouse(String code, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setCity("Testville");
        warehouse.setActive(true);
        return entityManager.persist(warehouse);
    }

    private void persistLevel(Product product, Warehouse warehouse, int onHand, int reserved) {
        StockLevel level = new StockLevel();
        level.setProduct(product);
        level.setWarehouse(warehouse);
        level.setQuantityOnHand(onHand);
        level.setQuantityReserved(reserved);
        level.setUpdatedAt(Instant.now());
        entityManager.persist(level);
    }
}
