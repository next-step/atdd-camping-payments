package com.camping.payments.infra;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BasicAuthFilter implements Filter {

    private final String secretKey;

    public BasicAuthFilter(@Value("${payments.secret-key:test_sk_dummy}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) {
            unauthorized(res);
            return;
        }
        String token = auth.substring("Basic ".length());
        String decoded = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8);
        String user = decoded.split(":", 2)[0];
        if (!secretKey.equals(user)) {
            unauthorized(res);
            return;
        }
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse res) throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType("application/json");
        res.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid secret key\"}");
    }
}


