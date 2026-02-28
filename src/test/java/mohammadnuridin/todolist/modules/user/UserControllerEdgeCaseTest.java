package mohammadnuridin.todolist.modules.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import mohammadnuridin.todolist.core.security.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Edge-case tests untuk {@link UserController}.
 *
 * <p>
 * Mencakup skenario di luar happy path dan security attack yang belum
 * ter-cover di {@code UserControllerTest} dan
 * {@code UserControllerSecurityTest}:
 *
 * <ul>
 * <li>Boundary Value — nilai tepat di batas min/max constraint
 * <li>Data Integrity — password di-hash, data tidak berubah tanpa alasan
 * <li>Concurrency — race condition registrasi email duplikat secara bersamaan
 * <li>Response Contract — struktur JSON response konsisten di semua skenario
 * <li>Unicode & Special Chars — karakter non-ASCII, emoji, RTL text
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserController — Edge Case Tests")
class UserControllerEdgeCaseTest {

    private static final String REGISTER_URL = "/users/register";
    private static final String CURRENT_USER_URL = "/users/current";
    private static final String HASHED_PASSWORD = "$2a$10$dummyHashedPasswordForTestPurposesOnly";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. BOUNDARY VALUE — tepat di batas min/max constraint
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Boundary Value Analysis")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class BoundaryValueTests {

        // ── name: asumsi @Size(min=3, max=100) ──

        @Test
        @Order(1)
        @DisplayName("Name tepat 3 karakter (min boundary) → 201 CREATED")
        void register_nameAtMinBoundary_returns201() throws Exception {
            UserRequest req = new UserRequest("abc", "min@example.com", "password123");
            performPost(req).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("abc"));
        }

        @Test
        @Order(2)
        @DisplayName("Name 2 karakter (di bawah min boundary) → 400 BAD REQUEST")
        void register_nameBelowMinBoundary_returns400() throws Exception {
            UserRequest req = new UserRequest("ab", "below@example.com", "password123");
            performPost(req).andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());
        }

        @Test
        @Order(3)
        @DisplayName("Name tepat 100 karakter (max boundary) → 201 CREATED")
        void register_nameAtMaxBoundary_returns201() throws Exception {
            String maxName = "a".repeat(100);
            UserRequest req = new UserRequest(maxName, "max@example.com", "password123");
            performPost(req).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value(maxName));
        }

        @Test
        @Order(4)
        @DisplayName("Name 101 karakter (di atas max boundary) → 400 BAD REQUEST")
        void register_nameAboveMaxBoundary_returns400() throws Exception {
            String overName = "a".repeat(101);
            UserRequest req = new UserRequest(overName, "over@example.com", "password123");
            performPost(req).andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());
        }

        // ── password: asumsi @Size(min=8, max=72) ──

        @Test
        @Order(5)
        @DisplayName("Password tepat 8 karakter (min boundary) → 201 CREATED")
        void register_passwordAtMinBoundary_returns201() throws Exception {
            UserRequest req = new UserRequest("valid_user", "pass8@example.com", "12345678");
            performPost(req).andExpect(status().isCreated());
        }

        @Test
        @Order(6)
        @DisplayName("Password 7 karakter (di bawah min boundary) → 400 BAD REQUEST")
        void register_passwordBelowMinBoundary_returns400() throws Exception {
            UserRequest req = new UserRequest("valid_user", "pass7@example.com", "1234567");
            performPost(req).andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").isNotEmpty());
        }

        @Test
        @Order(7)
        @DisplayName("Password tepat 72 karakter (max boundary) → 201 CREATED")
        void register_passwordAtMaxBoundary_returns201() throws Exception {
            String maxPass = "p".repeat(72);
            UserRequest req = new UserRequest("max_pass_user", "pass72@example.com", maxPass);
            performPost(req).andExpect(status().isCreated());
        }

        @Test
        @Order(8)
        @DisplayName("Password 73 karakter (di atas max boundary) → 400 BAD REQUEST")
        void register_passwordAboveMaxBoundary_returns400() throws Exception {
            String overPass = "p".repeat(73);
            UserRequest req = new UserRequest("over_pass_user", "pass73@example.com", overPass);
            performPost(req).andDo(print())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").isNotEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. DATA INTEGRITY
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Data Integrity")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class DataIntegrityTests {

        @Test
        @Order(10)
        @DisplayName("Password disimpan sebagai bcrypt hash, bukan plain text")
        void register_passwordIsStoredAsHash() throws Exception {
            String plainPassword = "MySecretPass123";
            UserRequest req = new UserRequest("hash_test_user", "hash@example.com", plainPassword);

            performPost(req).andExpect(status().isCreated());

            User saved = userRepository.findByEmail("hash@example.com").orElseThrow();
            assertNotEquals(plainPassword, saved.getPassword(),
                    "Password harus di-hash, tidak boleh disimpan plain text");
            assertTrue(passwordEncoder.matches(plainPassword, saved.getPassword()),
                    "BCrypt harus bisa memverifikasi password asli dari hash yang tersimpan");
        }

        @Test
        @Order(11)
        @DisplayName("Response register tidak mengekspos field password")
        void register_responseDoesNotExposePassword() throws Exception {
            UserRequest req = new UserRequest("safe_user", "safe@example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @Order(12)
        @DisplayName("Email disimpan lowercase — tidak ada duplikat case-insensitive")
        void register_emailStoredNormalized() throws Exception {
            UserRequest req = new UserRequest("email_case_user", "Case@Example.COM", "password123");

            performPost(req).andExpect(status().isCreated());

            // Coba register lagi dengan huruf kecil semua — harus conflict
            UserRequest dupReq = new UserRequest("email_case_user2", "case@example.com", "password123");
            performPost(dupReq).andDo(print())
                    .andExpect(status().isConflict());
        }

        @Test
        @Order(13)
        @DisplayName("Update tanpa perubahan nama → data tetap sama")
        void updateCurrentUser_noChange_dataRemainsUnchanged() throws Exception {
            User saved = seedUser("stable_user", "stable@example.com");
            UpdateUserRequest req = new UpdateUserRequest("stable_user", null, null);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("stable_user"))
                    .andExpect(jsonPath("$.data.email").value("stable@example.com"));
        }

        @Test
        @Order(14)
        @DisplayName("Update password ter-re-hash dengan benar")
        void updateCurrentUser_newPasswordIsRehashed() throws Exception {
            User saved = seedUser("repass_user", "repass@example.com");
            String newPassword = "NewPassword456!";
            UpdateUserRequest req = new UpdateUserRequest(null, null, newPassword);

            mockMvc.perform(patch(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andExpect(status().isOk());

            User updated = userRepository.findById(saved.getId()).orElseThrow();
            assertNotEquals(newPassword, updated.getPassword(),
                    "Password baru harus di-hash");
            assertTrue(passwordEncoder.matches(newPassword, updated.getPassword()),
                    "BCrypt harus verify password baru dari hash yang diupdate");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. CONCURRENCY — race condition registrasi simultan
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Concurrency — Race Condition")
    class ConcurrencyTests {

        @Test
        @Order(20)
        @DisplayName("Registrasi 10 request simultan dengan email sama → hanya 1 sukses")
        @Timeout(value = 10)
        void register_concurrentSameEmail_onlyOneSucceeds() throws Exception {
            int threadCount = 10;
            String sharedEmail = "race@example.com";

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger created = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0); // 409 CONFLICT
            List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();

                        // Setiap thread pakai name unik agar name constraint tidak interfere
                        UserRequest req = new UserRequest(
                                "race_user_" + idx + "_" + System.nanoTime(),
                                sharedEmail,
                                "password123");

                        int status = mockMvc.perform(post(REGISTER_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(toJson(req)))
                                .andReturn().getResponse().getStatus();

                        if (status == 201)
                            created.incrementAndGet();
                        else if (status == 409)
                            rejected.incrementAndGet();
                        // status 500 tidak di-count — akan ketahuan dari assertion created==1

                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(8, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(errors.isEmpty(),
                    "Tidak boleh ada exception tak tertangani: " + errors);

            assertEquals(1, created.get(),
                    "Tepat 1 registrasi harus berhasil. created=" + created.get()
                            + ", rejected=" + rejected.get());

            // Yang paling penting: DB hanya boleh punya 1 user
            long totalInDb = userRepository.count();
            assertEquals(1, totalInDb,
                    "Hanya 1 user yang boleh tersimpan di database, actual=" + totalInDb);

            // Semua yang tidak sukses harus 409 (bukan diam-diam 500)
            assertEquals(threadCount - 1, rejected.get(),
                    "Semua request yang gagal harus 409 CONFLICT. "
                            + "created=" + created.get() + ", rejected=" + rejected.get()
                            + " (total=" + (created.get() + rejected.get()) + "/" + threadCount + ")");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. RESPONSE CONTRACT — struktur JSON konsisten
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Response Contract")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class ResponseContractTests {

        @Test
        @Order(30)
        @DisplayName("Success response memiliki field: code, status, data")
        void successResponse_hasRequiredTopLevelFields() throws Exception {
            UserRequest req = new UserRequest("contract_user", "contract@example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @Order(31)
        @DisplayName("Error response (400) memiliki field: code, status, errors")
        void errorResponse_hasRequiredTopLevelFields() throws Exception {
            UserRequest req = new UserRequest("", "bad", "");

            performPost(req)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.errors").exists());
        }

        @Test
        @Order(32)
        @DisplayName("Success response code sesuai HTTP status (201 untuk register)")
        void successResponse_codeMatchesHttpStatus() throws Exception {
            UserRequest req = new UserRequest("code_user", "code@example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201));
        }

        @Test
        @Order(33)
        @DisplayName("Data user mengandung field wajib: id, name, email")
        void userDataResponse_hasRequiredUserFields() throws Exception {
            UserRequest req = new UserRequest("field_user", "field@example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.name").isNotEmpty())
                    .andExpect(jsonPath("$.data.email").isNotEmpty());
        }

        @Test
        @Order(34)
        @DisplayName("GET /current response structure sama dengan POST /register")
        void getCurrentUser_responseStructureConsistentWithRegister() throws Exception {
            User saved = seedUser("struct_user", "struct@example.com");

            mockMvc.perform(get(CURRENT_USER_URL)
                    .with(asAuthenticatedUser(saved))
                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.data.id").isNotEmpty())
                    .andExpect(jsonPath("$.data.name").isNotEmpty())
                    .andExpect(jsonPath("$.data.email").isNotEmpty())
                    .andExpect(jsonPath("$.data.password").doesNotExist());
        }

        @Test
        @Order(35)
        @DisplayName("409 Conflict response mengandung pesan error yang bermakna")
        void conflictResponse_containsMeaningfulMessage() throws Exception {
            performPost(new UserRequest("dup_user", "dup@example.com", "password123"))
                    .andExpect(status().isCreated());

            performPost(new UserRequest("dup_user", "dup@example.com", "password123"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors").isNotEmpty())
                    // Tidak boleh stack trace
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. UNICODE & SPECIAL CHARACTERS
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unicode & Special Characters")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UnicodeAndSpecialCharTests {

        @ParameterizedTest(name = "[{index}] name = \"{0}\"")
        @Order(40)
        @MethodSource("unicodeNameProvider")
        @DisplayName("Nama Unicode internasional → 201 CREATED dan disimpan utuh")
        void register_unicodeName_isStoredAndReturnedCorrectly(String unicodeName, String email)
                throws Exception {

            UserRequest req = new UserRequest(unicodeName, email, "password123");

            performPost(req)
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value(unicodeName));
        }

        static Stream<Arguments> unicodeNameProvider() {
            return Stream.of(
                    Arguments.of("Muhammad Nūr", "unicode1@example.com"),
                    Arguments.of("Иван Петров", "unicode2@example.com"),
                    Arguments.of("田中 太郎", "unicode3@example.com"),
                    Arguments.of("محمد نوريدين", "unicode4@example.com"),
                    Arguments.of("José Müller-Weiß", "unicode5@example.com"),
                    Arguments.of("Ångström Ñoño", "unicode6@example.com"));
        }

        @Test
        @Order(41)
        @DisplayName("Nama dengan emoji → 201 CREATED atau 400 (tidak boleh 500)")
        void register_nameWithEmoji_handledGracefully() throws Exception {
            UserRequest req = new UserRequest("John 🚀 Doe", "emoji@example.com", "password123");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(req)))
                    .andDo(print())
                    // 201 jika DB mendukung utf8mb4, 400 jika ada validasi karakter — keduanya OK
                    // Yang TIDAK boleh: 500 Internal Server Error
                    .andExpect(status().is(org.hamcrest.Matchers.not(500)));
        }

        @Test
        @Order(42)
        @DisplayName("Nama dengan karakter RTL Arabic tidak menyebabkan 500")
        void register_rtlArabicName_doesNotCauseServerError() throws Exception {
            UserRequest req = new UserRequest("عمر خيام", "omar@example.com", "password123");

            performPost(req)
                    .andDo(print())
                    .andExpect(status().is(org.hamcrest.Matchers.not(500)));
        }

        @Test
        @Order(43)
        @DisplayName("Email dengan subdomain panjang → divalidasi benar")
        void register_emailWithLongSubdomain_isValidated() throws Exception {
            UserRequest req = new UserRequest("subdomain_user",
                    "user@mail.very.deep.subdomain.example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated());
        }

        @Test
        @Order(44)
        @DisplayName("Email dengan karakter plus (alias) → diterima sebagai valid")
        void register_emailWithPlusAlias_isAccepted() throws Exception {
            UserRequest req = new UserRequest("plus_user",
                    "user+tag@example.com", "password123");

            performPost(req)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.email").value("user+tag@example.com"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions performPost(UserRequest req)
            throws Exception {
        return mockMvc.perform(post(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(toJson(req)));
    }

    private String toJson(Object object) throws Exception {
        return objectMapper.writeValueAsString(object);
    }

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