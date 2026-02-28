package mohammadnuridin.todolist.modules.user;

import com.fasterxml.jackson.databind.ObjectMapper;

import mohammadnuridin.todolist.core.security.AuthenticatedUser;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link UserController}.
 *
 * <p>
 * Uses a full Spring application context (via {@link SpringBootTest}) with
 * {@link AutoConfigureMockMvc} to drive the real security filter chain,
 * validation, and service layer through the HTTP layer.
 *
 * <p>
 * Database: H2 in-memory via {@code application-test.properties}.
 *
 * <p>
 * <b>Naming convention:</b>
 * {@code methodUnderTest_stateUnderTest_expectedBehavior}
 */

/*
 * UserControllerTest (fungsional) ~20 tests
 * ├── POST /register happy path + validation
 * ├── GET /current happy path + auth
 * └── PATCH /current partial update + validation
 * 
 * UserControllerSecurityTest ~15 tests
 * ├── Injection (SQL, JSON)
 * ├── XSS
 * ├── Mass Assignment
 * ├── Enumeration
 * ├── IDOR
 * └── Oversized Payload
 * 
 * UserControllerEdgeCaseTest (baru) ~18 tests
 * ├── Boundary Value tepat di batas min/max constraint
 * ├── Data Integrity hash password, data tidak berubah
 * ├── Concurrency race condition email duplikat
 * ├── Contract struktur response konsisten
 * └── Unicode & Special Chars karakter non-ASCII
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties") // explicit fallback
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserController — Integration Tests")
class UserControllerTest {

    // ──────────────────────────────────────────────────────────────────────────
    // URL Constants
    // ──────────────────────────────────────────────────────────────────────────

    private static final String REGISTER_URL = "/users/register";
    private static final String CURRENT_USER_URL = "/users/current";

    // ──────────────────────────────────────────────────────────────────────────
    // Fixture Constants
    // ──────────────────────────────────────────────────────────────────────────

    private static final String VALID_NAME = "john_doe";
    private static final String VALID_EMAIL = "john@example.com";
    private static final String VALID_PASSWORD = "SecurePass123!";
    private static final String HASHED_PASSWORD = "$2a$10$dummyHashedPasswordForTestPurposesOnly";

    // ──────────────────────────────────────────────────────────────────────────
    // Injected Beans
    // ──────────────────────────────────────────────────────────────────────────

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /users/register
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /users/register")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RegisterEndpoint {

        @Test
        @Order(1)
        @DisplayName("✅ Valid request → 201 CREATED with user payload")
        void register_validRequest_returns201WithUserData() throws Exception {
            performPost(REGISTER_URL, validUserRequest())
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(HttpStatus.CREATED.value()))
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.name").value(VALID_NAME))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @Order(2)
        @DisplayName("✅ Valid request → User is persisted in database")
        void register_validRequest_persistsUserInDatabase() throws Exception {
            performPost(REGISTER_URL, validUserRequest()).andExpect(status().isCreated());

            assertTrue(
                    userRepository.existsByEmail(VALID_EMAIL),
                    "User should be persisted after successful registration");
        }

        @Test
        @Order(3)
        @DisplayName("❌ Duplicate name → 409 CONFLICT")
        void register_duplicatename_returns409Conflict() throws Exception {
            performPost(REGISTER_URL, validUserRequest()).andExpect(status().isCreated());

            performPost(REGISTER_URL, validUserRequest())
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(HttpStatus.CONFLICT.value()))
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }

        @Test
        @Order(4)
        @DisplayName("Duplicate email different name returns 409 CONFLICT")
        void register_duplicateEmail_returns409Conflict() throws Exception {
            performPost(REGISTER_URL, validUserRequest()).andExpect(status().isCreated());

            UserRequest sameEmail = new UserRequest("different_name", VALID_EMAIL, VALID_PASSWORD);
            performPost(REGISTER_URL, sameEmail)
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }

        @Test
        @Order(5)
        @DisplayName("❌ Missing request body → 400 BAD REQUEST")
        void register_missingBody_returns400() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(6)
        @DisplayName("❌ Wrong content-type → 415 UNSUPPORTED MEDIA TYPE")
        void register_wrongContentType_returns415() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("some plain text"))
                    .andDo(print())
                    .andExpect(status().isUnsupportedMediaType());
        }

        @ParameterizedTest(name = "[{index}] name=''{0}''")
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @Order(7)
        @DisplayName("❌ Blank/null name → 400 BAD REQUEST")
        void register_blankname_returns400(String name) throws Exception {
            UserRequest req = new UserRequest(name, name, name);
            performPost(REGISTER_URL, req)
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isNotEmpty());
        }

        @Test
        @Order(8)
        @DisplayName("❌ Invalid email format → 400 BAD REQUEST with field error")
        void register_invalidEmailFormat_returns400WithFieldError() throws Exception {
            UserRequest req = new UserRequest(VALID_NAME, "not-a-valid@@email", VALID_PASSWORD);

            performPost(REGISTER_URL, req)
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").isNotEmpty());
        }

        @Test
        @Order(9)
        @DisplayName("❌ Password too short → 400 BAD REQUEST")
        void register_passwordTooShort_returns400() throws Exception {
            UserRequest req = new UserRequest(VALID_NAME, VALID_EMAIL, "abc");

            performPost(REGISTER_URL, req)
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(10)
        @DisplayName("❌ All fields missing → 400 BAD REQUEST with multiple field errors")
        void register_allFieldsMissing_returns400WithMultipleErrors() throws Exception {
            performPost(REGISTER_URL, new UserRequest(null, null, null))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", aMapWithSize(greaterThan(1))));
        }

        @Test
        @Order(11)
        @DisplayName("Name exceeds max length returns 400 BAD REQUEST")
        void register_nameTooLong_returns400() throws Exception {
            String tooLong = "a".repeat(101);
            UserRequest req = new UserRequest(tooLong, VALID_EMAIL, VALID_PASSWORD);

            performPost(REGISTER_URL, req)
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /users/current
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /users/current")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class GetCurrentUserEndpoint {

        @Test
        @Order(12)
        @DisplayName("✅ Authenticated user → 200 OK with user data")
        void getCurrentUser_authenticated_returns200WithUserData() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);

            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved)) // ← inject auth via SecurityMockMvc
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(HttpStatus.OK.value()))
                    .andExpect(jsonPath("$.status").value("Current user retrieved"))
                    .andExpect(jsonPath("$.data.id").value(saved.getId()))
                    .andExpect(jsonPath("$.data.name").value(VALID_NAME))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
        }

        @Test
        @Order(13)
        @DisplayName("Response must NOT expose password or token field")
        void getCurrentUser_responseDoesNotExposePassword() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);

            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.token").doesNotExist());
        }

        @Test
        @Order(14)
        @DisplayName("❌ No authentication → 401 UNAUTHORIZED")
        void getCurrentUser_noAuthentication_returns401() throws Exception {
            mockMvc.perform(get(CURRENT_USER_URL)
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(15)
        @DisplayName("❌ Authenticated but user deleted from DB → 404 NOT FOUND")
        void getCurrentUser_deletedUser_returns404() throws Exception {
            User user = seedUser(VALID_NAME, VALID_EMAIL);
            userRepository.deleteById(user.getId());

            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(user))
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PATCH /users/current
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PATCH /users/current")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UpdateCurrentUserEndpoint {

        @Test
        @Order(20)
        @DisplayName("✅ Update name only → 200 OK, email unchanged")
        void updateCurrentUser_nameOnly_returns200() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest("Partial Update Name", null, null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Partial Update Name"))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL)); // unchanged
        }

        @Test
        @Order(21)
        @DisplayName("✅ Update email only → 200 OK, name unchanged")
        void updateCurrentUser_emailOnly_returns200() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, "newemail@example.com", null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value(VALID_NAME)) // unchanged
                    .andExpect(jsonPath("$.data.email").value("newemail@example.com"));
        }

        @Test
        @Order(22)
        @DisplayName("✅ Update password only → 200 OK")
        void updateCurrentUser_passwordOnly_returns200() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, null, "NewPassword123!");

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value(VALID_NAME))
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
        }

        @Test
        @Order(23)
        @DisplayName("✅ Update all fields → 200 OK")
        void updateCurrentUser_allFields_returns200() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest("New Name", "new@example.com", "NewPass123!");

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("New Name"))
                    .andExpect(jsonPath("$.data.email").value("new@example.com"));
        }

        @Test
        @Order(24)
        @DisplayName("❌ Empty body {} → 400 BAD REQUEST")
        void updateCurrentUser_emptyBody_returns400() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, null, null); // semua null

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @Order(25)
        @DisplayName("❌ Invalid email format → 400 BAD REQUEST")
        void updateCurrentUser_invalidEmail_returns400() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, "not-valid@@email", null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").isNotEmpty());
        }

        @Test
        @Order(26)
        @DisplayName("❌ Password too short → 400 BAD REQUEST")
        void updateCurrentUser_passwordTooShort_returns400() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, null, "abc");

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").isNotEmpty());
        }

        @Test
        @Order(27)
        @DisplayName("❌ Email already taken → 409 CONFLICT")
        void updateCurrentUser_emailAlreadyTaken_returns409() throws Exception {
            User userA = seedUser(VALID_NAME, VALID_EMAIL);
            User userB = seedUser("Other User", "other@example.com");
            UpdateUserRequest req = new UpdateUserRequest(null, userB.getEmail(), null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isConflict());
        }

        @Test
        @Order(28)
        @DisplayName("❌ No authentication → 401 UNAUTHORIZED")
        void updateCurrentUser_noAuthentication_returns401() throws Exception {
            UpdateUserRequest req = new UpdateUserRequest("Name", null, null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(29)
        @DisplayName("Update email same as current value returns 200 not conflict")
        void updateCurrentUser_sameEmailAsCurrent_returns200() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest(null, VALID_EMAIL, null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value(VALID_EMAIL));
        }

        @Test
        @Order(30)
        @DisplayName("Update response must NOT expose password or token field")
        void updateCurrentUser_responseDoesNotExposePassword() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);
            UpdateUserRequest req = new UpdateUserRequest("New Name", null, null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.data.token").doesNotExist());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Security & Cross-cutting
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security & Cross-cutting Concerns")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class SecurityAndCrossCuttingTests {

        @Test
        @Order(30)
        @DisplayName("Error responses must NOT leak stack traces")
        void errorResponse_doesNotLeakInternalDetails() throws Exception {
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": null}"))
                    .andDo(print())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.exception").doesNotExist())
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
        }

        @Test
        @Order(31)
        @DisplayName("POST /register is publicly accessible without auth token")
        void registerEndpoint_isPubliclyAccessible() throws Exception {
            performPost(REGISTER_URL, validUserRequest())
                    .andExpect(status().isCreated());
        }

        @Test
        @Order(32)
        @DisplayName("All user endpoints respond with application/json Content-Type")
        void allEndpoints_respondWithJsonContentType() throws Exception {
            // POST /register
            performPost(REGISTER_URL, validUserRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

            // GET /current (authenticated)
            User saved = seedUser("ct_user", "ct@example.com");
            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }

        @Test
        @Order(33)
        @DisplayName("GET on register endpoint returns 405 METHOD NOT ALLOWED")
        void register_getMethod_returns405() throws Exception {
            mockMvc.perform(get(REGISTER_URL))
                    .andDo(print())
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @Order(34)
        @DisplayName("DELETE on current endpoint returns 405 METHOD NOT ALLOWED")
        void currentUser_deleteMethod_returns405() throws Exception {
            User saved = seedUser(VALID_NAME, VALID_EMAIL);

            mockMvc.perform(delete(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved)))
                    .andDo(print())
                    .andExpect(status().isMethodNotAllowed());
        }

        // Gap: invalid JWT token belum ditest
        @Test
        @Order(35)
        @DisplayName("Invalid Bearer token returns 401 UNAUTHORIZED")
        void invalidJwtToken_returns401() throws Exception {
            mockMvc.perform(get(CURRENT_USER_URL)
                    .header("Authorization", "Bearer invalid.jwt.token")
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private UserRequest validUserRequest() {
        return new UserRequest(VALID_NAME, VALID_EMAIL, VALID_PASSWORD);
    }

    private User seedUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(HASHED_PASSWORD)
                .build());
    }

    private ResultActions performPost(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(toJson(body)));
    }

    private String toJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Injects a fully populated {@link AuthenticatedUser} principal into the
     * Spring Security context, simulating an authenticated HTTP request.
     * Adjust the {@code AuthenticatedUser} constructor to match your project.
     */
    private static RequestPostProcessor asAuthenticatedUser(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }

}