package com.hotsearch.config;

import com.hotsearch.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String header = request.getHeader("Authorization");

        log.info("JwtAuthFilter: {} {} — Auth header: {}", method, path,
                header != null ? header.substring(0, Math.min(30, header.length())) + "..." : "MISSING");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            boolean valid = jwtUtil.validate(token);
            log.info("JwtAuthFilter: token valid={}", valid);
            if (valid) {
                String username = jwtUtil.getUsername(token);
                Long userId = jwtUtil.getUserId(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        new UserPrincipal(userId, username), null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.info("JwtAuthFilter: authenticated user={} id={}", username, userId);
            }
        }
        chain.doFilter(request, response);
    }

    public record UserPrincipal(Long id, String username) {}
}
