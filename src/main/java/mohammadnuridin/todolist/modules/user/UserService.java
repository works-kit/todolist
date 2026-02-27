package mohammadnuridin.todolist.modules.user;

import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.service.ValidationService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ValidationService validationService;

    /**
     * Logika Registrasi User Baru
     */
    @Transactional
    public UserResponse register(UserRequest request) {
        validationService.validate(request);
        // 1. Cek duplikasi email
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "{user.email.already_exists}");
        }

        // 2. Map DTO ke Entity & Hash Password
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        // 3. Simpan (ID digenerate otomatis oleh @PrePersist di Entity)
        userRepository.saveAndFlush(user);

        return toResponse(user);
    }

    /**
     * Mengambil data profil user yang sedang login (Current User)
     */
    @Transactional(readOnly = true)
    public UserResponse get(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "{user.not_found}"));
        return toResponse(user);
    }

    /**
     * Update data User (Nama & Password)
     */
    @Transactional
    public UserResponse update(String userId, UserRequest request) {
        validationService.validate(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "{user.not_found}"));

        // Update nama
        user.setName(request.name());

        // Update password jika diberikan (tidak kosong)
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }

        userRepository.saveAndFlush(user);
        return toResponse(user);
    }

    /**
     * Mapper sederhana dari Entity ke Response DTO
     */
    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail());
    }
}