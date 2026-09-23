package com.harbourview.clinic.repository;

import com.harbourview.clinic.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findByActiveTrueOrderByFullNameAsc();
}
