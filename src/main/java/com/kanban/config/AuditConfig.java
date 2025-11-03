package com.kanban.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kanban.audit")
@Data
public class AuditConfig {
    
    private AutoArchive autoArchive = new AutoArchive();
    
    @Data
    public static class AutoArchive {
        private boolean enabled = true;
        private int retentionDays = 90;
    }
}