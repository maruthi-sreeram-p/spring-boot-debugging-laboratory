package com.northgate.hr.controller;

import com.northgate.hr.config.DirectoryProperties;
import com.northgate.hr.dto.DepartmentResponse;
import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.dto.PagedResponse;
import com.northgate.hr.service.DepartmentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DirectoryProperties properties;

    public DepartmentController(DepartmentService departmentService, DirectoryProperties properties) {
        this.departmentService = departmentService;
        this.properties = properties;
    }

    @GetMapping
    public List<DepartmentResponse> list() {
        return departmentService.listDepartments();
    }

    @GetMapping("/{departmentId}/employees")
    public PagedResponse<EmployeeResponse> employees(@PathVariable Long departmentId,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(required = false) Integer size) {
        int pageSize = Math.min(size == null ? properties.getDefaultPageSize() : size, properties.getMaxPageSize());
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, "lastName"));
        return departmentService.employeesOf(departmentId, pageable);
    }
}
