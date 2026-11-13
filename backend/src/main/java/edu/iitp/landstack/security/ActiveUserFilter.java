package edu.iitp.landstack.security;

import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.AppUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Re-checks the signed-in user against the database on every request, so a deactivated account
 * loses access immediately and a role change takes effect immediately, even if a session survived.
 */
public class ActiveUserFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    private final SecurityErrorWriter errors;

    public ActiveUserFilter(AppUserRepository users, SecurityErrorWriter errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal p) {
            Optional<AppUser> fresh = users.findById(p.id());
            if (fresh.isEmpty() || !fresh.get().isActive()) {
                SecurityContextHolder.clearContext();
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                errors.write(req, res, ErrorCode.AUTH_REQUIRED, "Your session has ended. Please sign in again.");
                return;
            }
            AppUser u = fresh.get();
            if (u.getRole() != p.role() || !u.getEmail().equals(p.getUsername())) {
                AppUserPrincipal updated = AppUserPrincipal.from(u);
                updated.eraseCredentials();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(updated, null, updated.getAuthorities()));
            }
        }
        chain.doFilter(req, res);
    }
}
