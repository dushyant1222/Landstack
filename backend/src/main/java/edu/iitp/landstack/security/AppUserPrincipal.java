package edu.iitp.landstack.security;

import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Session principal. The password hash is erased after authentication and never serialized. */
public class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String email;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private transient String passwordHash;

    public AppUserPrincipal(UUID id, String email, String displayName, Role role, boolean active, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.active = active;
        this.passwordHash = passwordHash;
    }

    public static AppUserPrincipal from(AppUser u) {
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isActive(), u.getPasswordHash());
    }

    public UUID id() { return id; }
    public Role role() { return role; }
    public String displayName() { return displayName; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority(role.authority())); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }

    @Override public void eraseCredentials() { passwordHash = null; }
}
