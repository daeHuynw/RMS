package com.rms.config;

import com.rms.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    //PasswordEncoder la mot interface trong Spring Security duoc su dung de ma hoa mat khau. 
    // Khi nguoi dung dang ky hoac thay doi mat khau, mat khau se duoc ma hoa bang mot thuat toan ma hoa (nhu BCrypt) truoc khi luu vao co so du lieu. 
    // Khi nguoi dung dang nhap, mat khau nhap vao se duoc ma hoa va so sanh voi gia tri da ma hoa trong co so du lieu de xac thuc nguoi dung.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    //SecurityFilterChain la mot chuoi cac bo loc (filter) duoc su dung de xu ly cac yeu cau HTTP den ung dung.
    // su dung de cau hinh cac quy tac bao mat cho cac yeu cau HTTP, nhu xac thuc nguoi dung, uan ly phien lam viec, va kiem soat truy cap vao cac tai nguyen.
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.rms.security.JwtAuthenticationFilter> jwtFilterRegistration(
            com.rms.security.JwtAuthenticationFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            com.rms.security.JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/register").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/menu", "/api/v1/menu/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, failure) -> writeUnauthorized(objectMapper, request, response)))
                .build();
    }

    private static void writeUnauthorized(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), 401, "UNAUTHORIZED", "Authentication required", null, request.getRequestURI()));
    }
}