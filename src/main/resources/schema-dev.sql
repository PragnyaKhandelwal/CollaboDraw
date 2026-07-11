-- H2 schema for local interactive dev (SPRING_PROFILES_ACTIVE=dev). Mirrors the full table
-- set from collaborative_workspace_mysql.sql so every page/feature works against the
-- in-memory DB, not just the subset exercised by tests (see src/test/resources/schema.sql).
-- ENUM columns become VARCHAR and JSON columns become VARCHAR(65535), same simplification
-- src/test/resources/schema.sql already makes, since application code only ever treats them
-- as strings. Triggers/procedures/views are DB-side automation the app never relies on -
-- equivalent behavior (e.g. owner membership on board creation) is done explicitly in Java -
-- so they're left out rather than ported to H2's different trigger/procedure syntax.

DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS templates;
DROP TABLE IF EXISTS element_audit;
DROP TABLE IF EXISTS cursors;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS activity_log;
DROP TABLE IF EXISTS element_versions;
DROP TABLE IF EXISTS elements;
DROP TABLE IF EXISTS board_invites;
DROP TABLE IF EXISTS board_membership;
DROP TABLE IF EXISTS team_members;
DROP TABLE IF EXISTS teams;
DROP TABLE IF EXISTS boards;
DROP TABLE IF EXISTS user_settings;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_settings (
    user_id INT PRIMARY KEY,
    display_name VARCHAR(100),
    description VARCHAR(255),
    bio VARCHAR(2000),
    theme VARCHAR(10) DEFAULT 'system',
    language VARCHAR(10) DEFAULT 'en',
    timezone VARCHAR(64) DEFAULT 'UTC',
    email_notifications BOOLEAN DEFAULT TRUE,
    push_notifications BOOLEAN DEFAULT TRUE,
    board_updates BOOLEAN DEFAULT TRUE,
    mentions BOOLEAN DEFAULT TRUE,
    marketing_emails BOOLEAN DEFAULT FALSE,
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    avatar_data VARCHAR(65535),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
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

CREATE TABLE teams (
    team_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE team_members (
    team_id INT NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'member',
    status VARCHAR(20) DEFAULT 'active',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
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

CREATE TABLE board_invites (
    invite_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    inviter_id INT NOT NULL,
    invitee_id INT NOT NULL,
    role VARCHAR(20) DEFAULT 'viewer',
    status VARCHAR(20) DEFAULT 'pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    responded_at DATETIME NULL,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (inviter_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (invitee_id) REFERENCES users(user_id) ON DELETE CASCADE
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

CREATE TABLE element_versions (
    version_id INT AUTO_INCREMENT PRIMARY KEY,
    element_id INT NOT NULL,
    board_id INT NOT NULL,
    editor_id INT NOT NULL,
    version_num INT NOT NULL,
    data VARCHAR(65535),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (element_id) REFERENCES elements(element_id) ON DELETE CASCADE,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (editor_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE activity_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    actor_id INT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id INT,
    details VARCHAR(65535),
    at_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE CASCADE
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

CREATE TABLE element_audit (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    element_id INT NOT NULL,
    board_id INT NOT NULL,
    action VARCHAR(30) NOT NULL,
    user_id INT NOT NULL,
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    before_data VARCHAR(65535),
    after_data VARCHAR(65535)
);

CREATE TABLE templates (
    template_id INT AUTO_INCREMENT PRIMARY KEY,
    template_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(2000),
    category VARCHAR(64) NOT NULL,
    icon VARCHAR(32),
    plan VARCHAR(16) DEFAULT 'FREE',
    is_new BOOLEAN DEFAULT FALSE,
    is_featured BOOLEAN DEFAULT FALSE,
    usage_count INT DEFAULT 0
);

CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(2000),
    link_url VARCHAR(255),
    board_id INT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE SET NULL
);
