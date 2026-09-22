package com.northgate.hr.controller;

import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.security.DirectoryUser;
import com.northgate.hr.service.EmployeeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final EmployeeService employeeService;

    public ProfileController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public EmployeeResponse me(@AuthenticationPrincipal DirectoryUser principal) {
        return employeeService.getEmployee(principal.getEmployeeId(), principal);
    }
}
