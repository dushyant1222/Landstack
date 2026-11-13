package edu.iitp.landstack.auth;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.auth.AuthDtos.*;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.security.AppUserPrincipal;
import edu.iitp.landstack.security.ClientIp;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.LoginRateLimiter;
import edu.iitp.landstack.security.SessionRevoker;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.AppUserRepository;
import edu.iitp.landstack.users.PasswordPolicy;
import edu.iitp.landstack.users.UserDtos.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {
    private static final String GENERIC_FAILURE = "Invalid email or password.";

    private final AuthenticationManager authManager;
    private final SecurityContextRepository contextRepository;
    private final LoginRateLimiter rateLimiter;
    private final AppUserRepository users;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final SessionRevoker sessions;

    public AuthController(AuthenticationManager authManager, SecurityContextRepository contextRepository,
                          LoginRateLimiter rateLimiter, AppUserRepository users, AuditService audit,
                          PasswordEncoder encoder, SessionRevoker sessions) {
        this.authManager = authManager;
        this.contextRepository = contextRepository;
        this.rateLimiter = rateLimiter;
        this.users = users;
        this.audit = audit;
        this.encoder = encoder;
        this.sessions = sessions;
    }

    /** Returns the CSRF token (also set as the XSRF-TOKEN cookie). Call before the first mutating request. */
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public UserDto login(@Valid @RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String email = req.email().strip().toLowerCase();
        String ip = ClientIp.of(request);
        if (rateLimiter.isBlocked(email, ip)) {
            audit.recordAs(null, null, "LOGIN_RATE_LIMITED", "AUTH", null, Map.of());
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many failed sign-in attempts. Please wait and try again.");
        }
        Authentication auth;
        try {
            auth = authManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, req.password()));
        } catch (AuthenticationException ex) {
            rateLimiter.recordFailure(email, ip);
            audit.recordAs(null, null, "LOGIN_FAILED", "AUTH", null, Map.of("reason", ex.getClass().getSimpleName()));
            // Same message whether the account is unknown, disabled or the password is wrong.
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, GENERIC_FAILURE);
        }
        rateLimiter.reset(email);

        // New session id on login (session fixation protection).
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true);

        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, request, response);

        AppUserPrincipal p = (AppUserPrincipal) auth.getPrincipal();
        AppUser u = users.findById(p.id()).orElseThrow();
        u.setLastLoginAt(Instant.now());
        users.save(u);
        audit.recordAs(u.getId(), u.getRole().name(), "LOGIN_SUCCEEDED", "USER", u.getId(), Map.of());
        return UserDto.from(u);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        CurrentUser.optional().ifPresent(p -> audit.recordAs(p.id(), p.role().name(), "LOGOUT", "USER", p.id(), Map.of()));
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserDto me() {
        return users.findById(CurrentUser.id()).map(UserDto::from)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "Sign in to continue."));
    }

    /** Self-service password change. Revokes all other sessions; the caller must sign in again. */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletRequest request) {
        AppUser u = users.findById(CurrentUser.id()).orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED, "Sign in to continue."));
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.field("currentPassword", "is incorrect");
        }
        PasswordPolicy.check("newPassword", req.newPassword());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        audit.record("PASSWORD_CHANGED", "USER", u.getId(), Map.of());
        sessions.revokeAll(u.getEmail());
        HttpSession s = request.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
    }
}
