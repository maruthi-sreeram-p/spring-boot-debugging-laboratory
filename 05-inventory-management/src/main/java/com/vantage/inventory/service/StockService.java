package com.vantage.inventory.service;

import com.vantage.inventory.cache.StockCache;
import com.vantage.inventory.dto.AdjustStockRequest;
import com.vantage.inventory.dto.PagedResponse;
import com.vantage.inventory.dto.ProductStockResponse;
import com.vantage.inventory.dto.StockLevelResponse;
import com.vantage.inventory.dto.StockMovementResponse;
import com.vantage.inventory.entity.MovementType;
import com.vantage.inventory.entity.Product;
import com.vantage.inventory.entity.StockLevel;
import com.vantage.inventory.entity.StockMovement;
import com.vantage.inventory.entity.Warehouse;
import com.vantage.inventory.exception.InsufficientStockException;
import com.vantage.inventory.exception.ResourceNotFoundException;
import com.vantage.inventory.mapper.InventoryMapper;
import com.vantage.inventory.repository.ProductRepository;
import com.vantage.inventory.repository.StockLevelRepository;
import com.vantage.inventory.repository.StockMovementRepository;
import com.vantage.inventory.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final StockLevelRepository stockLevelRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryMapper inventoryMapper;
    private final StockCache stockCache;

    public StockService(StockLevelRepository stockLevelRepository,
                        StockMovementRepository stockMovementRepository,
                        ProductRepository productRepository,
                        WarehouseRepository warehouseRepository,
                        InventoryMapper inventoryMapper,
                        StockCache stockCache) {
        this.stockLevelRepository = stockLevelRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryMapper = inventoryMapper;
        this.stockCache = stockCache;
    }

    /**
     * Single warehouse lookup. This is the hot path, so it is served from the cache
     * whenever there is an entry and populated from the database when there is not.
     */
    @Transactional(readOnly = true)
    public StockLevelResponse stockAt(Long productId, Long warehouseId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));

        Optional<StockCache.CachedStock> cached = stockCache.read(productId, warehouseId);
        if (cached.isPresent()) {
            log.debug("Stock cache hit for product {} in warehouse {}", productId, warehouseId);
            return inventoryMapper.toResponse(product, warehouse,
                    cached.get().onHand(), cached.get().reserved(), Instant.now());
        }

        StockLevel level = stockLevelRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock level for product " + productId + " in warehouse", warehouseId));

        stockCache.write(productId, warehouseId, level.getQuantityOnHand(), level.getQuantityReserved());
        return inventoryMapper.toResponse(level);
    }

    /**
     * Network-wide view of one product. Read straight from the database because the
     * reporting screens that use it are not on the hot path.
     */
    @Transactional(readOnly = true)
    public ProductStockResponse stockAcrossNetwork(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        List<StockLevel> levels = stockLevelRepository.findByProductIdOrderByWarehouseIdAsc(productId);
        return inventoryMapper.toProductStock(product, inventoryMapper.toStockResponses(levels));
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> stockInWarehouse(Long warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new ResourceNotFoundException("Warehouse", warehouseId);
        }
        return inventoryMapper.toStockResponses(
                stockLevelRepository.findByWarehouseIdOrderByProductIdAsc(warehouseId));
    }

    @Transactional
    public StockLevelResponse adjust(AdjustStockRequest request) {
        StockLevel level = stockLevelRepository
                .findByProductIdAndWarehouseId(request.getProductId(), request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock level for product " + request.getProductId() + " in warehouse",
                        request.getWarehouseId()));

        int delta = request.getQuantityDelta();
        int updated = level.getQuantityOnHand() + delta;
        if (updated < 0) {
            throw new InsufficientStockException("Adjusting by " + delta + " would take "
                    + level.getProduct().getSku() + " below zero in " + level.getWarehouse().getCode());
        }

        level.setQuantityOnHand(updated);
        level.setUpdatedAt(Instant.now());

        if (delta > 0) {
            recordMovement(level.getProduct(), level.getWarehouse(),
                    MovementType.ADJUSTMENT, delta, request.getReason(), "ADJ");
        }

        stockCache.evict(request.getProductId(), request.getWarehouseId());

        log.info("Adjusted {} in {} by {} ({})", level.getProduct().getSku(),
                level.getWarehouse().getCode(), delta, request.getReason());
        return inventoryMapper.toResponse(level);
    }

    @Transactional(readOnly = true)
    public PagedResponse<StockMovementResponse> movements(Long productId, Long warehouseId, Pageable pageable) {
        Page<StockMovement> page = warehouseId == null
                ? stockMovementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                : stockMovementRepository.findByProductIdAndWarehouseIdOrderByCreatedAtDesc(
                        productId, warehouseId, pageable);
        return PagedResponse.of(page, inventoryMapper.toMovementResponses(page.getContent()));
    }

    StockMovement recordMovement(Product product, Warehouse warehouse, MovementType type,
                                 int quantity, String reason, String prefix) {
        StockMovement movement = new StockMovement();
        movement.setReference(nextReference(prefix));
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        return stockMovementRepository.save(movement);
    }

    List<StockLevelResponse> asResponses(List<StockLevel> levels) {
        return new ArrayList<>(inventoryMapper.toStockResponses(levels));
    }

    private String nextReference(String prefix) {
        return prefix + "-" + LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%04d", ThreadLocalRandom.current().nextInt(1, 9999));
    }
}
