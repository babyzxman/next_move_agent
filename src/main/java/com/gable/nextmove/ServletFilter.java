package com.gable.nextmove;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class ServletFilter implements Filter {

    private static final List<String> BLOCKED_METHODS = Arrays.asList("OPTIONS", "TRACE");



    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();

        // Trace/log request
        System.out.println("[Filter] Request received: Method = " + method + ", URI = " + uri);

        if (BLOCKED_METHODS.contains(method)) {
            System.out.println("[Filter] Blocking method: " + method);
            httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, method + " not allowed.");
            return;
        }

        // Proceed with other filters or target resource
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
