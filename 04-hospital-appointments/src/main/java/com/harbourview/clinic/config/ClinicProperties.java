package com.harbourview.clinic.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clinic")
@Getter
@Setter
public class ClinicProperties {

    private String timezone = "Asia/Kolkata";
    private Booking booking = new Booking();
    private Cancellation cancellation = new Cancellation();
    private NoShow noShow = new NoShow();

    @Getter
    @Setter
    public static class Booking {
        private int minimumNoticeMinutes = 30;
        private int maximumDaysAhead = 60;
    }

    @Getter
    @Setter
    public static class Cancellation {
        private int minimumNoticeHours = 2;
    }

    @Getter
    @Setter
    public static class NoShow {
        private int graceMinutes = 20;
        private String sweepCron = "0 */5 * * * *";
    }
}
