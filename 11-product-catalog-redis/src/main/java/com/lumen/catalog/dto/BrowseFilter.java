package com.lumen.catalog.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * The saved filter behind a browse rail on the home page. Rails are configured by
 * merchandising and reused across many requests.
 */
@Getter
@Setter
public class BrowseFilter implements Serializable {

    private String brand;
    private Long categoryId;

    public BrowseFilter() {
    }

    public BrowseFilter(String brand, Long categoryId) {
        this.brand = brand;
        this.categoryId = categoryId;
    }

    /**
     * Rails are labelled by the brand they promote. Dashboards and logs print this.
     */
    @Override
    public String toString() {
        return brand == null ? "all-brands" : brand;
    }
}
