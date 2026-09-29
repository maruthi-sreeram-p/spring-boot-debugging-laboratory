package com.lumen.catalog.mapper;

import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.VariantResponse;
import com.lumen.catalog.entity.Category;
import com.lumen.catalog.entity.Product;
import com.lumen.catalog.entity.ProductVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMapperTest {

    private final CatalogMapper mapper = new CatalogMapper();

    @Test
    void mapsProductWithItsCategory() {
        Category category = new Category();
        category.setId(2L);
        category.setSlug("audio");
        category.setName("Audio");
        category.setPosition(2);

        Product product = new Product();
        product.setId(4L);
        product.setSku("LM-AUD-2201");
        product.setName("Auralis Over-Ear ANC");
        product.setDescription("Active noise cancelling headphones");
        product.setBrand("Auralis");
        product.setCategory(category);
        product.setPrice(new BigDecimal("27900.00"));
        product.setCurrency("INR");
        product.setActive(true);
        product.setUpdatedAt(Instant.parse("2025-03-14T10:02:00Z"));

        ProductResponse response = mapper.toResponse(product);

        assertThat(response.getSku()).isEqualTo("LM-AUD-2201");
        assertThat(response.getCategoryName()).isEqualTo("Audio");
        assertThat(response.getPrice()).isEqualByComparingTo("27900.00");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void addsTheVariantDeltaToTheBasePrice() {
        ProductVariant variant = new ProductVariant();
        variant.setId(2L);
        variant.setVariantSku("LM-LAP-1401-GRP");
        variant.setLabel("Graphite");
        variant.setPriceDelta(new BigDecimal("2000.00"));

        List<VariantResponse> responses =
                mapper.toVariantResponses(List.of(variant), new BigDecimal("124900.00"));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getEffectivePrice()).isEqualByComparingTo("126900.00");
    }
}
