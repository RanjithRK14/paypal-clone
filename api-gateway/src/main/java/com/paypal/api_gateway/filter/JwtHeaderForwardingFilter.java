package com.paypal.api_gateway.filter;

import com.paypal.api_gateway.model.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * JWT authentication + header-forwarding Gateway GlobalFilter.
 *
 * ROOT CAUSE of UnsupportedOperationException (Spring Framework 6.1 / Spring Boot 3.3.x):
 * ─────────────────────────────────────────────────────────────────────────────────────────
 * In Spring Cloud Gateway, ALL requests arrive with ReadOnlyHttpHeaders. Two approaches fail:
 *
 *   ✗ exchange.getRequest().mutate().header("X-User-Id", value)
 *     → throws UnsupportedOperationException: ReadOnlyHttpHeaders.set()
 *
 *   ✗ exchange.getRequest().mutate().headers(h -> { h.set("X-User-Id", value); })
 *     → DefaultServerHttpRequestBuilder.headers() calls headersConsumer.accept(this.headers)
 *       where this.headers is HttpHeaders.writableHttpHeaders(request.getHeaders())
 *       BUT writableHttpHeaders() wraps in a thin layer — the underlying delegate is STILL
 *       the ReadOnlyHttpHeaders, and HttpHeaders.set() eventually delegates to it → same crash.
 *
 * THE CORRECT FIX: ServerHttpRequestDecorator
 * ────────────────────────────────────────────
 * Create a BRAND NEW HttpHeaders object (fully mutable), copy all original headers into it,
 * add our custom headers, then wrap the original request in a ServerHttpRequestDecorator
 * that overrides getHeaders() to return our new mutable headers.
 * This completely bypasses ReadOnlyHttpHeaders — no mutation of the original at all.
 */
@Component
public class JwtHeaderForwardingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path     = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        // Always pass OPTIONS (CORS preflight) through
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // Public auth endpoints — no token required
        if (path.startsWith("/auth/") || path.equals("/auth")) {
            return chain.filter(exchange);
        }

        // All other routes require a valid Bearer token
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.err.println("[JwtFilter] Missing Authorization header: " + path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token;
        Map<String, Object> claims;
        try {
            token  = authHeader.substring(7).trim();
            claims = JwtUtil.validateToken(token);
        } catch (Exception e) {
            System.err.println("[JwtFilter] JWT invalid for " + path
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String username  = (String) claims.get("sub");
        String role      = (String) claims.get("role");
        Object userIdObj = claims.get("userId");

        if (username == null || username.isBlank()) {
            System.err.println("[JwtFilter] JWT missing 'sub' for " + path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        final String userId    = userIdObj != null ? String.valueOf(userIdObj) : "";
        final String finalRole = (role != null && !role.isBlank()) ? role : "ROLE_USER";
        final String finalUser = username;

        // ✅ THE CORRECT FIX for Spring Framework 6.1 ReadOnlyHttpHeaders:
        //
        // Build a BRAND NEW HttpHeaders (fully mutable) from scratch.
        // Copy every existing header, then add our three custom headers.
        // Wrap the original request in a ServerHttpRequestDecorator that
        // overrides getHeaders() — zero mutation of the original ReadOnlyHttpHeaders.
        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.addAll(exchange.getRequest().getHeaders());  // copy all originals
        newHeaders.set("X-User-Id",    userId);
        newHeaders.set("X-User-Email", finalUser);
        newHeaders.set("X-User-Role",  finalRole);

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public HttpHeaders getHeaders() {
                return newHeaders;
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(decoratedRequest)
                .build();

        System.out.println("[JwtFilter] OK user=" + finalUser
                + " userId=" + userId + " → " + path);
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() { return -1; }
}
