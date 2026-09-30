package com.athenaeum.lending.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BorrowRequest {

    @NotNull
    private Long bookId;

    @NotNull
    private Long memberId;
}
