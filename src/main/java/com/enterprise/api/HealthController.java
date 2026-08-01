package com.enterprise.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping("/liveness")
    public ResponseEntity<Void> liveness() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/readiness")
    public ResponseEntity<Void> readiness() {
        try {
            dataSource.getConnection().close();
            if (redisTemplate != null) {
                redisTemplate.opsForValue().get("health-check");
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(503).build();
        }
    }
}