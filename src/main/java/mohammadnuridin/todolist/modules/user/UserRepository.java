package mohammadnuridin.todolist.modules.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByToken(String token);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);
}