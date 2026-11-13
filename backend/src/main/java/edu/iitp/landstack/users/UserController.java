package edu.iitp.landstack.users;

import edu.iitp.landstack.common.PageResponse;
import edu.iitp.landstack.users.UserDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Administrator-only account provisioning")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<UserDto> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Role role,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "25") int size,
                                      @RequestParam(required = false) String sort) {
        return service.list(q, role, active, page, size, sort);
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/role")
    public UserDto changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest req) {
        return service.changeRole(id, req.role());
    }

    @PostMapping("/{id}/activate")
    public UserDto activate(@PathVariable UUID id) {
        return service.setActive(id, true);
    }

    @PostMapping("/{id}/deactivate")
    public UserDto deactivate(@PathVariable UUID id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest req) {
        service.resetPassword(id, req.newPassword());
    }
}
