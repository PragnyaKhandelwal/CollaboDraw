# Collaborative Workspace Database System - MySQL Schema
# Generated for DBMS Project

# Create Database
CREATE DATABASE IF NOT EXISTS collaborative_workspace_db;
USE collaborative_workspace_db;

# Drop existing tables if they exist (for clean setup)
DROP TABLE IF EXISTS element_audit;
DROP TABLE IF EXISTS cursors;
DROP TABLE IF EXISTS sessions;
DROP TABLE IF EXISTS activity_log;
DROP TABLE IF EXISTS element_versions;
DROP TABLE IF EXISTS elements;
DROP TABLE IF EXISTS board_membership;
DROP TABLE IF EXISTS boards;
DROP TABLE IF EXISTS users;

# Create Users table
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email)
);

# Create Boards table
CREATE TABLE boards (
    board_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_id INT NOT NULL,
    board_name VARCHAR(100) NOT NULL,
    is_public BOOLEAN DEFAULT FALSE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_owner (owner_id),
    INDEX idx_public (is_public),
    INDEX idx_last_modified (last_modified)
);

# Create Board Membership table (Many-to-Many relationship)
CREATE TABLE board_membership (
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    role ENUM('owner', 'editor', 'viewer') DEFAULT 'viewer',
    joined_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (board_id, user_id),
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_role (role),
    INDEX idx_joined (joined_at)
);

# Create Elements table
CREATE TABLE elements (
    element_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    creator_id INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    z_order INT DEFAULT 0,
    data JSON NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (creator_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_board (board_id),
    INDEX idx_creator (creator_id),
    INDEX idx_type (type),
    INDEX idx_z_order (z_order),
    INDEX idx_updated (updated_at)
);

# Create Element Versions table (for undo/redo functionality)
CREATE TABLE element_versions (
    version_id INT AUTO_INCREMENT PRIMARY KEY,
    element_id INT NOT NULL,
    board_id INT NOT NULL,
    editor_id INT NOT NULL,
    version_num INT NOT NULL,
    data JSON NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (element_id) REFERENCES elements(element_id) ON DELETE CASCADE,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (editor_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_element (element_id),
    INDEX idx_version (version_num),
    INDEX idx_updated (updated_at)
);

# Create Activity Log table
CREATE TABLE activity_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    actor_id INT NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_id INT,
    details JSON,
    at_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (actor_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_board (board_id),
    INDEX idx_actor (actor_id),
    INDEX idx_action (action),
    INDEX idx_time (at_time)
);

# Create Sessions table (for real-time collaboration tracking)
CREATE TABLE sessions (
    session_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    connected_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    disconnected_at DATETIME,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_board (board_id),
    INDEX idx_user (user_id),
    INDEX idx_connected (connected_at),
    INDEX idx_active (disconnected_at)
);

# Create Cursors table (for real-time cursor tracking)
CREATE TABLE cursors (
    cursor_id INT AUTO_INCREMENT PRIMARY KEY,
    board_id INT NOT NULL,
    user_id INT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (board_id) REFERENCES boards(board_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_board (board_id),
    INDEX idx_user (user_id),
    INDEX idx_position (x, y),
    INDEX idx_updated (updated_at)
);

# Create Element Audit table for change tracking
CREATE TABLE element_audit (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    element_id INT NOT NULL,
    board_id INT NOT NULL,
    action VARCHAR(30) NOT NULL,
    user_id INT NOT NULL,
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    before_data JSON,
    after_data JSON,
    INDEX idx_element (element_id),
    INDEX idx_board (board_id),
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_time (action_time)
);

# TRIGGERS for automatic audit logging and timestamp updates

DELIMITER $$

# Trigger: Log element insertions
CREATE TRIGGER trg_element_insert
AFTER INSERT ON elements
FOR EACH ROW
BEGIN
    INSERT INTO element_audit (element_id, board_id, action, user_id, after_data)
    VALUES (NEW.element_id, NEW.board_id, 'INSERT', NEW.creator_id, NEW.data);
    
    INSERT INTO activity_log (board_id, actor_id, action, target_id, details)
    VALUES (NEW.board_id, NEW.creator_id, 'create_element', NEW.element_id, 
            JSON_OBJECT('element_type', NEW.type, 'z_order', NEW.z_order));
END $$

# Trigger: Log element updates
CREATE TRIGGER trg_element_update
AFTER UPDATE ON elements
FOR EACH ROW
BEGIN
    INSERT INTO element_audit (element_id, board_id, action, user_id, action_time, before_data, after_data)
    VALUES (NEW.element_id, NEW.board_id, 'UPDATE', NEW.creator_id, NOW(), OLD.data, NEW.data);
    
    INSERT INTO activity_log (board_id, actor_id, action, target_id, details)
    VALUES (NEW.board_id, NEW.creator_id, 'update_element', NEW.element_id,
            JSON_OBJECT('old_type', OLD.type, 'new_type', NEW.type));
END $$

# Trigger: Log element deletions
CREATE TRIGGER trg_element_delete
AFTER DELETE ON elements
FOR EACH ROW
BEGIN
    INSERT INTO element_audit (element_id, board_id, action, user_id, action_time, before_data)
    VALUES (OLD.element_id, OLD.board_id, 'DELETE', OLD.creator_id, NOW(), OLD.data);
    
    INSERT INTO activity_log (board_id, actor_id, action, target_id, details)
    VALUES (OLD.board_id, OLD.creator_id, 'delete_element', OLD.element_id,
            JSON_OBJECT('element_type', OLD.type));
END $$

# Trigger: Auto-update board's last_modified when elements change
CREATE TRIGGER trg_update_board_timestamp
AFTER UPDATE ON elements
FOR EACH ROW
BEGIN
    UPDATE boards SET last_modified = NOW() WHERE board_id = NEW.board_id;
END $$

# Trigger: Auto-create board membership for board owner
CREATE TRIGGER trg_board_owner_membership
AFTER INSERT ON boards
FOR EACH ROW
BEGIN
    INSERT INTO board_membership (board_id, user_id, role, joined_at)
    VALUES (NEW.board_id, NEW.owner_id, 'owner', NOW())
    ON DUPLICATE KEY UPDATE role = 'owner';
END $$

DELIMITER ;

# STORED PROCEDURES for common operations

DELIMITER $$

# Procedure: Add user to board with specific role
CREATE PROCEDURE AddUserToBoard(
    IN p_board_id INT,
    IN p_user_id INT,
    IN p_role ENUM('owner', 'editor', 'viewer')
)
BEGIN
    DECLARE board_exists INT DEFAULT 0;
    DECLARE user_exists INT DEFAULT 0;
    
    # Check if board and user exist
    SELECT COUNT(*) INTO board_exists FROM boards WHERE board_id = p_board_id;
    SELECT COUNT(*) INTO user_exists FROM users WHERE user_id = p_user_id;
    
    IF board_exists = 1 AND user_exists = 1 THEN
        INSERT INTO board_membership (board_id, user_id, role, joined_at)
        VALUES (p_board_id, p_user_id, p_role, NOW())
        ON DUPLICATE KEY UPDATE role = p_role, joined_at = NOW();
        
        # Log the activity
        INSERT INTO activity_log (board_id, actor_id, action, target_id, details)
        VALUES (p_board_id, p_user_id, 'join_board', p_board_id,
                JSON_OBJECT('role', p_role));
    END IF;
END $$

# Function: Count elements on a board
CREATE FUNCTION CountBoardElements(p_board_id INT)
RETURNS INT
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE element_count INT DEFAULT 0;
    SELECT COUNT(*) INTO element_count 
    FROM elements 
    WHERE board_id = p_board_id;
    RETURN element_count;
END $$

# Function: Get user's role on a board
CREATE FUNCTION GetUserBoardRole(p_user_id INT, p_board_id INT)
RETURNS VARCHAR(10)
READS SQL DATA
DETERMINISTIC
BEGIN
    DECLARE user_role VARCHAR(10) DEFAULT 'none';
    SELECT role INTO user_role
    FROM board_membership
    WHERE user_id = p_user_id AND board_id = p_board_id;
    RETURN IFNULL(user_role, 'none');
END $$

DELIMITER ;

# VIEWS for common queries

# View: Active user sessions
CREATE VIEW active_sessions AS
SELECT 
    s.session_id,
    u.username,
    b.board_name,
    s.connected_at,
    TIMEDIFF(NOW(), s.connected_at) as session_duration
FROM sessions s
JOIN users u ON s.user_id = u.user_id
JOIN boards b ON s.board_id = b.board_id
WHERE s.disconnected_at IS NULL;

# View: Board statistics
CREATE VIEW board_stats AS
SELECT 
    b.board_id,
    b.board_name,
    u.username as owner_name,
    COUNT(DISTINCT bm.user_id) as member_count,
    COUNT(DISTINCT e.element_id) as element_count,
    b.created_at,
    b.last_modified
FROM boards b
JOIN users u ON b.owner_id = u.user_id
LEFT JOIN board_membership bm ON b.board_id = bm.board_id
LEFT JOIN elements e ON b.board_id = e.board_id
GROUP BY b.board_id, b.board_name, u.username, b.created_at, b.last_modified;

# View: Recent activity summary
CREATE VIEW recent_activity AS
SELECT 
    al.log_id,
    b.board_name,
    u.username as actor_name,
    al.action,
    al.at_time,
    al.details
FROM activity_log al
JOIN boards b ON al.board_id = b.board_id
JOIN users u ON al.actor_id = u.user_id
ORDER BY al.at_time DESC
LIMIT 100;

# Sample data insertion (optional - uncomment to add test data)
/*
# Insert sample users
INSERT INTO users (username, email, password_hash) VALUES
('john_doe', 'john@example.com', 'hashed_password_1'),
('jane_smith', 'jane@example.com', 'hashed_password_2'),
('bob_wilson', 'bob@example.com', 'hashed_password_3');

# Insert sample board
INSERT INTO boards (owner_id, board_name, is_public) VALUES
(1, 'Team Brainstorming Board', TRUE);

# Add sample elements
INSERT INTO elements (board_id, creator_id, type, data) VALUES
(1, 1, 'rectangle', '{"x": 100, "y": 200, "width": 150, "height": 80, "color": "#ff0000"}'),
(1, 2, 'text', '{"x": 300, "y": 150, "content": "Hello World", "fontSize": 16, "color": "#000000"}');
*/

# Display completion message
SELECT 'Collaborative Workspace Database Schema Created Successfully!' as Status;

# Show created tables
SHOW TABLES;