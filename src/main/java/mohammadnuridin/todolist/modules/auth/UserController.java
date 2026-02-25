package mohammadnuridin.todolist.modules.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.dto.WebResponse;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public WebResponse<UserResponse> register(@RequestBody UserRequest request) {
        UserResponse userResponse = userService.register(request);
        return WebResponse.<UserResponse>builder()
                .code(HttpStatus.CREATED.value())
                .status("success")
                .data(userResponse)
                .build();
    }

    @GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse<UserResponse> get(User user) {
        // Asumsi: user didapat dari ArgumentResolver (Current User)
        UserResponse userResponse = userService.get(user.getId());
        return WebResponse.<UserResponse>builder()
                .data(userResponse)
                .build();
    }

    @PatchMapping(path = "/current", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse<UserResponse> update(User user, @RequestBody UserRequest request) {
        UserResponse userResponse = userService.update(user.getId(), request);
        return WebResponse.<UserResponse>builder()
                .data(userResponse)
                .build();
    }
}