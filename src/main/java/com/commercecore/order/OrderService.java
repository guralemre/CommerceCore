package com.commercecore.order;

import com.commercecore.inventory.Inventory;
import com.commercecore.inventory.InventoryRepository;
import com.commercecore.payment.Payment;
import com.commercecore.payment.PaymentGateway;
import com.commercecore.payment.PaymentRepository;
import com.commercecore.payment.PaymentResult;
import com.commercecore.product.Product;
import com.commercecore.product.ProductRepository;
import com.commercecore.user.User;
import com.commercecore.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    public OrderService(OrderRepository orderRepository,
                         UserRepository userRepository,
                         ProductRepository productRepository,
                         InventoryRepository inventoryRepository,
                         PaymentRepository paymentRepository,
                         PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.inventoryRepository = inventoryRepository;
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
    }

    public record OrderLine(Long productId, int quantity) {
    }

    @Transactional
    public Order placeOrder(Long userId, List<OrderLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Order order = new Order(user);
        for (OrderLine line : lines) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + line.productId()));
            order.addItem(new OrderItem(product, line.quantity(), product.getPrice()));
        }

        // PESSIMISTIC_WRITE locks every involved inventory row for the life of this
        // transaction, so concurrent orders on the same product serialize here instead
        // of racing past the availability check. @Version on Inventory still guards any
        // row touched outside this path (e.g. a stock adjustment that skips the lock).
        Map<Long, Inventory> inventories = lockInventory(order);

        for (OrderItem item : order.getItems()) {
            inventories.get(item.getProduct().getId()).reserve(item.getQuantity());
        }
        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PENDING);

        Payment payment = new Payment(order, order.getTotalAmount());
        PaymentResult result = paymentGateway.charge(order);

        if (result.success()) {
            payment.succeed(result.transactionId());
            for (OrderItem item : order.getItems()) {
                inventories.get(item.getProduct().getId()).confirm(item.getQuantity());
            }
            order.transitionTo(OrderStatus.CONFIRMED);
        } else {
            payment.fail();
            for (OrderItem item : order.getItems()) {
                inventories.get(item.getProduct().getId()).release(item.getQuantity());
            }
            order.transitionTo(OrderStatus.FAILED);
        }

        // Order must exist before Payment (not-null FK to order_id) can be saved.
        Order savedOrder = orderRepository.save(order);
        paymentRepository.save(payment);
        return savedOrder;
    }

    private Map<Long, Inventory> lockInventory(Order order) {
        Map<Long, Inventory> inventories = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Long productId = item.getProduct().getId();
            if (inventories.containsKey(productId)) {
                continue;
            }
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new IllegalStateException("No inventory record for product " + productId));
            inventories.put(productId, inventory);
        }
        return inventories;
    }
}
