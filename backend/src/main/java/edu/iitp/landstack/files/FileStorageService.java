package edu.iitp.landstack.files;

import edu.iitp.landstack.common.ApiException;
import edu.iitp.landstack.common.ErrorCode;
import edu.iitp.landstack.common.Hashing;
import edu.iitp.landstack.config.LandStackProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores uploads in a private directory. Storage keys are server-generated ({yyyy}/{mm}/{uuid}) and
 * contain no user input, so filenames cannot influence the path. Paths are never exposed via the API.
 */
@Service
public class FileStorageService {
    private final Path root;
    private final long maxBytes;

    public FileStorageService(LandStackProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
        this.maxBytes = props.upload().maxBytes();
    }

    public record TempFile(Path path, String sha256, long size) {}

    /** Streams the upload to a temp file while hashing it and enforcing the size limit. */
    public TempFile writeTemp(InputStream in) throws IOException {
        Path tmpDir = root.resolve(".tmp");
        Files.createDirectories(tmpDir);
        Path tmp = tmpDir.resolve(UUID.randomUUID().toString());
        MessageDigest digest = Hashing.sha256Digest();
        long total = 0;
        try (InputStream din = new DigestInputStream(in, digest); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = din.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new ApiException(ErrorCode.FILE_TOO_LARGE, "The file exceeds the maximum allowed upload size.");
                }
                out.write(buf, 0, n);
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        if (total == 0) {
            Files.deleteIfExists(tmp);
            throw new ApiException(ErrorCode.FILE_UNREADABLE, "The file is empty.");
        }
        return new TempFile(tmp, HexFormat.of().formatHex(digest.digest()), total);
    }

    /** Moves a validated temp file into permanent storage and returns its storage key. */
    public String commit(TempFile temp) throws IOException {
        LocalDate d = LocalDate.now(ZoneOffset.UTC);
        String key = "%04d/%02d/%s".formatted(d.getYear(), d.getMonthValue(), UUID.randomUUID());
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.move(temp.path(), target, StandardCopyOption.ATOMIC_MOVE);
        return key;
    }

    public Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || p.equals(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }

    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // best effort; retention job retries
        }
    }

    public void deleteTemp(TempFile temp) {
        try {
            Files.deleteIfExists(temp.path());
        } catch (IOException ignored) {
        }
    }

    public Path root() {
        return root;
    }
}
