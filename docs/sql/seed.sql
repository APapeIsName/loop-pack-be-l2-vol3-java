-- ============================================
-- Test Seeding Data
-- ============================================
-- MySQL 8.0+ (Recursive CTE 사용)
-- 환경: EXPLAIN 검증용 대량 데이터

-- CTE 재귀 깊이 설정
SET SESSION cte_max_recursion_depth = 100000;

-- ============================================
-- Brand: 500개
-- ============================================
INSERT INTO brand (name, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 500
)
SELECT CONCAT('브랜드_', n), NOW(), NOW()
FROM seq;

-- ============================================
-- Product: 10만 건
-- ============================================
-- 브랜드당 평균 200개, 삭제율 5%, 좋아요 0~10000, 가격 1000~500000
INSERT INTO product (name, description, price, stock, brand_id, likes_count, created_at, updated_at, deleted_at)
WITH RECURSIVE seq AS (
    SELECT 0 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 99999
)
SELECT
    CONCAT('상품_', n),
    CONCAT('설명_', n),
    FLOOR(1000 + RAND() * 499000),
    FLOOR(RAND() * 1000),
    FLOOR(1 + RAND() * 500),
    FLOOR(RAND() * 10001),
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
    NOW(),
    IF(RAND() < 0.05, NOW(), NULL)
FROM seq;

-- ============================================
-- Order: 5만 건
-- ============================================
-- 회원 5000명, 최근 1년 내 랜덤 생성일
INSERT INTO orders (member_id, status, original_amount, discount_amount, final_amount, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 0 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 49999
)
SELECT
    FLOOR(1 + RAND() * 5000),
    'ACCEPTED',
    FLOOR(10000 + RAND() * 500000),
    0,
    FLOOR(10000 + RAND() * 500000),
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
    NOW()
FROM seq;

-- ============================================
-- Like: 3만 건
-- ============================================
-- 회원 3000명, 상품 1000개, UK 충돌 방지 (modular 분배)
INSERT INTO likes (member_id, subject_type, subject_id, created_at, updated_at)
WITH RECURSIVE seq AS (
    SELECT 0 AS n UNION ALL SELECT n + 1 FROM seq WHERE n < 29999
)
SELECT
    (n % 3000) + 1,
    'PRODUCT',
    FLOOR(n / 3000) + 1,
    NOW(),
    NOW()
FROM seq;
