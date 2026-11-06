package com.framebucket.user.auth.api;

import com.framebucket.user.auth.api.AuthDtos.LoginRequest;
import com.framebucket.user.auth.api.AuthDtos.RegisterRequest;
import com.framebucket.user.auth.api.AuthDtos.ResendMobileCodeRequest;
import com.framebucket.user.auth.api.AuthDtos.ResendVerificationRequest;
import com.framebucket.user.auth.api.AuthDtos.UserResponse;
import com.framebucket.user.auth.api.AuthDtos.VerifyEmailRequest;
import com.framebucket.user.auth.api.AuthDtos.VerifyMobileRequest;
import com.framebucket.user.auth.model.PhotographerPrincipal;
import com.framebucket.user.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(
            AuthService authService,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        // Resolving the argument causes the SPA CSRF handler to issue the cookie.
        return Map.of("ready", "true");
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        String email = authService.verifyEmail(request.token());
        return Map.of("email", email, "message", "Your email has been verified. Verify your mobile number to finish activating your account.");
    }

    @PostMapping("/resend-verification")
    public Map<String, String> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return Map.of("message", "If this account needs verification, a new link has been sent.");
    }

    @PostMapping("/verify-mobile")
    public Map<String, String> verifyMobile(@Valid @RequestBody VerifyMobileRequest request) {
        if (!authService.verifyMobile(request.email(), request.code())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The mobile verification code is invalid or expired. Request a new code and try again."
            );
        }
        return Map.of("message", "Your mobile number has been verified. You can now sign in.");
    }

    @PostMapping("/resend-mobile-code")
    public Map<String, String> resendMobileCode(@Valid @RequestBody ResendMobileCodeRequest request) {
        authService.resendMobileCode(request.email());
        return Map.of("message", "If this account needs mobile verification, a new code has been sent.");
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email().trim(),
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Please enter correct password or Password authentication failed"
            );
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof PhotographerPrincipal photographer && !photographer.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email before signing in.");
        }
        if (principal instanceof PhotographerPrincipal photographer && !photographer.isMobileVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your mobile number before signing in.");
        }

        // Discard any pre-login session before storing the authenticated context.
        var existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        // Rotate the anonymous CSRF token after login. The SPA fetches a fresh
        // cookie before its next state-changing request.
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        if (!(principal instanceof PhotographerPrincipal photographer)) {
            contextHolderStrategy.clearContext();
            throw new AuthenticationServiceException("Unexpected authenticated principal.");
        }
        return authService.toResponse(photographer);
    }

    @GetMapping("/me")
    public UserResponse currentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal PhotographerPrincipal principal
    ) {
        return authService.toResponse(principal);
    }
}
