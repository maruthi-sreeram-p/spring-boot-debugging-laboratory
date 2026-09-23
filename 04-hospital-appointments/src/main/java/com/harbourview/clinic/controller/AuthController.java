package com.harbourview.clinic.controller;

import com.harbourview.clinic.security.ClinicUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal ClinicUser principal) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", principal.getUsername());
        body.put("role", principal.getRoleName());
        body.put("patientId", principal.getPatientId());
        body.put("doctorId", principal.getDoctorId());
        return body;
    }
}
