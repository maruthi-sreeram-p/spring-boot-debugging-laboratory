package com.harbourview.clinic.controller;

import com.harbourview.clinic.dto.AppointmentResponse;
import com.harbourview.clinic.scheduling.NoShowSweeper;
import com.harbourview.clinic.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/back-office")
public class BackOfficeController {

    private final AppointmentService appointmentService;
    private final NoShowSweeper noShowSweeper;

    public BackOfficeController(AppointmentService appointmentService, NoShowSweeper noShowSweeper) {
        this.appointmentService = appointmentService;
        this.noShowSweeper = noShowSweeper;
    }

    @GetMapping("/doctors/{doctorId}/day-sheet")
    public List<AppointmentResponse> daySheet(@PathVariable Long doctorId,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return appointmentService.daySheet(doctorId, date);
    }

    @PostMapping("/jobs/no-show-sweep")
    public Map<String, Object> runNoShowSweep() {
        int marked = noShowSweeper.sweep();
        return Map.of("marked", marked);
    }
}
