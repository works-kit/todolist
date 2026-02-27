package mohammadnuridin.todolist.common.controller;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.dto.WebResponse;
import mohammadnuridin.todolist.common.exception.ResourceNotFoundException;

import java.util.Map;
import java.util.stream.Collectors;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorController {

        private final MessageSource messageSource;

        // 1. Menangani Validasi Bean (@Valid pada RequestBody)
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<WebResponse<Object>> methodArgumentNotValidException(
                        MethodArgumentNotValidException exception) {

                HttpStatus status = HttpStatus.BAD_REQUEST;

                Map<String, String> errors = new HashMap<>();
                exception.getBindingResult().getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors(errors)
                                .build();

                return ResponseEntity.status(status).body(response);
        }

        // 2. Menangani Resource Not Found (Custom)
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<WebResponse<Object>> resourceNotFoundException(ResourceNotFoundException exception) {

                HttpStatus status = HttpStatus.NOT_FOUND;

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors(exception.getMessage())
                                .build();

                return ResponseEntity.status(status).body(response);
        }

        // 3. Menangani Access Denied (Spring Security Authorization Error)
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<WebResponse<Object>> accessDeniedException(AccessDeniedException exception) {

                HttpStatus status = HttpStatus.FORBIDDEN;

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors("You do not have permission to access this resource")
                                .build();

                return ResponseEntity.status(status).body(response);
        }

        // 4. Menangani Constraint Violation (misal pada @PathVariable atau
        // @RequestParam)
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<WebResponse<Object>> constraintViolationException(
                        ConstraintViolationException exception) {

                HttpStatus status = HttpStatus.BAD_REQUEST;

                String translatedMessage = exception.getConstraintViolations()
                                .stream()
                                .map(violation -> {
                                        String rawMessage = violation.getMessage();

                                        if (rawMessage != null && rawMessage.startsWith("{")
                                                        && rawMessage.endsWith("}")) {
                                                String key = rawMessage.substring(1, rawMessage.length() - 1);
                                                try {
                                                        return messageSource.getMessage(key, null,
                                                                        LocaleContextHolder.getLocale());
                                                } catch (NoSuchMessageException e) {
                                                        return key;
                                                }
                                        }

                                        return rawMessage;
                                })
                                .collect(Collectors.joining(", "));

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors(translatedMessage)
                                .build();

                return ResponseEntity.status(status).body(response);
        }

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<WebResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {

                HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());

                String rawReason = ex.getReason();
                String translatedMessage = rawReason;

                if (rawReason != null && rawReason.startsWith("{") && rawReason.endsWith("}")) {
                        String key = rawReason.substring(1, rawReason.length() - 1);
                        try {
                                translatedMessage = messageSource.getMessage(
                                                key, null, LocaleContextHolder.getLocale());
                        } catch (NoSuchMessageException e) {
                                translatedMessage = key;
                        }
                }

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors(translatedMessage)
                                .build();

                return ResponseEntity.status(status).body(response);
        }

        // 6. Menangani Exception Umum (Fallback)
        @ExceptionHandler(Exception.class)
        public ResponseEntity<WebResponse<Object>> generalException(Exception exception) {

                HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

                WebResponse<Object> response = WebResponse.builder()
                                .code(status.value())
                                .status("failed")
                                .errors("An unexpected error occurred: " + exception.getMessage())
                                .build();

                return ResponseEntity.status(status).body(response);
        }

}