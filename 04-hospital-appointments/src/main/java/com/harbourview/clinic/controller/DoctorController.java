package com.harbourview.clinic.controller;

import com.harbourview.clinic.dto.DaySlotsResponse;
import com.harbourview.clinic.dto.DoctorResponse;
import com.harbourview.clinic.exception.ResourceNotFoundException;
import com.harbourview.clinic.mapper.ClinicMapper;
import com.harbourview.clinic.repository.DoctorRepository;
import com.harbourview.clinic.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final ScheduleService scheduleService;
    private final ClinicMapper clinicMapper;

    public DoctorController(DoctorRepository doctorRepository,
                            ScheduleService scheduleService,
                            ClinicMapper clinicMapper) {
        this.doctorRepository = doctorRepository;
        this.scheduleService = scheduleService;
        this.clinicMapper = clinicMapper;
    }

    @GetMapping
    public List<DoctorResponse> list() {
        return clinicMapper.toDoctorResponses(doctorRepository.findByActiveTrueOrderByFullNameAsc());
    }

    @GetMapping("/{doctorId}")
    public DoctorResponse get(@PathVariable Long doctorId) {
        return clinicMapper.toResponse(doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor", doctorId)));
    }

    @GetMapping("/{doctorId}/slots")
    public DaySlotsResponse slots(@PathVariable Long doctorId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduleService.slotsFor(doctorId, date);
    }
}
