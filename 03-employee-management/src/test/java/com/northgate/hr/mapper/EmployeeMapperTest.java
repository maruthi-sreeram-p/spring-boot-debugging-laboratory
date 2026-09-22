package com.northgate.hr.mapper;

import com.northgate.hr.dto.EmployeeResponse;
import com.northgate.hr.entity.Department;
import com.northgate.hr.entity.Employee;
import com.northgate.hr.entity.EmploymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeMapperTest {

    private final EmployeeMapper mapper = new EmployeeMapper();

    @Test
    void buildsDirectoryEntryFromEmployee() {
        Department engineering = new Department();
        engineering.setId(1L);
        engineering.setCode("ENG");
        engineering.setName("Engineering");
        engineering.setLocation("Bengaluru");

        Employee manager = new Employee();
        manager.setId(2L);
        manager.setFirstName("Arjun");
        manager.setLastName("Reddy");

        Employee employee = new Employee();
        employee.setId(12L);
        employee.setEmployeeCode("NG-1012");
        employee.setFirstName("Aarav");
        employee.setLastName("Nair");
        employee.setEmail("aarav.nair@northgate.test");
        employee.setPhone("+91-9812345678");
        employee.setJobTitle("Senior Software Engineer");
        employee.setDepartment(engineering);
        employee.setManager(manager);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setHireDate(LocalDate.of(2021, 4, 12));
        employee.setSalary(new BigDecimal("2450000.00"));

        EmployeeResponse response = mapper.toResponse(employee);

        assertThat(response.getId()).isEqualTo(12L);
        assertThat(response.getEmployeeCode()).isEqualTo("NG-1012");
        assertThat(response.getFullName()).isEqualTo("Aarav Nair");
        assertThat(response.getJobTitle()).isEqualTo("Senior Software Engineer");
        assertThat(response.getDepartmentName()).isEqualTo("Engineering");
        assertThat(response.getManagerName()).isEqualTo("Arjun Reddy");
        assertThat(response.getEmploymentStatus()).isEqualTo("ACTIVE");
        assertThat(response.getHireDate()).isEqualTo(LocalDate.of(2021, 4, 12));
    }

    @Test
    void toleratesEmployeeWithoutManagerOrDepartment() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setEmployeeCode("NG-1001");
        employee.setFirstName("Ishita");
        employee.setLastName("Sheikh");
        employee.setEmail("ishita.sheikh@northgate.test");
        employee.setJobTitle("Managing Director");
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setHireDate(LocalDate.of(2019, 9, 11));
        employee.setSalary(new BigDecimal("9800000.00"));

        EmployeeResponse response = mapper.toResponse(employee);

        assertThat(response.getManagerName()).isNull();
        assertThat(response.getDepartmentName()).isNull();
        assertThat(response.getFullName()).isEqualTo("Ishita Sheikh");
    }
}
