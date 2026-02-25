package mohammadnuridin.todolist.modules.auth;

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
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "{user.email.already_exists}");
        }

        // 2. Map DTO ke Entity & Hash Password
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
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
        user.setName(request.getName());

        // Update password jika diberikan (tidak kosong)
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.saveAndFlush(user);
        return toResponse(user);
    }

    /**
     * Mapper sederhana dari Entity ke Response DTO
     */
    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}