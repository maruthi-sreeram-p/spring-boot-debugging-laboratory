package com.harbourview.clinic.controller;

import com.harbourview.clinic.dto.AppointmentResponse;
import com.harbourview.clinic.dto.BookAppointmentRequest;
import com.harbourview.clinic.dto.CancelAppointmentRequest;
import com.harbourview.clinic.dto.PagedResponse;
import com.harbourview.clinic.security.ClinicUser;
import com.harbourview.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse book(@AuthenticationPrincipal ClinicUser principal,
                                    @Valid @RequestBody BookAppointmentRequest request) {
        return appointmentService.book(request, principal);
    }

    @GetMapping
    public PagedResponse<AppointmentResponse> mine(@AuthenticationPrincipal ClinicUser principal,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return appointmentService.myAppointments(principal, pageable);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@AuthenticationPrincipal ClinicUser principal,
                                   @PathVariable Long appointmentId) {
        return appointmentService.getAppointment(appointmentId, principal);
    }

    @PostMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal ClinicUser principal,
                                      @PathVariable Long appointmentId,
                                      @Valid @RequestBody CancelAppointmentRequest request) {
        return appointmentService.cancel(appointmentId, request, principal);
    }

    @PostMapping("/{appointmentId}/complete")
    public AppointmentResponse complete(@AuthenticationPrincipal ClinicUser principal,
                                        @PathVariable Long appointmentId) {
        return appointmentService.complete(appointmentId, principal);
    }
}
