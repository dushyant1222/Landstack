package edu.iitp.landstack.audit;

import edu.iitp.landstack.common.Hashing;
import edu.iitp.landstack.config.LandStackProperties;
import edu.iitp.landstack.security.AppUserPrincipal;
import edu.iitp.landstack.security.ClientIp;
import edu.iitp.landstack.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {
    private final AuditEventRepository repo;
    private final String salt;

    public AuditService(AuditEventRepository repo, LandStackProperties props) {
        this.repo = repo;
        this.salt = props.security().ipHashSalt();
    }

    /** Records an event inside the caller's transaction (rolled back together with the business change). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String action, String targetType, UUID targetId, Map<String, ?> summary) {
        AppUserPrincipal p = CurrentUser.optional().orElse(null);
        save(p == null ? null : p.id(), p == null ? null : p.role().name(), action, targetType, targetId, summary);
    }

    /** Records an event in its own transaction (survives a rollback of the caller, e.g. failed confirmation). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(String action, String targetType, UUID targetId, Map<String, ?> summary) {
        record(action, targetType, targetId, summary);
    }

    /** For events where the actor is known but not yet in the security context (login), or absent (failed login). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(UUID actorId, String actorRole, String action, String targetType, UUID targetId, Map<String, ?> summary) {
        save(actorId, actorRole, action, targetType, targetId, summary);
    }

    private void save(UUID actorId, String role, String action, String targetType, UUID targetId, Map<String, ?> summary) {
        repo.save(new AuditEvent(actorId, role, action, targetType, targetId,
                summary == null ? Map.of() : new LinkedHashMap<>(summary), ipHash()));
    }

    private String ipHash() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return Hashing.sha256Hex(salt + "|" + ClientIp.of(req)).substring(0, 16);
        }
        return null;
    }
}
