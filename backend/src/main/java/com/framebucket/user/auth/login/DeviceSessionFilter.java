package com.framebucket.user.auth.login;

import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.customer.service.CustomerLoginService;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Signs an app user back in from their remembered device token when their server session has gone (server restart,
 * idle timeout). Registered in the security chain right after the security context is loaded; not a bean, so the
 * servlet container does not register it a second time.
 */
public class DeviceSessionFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceSessionFilter.class);

    private final DeviceSessionService devices;
    private final PhotographerLoginCodeService photographers;
    private final CustomerLoginService customers;
    private final SecurityContextRepository repository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    public DeviceSessionFilter(DeviceSessionService devices, PhotographerLoginCodeService photographers,
                               CustomerLoginService customers, SecurityContextRepository repository) {
        this.devices = devices;
        this.photographers = photographers;
        this.customers = customers;
        this.repository = repository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        // Only API calls need a principal; logout must not be undone by restoring the session first.
        return !path.startsWith("/api/") || path.equals("/api/auth/logout") || DeviceSessionService.token(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = holder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken || !current.isAuthenticated()) {
            try {
                restore(request, response);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not restore a remembered app sign-in: {}", exception.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private void restore(HttpServletRequest request, HttpServletResponse response) {
        Optional<DeviceSessionService.DeviceSession> found = devices.resolve(request);
        if (found.isEmpty()) {
            devices.clearCookie(response);
            return;
        }
        DeviceSessionService.DeviceSession session = found.get();
        Authentication authentication = null;
        if (session.kind() == DeviceSessionService.Kind.PHOTOGRAPHER && session.userId() != null) {
            PhotographerPrincipal principal = photographers.principal(session.userId()).orElse(null);
            if (principal != null) {
                if (principal.isEnabled()) {
                    authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
                }
            }
        } else if (session.kind() == DeviceSessionService.Kind.CUSTOMER && session.customerMobile() != null) {
            CustomerPrincipal principal = customers.principalFor(session.customerMobile());
            if (principal != null) {
                authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
            }
        }
        if (authentication == null) {
            devices.revoke(session.id());
            devices.clearCookie(response);
            return;
        }
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        repository.saveContext(context, request, response);
    }
}
