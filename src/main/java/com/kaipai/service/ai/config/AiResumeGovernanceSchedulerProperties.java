package com.kaipai.service.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kaipai.ai.resume.governance-scheduler")
public class AiResumeGovernanceSchedulerProperties {

    private boolean enabled = false;

    private Duration initialDelay = Duration.ofMinutes(2);

    private Duration fixedDelay = Duration.ofMinutes(15);

    private int limit = 20;

    private Duration lockTtl = Duration.ofMinutes(14);

    private String reason = "AI治理定时sweep";
}
