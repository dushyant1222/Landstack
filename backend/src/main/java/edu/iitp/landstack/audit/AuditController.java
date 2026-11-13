package edu.iitp.landstack.audit;

import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.common.Paging;
import edu.iitp.landstack.security.CurrentUser;
import edu.iitp.landstack.security.Perms;
import edu.iitp.landstack.users.AppUser;
import edu.iitp.landstack.users.AppUserRepository;
import edu.iitp.landstack.users.Role;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/audit-events")
@Tag(name = "Audit trail")
public class AuditController {
    private final AuditEventRepository repo;
    private final AppUserRepository users;

    public AuditController(AuditEventRepository repo, AppUserRepository users) {
        this.repo = repo;
        this.users = users;
    }

    public record AuditEventDto(UUID id, UUID actorId, String actorName, String actorRole, String action,
                                String targetType, UUID targetId, Map<String, Object> summary, Instant occurredAt) {}

    /** Administrators see all events; data operators see only their own actions; reviewers have no access. */
    @GetMapping
    @PreAuthorize(Perms.OPERATE)
    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> list(@RequestParam(required = false) String action,
                                            @RequestParam(required = false) String targetType,
                                            @RequestParam(required = false) UUID targetId,
                                            @RequestParam(required = false) UUID actorId,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        UUID effectiveActor = CurrentUser.role() == Role.ADMINISTRATOR ? actorId : CurrentUser.id();
        Specification<AuditEvent> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (action != null && !action.isBlank()) ps.add(cb.equal(root.get("action"), action.strip()));
            if (targetType != null && !targetType.isBlank()) ps.add(cb.equal(root.get("targetType"), targetType.strip()));
            if (targetId != null) ps.add(cb.equal(root.get("targetId"), targetId));
            if (effectiveActor != null) ps.add(cb.equal(root.get("actorId"), effectiveActor));
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) ps.add(cb.lessThan(root.get("occurredAt"), to));
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var result = repo.findAll(spec, Paging.of(page, size, "occurredAt,desc", Map.of("occurredAt", "occurredAt"), "occurredAt,desc"));
        Set<UUID> actorIds = result.getContent().stream().map(AuditEvent::getActorId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(actorIds).forEach((AppUser u) -> names.put(u.getId(), u.getDisplayName()));
        return PageResponse.of(result, e -> new AuditEventDto(e.getId(), e.getActorId(), names.get(e.getActorId()),
                e.getActorRole(), e.getAction(), e.getTargetType(), e.getTargetId(), e.getSummary(), e.getOccurredAt()));
    }
}
