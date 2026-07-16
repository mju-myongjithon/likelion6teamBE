BEGIN;

DELETE FROM event_tags
WHERE event_id IN (
    SELECT e.event_id
    FROM events e
    JOIN users u ON u.user_id = e.creator_user_id
    WHERE u.email = 'mock-events@mju.ac.kr'
      AND u.password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352'
      AND e.related_url LIKE 'https://example.com/mjuton/mock-events/%'
);

DELETE FROM events
WHERE creator_user_id = (SELECT user_id FROM users WHERE email = 'mock-events@mju.ac.kr'
  AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352')
  AND related_url LIKE 'https://example.com/mjuton/mock-events/%';

-- Shared tags are intentionally retained because their rows may predate this seed.

DELETE FROM profile_tags
WHERE user_id = (SELECT user_id FROM users WHERE email = 'mock-events@mju.ac.kr'
  AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352')
  AND NOT EXISTS (
      SELECT 1 FROM events e
      WHERE e.creator_user_id = profile_tags.user_id
  )
  AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.leader_user_id = profile_tags.user_id);

DELETE FROM profiles
WHERE user_id = (SELECT user_id FROM users WHERE email = 'mock-events@mju.ac.kr'
  AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352')
  AND NOT EXISTS (
      SELECT 1 FROM events e
      WHERE e.creator_user_id = profiles.user_id
  )
  AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.leader_user_id = profiles.user_id);

DELETE FROM users
WHERE email = 'mock-events@mju.ac.kr'
  AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352'
  AND NOT EXISTS (SELECT 1 FROM events e WHERE e.creator_user_id = users.user_id)
  AND NOT EXISTS (SELECT 1 FROM groups g WHERE g.leader_user_id = users.user_id);

COMMIT;
