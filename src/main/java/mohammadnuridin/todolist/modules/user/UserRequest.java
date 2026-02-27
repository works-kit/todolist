package mohammadnuridin.todolist.modules.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank(message = "{user.name.not_blank}") @Size(max = 100, message = "{user.name.size}") String name,

        @NotBlank(message = "{user.email.not_blank}") @Email(message = "{user.email.invalid}") @Size(max = 150) String email,

        @NotBlank(message = "{user.password.not_blank}") @Size(min = 6, max = 100, message = "{user.password.size}") String password) {
}