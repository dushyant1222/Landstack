package edu.iitp.landstack.security;

import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.users.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(AppUserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(uds);
        provider.setHideUserNotFoundExceptions(true);
        ProviderManager manager = new ProviderManager(provider);
        manager.setEraseCredentialsAfterAuthentication(true);
        return manager;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, LandStackProperties props, AppUserRepository users,
                                    SecurityErrorWriter errors, SecurityContextRepository contextRepository) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(c -> c.sameSite("Lax").path("/"));
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null); // load the token eagerly so the cookie is always issued

        boolean publicDocs = props.security().publicApiDocs();

        http
            .cors(c -> c.configurationSource(corsSource(props)))
            .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(csrfHandler))
            .securityContext(c -> c.securityContextRepository(contextRepository))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(f -> f.changeSessionId()))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .headers(h -> h
                    .contentTypeOptions(c -> {})
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(Duration.ofDays(180).toSeconds()))
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
                                    + "script-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'")))
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, ErrorCode.AUTH_REQUIRED, "Sign in to continue."))
                    .accessDeniedHandler((req, res, ex) -> errors.write(req, res, ErrorCode.ACCESS_DENIED,
                            ex instanceof org.springframework.security.web.csrf.CsrfException
                                    ? "Security token missing or expired. Reload the page and try again."
                                    : "You do not have permission to perform this action.")))
            .authorizeHttpRequests(a -> {
                a.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
                a.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll();
                a.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                if (publicDocs) {
                    a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                } else {
                    a.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").hasRole("ADMINISTRATOR");
                }
                a.requestMatchers("/actuator/**").hasRole("ADMINISTRATOR");
                a.requestMatchers("/api/v1/users/**").hasRole("ADMINISTRATOR");
                a.requestMatchers("/api/v1/**").authenticated();
                a.anyRequest().denyAll();
            })
            .addFilterAfter(new ActiveUserFilter(users, errors), BasicAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsSource(LandStackProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = props.security().corsOrigins() == null ? List.of() : props.security().corsOrigins();
        cfg.setAllowedOrigins(origins.stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "If-Match"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
