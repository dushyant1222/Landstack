package edu.iitp.landstack.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    /** Safe profile projection: never contains the password hash. */
    public record UserDto(UUID id, String email, String displayName, Role role, boolean active,
                          Instant lastLoginAt, Instant createdAt) {
        public static UserDto from(AppUser u) {
            return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isActive(),
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 10, max = 128, message = "must be 10-128 characters") String password,
            @NotNull Role role) {}

    public record UpdateUserRequest(@NotBlank @Size(max = 120) String displayName) {}

    public record ChangeRoleRequest(@NotNull Role role) {}

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128, message = "must be 10-128 characters") String newPassword) {}
}
