package edu.iitp.landstack.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {
    List<SourceFile> findBySha256OrderByUploadedAtDesc(String sha256);
}
