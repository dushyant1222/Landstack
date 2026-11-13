package edu.iitp.landstack.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Deletes every server-side session of a user (used on deactivation, role change and password reset). */
@Component
public class SessionRevoker {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void revokeAll(String principalName) {
        sessions.findByPrincipalName(principalName).keySet().forEach(sessions::deleteById);
    }
}
