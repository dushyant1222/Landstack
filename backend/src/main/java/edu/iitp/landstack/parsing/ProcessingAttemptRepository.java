package edu.iitp.landstack.parsing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttempt, UUID> {
    List<ProcessingAttempt> findByImportJobIdOrderByStartedAtAsc(UUID importJobId);

    Optional<ProcessingAttempt> findFirstByImportJobIdAndStageInAndOutcomeOrderByStartedAtDesc(
            UUID importJobId, List<String> stages, ProcessingAttempt.Outcome outcome);
}
