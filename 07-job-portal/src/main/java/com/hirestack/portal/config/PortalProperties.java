package com.hirestack.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hirestack")
@Getter
@Setter
public class PortalProperties {

    private Search search = new Search();
    private Applications applications = new Applications();

    @Getter
    @Setter
    public static class Search {
        private int defaultPageSize = 20;
        private int maxPageSize = 50;
    }

    @Getter
    @Setter
    public static class Applications {
        private int maxOpenPerCandidate = 25;
    }
}
