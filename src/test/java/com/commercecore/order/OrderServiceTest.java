package com.commercecore.order;

import com.commercecore.common.ResourceNotFoundException;
import com.commercecore.inventory.Inventory;
import com.commercecore.inventory.InsufficientStockException;
import com.commercecore.inventory.InventoryRepository;
import com.commercecore.payment.Payment;
import com.commercecore.payment.PaymentGateway;
import com.commercecore.payment.PaymentRepository;
import com.commercecore.payment.PaymentResult;
import com.commercecore.payment.PaymentStatus;
import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import com.commercecore.user.Role;
import com.commercecore.user.User;
import com.commercecore.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;

    private OrderService orderService;

    private User user;
    private Product product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, userRepository, productRepository,
                inventoryRepository, paymentRepository, paymentGateway);

        user = new User("alice@example.com", "hash", "Alice", "Smith", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", 1L);

        product = new Product("SKU-1", "Widget", "desc", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(product, "id", 100L);

        inventory = new Inventory(product, 5);
    }

    @Test
    void placeOrder_confirmsOrderAndDeductsStock_whenPaymentSucceeds() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(inventory));
        when(paymentGateway.charge(any())).thenReturn(PaymentResult.success("tx-1"));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(1L, List.of(new OrderService.OrderLine(100L, 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("20.00");
        assertThat(inventory.getQuantity()).isEqualTo(3);
        assertThat(inventory.getReservedQuantity()).isZero();

        verify(inventoryRepository).findByProductIdForUpdate(100L);
        verify(inventoryRepository, never()).findByProductId(anyLong());
        verify(orderRepository).save(result);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(paymentCaptor.getValue().getTransactionId()).isEqualTo("tx-1");
    }

    @Test
    void placeOrder_failsOrderAndReleasesStock_whenPaymentFails() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(inventory));
        when(paymentGateway.charge(any())).thenReturn(PaymentResult.failure());
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(1L, List.of(new OrderService.OrderLine(100L, 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(inventory.getQuantity()).isEqualTo(5);
        assertThat(inventory.getReservedQuantity()).isZero();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void placeOrder_throwsInsufficientStockAndPersistsNothing_whenStockTooLow() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> orderService.placeOrder(1L, List.of(new OrderService.OrderLine(100L, 10))))
                .isInstanceOf(InsufficientStockException.class);

        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void placeOrder_throwsResourceNotFound_whenUserMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(99L, List.of(new OrderService.OrderLine(100L, 1))))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(productRepository);
        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void placeOrder_throwsResourceNotFound_whenProductMissing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(1L, List.of(new OrderService.OrderLine(999L, 1))))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    void placeOrder_throwsIllegalArgument_whenNoItems() {
        assertThatThrownBy(() -> orderService.placeOrder(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void placeOrder_locksInventoryOnce_whenSameProductOrderedInTwoLines() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdForUpdate(100L)).thenReturn(Optional.of(inventory));
        when(paymentGateway.charge(any())).thenReturn(PaymentResult.success("tx-2"));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.placeOrder(1L, List.of(
                new OrderService.OrderLine(100L, 1),
                new OrderService.OrderLine(100L, 2)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(inventory.getQuantity()).isEqualTo(2);
        verify(inventoryRepository, times(1)).findByProductIdForUpdate(100L);
    }
}
