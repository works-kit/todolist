package mohammadnuridin.todolist.common.exception;

import jakarta.validation.ConstraintViolationException;
import mohammadnuridin.todolist.common.dto.WebResponse;

import java.util.Map;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ErrorController {

    // 1. Menangani Validasi Bean (@Valid pada RequestBody)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WebResponse<Map<String, String>>> methodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        Map<String, String> errors = new HashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(WebResponse.<Map<String, String>>builder().errors(errors).build());
    }

    // 2. Menangani Resource Not Found (Custom)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<WebResponse<String>> resourceNotFoundException(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(WebResponse.<String>builder().errors(exception.getMessage()).build());
    }

    // 3. Menangani Access Denied (Spring Security Authorization Error)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<WebResponse<String>> accessDeniedException(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(WebResponse.<String>builder().errors("You do not have permission to access this resource")
                        .build());
    }

    // 4. Menangani Constraint Violation (misal pada @PathVariable atau
    // @RequestParam)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<WebResponse<String>> constraintViolationException(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(WebResponse.<String>builder().errors(exception.getMessage()).build());
    }

    // 5. Menangani ResponseStatusException (Generic Spring Web Exception)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<WebResponse<String>> apiException(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(WebResponse.<String>builder().errors(exception.getReason()).build());
    }
}