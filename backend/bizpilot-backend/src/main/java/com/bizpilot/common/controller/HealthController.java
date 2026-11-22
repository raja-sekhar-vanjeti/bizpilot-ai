package com.bizpilot.common.controller;

import com.bizpilot.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "bizpilot-backend");
        data.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.ok("Service is healthy", data));
    }
}
