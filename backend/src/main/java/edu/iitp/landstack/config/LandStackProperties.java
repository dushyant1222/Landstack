package edu.iitp.landstack.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * All LandStack-specific configuration. Validated at startup so that a missing storage
 * directory or nonsensical limit fails fast with a readable message.
 */
@Validated
@ConfigurationProperties(prefix = "landstack")
public record LandStackProperties(
        @Valid @NotNull Storage storage,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Parsing parsing,
        @Valid @NotNull Processing processing,
        @Valid @NotNull Security security,
        @Valid @NotNull Privacy privacy,
        @Valid @NotNull Quality quality,
        @Valid @NotNull Retention retention,
        @Valid @NotNull Export export,
        @Valid @NotNull Bootstrap bootstrap,
        @Valid @NotNull Seed seed) {

    public record Storage(@NotBlank(message = "landstack.storage.dir (env LANDSTACK_STORAGE_DIR) must be set to a private, writable directory outside any web root") String dir) {}

    public record Upload(@Min(1024) long maxBytes) {}

    public record Parsing(@Min(1) int maxRows, @Min(1) int maxColumns, @Min(16) int maxCellLength, @Min(1) int maxPdfPages) {}

    public record Processing(boolean enabled, String url, String token,
                             @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {}

    public record Security(List<String> corsOrigins, @Min(1) int loginMaxAttempts,
                           @NotNull Duration loginWindow, boolean publicApiDocs, @NotBlank String ipHashSalt) {}

    public record Privacy(boolean maskOwnerForReviewer) {}

    public record Quality(@NotBlank String rulesVersion,
                          @NotNull @DecimalMin("0") BigDecimal areaMinSqm,
                          @NotNull @DecimalMin("0") BigDecimal areaMaxSqm,
                          @NotNull @DecimalMin("0") BigDecimal conflictingAreaTolerance,
                          @Min(3) int statisticalMinGroupSize,
                          @NotNull @DecimalMin("0.5") BigDecimal statisticalIqrMultiplier) {}

    public record Retention(@Min(1) int stagingDays, @Min(1) int abandonedJobDays, @NotBlank String cron) {}

    public record Export(@Min(1) int maxRows) {}

    public record Bootstrap(String adminEmail, String adminPassword) {}

    public record Seed(boolean demoUsers, String demoPassword) {}
}
