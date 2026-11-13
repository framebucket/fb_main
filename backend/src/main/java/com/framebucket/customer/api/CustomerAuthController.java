package com.framebucket.customer.api;

import com.framebucket.customer.api.CustomerAuthDtos.CustomerAuthMessage;
import com.framebucket.customer.api.CustomerAuthDtos.CustomerOtpRequest;
import com.framebucket.customer.api.CustomerAuthDtos.CustomerOtpVerifyRequest;
import com.framebucket.customer.api.CustomerAuthDtos.CustomerPortalProfileResponse;
import com.framebucket.customer.model.CustomerPrincipal;
import com.framebucket.customer.service.CustomerLoginService;
import com.framebucket.customer.service.CustomerLoginService.CodeRequestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customer-auth")
public class CustomerAuthController {
    private final CustomerLoginService customerLoginService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.user.auth.login.DeviceSessionService deviceSessions;
    private final SecurityContextHolderStrategy contextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public CustomerAuthController(
            CustomerLoginService customerLoginService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.customerLoginService = customerLoginService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @PostMapping("/request-code")
    public CustomerAuthMessage requestCode(@Valid @RequestBody CustomerOtpRequest request) {
        CodeRequestResult result = customerLoginService.requestCode(request.mobileNumber());
        if (result == CodeRequestResult.NOT_REGISTERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Number not registered. Please enter registered mobile number");
        }
        if (result == CodeRequestResult.RESEND_COOLDOWN) {
            return new CustomerAuthMessage("A sign-in code was sent recently. Enter that code or wait 60 seconds before requesting another.");
        }
        return new CustomerAuthMessage("A sign-in code has been sent to your registered email and mobile number.");
    }

    @PostMapping("/verify-code")
    public CustomerPortalProfileResponse verifyCode(
            @Valid @RequestBody CustomerOtpVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        CustomerPrincipal principal = customerLoginService.verifyCode(request.mobileNumber(), request.code());
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The sign-in code is invalid or expired. Request a new code and try again.");
        }

        var existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        deviceSessions.remember(httpRequest, httpResponse,
                com.framebucket.user.auth.login.DeviceSessionService.Kind.CUSTOMER, null, principal.getMobileNumber());

        return CustomerLoginService.toProfile(principal);
    }

    @GetMapping("/me")
    public CustomerPortalProfileResponse currentCustomer(
            @org.springframework.security.core.annotation.AuthenticationPrincipal CustomerPrincipal principal
    ) {
        return CustomerLoginService.toProfile(principal);
    }
}
