package edu.iitp.landstack.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String password) {}

    public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
                                        @NotBlank @Size(min = 10, max = 128, message = "must be 10-128 characters") String newPassword) {}

    public record CsrfResponse(String headerName, String token) {}
}
