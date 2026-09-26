package com.heuristq.dinein.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.menu.domain.AddonEntity;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.FoodType;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.domain.ItemVariantEntity;
import com.heuristq.dinein.payment.gateway.razorpay.RazorpayPaymentGateway;
import com.heuristq.dinein.shared.util.Hmac;
import com.heuristq.dinein.table.TableService;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Boots the full application against a real PostgreSQL (Testcontainers). The Razorpay adapter is a spy: signature
 * checks and webhook parsing are real, only the outbound HTTP calls are stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String KEY_SECRET = "test_key_secret";
    protected static final String WEBHOOK_SECRET = "test_webhook_secret";

    /** One container for the whole test run, shared by every cached Spring context (stopped by Ryuk at JVM exit). */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected CategoryRepository categoryRepository;
    @Autowired
    protected ItemRepository itemRepository;
    @Autowired
    protected DiningTableRepository tableRepository;

    @MockitoSpyBean
    protected RazorpayPaymentGateway razorpay;

    protected DiningTableEntity table;
    protected ItemEntity paneer;      // 240.00, 5% GST, no variants, one addon (20.00)
    protected ItemEntity biryani;     // variants Half 160 / Full 260

    @BeforeEach
    void seedMenuAndStubProvider() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        table = new DiningTableEntity();
        table.setLabel("T" + suffix.substring(0, 6).toUpperCase());
        table.setQrToken(TableService.newQrToken());
        tableRepository.save(table);

        CategoryEntity category = new CategoryEntity();
        category.setName("Cat " + suffix);
        categoryRepository.save(category);

        paneer = new ItemEntity();
        paneer.setCategoryId(category.getId());
        paneer.setName("Paneer " + suffix);
        paneer.setFoodType(FoodType.VEG);
        paneer.setBasePrice(new BigDecimal("240.00"));
        AddonEntity chutney = new AddonEntity();
        chutney.setItem(paneer);
        chutney.setName("Chutney");
        chutney.setPrice(new BigDecimal("20.00"));
        paneer.getAddons().add(chutney);
        itemRepository.save(paneer);

        biryani = new ItemEntity();
        biryani.setCategoryId(category.getId());
        biryani.setName("Biryani " + suffix);
        biryani.setFoodType(FoodType.NON_VEG);
        for (String[] v : List.of(new String[]{"Half", "160.00"}, new String[]{"Full", "260.00"})) {
            ItemVariantEntity variant = new ItemVariantEntity();
            variant.setItem(biryani);
            variant.setName(v[0]);
            variant.setPrice(new BigDecimal(v[1]));
            biryani.getVariants().add(variant);
        }
        itemRepository.save(biryani);

        doAnswer(inv -> "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14))
                .when(razorpay).createProviderOrder(any());
    }

    /** Scans the table QR and returns the guest-session cookie. */
    protected Cookie guestCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/public/session").param("t", table.getQrToken())).andReturn();
        Cookie cookie = result.getResponse().getCookie("dinein_gs");
        if (cookie == null) {
            throw new IllegalStateException("no guest cookie: " + result.getResponse().getContentAsString());
        }
        return cookie;
    }

    protected MvcResult placeOrder(Cookie guest, String idempotencyKey, String body) throws Exception {
        return mvc.perform(post("/api/public/orders").with(csrf()).cookie(guest)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    protected String simpleCart() {
        return """
                {"items":[{"itemId":%d,"addonIds":[%d],"quantity":2},
                          {"itemId":%d,"variantId":%d,"quantity":1}],
                 "customerName":"Asha","customerPhone":"9876543210"}
                """.formatted(paneer.getId(), paneer.getAddons().getFirst().getId(), biryani.getId(),
                biryani.getVariants().get(1).getId());
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected static String paymentSignature(String orderId, String paymentId) {
        return Hmac.sha256Hex(orderId + "|" + paymentId, KEY_SECRET);
    }

    protected static String webhookSignature(String payload) {
        return Hmac.sha256Hex(payload.getBytes(StandardCharsets.UTF_8), WEBHOOK_SECRET);
    }

    protected static String capturedWebhook(String razorpayOrderId, String paymentId, long amountPaise) {
        return """
                {"entity":"event","event":"payment.captured","payload":{"payment":{"entity":{
                "id":"%s","order_id":"%s","status":"captured","amount":%d,"method":"upi"}}}}"""
                .formatted(paymentId, razorpayOrderId, amountPaise);
    }
}
