package mohammadnuridin.todolist.modules.category;

import jakarta.persistence.*;
import lombok.*;
import mohammadnuridin.todolist.modules.todo.Todo;
import mohammadnuridin.todolist.modules.user.User;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories", uniqueConstraints = {
                @UniqueConstraint(name = "idx_user_category_name", columnNames = { "user_id", "name" })
}, indexes = {
                @Index(name = "idx_user_category_created", columnList = "user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

        @Id
        @Column(length = 36, nullable = false, updatable = false)
        private String id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_category_user"))
        private User user;

        @Column(nullable = false, length = 100)
        private String name;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(length = 20)
        private String color;

        @Builder.Default
        @Column(name = "is_default")
        private Boolean isDefault = false;

        @Builder.Default
        @ManyToMany(mappedBy = "categories")
        private Set<Todo> todos = new HashSet<>();

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
        private LocalDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
        private LocalDateTime updatedAt;
}