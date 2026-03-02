package com.paypal.api_gateway.filter;

import com.paypal.api_gateway.model.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/auth/signup",
            "/auth/login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();
        String normalizedPath = path.replaceAll("/+$", "");

        System.out.println("Incoming Request: " + normalizedPath);

        // Skip public endpoints
        if (PUBLIC_PATHS.contains(normalizedPath)) {

            System.out.println("Public endpoint — skipping JWT");

            return chain.filter(exchange);
        }

        String authHeader =
                exchange.getRequest()
                        .getHeaders()
                        .getFirst(HttpHeaders.AUTHORIZATION);


        // Check header exists
        if (authHeader == null) {

            System.out.println("Authorization header missing");

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }


        // Check Bearer format
        if (!authHeader.startsWith("Bearer ")) {

            System.out.println("Invalid Authorization format");

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }


        try {

            String token = authHeader.substring(7);

            System.out.println("Token received: " + token);

            Claims claims = JwtUtil.validateToken(token);

            System.out.println("Token valid for user: " +
                    claims.getSubject());


            exchange.getRequest()
                    .mutate()
                    .header("X-User-Email", claims.getSubject())
                    .header("X-User-Id", claims.get("userId", String.class))
                    .header("X-User-Role", claims.get("role", String.class))
                    .build();
            return chain.filter(exchange);

        }
        catch (Exception e) {

            System.out.println("Token validation failed: "
                    + e.getMessage());

            exchange.getResponse()
                    .setStatusCode(HttpStatus.UNAUTHORIZED);

            return exchange.getResponse().setComplete();
        }
    }


    @Override
    public int getOrder() {

        return -100;
    }
}
