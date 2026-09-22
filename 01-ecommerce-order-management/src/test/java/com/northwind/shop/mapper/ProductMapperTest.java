package com.northwind.shop.mapper;

import com.northwind.shop.dto.ProductResponse;
import com.northwind.shop.entity.Category;
import com.northwind.shop.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    @Test
    void mapsCatalogueFieldsIncludingCategoryName() {
        Category category = new Category();
        category.setId(2L);
        category.setName("Audio");
        category.setSlug("audio");

        Product product = new Product();
        product.setId(4L);
        product.setSku("NW-AUD-2201");
        product.setName("Auralis Over-Ear ANC");
        product.setDescription("Active noise cancelling headphones");
        product.setPrice(new BigDecimal("279.00"));
        product.setStockQuantity(52);
        product.setCategory(category);
        product.setActive(true);

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.getId()).isEqualTo(4L);
        assertThat(response.getSku()).isEqualTo("NW-AUD-2201");
        assertThat(response.getPrice()).isEqualByComparingTo("279.00");
        assertThat(response.getStockQuantity()).isEqualTo(52);
        assertThat(response.getCategoryName()).isEqualTo("Audio");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void toleratesProductWithoutCategory() {
        Product product = new Product();
        product.setId(99L);
        product.setSku("NW-TMP-0001");
        product.setName("Unfiled item");
        product.setPrice(new BigDecimal("10.00"));
        product.setStockQuantity(1);

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.getCategoryName()).isNull();
    }
}
