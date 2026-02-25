package mohammadnuridin.todolist.modules.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRequest {

    @NotBlank(message = "{user.name.not_blank}")
    @Size(max = 100, message = "{user.name.size}")
    private String name;

    @NotBlank(message = "{user.email.not_blank}")
    @Email(message = "{user.email.invalid}")
    @Size(max = 150, message = "{user.email.size}")
    private String email;

    @NotBlank(message = "{user.password.not_blank}")
    @Size(min = 8, message = "{user.password.size}")
    private String password;
}