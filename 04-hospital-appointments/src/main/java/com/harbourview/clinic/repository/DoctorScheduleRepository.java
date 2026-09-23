package com.harbourview.clinic.repository;

import com.harbourview.clinic.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    List<DoctorSchedule> findByDoctorIdAndDayOfWeekAndActiveTrue(Long doctorId, DayOfWeek dayOfWeek);

    List<DoctorSchedule> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);
}
