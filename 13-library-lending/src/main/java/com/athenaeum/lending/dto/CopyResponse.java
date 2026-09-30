package com.athenaeum.lending.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class CopyResponse {

    private final Long id;
    private final String barcode;
    private final String branch;
    private final String status;
    private final LocalDate acquiredOn;
}
