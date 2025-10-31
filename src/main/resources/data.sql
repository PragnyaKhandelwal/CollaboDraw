-- NOTE: This file assumes the authoritative MySQL schema is applied from
--       'collaborative_workspace_mysql.sql'. It also inserts rows into the
--       'templates' table that now exists in that schema.
-- You may still use parts of this file selectively for users/boards/elements.

-- Users (password = bcrypt of 'password')
INSERT INTO users (username, email, password_hash) VALUES
('alice', 'alice@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFe5pNUeOal8UMrP8r8w/dGy'),
('bob', 'bob@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFe5pNUeOal8UMrP8r8w/dGy'),
('charlie', 'charlie@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iYqiSfFe5pNUeOal8UMrP8r8w/dGy');

-- Boards
INSERT INTO boards (owner_id, board_name, is_public) VALUES
((SELECT user_id FROM users WHERE username='alice'), 'Product Roadmap', TRUE),
((SELECT user_id FROM users WHERE username='alice'), 'UI Wireframes', FALSE),
((SELECT user_id FROM users WHERE username='bob'), 'Team Kanban', TRUE),
((SELECT user_id FROM users WHERE username='charlie'), 'Mind Map - Thesis', FALSE);

-- Board Memberships (owner plus collaborators)
INSERT INTO board_membership (board_id, user_id, role) VALUES
((SELECT board_id FROM boards WHERE board_name='Product Roadmap'), (SELECT user_id FROM users WHERE username='alice'), 'owner'),
((SELECT board_id FROM boards WHERE board_name='Product Roadmap'), (SELECT user_id FROM users WHERE username='bob'), 'editor'),
((SELECT board_id FROM boards WHERE board_name='Team Kanban'), (SELECT user_id FROM users WHERE username='bob'), 'owner'),
((SELECT board_id FROM boards WHERE board_name='Team Kanban'), (SELECT user_id FROM users WHERE username='charlie'), 'viewer'),
((SELECT board_id FROM boards WHERE board_name='UI Wireframes'), (SELECT user_id FROM users WHERE username='alice'), 'owner'),
((SELECT board_id FROM boards WHERE board_name='Mind Map - Thesis'), (SELECT user_id FROM users WHERE username='charlie'), 'owner');

-- Elements (a couple of sample shapes as JSON)
INSERT INTO elements (board_id, creator_id, type, z_order, data) VALUES
((SELECT board_id FROM boards WHERE board_name='Product Roadmap'), (SELECT user_id FROM users WHERE username='alice'), 'text', 1, '{"text":"Q4 Goals","x":100,"y":80}'),
((SELECT board_id FROM boards WHERE board_name='Team Kanban'), (SELECT user_id FROM users WHERE username='bob'), 'rect', 1, '{"x":50,"y":50,"w":200,"h":120}');

-- Templates
INSERT INTO templates (template_key, name, description, category, icon, plan, is_new, is_featured, usage_count) VALUES
('blank', 'Blank Canvas', 'Start from scratch with unlimited possibilities', 'popular', '✨', 'FREE', FALSE, TRUE, 99999),
('mindmap', 'Mind Map', 'Organize ideas and concepts in a visual hierarchy.', 'popular', '🧠', 'FREE', FALSE, FALSE, 12500),
('kanban', 'Kanban Board', 'Visualize work progress with customizable columns.', 'popular', '📋', 'FREE', FALSE, FALSE, 8200),
('brainstorm', 'Brainstorming Session', 'Structured framework for creative ideation.', 'design', '💡', 'FREE', TRUE, FALSE, 15100),
('flowchart', 'Process Flowchart', 'Map out processes and workflows.', 'business', '🔄', 'FREE', FALSE, FALSE, 9800),
('swot', 'SWOT Analysis', 'Evaluate Strengths, Weaknesses, Opportunities, and Threats.', 'business', '⚖️', 'PRO', FALSE, FALSE, 6400),
('wireframe', 'UI Wireframe', 'Design layouts with pre-built UI components.', 'design', '📱', 'FREE', FALSE, FALSE, 11200),
('journey', 'User Journey Map', 'Visualize customer touchpoints across stages.', 'business', '🗺️', 'FREE', FALSE, FALSE, 5900);

-- User settings defaults
INSERT INTO user_settings (user_id, display_name, description, bio)
SELECT u.user_id, u.username, '', ''
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.user_id);

-- Teams: create a personal team per user if missing
INSERT INTO teams (owner_id, name, description)
SELECT u.user_id, CONCAT(u.username, ' Team'), CONCAT('Default team for ', u.username)
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM teams t WHERE t.owner_id = u.user_id);

-- Team members: ensure owners belong to their team
INSERT INTO team_members (team_id, user_id, role, status)
SELECT t.team_id, t.owner_id, 'owner', 'active'
FROM teams t
LEFT JOIN team_members tm ON tm.team_id=t.team_id AND tm.user_id=t.owner_id
WHERE tm.user_id IS NULL;

-- Sample notifications for demo users
INSERT INTO notifications (user_id, type, title, message, is_read)
VALUES
((SELECT user_id FROM users WHERE username='alice'), 'info', 'Welcome to CollaboDraw', 'Your workspace is ready. Explore templates to get started!', false),
((SELECT user_id FROM users WHERE username='bob'), 'mention', 'You were mentioned', 'Alice mentioned you on Product Roadmap', false),
((SELECT user_id FROM users WHERE username='charlie'), 'invite', 'Board invite', 'Bob invited you to Team Kanban', true);
