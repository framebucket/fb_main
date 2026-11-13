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
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.user.auth.login.PhotographerLoginCodeService loginCodes;
    @org.springframework.beans.factory.annotation.Autowired
    private com.framebucket.user.auth.login.DeviceSessionService deviceSessions;
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
        UserResponse created = authService.register(request);
        // Straight to the WhatsApp code: entering it signs the new photographer in and verifies the number.
        loginCodes.requestCode(created.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login/request-code")
    public AuthDtos.LoginCodeResponse requestLoginCode(@Valid @RequestBody AuthDtos.LoginCodeRequest request) {
        var result = loginCodes.requestCode(request.identifier());
        return switch (result.result()) {
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "We couldn't find a FrameBucket account with that mobile number or email.");
            case COOLDOWN -> new AuthDtos.LoginCodeResponse(
                    "A code was sent a moment ago. Enter it, or wait 60 seconds to get a new one.", result.maskedMobile());
            case SENT -> new AuthDtos.LoginCodeResponse(
                    "We sent a 6-digit code to your WhatsApp (and a copy to your email).", result.maskedMobile());
        };
    }

    @PostMapping("/login/verify-code")
    public UserResponse verifyLoginCode(
            @Valid @RequestBody AuthDtos.LoginCodeVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        PhotographerPrincipal principal = loginCodes.verifyPrincipal(request.identifier(), request.code()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That code is wrong or has expired. Check WhatsApp or request a new code."));
        if (!principal.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account has been deactivated. Contact FrameBucket support.");
        }
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        startSession(authentication, httpRequest, httpResponse);
        deviceSessions.remember(httpRequest, httpResponse,
                com.framebucket.user.auth.login.DeviceSessionService.Kind.PHOTOGRAPHER, principal.getId(), null);
        jdbcTemplate.update("UPDATE fb_user SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", principal.getId());
        return authService.toResponse(principal);
    }

    private void startSession(Authentication authentication, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var existingSession = httpRequest.getSession(false);
        if (existingSession != null) {
            existingSession.invalidate();
        }
        csrfTokenRepository.saveToken(null, httpRequest, httpResponse);
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
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
        // Accounts now get one WhatsApp sign-in code at sign-up; it verifies the number here too.
        if (!authService.verifyMobile(request.identifier(), request.code())
                && loginCodes.verify(request.identifier(), request.code()).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The mobile verification code is invalid or expired. Request a new code and try again."
            );
        }
        return Map.of("message", "Your mobile number has been verified. You can now sign in.");
    }

    @PostMapping("/resend-mobile-code")
    public Map<String, String> resendMobileCode(@Valid @RequestBody ResendMobileCodeRequest request) {
        authService.resendMobileCode(request.identifier());
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
                            request.username().trim(),
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
        jdbcTemplate.update("UPDATE fb_user SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", photographer.getId());
        return authService.toResponse(photographer);
    }

    @GetMapping("/me")
    public UserResponse currentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal PhotographerPrincipal principal
    ) {
        return authService.toResponse(principal);
    }
}
