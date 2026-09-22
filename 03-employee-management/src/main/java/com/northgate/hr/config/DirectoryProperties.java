package com.northgate.hr.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hr.directory")
@Getter
@Setter
public class DirectoryProperties {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
}
