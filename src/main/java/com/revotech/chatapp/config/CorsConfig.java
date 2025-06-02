package com.revotech.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${app.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Set allowed origins
        if (allowedOrigins.length == 1 && "*".equals(allowedOrigins[0])) {
            configuration.setAllowedOriginPatterns(List.of("*"));
            log.warn("CORS configured to allow all origins (*). This should not be used in production!");
        } else {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        }

        // Set allowed methods
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));

        // Set allowed headers
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(List.of("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        // Set allow credentials
        configuration.setAllowCredentials(allowCredentials);

        // Expose headers that clients can access
        configuration.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Link",
                "X-Total-Count",
                "X-Page-Number",
                "X-Page-Size",
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Headers",
                "Access-Control-Expose-Headers"
        ));

        // Set max age for preflight requests
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configured - Origins: {}, Methods: {}, Allow credentials: {}",
                Arrays.toString(allowedOrigins), Arrays.toString(allowedMethods), allowCredentials);

        return source;
    }
}