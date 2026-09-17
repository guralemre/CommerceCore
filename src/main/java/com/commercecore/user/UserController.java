package com.commercecore.user;

import com.commercecore.common.ConflictException;
import com.commercecore.common.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final CurrentUserResolver currentUserResolver;

    public UserController(UserRepository userRepository, CurrentUserResolver currentUserResolver) {
        this.userRepository = userRepository;
        this.currentUserResolver = currentUserResolver;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return UserResponse.from(currentUserResolver.resolve(authentication));
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        boolean demotingLastAdmin = target.getRole() == Role.ADMIN
                && request.role() != Role.ADMIN
                && userRepository.countByRole(Role.ADMIN) <= 1;
        if (demotingLastAdmin) {
            throw new ConflictException("Cannot demote the last remaining admin");
        }

        target.setRole(request.role());
        return UserResponse.from(userRepository.save(target));
    }
}
