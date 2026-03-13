-- ============================================
-- Unique Constraints
-- ============================================

-- Member: 로그인 ID 중복 방지
ALTER TABLE member
    ADD CONSTRAINT uk_member_login_id UNIQUE (login_id);

-- Brand: 브랜드 이름 중복 방지
ALTER TABLE brand
    ADD CONSTRAINT uk_brand_name UNIQUE (name);

-- Like: 동일 회원의 동일 대상 좋아요 중복 방지 (동시성 방어)
ALTER TABLE likes
    ADD CONSTRAINT uk_likes_member_subject UNIQUE (member_id, subject_type, subject_id);
