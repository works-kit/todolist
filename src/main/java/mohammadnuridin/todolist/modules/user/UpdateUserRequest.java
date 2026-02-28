package mohammadnuridin.todolist.modules.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request DTO khusus untuk PATCH /users/current.
 *
 * Semua field OPSIONAL — hanya field yang non-null yang akan diupdate (partial
 * update).
 * Tidak ada @NotBlank karena user boleh hanya update name saja, email saja,
 * atau password saja.
 *
 * Berbeda dengan UserRequest (register) yang semua field wajib diisi.
 */
public record UpdateUserRequest(

        // Tidak ada @NotBlank — null berarti "tidak diupdate"
        @Size(max = 100, message = "{user.name.size}") String name,

        @Email(message = "{user.email.invalid}") @Size(max = 150, message = "{user.email.size}") String email,

        @Size(min = 6, max = 100, message = "{user.password.size}") String password

) {
    /**
     * Validasi custom: minimal satu field harus diisi.
     * Mencegah request kosong {} yang tidak melakukan apa-apa.
     */
    public boolean hasAnyField() {
        return (name != null && !name.isBlank())
                || (email != null && !email.isBlank())
                || (password != null && !password.isBlank());
    }
}