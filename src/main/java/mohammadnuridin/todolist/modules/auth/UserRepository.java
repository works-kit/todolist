package mohammadnuridin.todolist.modules.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    // 1. Mencari user berdasarkan email (penting untuk login/registrasi)
    Optional<User> findByEmail(String email);

    // 2. Cek apakah email sudah terdaftar
    boolean existsByEmail(String email);

    // 3. Implementasi Fulltext Search (MySQL Native Query)
    // Berdasarkan FULLTEXT INDEX idx_user_search (name, email)
    @Query(value = "SELECT * FROM users u WHERE MATCH(name, email) AGAINST(:keyword IN NATURAL LANGUAGE MODE)", nativeQuery = true)
    List<User> searchByKeyword(@Param("keyword") String keyword);

    // 4. Mencari user berdasarkan nama (menggunakan Spring Data JPA naming
    // convention)
    List<User> findByNameContainingIgnoreCase(String name);

    Optional<User> findFirstByToken(String token);
}