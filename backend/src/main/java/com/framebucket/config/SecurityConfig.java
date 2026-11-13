package com.framebucket.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Public enquiry form: anonymous, often embedded on another site where cookies are blocked.
                        .ignoringRequestMatchers(new org.springframework.security.web.util.matcher.AntPathRequestMatcher(
                                "/api/public/studios/*/enquiries", "POST"))
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.disable())
                        // Everything stays DENY except the enquiry form, which studios embed on their websites.
                        .addHeaderWriter(new org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter(
                                // Match on the original URL: /enquire/{slug} is forwarded to /index.html.
                                (org.springframework.security.web.util.matcher.RequestMatcher) request -> {
                                    Object forwarded = request.getAttribute(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI);
                                    String uri = forwarded instanceof String value ? value : request.getRequestURI();
                                    return !(uri.equals(request.getContextPath() + "/enquire") || uri.startsWith(request.getContextPath() + "/enquire/"));
                                },
                                new org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter(
                                        org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode.DENY)))
                )
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/app",
                                "/app/",
                                "/app.html",
                                "/assets/**",
                                "/framebucket-mark.png",
                                "/framebucket-logo.png",
                                "/favicon.ico",
                                "/register",
                                "/verify-email",
                                "/payment",
                                "/login",
                                "/dashboard",
                                "/customer-portal",
                                "/quote/**",
                                "/team-invite/**",
                                "/event-checkin/**",
                                "/portfolio/**",
                                "/flipbook/**",
                                "/admin",
                                "/admin/**",
                                "/enquire",
                                "/enquire/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/subscriptions").permitAll()
                        .requestMatchers("/api/subscriptions/me", "/api/subscriptions/requests/**", "/api/subscriptions/requests").hasRole("PHOTOGRAPHER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/verify-mobile",
                                "/api/auth/resend-mobile-code",
                                "/api/customer-auth/request-code",
                                "/api/customer-auth/verify-code"
                        ).permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/customers/**", "/api/events", "/api/events/**", "/api/team", "/api/team/**", "/api/auth/me").hasRole("PHOTOGRAPHER")
                        .requestMatchers("/api/enquiries", "/api/enquiries/**").hasRole("PHOTOGRAPHER")
                        .requestMatchers("/api/dashboard/**").hasRole("PHOTOGRAPHER")
                        .requestMatchers("/api/studio", "/api/studio/**").hasRole("PHOTOGRAPHER")
                        .requestMatchers("/api/customer-auth/me", "/api/customer-portal/**").hasRole("CUSTOMER")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
