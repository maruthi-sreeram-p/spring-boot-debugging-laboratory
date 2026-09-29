package com.lumen.catalog.repository;

import com.lumen.catalog.entity.Category;
import com.lumen.catalog.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void listsActiveProductsInACategory() {
        Category category = new Category();
        category.setSlug("test-cat");
        category.setName("Test category");
        category.setPosition(1);
        entityManager.persist(category);

        persist("T-001", "Alpha", category, true);
        persist("T-002", "Bravo", category, true);
        persist("T-003", "Retired", category, false);
        entityManager.flush();
        entityManager.clear();

        List<Product> found = productRepository
                .findByCategoryIdAndActiveTrueOrderByNameAsc(category.getId());

        assertThat(found).extracting(Product::getSku).containsExactly("T-001", "T-002");
        assertThat(productRepository.findBySku("T-003")).isPresent();
        assertThat(productRepository.existsBySku("T-999")).isFalse();
    }

    private void persist(String sku, String name, Category category, boolean active) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setBrand("TestBrand");
        product.setCategory(category);
        product.setPrice(new BigDecimal("1000.00"));
        product.setActive(active);
        entityManager.persist(product);
    }
}
