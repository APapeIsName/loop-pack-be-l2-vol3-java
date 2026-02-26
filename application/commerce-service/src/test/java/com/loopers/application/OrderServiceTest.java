package com.loopers.application;

import com.loopers.application.service.OrderService;
import com.loopers.application.service.dto.OrderCreateCommand;
import com.loopers.application.service.dto.OrderInfo;
import com.loopers.application.service.dto.OrderLineRequest;
import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductExceptionMessage;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.vo.Money;
import com.loopers.domain.catalog.product.vo.Quantity;
import com.loopers.domain.catalog.product.vo.Stock;
import com.loopers.domain.order.*;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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
    private OrderRepository orderRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private OrderLineSnapshotRepository orderLineSnapshotRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    // 주문을 생성한다

    @Test
    void 주문_생성_성공_재고_충분하면_수락() {
        // given
        givenProductAndBrand(1L, "에어맥스", 50, 1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ));

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.isAccepted()).isTrue();
    }

    @Test
    void 주문_생성_성공_재고_부족하면_거절() {
        // given
        givenProductAndBrand(1L, "에어맥스", 1, 1L, "나이키");
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 5)
        ));

        // when
        OrderInfo result = orderService.create(command);

        // then
        assertThat(result.isAccepted()).isFalse();
    }

    @Test
    void 주문_거절_시_재고_차감하지_않는다() {
        // given
        Product product = createProduct(1L, "에어맥스", 1, 1L);
        Brand brand = createBrand(1L, "나이키");
        given(productRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(product));
        given(brandRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(brand));
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 5)
        ));

        // when
        orderService.create(command);

        // then
        assertThat(product.hasEnoughStock(Quantity.of(1))).isTrue();
    }

    @Test
    void 주문_수락_시_재고_차감된다() {
        // given
        Product product = createProduct(1L, "에어맥스", 50, 1L);
        Brand brand = createBrand(1L, "나이키");
        given(productRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(product));
        given(brandRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(brand));
        givenOrderSave();
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ));

        // when
        orderService.create(command);

        // then
        assertThat(product.hasEnoughStock(Quantity.of(49))).isFalse();
    }

    @Test
    void 삭제된_상품_포함_시_예외() {
        // given
        Product product = createProduct(1L, "에어맥스", 50, 1L);
        product.delete();
        given(productRepository.findAllByIdIn(List.of(1L))).willReturn(List.of(product));
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2)
        ));

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.ALREADY_DELETED.message());
    }

    @Test
    void 존재하지_않는_상품_포함_시_예외() {
        // given
        given(productRepository.findAllByIdIn(List.of(999L))).willReturn(List.of());
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(999L, 2)
        ));

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(ProductExceptionMessage.Product.NOT_FOUND.message());
    }

    @Test
    void 중복_상품_주문_시_예외() {
        // given
        givenProductAndBrand(1L, "에어맥스", 50, 1L, "나이키");
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of(
                new OrderLineRequest(1L, 2),
                new OrderLineRequest(1L, 3)
        ));

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.DUPLICATE_PRODUCT.message());
    }

    @Test
    void 빈_주문_시_예외() {
        // given
        OrderCreateCommand command = new OrderCreateCommand(10L, List.of());

        // when & then
        assertThatThrownBy(() -> orderService.create(command))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.EMPTY_ORDER_LINES.message());
    }

    // 내 주문 내역을 조회한다

    @Test
    void 내_주문_내역_조회() {
        // given
        Long memberId = 10L;
        Order order = Order.place(memberId, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED);
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
        ), OrderStatus.ACCEPTED);
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
        ), OrderStatus.ACCEPTED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderService.getById(orderId, 99L))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_OWNER.message());
    }

    @Test
    void 존재하지_않는_주문_조회_시_예외() {
        // given
        given(orderRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.getById(999L, 10L))
                .isInstanceOf(CoreException.class)
                .hasMessage(OrderExceptionMessage.Order.NOT_FOUND.message());
    }

    // 전체 주문을 조회한다 (관리자)

    @Test
    void 전체_주문_목록_조회() {
        // given
        Order order = Order.place(10L, List.of(
                OrderLine.of(1L, Quantity.of(2), "에어맥스", "설명", 100000, "나이키")
        ), OrderStatus.ACCEPTED);
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

    private void givenProductAndBrand(Long productId, String productName, long stock, Long brandId, String brandName) {
        Product product = createProduct(productId, productName, stock, brandId);
        Brand brand = createBrand(brandId, brandName);
        given(productRepository.findAllByIdIn(List.of(productId))).willReturn(List.of(product));
        given(brandRepository.findAllByIdIn(List.of(brandId))).willReturn(List.of(brand));
    }

    private void givenOrderSave() {
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(orderLineRepository.saveAll(any())).willAnswer(invocation -> invocation.getArgument(0));
    }
}
