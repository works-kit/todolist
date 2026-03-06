-- ============================================================
-- Migration V1 — Initial Schema
-- Generated from: User, Category, Todo, Priority (enum)
-- Engine: MySQL 8.0
-- Lokasi: src/main/resources/db/migration/V1__init_schema.sql
-- ============================================================

-- TABLE: users
CREATE TABLE users (
    id               CHAR(36)     NOT NULL,
    name             VARCHAR(100) NOT NULL,
    email            VARCHAR(150) NOT NULL,
    password         VARCHAR(255) NOT NULL,
    token            VARCHAR(255)          DEFAULT NULL,
    token_expired_at BIGINT                DEFAULT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE INDEX idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TABLE: categories
CREATE TABLE categories (
    id          CHAR(36)     NOT NULL,
    user_id     CHAR(36)     NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT                  DEFAULT NULL,
    color       VARCHAR(20)           DEFAULT NULL,
    is_default  TINYINT(1)            DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE INDEX idx_user_category_name (user_id, name),
    INDEX idx_user_category_created (user_id, created_at),
    CONSTRAINT fk_category_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TABLE: todos
CREATE TABLE todos (
    id          CHAR(36)                      NOT NULL,
    user_id     CHAR(36)                      NOT NULL,
    title       VARCHAR(255)                  NOT NULL,
    description TEXT                          DEFAULT NULL,
    completed   TINYINT(1)                    DEFAULT 0,
    due_date    DATETIME                      DEFAULT NULL,
    priority    ENUM('low', 'medium', 'high') DEFAULT NULL,
    created_at  TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_user_status   (user_id, completed),
    INDEX idx_user_due      (user_id, due_date),
    INDEX idx_user_priority (user_id, priority),
    INDEX idx_user_created  (user_id, created_at),
    CONSTRAINT fk_todo_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- TABLE: todo_categories (join table ManyToMany Todo <-> Category)
CREATE TABLE todo_categories (
    todo_id     CHAR(36) NOT NULL,
    category_id CHAR(36) NOT NULL,

    PRIMARY KEY (todo_id, category_id),
    CONSTRAINT fk_tc_todo FOREIGN KEY (todo_id)
        REFERENCES todos (id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_tc_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
