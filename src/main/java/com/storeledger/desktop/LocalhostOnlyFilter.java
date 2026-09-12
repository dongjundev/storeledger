package com.storeledger.desktop;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Mac 앱 모드에서는 이 컴퓨터 주소(localhost, 127.0.0.1)로 온 요청만 받는다.
 * 127.0.0.1에만 바인딩해도, 악성 웹페이지가 DNS 리바인딩으로 자기 도메인을 127.0.0.1로 돌려 요청할 수 있어서
 * Host 헤더까지 확인한다.
 */
@Component
@Profile("desktop")
class LocalhostOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ALLOWED_HOSTS.contains(request.getServerName())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }
}
