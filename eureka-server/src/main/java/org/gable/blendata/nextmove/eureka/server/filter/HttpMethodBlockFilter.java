package org.gable.blendata.nextmove.eureka.server.filter;

import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
@Component
public class HttpMethodBlockFilter implements Filter {

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

        if (BLOCKED_METHODS.contains(method)) {
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
