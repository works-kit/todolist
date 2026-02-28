package mohammadnuridin.todolist.modules.user;

import com.fasterxml.jackson.databind.ObjectMapper;

import mohammadnuridin.todolist.core.security.AuthenticatedUser;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security-focused tests untuk UserController.
 * Menguji attack vectors yang tidak tercover di unit test fungsional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserController — Security Tests")
class UserControllerSecurityTest {

    private static final String REGISTER_URL = "/users/register";
    private static final String CURRENT_USER_URL = "/users/current";
    private static final String HASHED_PASSWORD = "$2a$10$dummyHashedPasswordForTestPurposesOnly";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. INJECTION ATTACKS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Injection Attacks")
    class InjectionAttacks {

        @Test
        @Order(1)
        @DisplayName("SQL Injection di email field → 400 atau diproses aman")
        void register_sqlInjectionInEmail_isSafelyHandled() throws Exception {
            UserRequest req = new UserRequest(
                    "hacker",
                    "' OR '1'='1'; DROP TABLE users; --",
                    "password123");

            // Harus ditolak karena bukan email valid — bukan 500 (tidak boleh crash)
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest()) // @Email validation menolak
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @Order(2)
        @DisplayName("SQL Injection di name field → diproses aman tanpa 500")
        void register_sqlInjectionInName_isSafelyHandled() throws Exception {
            UserRequest req = new UserRequest(
                    "Robert'); DROP TABLE users;--", // classic Bobby Tables
                    "bobby@example.com",
                    "password123");

            // Nama ini valid secara format — harus masuk DB dengan aman (JPA parameterized
            // query)
            // Yang penting: tidak boleh 500, tidak boleh drop table
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isCreated()) // masuk tapi aman karena JPA
                    .andExpect(jsonPath("$.data.name")
                            .value("Robert'); DROP TABLE users;--")); // disimpan literal
        }

        @Test
        @Order(3)
        @DisplayName("NoSQL/JSON Injection di request body → tidak crash")
        void register_jsonInjectionPayload_isSafelyHandled() throws Exception {
            // Attacker mencoba inject JSON structure
            String maliciousBody = """
                    {
                      "name": "test",
                      "email": "test@test.com",
                      "password": "pass123",
                      "$where": "sleep(5000)",
                      "__proto__": {"admin": true}
                    }
                    """;

            // Field extra harus diabaikan Jackson (tidak boleh 500)
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(maliciousBody))
                    .andDo(print())
                    .andExpect(status().isCreated()) // field extra diabaikan
                    .andExpect(jsonPath("$.data.name").value("test"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. XSS (Cross-Site Scripting)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XSS Attacks")
    class XssAttacks {

        @Test
        @Order(10)
        @DisplayName("XSS script tag di name field → disimpan sebagai literal string")
        void register_xssInName_storedAsLiteralNotExecuted() throws Exception {
            UserRequest req = new UserRequest(
                    "<script>alert('xss')</script>",
                    "xss@example.com",
                    "password123");

            // Spring REST API mengembalikan JSON — XSS di JSON tidak executable di browser
            // Yang penting: tidak boleh mengubah response structure atau crash
            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    // Bisa 201 (disimpan literal) atau 400 (jika ada sanitization)
                    // Yang TIDAK boleh terjadi: 500
                    .andExpect(status().is(org.hamcrest.Matchers.not(500)));
        }

        @Test
        @Order(11)
        @DisplayName("XSS di update name → response tidak mengeksekusi script")
        void updateCurrentUser_xssInName_responseIsJsonSafe() throws Exception {
            User saved = seedUser("normal_user", "normal@example.com");
            UpdateUserRequest req = new UpdateUserRequest(
                    "<img src=x onerror=alert(1)>",
                    null,
                    null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().is(org.hamcrest.Matchers.not(500)))
                    // Response Content-Type harus application/json, bukan text/html
                    // Ini mencegah browser menginterpretasikan response sebagai HTML
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. MASS ASSIGNMENT / OVER-POSTING
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Mass Assignment / Over-posting")
    class MassAssignment {

        @Test
        @Order(20)
        @DisplayName("Extra fields di request body diabaikan — tidak bisa set role/admin")
        void register_extraFieldsInBody_areIgnored() throws Exception {
            // Attacker mencoba set field yang tidak ada di UserRequest
            String bodyWithExtraFields = """
                    {
                      "name": "hacker",
                      "email": "hacker@example.com",
                      "password": "password123",
                      "role": "ADMIN",
                      "isAdmin": true,
                      "id": "malicious-id-injection"
                    }
                    """;

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bodyWithExtraFields))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    // ID harus digenerate server, bukan dari request
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.id").value(
                            org.hamcrest.Matchers.not("malicious-id-injection")));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. ENUMERATION ATTACKS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Enumeration Attacks")
    class EnumerationAttacks {

        @Test
        @Order(30)
        @DisplayName("Register dengan email existing → response tidak membocorkan info user lain")
        void register_existingEmail_errorMessageNotVerbose() throws Exception {
            // Seed existing user
            userRepository.save(User.builder()
                    .name("existing")
                    .email("existing@example.com")
                    .password(HASHED_PASSWORD)
                    .build());

            UserRequest req = new UserRequest("attacker", "existing@example.com", "password123");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    // Error message tidak boleh membocorkan detail seperti
                    // "User with id=xxx already exists" atau stack trace
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. IDOR (Insecure Direct Object Reference)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("IDOR — Insecure Direct Object Reference")
    class IdorAttacks {

        @Test
        @Order(40)
        @DisplayName("User A tidak bisa membaca data User B via GET /current")
        void getCurrentUser_canOnlyAccessOwnData() throws Exception {
            User userA = seedUser("user_a", "a@example.com");
            User userB = seedUser("user_b", "b@example.com");

            // UserA terautentikasi — hanya boleh lihat data dirinya sendiri
            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(userA))
                    .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(userA.getId()))
                    .andExpect(jsonPath("$.data.email").value("a@example.com"))
                    // Harus bukan data userB
                    .andExpect(jsonPath("$.data.id")
                            .value(org.hamcrest.Matchers.not(userB.getId())));
        }

        @Test
        @Order(41)
        @DisplayName("User A tidak bisa update data User B via PATCH /current")
        void updateCurrentUser_canOnlyUpdateOwnData() throws Exception {
            User userA = seedUser("user_a", "a@example.com");
            User userB = seedUser("user_b", "b@example.com");

            UpdateUserRequest req = new UpdateUserRequest("Hacked Name", null, null);

            // UserA terautentikasi — update harus hanya mengubah data userA
            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(userA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(userA.getId()));

            // Verifikasi data userB tidak berubah
            User userBAfter = userRepository.findById(userB.getId()).orElseThrow();
            Assertions.assertEquals("user_b", userBAfter.getName(),
                    "UserB name should NOT be changed by UserA's update request");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. OVERSIZED PAYLOAD (DoS prevention)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Oversized Payload")
    class OversizedPayload {

        @Test
        @Order(50)
        @DisplayName("Sangat panjang name field → 400 BAD REQUEST, tidak OOM")
        void register_extremelyLongName_returns400() throws Exception {
            // 10.000 karakter — jauh melewati max 100
            String hugeName = "a".repeat(10_000);
            UserRequest req = new UserRequest(hugeName, "test@example.com", "password123");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());
        }

        @Test
        @Order(51)
        @DisplayName("Sangat panjang password field → 400 BAD REQUEST")
        void register_extremelyLongPassword_returns400() throws Exception {
            String hugePassword = "p".repeat(10_000);
            UserRequest req = new UserRequest("valid_name", "test@example.com", hugePassword);

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                    .andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").isNotEmpty());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private User seedUser(String name, String email) {
        return userRepository.save(User.builder()
                .name(name)
                .email(email)
                .password(HASHED_PASSWORD)
                .build());
    }

    private static RequestPostProcessor asAuthenticatedUser(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, Collections.emptyList());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}