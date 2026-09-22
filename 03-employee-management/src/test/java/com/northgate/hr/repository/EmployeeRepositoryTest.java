package com.northgate.hr.repository;

import com.northgate.hr.entity.Department;
import com.northgate.hr.entity.Employee;
import com.northgate.hr.entity.EmploymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmployeeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void findsDirectReportsOfAManager() {
        Department support = new Department();
        support.setCode("SUP");
        support.setName("Customer Support");
        support.setLocation("Hyderabad");
        entityManager.persist(support);

        Employee lead = persistEmployee("NG-9001", "Naina", "Bansal", support, null);
        Employee first = persistEmployee("NG-9002", "Ravi", "Vaidya", support, lead);
        Employee second = persistEmployee("NG-9003", "Bhavna", "Anand", support, lead);
        persistEmployee("NG-9004", "Omar", "Ali", support, null);
        entityManager.flush();
        entityManager.clear();

        List<Employee> reports = employeeRepository.findByManagerIdOrderByLastNameAsc(lead.getId());

        assertThat(reports).extracting(Employee::getEmployeeCode)
                .containsExactly(second.getEmployeeCode(), first.getEmployeeCode());
    }

    @Test
    void countsHeadcountPerDepartment() {
        Department finance = new Department();
        finance.setCode("FIN");
        finance.setName("Finance");
        finance.setLocation("Pune");
        entityManager.persist(finance);

        persistEmployee("NG-9101", "Tanvi", "Grover", finance, null);
        persistEmployee("NG-9102", "Deepak", "Wadhwa", finance, null);
        entityManager.flush();

        assertThat(employeeRepository.countByDepartmentId(finance.getId())).isEqualTo(2);
    }

    private Employee persistEmployee(String code, String firstName, String lastName,
                                     Department department, Employee manager) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(firstName.toLowerCase() + "." + lastName.toLowerCase() + "@northgate.test");
        employee.setJobTitle("Support Engineer");
        employee.setDepartment(department);
        employee.setManager(manager);
        employee.setEmploymentStatus(EmploymentStatus.ACTIVE);
        employee.setHireDate(LocalDate.of(2022, 3, 1));
        employee.setSalary(new BigDecimal("900000.00"));
        return entityManager.persist(employee);
    }
}
