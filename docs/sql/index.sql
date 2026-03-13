-- ============================================
-- Indexes
-- ============================================

-- Product: 상품 목록 정렬 (인기순, 최신순, 가격순)
CREATE INDEX idx_product_likes ON product (likes_count DESC);
CREATE INDEX idx_product_latest ON product (created_at DESC);
CREATE INDEX idx_product_price ON product (price);

-- Product: 브랜드 필터 + 정렬 (등치 선두 + 정렬 후미)
CREATE INDEX idx_product_brand_likes ON product (brand_id, likes_count DESC);
CREATE INDEX idx_product_brand_latest ON product (brand_id, created_at DESC);
CREATE INDEX idx_product_brand_price ON product (brand_id, price);

-- Order: 회원별 주문 목록 (최신순)
CREATE INDEX idx_order_member_created ON orders (member_id, created_at DESC);

-- IssuedCoupon: 회원별 쿠폰 목록 / 쿠폰별 발급 현황
CREATE INDEX idx_issued_coupon_member ON issued_coupon (member_id);
CREATE INDEX idx_issued_coupon_coupon ON issued_coupon (coupon_id);
