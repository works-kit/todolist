package mohammadnuridin.todolist.core.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Simple principal object stored in SecurityContext.
 * No roles/authorities needed — just user identity.
 */
@Getter
@AllArgsConstructor
public class AuthenticatedUser {
    private final String userId;
    private final String email;
}