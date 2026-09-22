package com.northgate.hr.controller;

import com.northgate.hr.config.DirectoryProperties;
import com.northgate.hr.dto.CreateEmployeeRequest;
import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.dto.EmployeeSearchCriteria;
import com.northgate.hr.dto.PagedResponse;
import com.northgate.hr.dto.UpdateEmployeeRequest;
import com.northgate.hr.entity.EmploymentStatus;
import com.northgate.hr.security.DirectoryUser;
import com.northgate.hr.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DirectoryProperties properties;

    public EmployeeController(EmployeeService employeeService, DirectoryProperties properties) {
        this.employeeService = employeeService;
        this.properties = properties;
    }

    @GetMapping
    public PagedResponse<EmployeeResponse> search(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false, name = "q") String term,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "lastName") String sort,
            @RequestParam(defaultValue = "asc") String direction) {

        EmployeeSearchCriteria criteria = new EmployeeSearchCriteria();
        criteria.setDepartmentId(departmentId);
        criteria.setStatus(status);
        criteria.setJobTitle(jobTitle);
        criteria.setTerm(term);

        int pageSize = Math.min(size == null ? properties.getDefaultPageSize() : size, properties.getMaxPageSize());
        Sort.Direction sortDirection = Sort.Direction.fromString(direction);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(sortDirection, sort));

        return employeeService.search(criteria, pageable);
    }

    @GetMapping("/{employeeId}")
    public EmployeeResponse get(@AuthenticationPrincipal DirectoryUser principal,
                                @PathVariable Long employeeId) {
        return employeeService.getEmployee(employeeId, principal);
    }

    @GetMapping("/{employeeId}/reports")
    public List<EmployeeResponse> reports(@PathVariable Long employeeId) {
        return employeeService.directReports(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.create(request);
    }

    @PutMapping("/{employeeId}")
    public EmployeeResponse update(@PathVariable Long employeeId,
                                   @Valid @RequestBody UpdateEmployeeRequest request) {
        return employeeService.update(employeeId, request);
    }

    @DeleteMapping("/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminate(@PathVariable Long employeeId) {
        employeeService.terminate(employeeId);
    }
}
