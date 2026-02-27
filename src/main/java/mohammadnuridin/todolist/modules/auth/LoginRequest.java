package mohammadnuridin.todolist.modules.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
                @NotBlank(message = "{user.email.not_blank}") @Email(message = "{auth.email.invalid}") String email,

                @NotBlank(message = "{user.password.not_blank}") @Size(min = 6, message = "{user.password.size}") String password) {
}