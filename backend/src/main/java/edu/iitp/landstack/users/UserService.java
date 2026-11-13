package edu.iitp.landstack.users;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.common.Text;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import edu.iitp.landstack.security.SessionRevoker;
import edu.iitp.landstack.users.UserDtos.CreateUserRequest;
import edu.iitp.landstack.users.UserDtos.UserDto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {
    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "createdAt", "email", "email", "displayName", "displayName", "role", "role");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final SessionRevoker sessions;

    public UserService(AppUserRepository users, PasswordEncoder encoder, AuditService audit, SessionRevoker sessions) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.sessions = sessions;
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String q, Role role, Boolean active, int page, int size, String sort) {
        Specification<AppUser> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!Text.isBlank(q)) {
                String like = Text.likeContains(q.strip());
                ps.add(cb.or(cb.like(cb.lower(root.get("email")), like, '\\'),
                        cb.like(cb.lower(root.get("displayName")), like, '\\')));
            }
            if (role != null) ps.add(cb.equal(root.get("role"), role));
            if (active != null) ps.add(cb.equal(root.get("active"), active));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(users.findAll(spec, Paging.of(page, size, sort, SORTS, "createdAt,desc")), UserDto::from);
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return UserDto.from(load(id));
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public UserDto create(CreateUserRequest req) {
        String email = req.email().strip().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.CONFLICT, "A user with this email already exists.");
        }
        PasswordPolicy.check("password", req.password());
        AppUser u = users.save(new AppUser(email, req.displayName(), encoder.encode(req.password()), req.role()));
        audit.record("USER_CREATED", "USER", u.getId(), Map.of("role", u.getRole().name()));
        return UserDto.from(u);
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public UserDto update(UUID id, UserDtos.UpdateUserRequest req) {
        AppUser u = load(id);
        u.setDisplayName(req.displayName());
        audit.record("USER_UPDATED", "USER", u.getId(), Map.of("fields", "displayName"));
        return UserDto.from(u);
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public UserDto changeRole(UUID id, Role role) {
        AppUser u = load(id);
        if (u.getId().equals(CurrentUser.id())) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot change your own role.");
        }
        Role previous = u.getRole();
        if (previous == role) return UserDto.from(u);
        guardLastAdmin(u, "change the role of");
        u.setRole(role);
        sessions.revokeAll(u.getEmail());
        audit.record("USER_ROLE_CHANGED", "USER", u.getId(), Map.of("from", previous.name(), "to", role.name()));
        return UserDto.from(u);
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public UserDto setActive(UUID id, boolean active) {
        AppUser u = load(id);
        if (!active && u.getId().equals(CurrentUser.id())) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot deactivate your own account.");
        }
        if (u.isActive() == active) return UserDto.from(u);
        if (!active) guardLastAdmin(u, "deactivate");
        u.setActive(active);
        if (!active) sessions.revokeAll(u.getEmail());
        audit.record(active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", u.getId(), Map.of());
        return UserDto.from(u);
    }

    @PreAuthorize(Perms.ADMIN)
    @Transactional
    public void resetPassword(UUID id, String newPassword) {
        AppUser u = load(id);
        PasswordPolicy.check("newPassword", newPassword);
        u.setPasswordHash(encoder.encode(newPassword));
        sessions.revokeAll(u.getEmail());
        audit.record("USER_PASSWORD_RESET", "USER", u.getId(), Map.of());
    }

    private void guardLastAdmin(AppUser u, String verb) {
        if (u.getRole() == Role.ADMINISTRATOR && u.isActive() && users.countByRoleAndActiveTrue(Role.ADMINISTRATOR) <= 1) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot " + verb + " the last active administrator.");
        }
    }

    private AppUser load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }
}
