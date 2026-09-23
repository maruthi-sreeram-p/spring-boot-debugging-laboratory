package com.harbourview.clinic.scheduling;

import com.harbourview.clinic.config.ClinicProperties;
import com.harbourview.clinic.entity.Appointment;
import com.harbourview.clinic.entity.AppointmentStatus;
import com.harbourview.clinic.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Walks appointments whose slot has finished and closes them off, so the daily reports do
 * not fill up with consultations that are still sitting in BOOKED days after the fact.
 * Runs on a cron and can also be triggered by the scheduling desk from the back office.
 */
@Component
public class NoShowSweeper {

    private static final Logger log = LoggerFactory.getLogger(NoShowSweeper.class);

    private final AppointmentRepository appointmentRepository;
    private final ClinicProperties properties;

    public NoShowSweeper(AppointmentRepository appointmentRepository, ClinicProperties properties) {
        this.appointmentRepository = appointmentRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "${clinic.no-show.sweep-cron}")
    @Transactional
    public void sweepScheduled() {
        int marked = sweep();
        if (marked > 0) {
            log.info("Scheduled sweep marked {} appointment(s) as no-show", marked);
        }
    }

    @Transactional
    public int sweep() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(properties.getNoShow().getGraceMinutes());
        List<Appointment> elapsed = appointmentRepository.findByEndTimeBefore(cutoff);

        int marked = 0;
        for (Appointment appointment : elapsed) {
            if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
                appointment.setStatus(AppointmentStatus.NO_SHOW);
                marked++;
            }
        }

        log.debug("Sweep inspected {} elapsed appointment(s) before {} and marked {}",
                elapsed.size(), cutoff, marked);
        return marked;
    }
}
