package dev.chaosmonkey.api;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import dev.chaosmonkey.engine.Policy;

@Configuration
public class SecurityConfig {
    @Bean Policy policy(@Value("${chaos.demo-url}") String target) { return new Policy(target); }
    @Bean UserDetailsService users(@Value("${chaos.admin-user}") String user,@Value("${chaos.admin-password}") String password) {
        if(password.length()<12)throw new IllegalStateException("CHAOS_ADMIN_PASSWORD must contain at least 12 characters");
        return new InMemoryUserDetailsManager(User.withUsername(user).password("{bcrypt}"+new BCryptPasswordEncoder().encode(password)).roles("ADMIN").build());
    }
    @Bean SecurityFilterChain filter(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/actuator/health").permitAll().anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults()).headers(h->h.contentTypeOptions(Customizer.withDefaults()).frameOptions(f->f.deny())).build();
    }
}
