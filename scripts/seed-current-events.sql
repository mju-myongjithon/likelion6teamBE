BEGIN;

-- Run scripts/delete-mock-events.sql first on databases that contain the old five mock events.
-- Seed-only BCrypt hash generated from a random password whose plaintext was discarded.
INSERT INTO users (email, password_hash, created_at, updated_at)
VALUES ('current-events@mju.ac.kr', '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352', NOW(), NOW())
ON CONFLICT (email) DO NOTHING;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users
        WHERE email = 'current-events@mju.ac.kr'
          AND password_hash = '$2y$10$Ek05oDdKjm8vX6ezSk5DYu9tptDOOW.iWmixTxGeILpbFIxHIE352'
    ) THEN
        RAISE EXCEPTION 'current-events@mju.ac.kr already belongs to a non-seed user';
    END IF;
END $$;

INSERT INTO profiles (user_id, name, school_name, department_name, residence_area, bio, avatar_url, created_at, updated_at)
SELECT user_id, '[운영] 행사 정보', '명지대학교', '서비스 운영', '서울',
       '공식 모집 공고를 확인해 등록한 행사 정보 계정입니다.', NULL, NOW(), NOW()
FROM users
WHERE email = 'current-events@mju.ac.kr'
ON CONFLICT (user_id) DO NOTHING;

WITH seed_events(title, description, organizer, deadline_at, starts_at, ends_at, location, related_url) AS (
    VALUES
        (
            '2026 자율주행 해커톤 경진대회 (AMET)',
            '자율주행 분야에 관심 있는 대학(원)생이 팀을 이루어 기술 과제를 해결하는 경진대회입니다. 산업통상부, 현대모비스, HL클레무브가 함께하며 사전교육·테스트 뒤 예선과 본선을 진행합니다.',
            '한국자율주행산업협회',
            '2026-07-22T14:59:00Z'::timestamptz,
            '2026-08-24T15:00:00Z'::timestamptz,
            '2026-08-27T14:59:00Z'::timestamptz,
            '서울 코엑스 Hall B',
            'https://www.kaami.or.kr/notice/event/read.jsp?no=43&reqPageNo=1'
        ),
        (
            '2026 스타트업 영그라운드 MVP 개발 해커톤',
            '초기 창업팀의 실제 아이디어와 기능 요구사항을 바탕으로 웹·앱 MVP를 구현하고 비즈니스 가치를 검증하는 실전형 해커톤입니다. 온라인 협업 후 오프라인 파이널데이에서 최종 발표와 시연을 진행합니다.',
            '어치브모먼트(Blaybus), 강동구 청년해냄센터',
            '2026-07-30T14:59:00Z'::timestamptz,
            '2026-07-31T15:00:00Z'::timestamptz,
            '2026-08-20T14:59:00Z'::timestamptz,
            '온라인 협업 · 서울 둔촌1동 주민센터 대강당',
            'https://www.blaybus.com/activities/928/landing'
        ),
        (
            '제7회 국립재활원 보조기기 해커톤',
            '노인·장애인의 삶의 질 향상을 위해 컴퓨터와 주변기기 접근성 보조기기를 개발하는 공익 해커톤입니다. 공학·디자인·재활 등 다양한 분야가 팀으로 참여하며 사전행사와 멘토링을 거쳐 본선에서 결과물을 시연합니다.',
            '국립재활원',
            '2026-07-31T04:00:00Z'::timestamptz,
            '2026-08-07T15:00:00Z'::timestamptz,
            '2026-08-28T14:59:00Z'::timestamptz,
            '하이서울유스호스텔 쥬빌리홀 · 온라인 · 고려대학교 X-Garage',
            'https://www.nrc.go.kr/nrc/board/boardView.do?bn=newsView&board_id=NRC_NOTICE_BOARD&fno=1&menu_cd=01_01&no=24068&pageIndex=1'
        ),
        (
            'NAN 2026: NHN Game × AI Hackathon',
            'AI를 활용해 새로운 게임 경험을 설계하고 48시간 동안 플레이 가능한 게임 프로토타입, AI 에이전트 설계서와 디렉팅 명세서를 완성하는 채용 연계형 해커톤입니다.',
            'NHN (문화체육관광부·한국콘텐츠진흥원 후원)',
            '2026-08-10T14:59:00Z'::timestamptz,
            '2026-09-03T15:00:00Z'::timestamptz,
            '2026-09-06T14:59:00Z'::timestamptz,
            '경기 성남시 NHN 플레이뮤지엄',
            'https://nan2026.nhn.com/'
        ),
        (
            '2026 제5회 전국 SW 테스트 경진대회',
            '실제 기업의 소프트웨어를 직접 테스트해 결함과 개선사항을 찾고 테스트 결과보고서를 작성하는 크라우드 테스팅 경진대회입니다. 본선 참가자에게 SW 품질관리 교육과 2박 3일 숙식이 제공됩니다.',
            '대구디지털혁신진흥원 등 지역 SW진흥기관',
            '2026-08-07T14:59:00Z'::timestamptz,
            '2026-08-23T15:00:00Z'::timestamptz,
            '2026-08-26T14:59:00Z'::timestamptz,
            '홀리데이인 광주호텔 3층 컨벤션홀',
            'https://dip.or.kr/home/notice/businessbbs/boardRead.ubs?sfpsize=10&fboardcd=business&sfkind=&sfcategory=&sfstdt=&sfendt=&sfsearch=ftitle&sfkeyword=&fboardnum=9103&sfpage=1'
        ),
        (
            '2026 ICEE 창의공학 캡스톤디자인 경진대회',
            '공학계열 학생팀이 하드웨어 시제품 또는 소프트웨어 구현 결과물을 출품하는 캡스톤디자인 경진대회입니다. 출품작은 서면심사를 거쳐 제작형과 연구형으로 나누어 본선 발표와 시상을 진행합니다.',
            '강원대학교 강릉공학교육혁신센터',
            '2026-07-31T14:59:00Z'::timestamptz,
            '2026-09-17T15:00:00Z'::timestamptz,
            '2026-09-18T14:59:00Z'::timestamptz,
            '본선 장소 미공개(공식 공고 기준)',
            'https://www.kangwon.ac.kr/ko/bbs/504/detail.do?pageIndex=1066&pageItm=10&pstSn=11135&searchGbn=0&searchOrderSort=0'
        ),
        (
            '2026 충북대학교 반도체 경진대회',
            '반도체 융합전공과 참여학과 재학생이 회로 설계 등 반도체 프로젝트 결과를 발표하는 경진대회입니다. 참가팀은 발표자료와 포스터를 제출하며, 회로 설계 분야 상위 팀은 시스템반도체 챌린지 참가 자격을 받습니다.',
            '충북대학교 반도체특성화대학사업단·IDEC 지역센터',
            '2026-07-31T14:59:00Z'::timestamptz,
            '2026-09-12T00:30:00Z'::timestamptz,
            '2026-09-12T05:00:00Z'::timestamptz,
            '충북대학교 학연산공동기술연구원(E9동) 101호~205호',
            'https://semicon.cbnu.ac.kr/notice/view/id/99'
        ),
        (
            '제8회 Future Finance A.I. Challenge',
            'AI 기술로 금융 서비스를 구현하는 대학(원)생 경진대회입니다. 리테일·개인화 추천, 리스크·이상탐지, 상담 자동화 등 실제 금융 현장의 문제를 풀고 오프라인 본선에서 결과물을 발표합니다.',
            'KB국민은행',
            '2026-08-03T07:00:00Z'::timestamptz,
            '2026-09-01T15:00:00Z'::timestamptz,
            '2026-09-02T14:59:00Z'::timestamptz,
            '이화여자대학교 ECC 이삼봉홀',
            'https://kb-aichallenge.com/'
        ),
        (
            '제10회 2026 미래에셋증권 AI Festival',
            'HyperCLOVA X를 활용해 금융상품 탐색과 투자 업무를 돕는 AI 에이전트 등 금융 AI 서비스를 개발하는 공모전입니다. 참가팀은 9월 6일까지 과제를 제출하고 예선 평가를 거쳐 10월 1일부터 16일까지 본선·멘토링에 참여하며, 결선은 10월 중 열립니다.',
            '미래에셋증권·네이버클라우드',
            '2026-07-20T14:59:00Z'::timestamptz,
            '2026-09-30T15:00:00Z'::timestamptz,
            '2026-10-16T14:59:00Z'::timestamptz,
            '본선·멘토링 및 결선 장소 미공개(공식 페이지 기준)',
            'https://miraeassetfesta.com/'
        ),
        (
            'NYPC 2026 Rookie Track',
            '만 14~18세 청소년이 휴리스틱 문제의 최적 답안을 찾는 코드를 작성해 경쟁하는 프로그래밍 대회입니다. 온라인 예선을 통과한 상위 참가자는 서울에서 열리는 오프라인 본선 라운드에 진출합니다.',
            '넥슨코리아',
            '2026-07-19T13:00:00Z'::timestamptz,
            '2026-08-28T15:00:00Z'::timestamptz,
            '2026-08-29T14:59:00Z'::timestamptz,
            '서울(오프라인 본선 세부 장소 추후 안내)',
            'https://new.nypc.co.kr/ko/notice/5?page=1'
        )
)
INSERT INTO events (creator_user_id, title, description, organizer, application_deadline_at, starts_at,
                    ends_at, location, related_url, created_at, updated_at)
SELECT u.user_id, s.title, s.description, s.organizer, s.deadline_at, s.starts_at,
       s.ends_at, s.location, s.related_url, NOW(), NOW()
FROM seed_events s
CROSS JOIN users u
WHERE u.email = 'current-events@mju.ac.kr'
  AND NOT EXISTS (SELECT 1 FROM events e WHERE e.related_url = s.related_url);

INSERT INTO tags (type, name)
VALUES ('EVENT', '해커톤'), ('EVENT', '개발'), ('EVENT', '자율주행'), ('EVENT', 'MVP'),
       ('EVENT', '창업'), ('EVENT', '접근성'), ('EVENT', '보조기기'), ('EVENT', 'AI'),
       ('EVENT', '게임'), ('EVENT', 'SW테스트'), ('EVENT', '캡스톤디자인'), ('EVENT', '반도체'),
       ('EVENT', '금융'), ('EVENT', '프로그래밍')
ON CONFLICT (type, name) DO NOTHING;

WITH event_tag_values(event_url, tag_name, position) AS (
    VALUES
        ('https://www.kaami.or.kr/notice/event/read.jsp?no=43&reqPageNo=1', '해커톤', 0),
        ('https://www.kaami.or.kr/notice/event/read.jsp?no=43&reqPageNo=1', '개발', 1),
        ('https://www.kaami.or.kr/notice/event/read.jsp?no=43&reqPageNo=1', '자율주행', 2),
        ('https://www.blaybus.com/activities/928/landing', '해커톤', 0),
        ('https://www.blaybus.com/activities/928/landing', 'MVP', 1),
        ('https://www.blaybus.com/activities/928/landing', '창업', 2),
        ('https://www.nrc.go.kr/nrc/board/boardView.do?bn=newsView&board_id=NRC_NOTICE_BOARD&fno=1&menu_cd=01_01&no=24068&pageIndex=1', '해커톤', 0),
        ('https://www.nrc.go.kr/nrc/board/boardView.do?bn=newsView&board_id=NRC_NOTICE_BOARD&fno=1&menu_cd=01_01&no=24068&pageIndex=1', '접근성', 1),
        ('https://www.nrc.go.kr/nrc/board/boardView.do?bn=newsView&board_id=NRC_NOTICE_BOARD&fno=1&menu_cd=01_01&no=24068&pageIndex=1', '보조기기', 2),
        ('https://nan2026.nhn.com/', '해커톤', 0),
        ('https://nan2026.nhn.com/', 'AI', 1),
        ('https://nan2026.nhn.com/', '게임', 2),
        ('https://dip.or.kr/home/notice/businessbbs/boardRead.ubs?sfpsize=10&fboardcd=business&sfkind=&sfcategory=&sfstdt=&sfendt=&sfsearch=ftitle&sfkeyword=&fboardnum=9103&sfpage=1', 'SW테스트', 0),
        ('https://dip.or.kr/home/notice/businessbbs/boardRead.ubs?sfpsize=10&fboardcd=business&sfkind=&sfcategory=&sfstdt=&sfendt=&sfsearch=ftitle&sfkeyword=&fboardnum=9103&sfpage=1', '개발', 1),
        ('https://www.kangwon.ac.kr/ko/bbs/504/detail.do?pageIndex=1066&pageItm=10&pstSn=11135&searchGbn=0&searchOrderSort=0', '캡스톤디자인', 0),
        ('https://www.kangwon.ac.kr/ko/bbs/504/detail.do?pageIndex=1066&pageItm=10&pstSn=11135&searchGbn=0&searchOrderSort=0', '개발', 1),
        ('https://www.kangwon.ac.kr/ko/bbs/504/detail.do?pageIndex=1066&pageItm=10&pstSn=11135&searchGbn=0&searchOrderSort=0', 'MVP', 2),
        ('https://semicon.cbnu.ac.kr/notice/view/id/99', '반도체', 0),
        ('https://semicon.cbnu.ac.kr/notice/view/id/99', '개발', 1),
        ('https://kb-aichallenge.com/', 'AI', 0),
        ('https://kb-aichallenge.com/', '금융', 1),
        ('https://kb-aichallenge.com/', '개발', 2),
        ('https://miraeassetfesta.com/', 'AI', 0),
        ('https://miraeassetfesta.com/', '금융', 1),
        ('https://miraeassetfesta.com/', '개발', 2),
        ('https://new.nypc.co.kr/ko/notice/5?page=1', '프로그래밍', 0),
        ('https://new.nypc.co.kr/ko/notice/5?page=1', '개발', 1)
)
INSERT INTO event_tags (event_id, tag_id, position)
SELECT e.event_id, t.tag_id, v.position
FROM event_tag_values v
JOIN events e ON e.related_url = v.event_url
JOIN tags t ON t.type = 'EVENT' AND t.name = v.tag_name
ON CONFLICT (event_id, tag_id) DO NOTHING;

COMMIT;
