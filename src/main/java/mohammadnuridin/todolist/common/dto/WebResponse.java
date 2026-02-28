package mohammadnuridin.todolist.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebResponse<T> {

    private Integer code;

    private String status;

    private T data;

    /**
     * Tipe Object (bukan T) agar bisa menampung:
     * - Map<String, String> untuk field-level validation errors → {"email": "must
     * be valid"}
     * - String untuk single message → "User not found"
     *
     * Jika tetap T, Jackson akan gagal serialize Map saat T = UserResponse.
     */
    private Object errors;

    /**
     * Tipe Object untuk meta agar fleksibel menampung pagination, dll.
     * e.g. {"page": 1, "totalPages": 10, "totalElements": 100}
     */
    private Object meta;
}