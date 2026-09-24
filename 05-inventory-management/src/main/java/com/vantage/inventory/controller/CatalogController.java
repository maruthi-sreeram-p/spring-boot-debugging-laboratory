package com.vantage.inventory.controller;

import com.vantage.inventory.entity.Product;
import com.vantage.inventory.entity.Supplier;
import com.vantage.inventory.entity.Warehouse;
import com.vantage.inventory.repository.ProductRepository;
import com.vantage.inventory.repository.SupplierRepository;
import com.vantage.inventory.repository.WarehouseRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;

    public CatalogController(ProductRepository productRepository,
                             WarehouseRepository warehouseRepository,
                             SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findByActiveTrueOrderByNameAsc();
    }

    @GetMapping("/warehouses")
    public List<Warehouse> warehouses() {
        return warehouseRepository.findByActiveTrueOrderByNameAsc();
    }

    @GetMapping("/suppliers")
    public List<Supplier> suppliers() {
        return supplierRepository.findAllByOrderByNameAsc();
    }
}
