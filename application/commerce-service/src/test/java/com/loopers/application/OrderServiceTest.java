package com.loopers.application;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.OrderStockService;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.coupon.CouponApplyResult;
import com.loopers.domain.coupon.CouponApplyService;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.order.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderStockService orderStockService;

    @Mock
    private CouponApplyService couponApplyService;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private OrderLineSnapshotRepository orderLineSnapshotRepository;

    // 주문을 생성한다

    @Test
    void 주문_생성_성공_재고_충분하면_수락() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), null);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void 주문_생성_성공_재고_부족하면_거절() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 1, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 5)
        ), null);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.isAccepted()).isFalse();
    }

    @Test
    void 주문_거절_시_재고_차감하지_않는다() {
        // given
        Product product = createProduct(1L, "에어맥스", 1, 1L);
        givenProductLockAndValidateWithProduct(product);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 5)
        ), null);

        // when
        orderService.create(command);

        // then
        assertThat(product.hasEnoughStock(Quantity.of(1))).isTrue();
    }

    @Test
    void 주문_수락_시_재고_차감된다() {
        // given
        Product product = createProduct(1L, "에어맥스", 50, 1L);
        givenProductLockAndValidateWithProduct(product);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), null);

        // when
        orderService.create(command);

        // then
        assertThat(product.hasEnoughStock(Quantity.of(49))).isFalse();
    }

    @Test
    void 중복_상품_주문_시_예외() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2),
                new OrderLineRequest(1L, 3)
        ), null);

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(com.loopers.support.error.CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.DUPLICATE_PRODUCT.message());
    }

    @Test
    void 빈_주문_시_예외() {
        // given
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(), null);

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(com.loopers.support.error.CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.EMPTY_ORDER_LINES.message());
    }

    @Test
    void 주문_원금액이_정확히_계산된다() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), null);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.originalAmount()).isEqualTo(200000);
    }

    @Test
    void 쿠폰_없이_주문하면_할인금액이_0이다() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), null);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.discountAmount()).isEqualTo(0);
    }

    // 쿠폰 적용 주문

    @Test
    void 쿠폰_적용_주문_성공_할인금액이_반영된다() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        given(couponApplyService.validate(100L, 10L, 200000))
                .willReturn(new CouponApplyResult(issuedCoupon, 3000));

        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), 100L);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.discountAmount()).isEqualTo(3000);
    }

    @Test
    void 쿠폰_적용_주문_성공_최종금액이_정확하다() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 50, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        given(couponApplyService.validate(100L, 10L, 200000))
                .willReturn(new CouponApplyResult(issuedCoupon, 3000));

        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ), 100L);

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.finalAmount()).isEqualTo(197000);
    }

    @Test
    void 쿠폰_적용_주문_거절_시_쿠폰_사용하지_않는다() {
        // given
        givenProductLockAndValidate(1L, "에어맥스", 1, 1L);
        givenBrands(1L, "나이키");
        givenOrderSave();

        IssuedCoupon issuedCoupon = IssuedCoupon.issue(1L, 10L);
        ReflectionTestUtils.setField(issuedCoupon, "id", 100L);
        given(couponApplyService.validate(100L, 10L, 500000))
                .willReturn(new CouponApplyResult(issuedCoupon, 3000));

        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 5)
        ), 100L);

        // when
        orderService.create(command);

        // then
        assertThat(issuedCoupon.isAvailable()).isTrue();
    }

    // 내 주문 내역을 조회한다

    @Test
    void 내_주문_내역_조회() {
        // given
        Long memberId = 10L;
        Order order = Order.place(memberId, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED, null, 200000, 0, 200000);
        given(orderRepository.findByMemberId(memberId)).willReturn(List.of(order));
        given(orderLineRepository.findByOrderId(any())).willReturn(List.of());
        given(orderLineSnapshotRepository.findByOrderLineIdIn(any())).willReturn(List.of());

        // when
        List<OrderInfo> result = orderService.getByMemberId(memberId);

        // then
        assertThat(result).hasSize(1);
    }

    // 주문 상세를 조회한다

    @Test
    void 주문_상세_조회_본인() {
        // given
        Long orderId = 1L;
        Long memberId = 10L;
        Order order = Order.place(memberId, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED, null, 200000, 0, 200000);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderLineRepository.findByOrderId(any())).willReturn(List.of());
        given(orderLineSnapshotRepository.findByOrderLineIdIn(any())).willReturn(List.of());

        // when
        OrderInfo result = orderService.getById(orderId, memberId);

        // then
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void 주문_상세_조회_타인_예외() {
        // given
        Long orderId = 1L;
        Order order = Order.place(10L, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED, null, 200000, 0, 200000);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.getById(orderId, 99L))
                .isInstanceOf(com.loopers.support.error.CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_OWNER.message());
    }

    @Test
    void 존재하지_않는_주문_조회_시_예외() {
        // given
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getById(999L, 10L))
                .isInstanceOf(com.loopers.support.error.CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_FOUND.message());
    }

    // 전체 주문을 조회한다 (관리자)

    @Test
    void 전체_주문_목록_조회() {
        // given
        Order order = Order.place(10L, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED, null, 200000, 0, 200000);
        given(orderRepository.findAll()).willReturn(List.of(order));
        given(orderLineRepository.findByOrderId(any())).willReturn(List.of());
        given(orderLineSnapshotRepository.findByOrderLineIdIn(any())).willReturn(List.of());

        // when
        List<OrderInfo> result = orderService.getAll();

        // then
        assertThat(result).hasSize(1);
    }

    private Product createProduct(Long id, String name, long stock, Long brandId) {
        Product product = Product.register(name, "설명", Money.of(100000), Stock.of(stock), brandId);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private Brand createBrand(Long id, String name) {
        Brand brand = Brand.register(name);
        ReflectionTestUtils.setField(brand, "id", id);
        return brand;
    }

    private void givenProductLockAndValidate(Long productId, String productName, long stock, Long brandId) {
        Product product = createProduct(productId, productName, stock, brandId);
        Map<Long, Product> productMap = new LinkedHashMap<>();
        productMap.put(productId, product);
        given(orderStockService.lockAndValidate(List.of(productId))).willReturn(productMap);
    }

    private void givenProductLockAndValidateWithProduct(Product product) {
        Map<Long, Product> productMap = new LinkedHashMap<>();
        productMap.put(product.getId(), product);
        given(orderStockService.lockAndValidate(List.of(product.getId()))).willReturn(productMap);
    }

    private void givenBrands(Long brandId, String brandName) {
        Brand brand = createBrand(brandId, brandName);
        given(brandRepository.findAllByIdIn(List.of(brandId))).willReturn(List.of(brand));
    }

    private void givenOrderSave() {
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(orderLineRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
    }
}
