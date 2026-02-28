package mohammadnuridin.todolist.modules.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.dto.WebResponse;
import mohammadnuridin.todolist.core.security.AuthenticatedUser;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<UserResponse> register(@RequestBody @Valid @NotNull UserRequest request) {
        UserResponse userResponse = userService.register(request);
        return WebResponse.<UserResponse>builder()
                .code(HttpStatus.CREATED.value())
                .status("success")
                .data(userResponse)
                .build();
    }

    // ─── GET /apiauth/me ───────────────────────────────────────────────────

    @GetMapping("/current")
    public WebResponse<UserResponse> get(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        UserResponse user = userService.get(currentUser.getUserId());
        return WebResponse.<UserResponse>builder()
                .status("Current user retrieved")
                .code(HttpStatus.OK.value())
                .data(user)
                .build();
    }

    @PatchMapping(path = "/current", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse<UserResponse> update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestBody @Valid UpdateUserRequest request) {
        UserResponse userResponse = userService.update(currentUser.getUserId(), request);
        return WebResponse.<UserResponse>builder()
                .status("Current user updated")
                .code(HttpStatus.OK.value())
                .data(userResponse)
                .build();
    }
}