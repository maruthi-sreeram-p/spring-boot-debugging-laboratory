package com.lumen.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse implements Serializable {

    private Long id;
    private String slug;
    private String name;
    private Integer position;
}
