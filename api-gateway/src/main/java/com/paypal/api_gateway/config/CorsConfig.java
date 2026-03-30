package com.paypal.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    @Value("${FRONTEND_URL:https://novapay-app.netlify.app}")
    private String frontendUrl;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList(frontendUrl));
        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /** Intercepts OPTIONS preflight before Spring Security can reject it with 401. */
    @Bean
    public WebFilter optionsPreflightFilter() {
        return new OptionsPreflightFilter(frontendUrl);
    }

    static class OptionsPreflightFilter implements WebFilter, Ordered {

        private final String frontendUrl;

        OptionsPreflightFilter(String frontendUrl) {
            this.frontendUrl = frontendUrl;
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
                ServerHttpResponse r = exchange.getResponse();
                r.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, frontendUrl);
                r.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "GET,POST,PUT,DELETE,OPTIONS,PATCH,HEAD");
                r.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "Authorization,Content-Type,X-Requested-With,Accept,Origin");
                r.getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                r.getHeaders().set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
                r.setStatusCode(HttpStatus.OK);
                return r.setComplete();
            }
            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}