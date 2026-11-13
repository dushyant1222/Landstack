package edu.iitp.landstack.security;

import edu.iitp.landstack.users.Role;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/** Reads the authenticated principal. Roles always come from the server-side session, never from requests. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AppUserPrincipal> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) return Optional.of(p);
        return Optional.empty();
    }

    public static AppUserPrincipal get() {
        return optional().orElseThrow(() -> new AuthenticationCredentialsNotFoundException("not authenticated"));
    }

    public static UUID id() {
        return get().id();
    }

    public static Role role() {
        return get().role();
    }

    public static boolean isReviewer() {
        return optional().map(p -> p.role() == Role.READ_ONLY_REVIEWER).orElse(true);
    }

    public static boolean canOperate() {
        return optional().map(p -> p.role() == Role.ADMINISTRATOR || p.role() == Role.DATA_OPERATOR).orElse(false);
    }
}
