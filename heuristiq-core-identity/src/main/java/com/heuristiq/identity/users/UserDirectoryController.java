package com.heuristiq.identity.users;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.enums.UamEnums;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * A deliberately narrow, read-only staff lookup for pickers in other applications (for example "assign this case to a
 * relationship manager"). It answers only who holds a role: id, name and email of active users, never credentials, phone
 * numbers or security settings, so an application can offer it to its staff without granting them the full user list.
 */
@RestController
@RequestMapping(UamUserConstants.DIRECTORY_PATH)
@Tag(name = "User Directory", description = "Narrow read-only lookup of active users who hold a given role, for pickers in other applications.")
public class UserDirectoryController {

    private final UamUserServiceUseCase userService;

    public UserDirectoryController(UamUserServiceUseCase userService) {
        this.userService = userService;
    }

    /** One person in the directory. */
    public record DirectoryUser(String id, String displayName, String email) {
        static DirectoryUser from(UamUser user) {
            String name = firstNonBlank(user.getDisplayName(), user.getUserName(), user.getEmail());
            return new DirectoryUser(user.getId(), name, user.getEmail());
        }

        private static String firstNonBlank(String... candidates) {
            for (String candidate : candidates) {
                if (candidate != null && !candidate.isBlank()) {
                    return candidate;
                }
            }
            return null;
        }
    }

    @Operation(summary = "Find active users who hold a role",
            description = "Returns id, display name and email of active users with the given role code, optionally filtered by a "
                    + "free-text search. The role is required so this can never list everyone. At most " + UamUserConstants.DIRECTORY_MAX_RESULTS + " results.")
    @GetMapping
    public CommonResponseObject list(
            @Parameter(description = "Role code the users must hold", required = true) @RequestParam String roleCode,
            @Parameter(description = "Optional free-text search over name and email") @RequestParam(required = false) String q,
            @Parameter(description = "Most results to return") @RequestParam(defaultValue = "" + UamUserConstants.DIRECTORY_DEFAULT_RESULTS) int limit) {
        int size = Math.max(1, Math.min(limit, UamUserConstants.DIRECTORY_MAX_RESULTS));
        String search = q == null || q.isBlank() ? null : q.trim();
        List<DirectoryUser> people = userService.search(new UamUserServiceUseCase.SearchInput(
                        0, size, roleCode.trim(), UamEnums.UserStatus.ACTIVE, search, null))
                .users().content().stream().map(DirectoryUser::from).toList();
        return CommonResponseObject.success(people);
    }
}
