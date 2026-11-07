package com.rms.common;

import com.rms.exception.ApiException;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString());
    }

    // @GetMapping("/error-demo")
    // public void errorDemo() {
    //     throw ApiException.notFound("Health error demo");
    // }
}