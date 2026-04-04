package com.paypal.api_gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Local health endpoint for the API Gateway itself.
 *
 * CRITICAL: This endpoint is answered by the GATEWAY directly.
 * It does NOT forward to any backend service.
 *
 * Why this exists:
 * - render.yaml sets healthCheckPath: /health on the gateway
 * - Render pings this every 30 seconds to keep the gateway alive
 * - If healthCheckPath was /ping/user, Render would ping a forwarded route
 *   which tries to reach user-service (also sleeping) → timeout → gateway killed
 * - /health answers in <1ms from the gateway itself → health check always passes
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "api-gateway"
        ));
    }
}
