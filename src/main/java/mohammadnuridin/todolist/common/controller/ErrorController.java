package mohammadnuridin.todolist.common.controller;

import mohammadnuridin.todolist.common.dto.WebResponse;
import mohammadnuridin.todolist.common.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

// Urutan yang rapi di ErrorController:
// 1. HttpMediaTypeNotSupportedException      → 415
// 2. HttpRequestMethodNotSupportedException  → 405  ← tambahkan di sini
// 3. HttpMessageNotReadableException         → 400
// 4. HandlerMethodValidationException        → 400
// 5. MethodArgumentNotValidException         → 400
// 6. ConstraintViolationException            → 400
// 7. ResourceNotFoundException               → 404
// 8. AccessDeniedException                   → 403
// 9. ResponseStatusException                 → dynamic
// 10. Exception (catch-all)                  → 500

@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorController {

        private final MessageSource messageSource;

        // ── 1. HttpMediaTypeNotSupportedException → 415 ──────────────────────────
        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<WebResponse<Object>> handleHttpMediaTypeNotSupported(
                        HttpMediaTypeNotSupportedException ex) {

                return ResponseEntity
                                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value())
                                                .status("error")
                                                .errors(Map.of("contentType",
                                                                "Content-Type '" + ex.getContentType()
                                                                                + "' is not supported. Use application/json"))
                                                .build());
        }

        // ── 2. HttpMessageNotReadableException → 400 ─────────────────────────────
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<WebResponse<Object>> handleHttpMessageNotReadable(
                        HttpMessageNotReadableException ex) {

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.BAD_REQUEST.value())
                                                .status("error")
                                                .errors(Map.of("body", "Request body is missing or malformed"))
                                                .build());
        }

        // ── 3. MethodArgumentNotValidException → 400 ─────────────────────────────
        // Triggered by @Valid on @RequestBody
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<WebResponse<Object>> methodArgumentNotValidException(
                        MethodArgumentNotValidException exception) {

                Map<String, String> errors = new HashMap<>();
                exception.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.BAD_REQUEST.value())
                                                .status("failed")
                                                .errors(errors)
                                                .build());
        }

        // ── 4. HandlerMethodValidationException → 400 ────────────────────────────
        // Triggered by Spring Boot 3.x MVC validation on @RequestBody record/params.
        // WAJIB ADA — tanpa ini jatuh ke generalException → 500.
        @ExceptionHandler(HandlerMethodValidationException.class)
        public ResponseEntity<WebResponse<Object>> handleHandlerMethodValidation(
                        HandlerMethodValidationException ex) {

                Map<String, String> errors = new HashMap<>();

                ex.getAllValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
                        // Ambil nama field: jika FieldError gunakan field name, fallback ke parameter
                        // name
                        String field = (error instanceof FieldError fe)
                                        ? fe.getField()
                                        : result.getMethodParameter().getParameterName();

                        String message = resolveMessage(error.getDefaultMessage());
                        errors.put(field != null ? field : "unknown", message);
                }));

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.BAD_REQUEST.value())
                                                .status("failed")
                                                .errors(errors)
                                                .build());
        }

        // ── 5. ConstraintViolationException → 400 ────────────────────────────────
        // SEBELUM: errors = String joined → $.errors.email tidak bisa diakses
        // SESUDAH: errors = Map<field, message> → $.errors.email bisa diakses
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<WebResponse<Object>> constraintViolationException(
                        ConstraintViolationException exception) {

                Map<String, String> errors = new HashMap<>();

                for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
                        // "register.request.email" → "email"
                        String path = violation.getPropertyPath().toString();
                        String field = path.contains(".")
                                        ? path.substring(path.lastIndexOf('.') + 1)
                                        : path;

                        String message = resolveMessage(violation.getMessage());
                        errors.put(field, message);
                }

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.BAD_REQUEST.value())
                                                .status("failed")
                                                .errors(errors)
                                                .build());
        }

        // ── 6. ResourceNotFoundException → 404 ───────────────────────────────────
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<WebResponse<Object>> resourceNotFoundException(
                        ResourceNotFoundException exception) {

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.NOT_FOUND.value())
                                                .status("failed")
                                                .errors(exception.getMessage())
                                                .build());
        }

        // ── 7. AccessDeniedException → 403 ───────────────────────────────────────
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<WebResponse<Object>> accessDeniedException(
                        AccessDeniedException exception) {

                return ResponseEntity
                                .status(HttpStatus.FORBIDDEN)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.FORBIDDEN.value())
                                                .status("failed")
                                                .errors("You do not have permission to access this resource")
                                                .build());
        }

        // ── 8. ResponseStatusException → dynamic status ──────────────────────────
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<WebResponse<Object>> handleResponseStatusException(
                        ResponseStatusException ex) {

                HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
                String message = resolveMessage(ex.getReason() != null ? ex.getReason() : ex.getMessage());

                return ResponseEntity
                                .status(status)
                                .body(WebResponse.builder()
                                                .code(status.value())
                                                .status("failed")
                                                .errors(message)
                                                .build());
        }

        // ── 9. Catch-all → 500 ───────────────────────────────────────────────────
        @ExceptionHandler(Exception.class)
        public ResponseEntity<WebResponse<Object>> generalException(Exception exception) {

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                                .status("failed")
                                                .errors("An unexpected error occurred: " + exception.getMessage())
                                                .build());
        }

        // ── 10. HttpRequestMethodNotSupportedException → 405 METHOD NOT ALLOWED
        // ───────────────────────────────────────────────────
        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<WebResponse<Object>> handleHttpRequestMethodNotSupported(
                        HttpRequestMethodNotSupportedException ex) {

                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(WebResponse.builder()
                                                .code(HttpStatus.METHOD_NOT_ALLOWED.value())
                                                .status("error")
                                                .errors(Map.of("method",
                                                                "HTTP method '" + ex.getMethod()
                                                                                + "' is not supported for this endpoint"))
                                                .build());
        }

        // ── Private Helper ────────────────────────────────────────────────────────

        /**
         * Resolves i18n message key jika format {key}, fallback ke raw message.
         * e.g. "{user.email.invalid}" → "Email must be a valid email address"
         */
        private String resolveMessage(String raw) {
                if (raw != null && raw.startsWith("{") && raw.endsWith("}")) {
                        String key = raw.substring(1, raw.length() - 1);
                        try {
                                return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
                        } catch (NoSuchMessageException e) {
                                return key;
                        }
                }
                return raw;
        }
}