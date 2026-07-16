BEGIN;

-- Seed-only BCrypt hash generated from a random password whose plaintext was discarded.
INSERT INTO users (email, password_hash, created_at, updated_at)
VALUES ('mock-events@mju.ac.kr', '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users
        WHERE email = 'mock-events@mju.ac.kr'
          AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352'
    ) THEN
        RAISE EXCEPTION 'mock-events@mju.ac.kr already belongs to a non-seed user';
    END IF;
END $$;

INSERT INTO profiles (user_id, name, school_name, department_name, residence_area, bio, avatar_url, created_at, updated_at)
SELECT user_id, '[목업] 행사 정보 운영자', '명지대학교', '서비스 운영', '서울', '개발용 목업 행사 정보 계정입니다.', NULL, NOW(), NOW()
FROM users
WHERE email = 'mock-events@mju.ac.kr'
ON CONFLICT (user_id) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM profiles p
        JOIN users u ON u.user_id = p.user_id
        WHERE u.email = 'mock-events@mju.ac.kr'
          AND p.name = '[목업] 행사 정보 운영자'
          AND p.school_name = '명지대학교'
          AND p.department_name = '서비스 운영'
          AND p.residence_area = '서울'
    ) THEN
        RAISE EXCEPTION 'mock-events@mju.ac.kr has a non-seed profile';
    END IF;
END $$;

WITH seed_events(title, description, organizer, deadline_at, starts_at, ends_at, location, related_url, created_at) AS (
    VALUES
        ('[목업] 2026 CampusLink 여름 해커톤', '대학생이 팀을 이루어 캠퍼스 문제를 해결하는 서비스를 개발합니다.', '[목업] CampusLink', '2026-08-01T14:59:59Z'::timestamptz, '2026-08-08T00:00:00Z'::timestamptz, '2026-08-09T09:00:00Z'::timestamptz, '명지대학교 인문캠퍼스', 'https://example.com/mjuton/mock-events/campuslink-summer-2026', '2026-07-16T01:00:00Z'::timestamptz),
        ('[목업] AI 서비스 아이디어톤', '생성형 AI를 활용한 생활 밀착형 서비스 아이디어를 발표하는 행사입니다.', '[목업] 명지대학교 SW중심대학사업단', '2026-08-20T14:59:59Z'::timestamptz, '2026-08-29T01:00:00Z'::timestamptz, '2026-08-29T09:00:00Z'::timestamptz, '명지대학교 자연캠퍼스', 'https://example.com/mjuton/mock-events/ai-ideathon-2026', '2026-07-16T01:01:00Z'::timestamptz),
        ('[목업] 오픈소스 컨트리뷰톤', '입문자와 경험자가 함께 오픈소스 프로젝트에 기여하는 온라인 행사입니다.', '[목업] Campus OSS Community', '2026-09-05T14:59:59Z'::timestamptz, '2026-09-12T00:00:00Z'::timestamptz, '2026-09-13T09:00:00Z'::timestamptz, '온라인', 'https://example.com/mjuton/mock-events/opensource-contribution-2026', '2026-07-16T01:02:00Z'::timestamptz),
        ('[목업] 대학생 클라우드 개발 캠프', '클라우드 기초부터 배포까지 실습하는 2일 과정입니다.', '[목업] Cloud Campus Korea', '2026-09-25T14:59:59Z'::timestamptz, '2026-10-03T00:00:00Z'::timestamptz, '2026-10-04T09:00:00Z'::timestamptz, '서울 스타트업 허브', 'https://example.com/mjuton/mock-events/cloud-camp-2026', '2026-07-16T01:03:00Z'::timestamptz),
        ('[목업] 캠퍼스 소셜벤처 데모데이', '사회 문제 해결을 목표로 한 대학생 팀의 서비스와 성과를 공유합니다.', '[목업] Campus Impact Network', '2026-10-30T14:59:59Z'::timestamptz, '2026-11-07T04:00:00Z'::timestamptz, '2026-11-07T09:00:00Z'::timestamptz, '서울 청년창업센터', 'https://example.com/mjuton/mock-events/social-venture-demo-2026', '2026-07-16T01:04:00Z'::timestamptz)
)
INSERT INTO events (creator_user_id, title, description, organizer, application_deadline_at, starts_at, ends_at, location, related_url, created_at, updated_at)
SELECT u.user_id, s.title, s.description, s.organizer, s.deadline_at, s.starts_at, s.ends_at, s.location, s.related_url, s.created_at, s.created_at
FROM seed_events s
CROSS JOIN users u
WHERE u.email = 'mock-events@mju.ac.kr'
  AND NOT EXISTS (
      SELECT 1 FROM events e
      WHERE e.creator_user_id = u.user_id AND e.related_url = s.related_url
  );

INSERT INTO tags (type, name)
VALUES ('EVENT', '목업-해커톤'), ('EVENT', '목업-AI'), ('EVENT', '목업-오픈소스'), ('EVENT', '목업-클라우드'), ('EVENT', '목업-소셜벤처'), ('EVENT', '목업-개발')
ON CONFLICT (type, name) DO NOTHING;

WITH event_tag_values(event_url, tag_name, position) AS (
    VALUES
        ('https://example.com/mjuton/mock-events/campuslink-summer-2026', '목업-해커톤', 0), ('https://example.com/mjuton/mock-events/campuslink-summer-2026', '목업-개발', 1),
        ('https://example.com/mjuton/mock-events/ai-ideathon-2026', '목업-AI', 0), ('https://example.com/mjuton/mock-events/ai-ideathon-2026', '목업-개발', 1),
        ('https://example.com/mjuton/mock-events/opensource-contribution-2026', '목업-오픈소스', 0), ('https://example.com/mjuton/mock-events/opensource-contribution-2026', '목업-개발', 1),
        ('https://example.com/mjuton/mock-events/cloud-camp-2026', '목업-클라우드', 0), ('https://example.com/mjuton/mock-events/cloud-camp-2026', '목업-개발', 1),
        ('https://example.com/mjuton/mock-events/social-venture-demo-2026', '목업-소셜벤처', 0)
)
INSERT INTO event_tags (event_id, tag_id, position)
SELECT e.event_id, t.tag_id, v.position
FROM event_tag_values v
JOIN users u ON u.email = 'mock-events@mju.ac.kr'
JOIN events e ON e.creator_user_id = u.user_id AND e.related_url = v.event_url
JOIN tags t ON t.type = 'EVENT' AND t.name = v.tag_name
ON CONFLICT (event_id, tag_id) DO NOTHING;

COMMIT;
