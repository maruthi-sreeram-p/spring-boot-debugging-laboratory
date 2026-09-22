package com.northwind.shop.mapper;

import com.northwind.shop.dto.ProductResponse;
import com.northwind.shop.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setActive(product.isActive());
        if (product.getCategory() != null) {
            response.setCategoryName(product.getCategory().getName());
        }
        return response;
    }

    public List<ProductResponse> toResponses(List<Product> products) {
        return products.stream().map(this::toResponse).toList();
    }
}
