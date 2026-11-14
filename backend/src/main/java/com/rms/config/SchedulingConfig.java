package com.rms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật @Scheduled. Tách thành class riêng (không đặt trên RmsApplication) để các test slice
 * như @WebMvcTest không khởi động tác vụ định kỳ.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
