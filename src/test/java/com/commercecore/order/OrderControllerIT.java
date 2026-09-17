package com.commercecore.order;

import com.commercecore.inventory.Inventory;
import com.commercecore.inventory.InventoryRepository;
import com.commercecore.user.Role;
import com.commercecore.user.User;
import com.commercecore.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the flagship concurrency scenario end-to-end against a real Postgres instead of
 * mocks: one unit of stock, three customers, three concurrent POST /api/orders. Confirms the
 * PESSIMISTIC_WRITE lock in InventoryRepository actually serializes the DB writes under real
 * contention - a test built on mocked repositories can't prove that, only real locking can.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderControllerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("commercecore_it")
            .withUsername("commercecore")
            .withPassword("commercecore");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void threeConcurrentOrders_onSingleUnitOfStock_exactlyOneSucceeds() throws Exception {
        User admin = new User("admin@it-test.com", passwordEncoder.encode("admin-pass"), "IT", "Admin", Role.ADMIN);
        userRepository.save(admin);
        String adminToken = login("admin@it-test.com", "admin-pass");

        ResponseEntity<Map> productResponse = post("/api/products", adminToken,
                Map.of("sku", "IT-SKU-1", "name", "Widget", "price", 10));
        assertThat(productResponse.getStatusCode().value()).isEqualTo(201);
        long productId = ((Number) productResponse.getBody().get("id")).longValue();

        ResponseEntity<Map> stockResponse = post("/api/inventory/" + productId + "/stock", adminToken, Map.of("quantity", 1));
        assertThat(stockResponse.getStatusCode().value()).isEqualTo(200);

        List<String> buyerTokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String email = "buyer" + i + "@it-test.com";
            post("/api/auth/register", null, Map.of(
                    "email", email, "password", "secret123", "firstName", "Buyer", "lastName", String.valueOf(i)));
            buyerTokens.add(login(email, "secret123"));
        }

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
        for (String token : buyerTokens) {
            Callable<ResponseEntity<Map>> task = () -> {
                ready.countDown();
                go.await();
                return post("/api/orders", token, Map.of(
                        "items", List.of(Map.of("productId", productId, "quantity", 1))));
            };
            futures.add(executor.submit(task));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        List<Integer> statuses = new ArrayList<>();
        for (Future<ResponseEntity<Map>> future : futures) {
            statuses.add(future.get(10, TimeUnit.SECONDS).getStatusCode().value());
        }
        executor.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409);

        Inventory finalInventory = inventoryRepository.findByProductId(productId).orElseThrow();
        assertThat(finalInventory.getQuantity()).isZero();
        assertThat(finalInventory.getReservedQuantity()).isZero();
    }

    private String login(String email, String password) {
        ResponseEntity<Map> response = post("/api/auth/login", null, Map.of("email", email, "password", password));
        return (String) response.getBody().get("accessToken");
    }

    private ResponseEntity<Map> post(String path, String bearerToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return restTemplate.exchange(
                "http://localhost:" + port + path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }
}
