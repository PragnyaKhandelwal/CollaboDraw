-- Minimal H2 schema for integration tests. Mirrors the subset of
-- collaborative_workspace_mysql.sql needed by the repositories exercised in
-- src/test/java (auth, board membership, WebSocket presence/cursor). ENUM columns from the
-- MySQL schema become VARCHAR here since application code only ever treats roles as strings;
-- triggers/procedures/views are DB-side automation the app never relies on (equivalent
-- behavior - e.g. owner membership on board creation - is done explicitly in Java), so they're
-- left out rather than ported.

DROP TABLE IF EXISTS cursors;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS elements;
DROP TABLE IF EXISTS board_membership;
DROP TABLE IF EXISTS boards;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE boards (
    board_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    board_name VARCHAR(100) NOT NULL,
    is_public BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE board_membership (
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'viewer',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_favorite BOOLEAN DEFAULT FALSE,
    is_archived BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (board_id, user_id),
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE elements (
    element_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    creator_id INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    z_order INT DEFAULT 0,
    data VARCHAR(65535),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (creator_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE sessions (
    session_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    connected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    disconnected_at DATETIME,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE cursors (
    cursor_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
