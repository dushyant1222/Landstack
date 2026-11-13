package edu.iitp.landstack.landrecords;

import edu.iitp.landstack.audit.AuditService;
import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.imports.ImportJobRepository;
import edu.iitp.landstack.landrecords.ConfirmationService.ConfirmRequest;
import edu.iitp.landstack.landrecords.ConfirmationService.ConfirmResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports/{id}/confirm")
@Tag(name = "Import confirmation")
public class ConfirmationController {
    private static final Logger log = LoggerFactory.getLogger(ConfirmationController.class);

    private final ConfirmationService service;
    private final ImportJobRepository jobs;
    private final AuditService audit;
    private final TransactionTemplate requiresNew;

    public ConfirmationController(ConfirmationService service, ImportJobRepository jobs, AuditService audit,
                                  PlatformTransactionManager txm) {
        this.service = service;
        this.jobs = jobs;
        this.audit = audit;
        this.requiresNew = new TransactionTemplate(txm);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Explicit confirmation. The {@code Idempotency-Key} header (or body confirmationKey) makes retries safe:
     * repeating the same key returns the original result without inserting anything.
     */
    @PostMapping
    public ConfirmResult confirm(@PathVariable UUID id,
                                 @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
                                 @RequestBody(required = false) ConfirmRequest body) {
        UUID key = idempotencyKey != null ? idempotencyKey : body == null ? null : body.confirmationKey();
        ConfirmRequest req = new ConfirmRequest(key, body == null ? null : body.includeWarningRows());
        try {
            return service.confirm(id, req);
        } catch (ApiException | org.springframework.security.access.AccessDeniedException
                 | org.springframework.security.core.AuthenticationException e) {
            throw e;
        } catch (RuntimeException e) {
            // The confirmation transaction has been rolled back: no records from this attempt exist.
            log.warn("Confirmation of import {} rolled back: {}", id, e.getClass().getSimpleName());
            requiresNew.executeWithoutResult(s -> jobs.findById(id).ifPresent(j ->
                    j.note("CONFIRMATION_ROLLED_BACK", "The last confirmation attempt failed and was fully rolled back; no records were saved. You can try again.")));
            audit.recordIndependent("IMPORT_CONFIRM_FAILED", "IMPORT_JOB", id, Map.of("reason", e.getClass().getSimpleName()));
            throw new ApiException(ErrorCode.CONFIRMATION_FAILED,
                    "Confirmation failed and was fully rolled back; no records were saved. Please try again.");
        }
    }
}
