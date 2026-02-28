package mohammadnuridin.todolist.modules.user;

import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.service.ValidationService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
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

        String normalizedEmail = request.email().trim().toLowerCase();

        // Cek duplikasi di aplikasi layer (fast fail, friendly error message)
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "{user.email.already_exists}");
        }

        User user = User.builder()
                .name(request.name())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .build();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Race condition: thread lain commit lebih dulu di antara existsByEmail() dan
            // saveAndFlush()
            // DB unique constraint menangkap ini dengan aman
            throw new ResponseStatusException(HttpStatus.CONFLICT, "{user.email.already_exists}");
        }

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
     * Update data User (Nama, Email & Password)
     */
    @Transactional
    public UserResponse update(String userId, UpdateUserRequest request) {
        validationService.validate(request);

        // Cek minimal satu field diisi
        if (!request.hasAnyField()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "{user.update.empty_request}");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "{user.not_found}"));

        // Update name — hanya jika disertakan
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name());
        }

        // Update email — hanya jika disertakan dan berbeda dari sekarang
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equals(user.getEmail())) {
            boolean emailTaken = userRepository.existsByEmailAndIdNot(request.email(), userId);
            if (emailTaken) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "{user.email.already_exists}");
            }
            user.setEmail(request.email());
        }

        // Update password — hanya jika disertakan
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