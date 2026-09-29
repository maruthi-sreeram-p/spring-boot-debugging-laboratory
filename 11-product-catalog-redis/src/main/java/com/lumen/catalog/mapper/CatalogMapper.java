package com.lumen.catalog.mapper;

import com.lumen.catalog.dto.CategoryResponse;
import com.lumen.catalog.dto.PriceHistoryResponse;
import com.lumen.catalog.dto.ProductResponse;
import com.lumen.catalog.dto.VariantResponse;
import com.lumen.catalog.entity.Category;
import com.lumen.catalog.entity.PriceHistoryEntry;
import com.lumen.catalog.entity.Product;
import com.lumen.catalog.entity.ProductVariant;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CatalogMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getPrice(),
                product.getCurrency(),
                product.isActive(),
                product.getUpdatedAt());
    }

    public List<ProductResponse> toResponses(List<Product> products) {
        return products.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public VariantResponse toResponse(ProductVariant variant, BigDecimal basePrice) {
        return new VariantResponse(
                variant.getId(),
                variant.getVariantSku(),
                variant.getLabel(),
                variant.getPriceDelta(),
                basePrice.add(variant.getPriceDelta()));
    }

    public List<VariantResponse> toVariantResponses(List<ProductVariant> variants, BigDecimal basePrice) {
        return variants.stream().map(variant -> toResponse(variant, basePrice)).collect(Collectors.toList());
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getSlug(),
                category.getName(), category.getPosition());
    }

    public List<CategoryResponse> toCategoryResponses(List<Category> categories) {
        return categories.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public PriceHistoryResponse toResponse(PriceHistoryEntry entry) {
        return new PriceHistoryResponse(entry.getId(), entry.getOldPrice(),
                entry.getNewPrice(), entry.getChangedBy(), entry.getChangedAt());
    }

    public List<PriceHistoryResponse> toPriceHistoryResponses(List<PriceHistoryEntry> entries) {
        return entries.stream().map(this::toResponse).collect(Collectors.toList());
    }
}
