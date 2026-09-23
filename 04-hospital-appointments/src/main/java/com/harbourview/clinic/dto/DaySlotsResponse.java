package com.harbourview.clinic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DaySlotsResponse {

    private Long doctorId;
    private String doctorName;
    private LocalDate date;
    private String dayOfWeek;
    private List<SlotResponse> slots;
}
