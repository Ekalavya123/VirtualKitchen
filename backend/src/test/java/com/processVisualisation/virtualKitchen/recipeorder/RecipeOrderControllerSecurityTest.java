package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.auth.service.JwtService;
import com.processVisualisation.virtualKitchen.common.config.SecurityConfig;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessMapper;
import com.processVisualisation.virtualKitchen.recipeorder.dto.RecipeOrderRequests;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The recipe-order API through the real {@link SecurityConfig} chain, with identities set the way
 * {@code JwtAuthenticationFilter} sets them: the user id always comes from the session, and body
 * fields that try to name a user, a status or a payment result are ignored.
 */
@WebMvcTest(RecipeOrderController.class)
@Import({SecurityConfig.class, RecipeOrderMapper.class, ProcessMapper.class, RecipeOrderProperties.class})
class RecipeOrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecipeOrderService service;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/recipe-orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/recipe-orders/5/advance")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void ordersAreCreatedForTheSessionUserWhateverTheBodySays() throws Exception {
        when(service.createDraft(eq(7L), any())).thenReturn(order(5, 7, RecipeOrderStatus.DRAFT));

        mockMvc.perform(post("/api/v1/recipe-orders").with(user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":1,\"servings\":2,\"userId\":99,\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCode").value("RO-000005"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(service).createDraft(eq(7L), eq(new RecipeOrderRequests.Create(1L, 2, null, null)));
        verify(service, never()).createDraft(eq(99L), any());
    }

    @Test
    void anotherUsersOrderAnswersNotFound() throws Exception {
        when(service.get(8L, 5L)).thenThrow(RecipeOrderException.notFound());

        mockMvc.perform(get("/api/v1/recipe-orders/5").with(user(8L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recipe order not found"));
    }

    @Test
    void paymentResultIsTheServersNotTheClients() throws Exception {
        when(service.pay(eq(7L), eq(5L), any())).thenThrow(RecipeOrderException.badRequest("method must be one of the demo options"));

        mockMvc.perform(post("/api/v1/recipe-orders/5/pay").with(user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PAID\",\"idempotencyKey\":\"k\",\"status\":\"PAID\",\"amount\":0}"))
                .andExpect(status().isBadRequest());

        verify(service).pay(7L, 5L, new RecipeOrderRequests.Pay("PAID", "k"));
    }

    @Test
    void lifecycleConflictsAnswer409() throws Exception {
        when(service.advance(eq(7L), anyLong(), any())).thenThrow(RecipeOrderException.conflict("Cannot advance an order that is awaiting payment"));

        mockMvc.perform(post("/api/v1/recipe-orders/5/advance").with(user(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"AWAITING_PAYMENT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(HttpStatus.CONFLICT.value()));
    }

    @Test
    void responsesNeverExposeInventoryOperationKeys() throws Exception {
        RecipeOrder order = order(5, 7, RecipeOrderStatus.AWAITING_PAYMENT);
        RecipeOrder.Allocation allocation = new RecipeOrder.Allocation();
        allocation.setInventoryId(11L);
        allocation.setReserveKey("ro:5:a1:0:reserve");
        allocation.setState(RecipeOrder.AllocationState.RESERVED);
        order.getAllocations().add(allocation);
        when(service.get(7L, 5L)).thenReturn(order);

        mockMvc.perform(get("/api/v1/recipe-orders/5").with(user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocations[0].state").value("RESERVED"))
                .andExpect(jsonPath("$.data.allocations[0].reserveKey").doesNotExist())
                .andExpect(jsonPath("$.data.allowedActions[0]").value("PAY"));
    }

    private static RecipeOrder order(long id, long userId, RecipeOrderStatus status) {
        RecipeOrder order = new RecipeOrder();
        order.setId(id);
        order.setOrderCode(String.format("RO-%06d", id));
        order.setUserId(userId);
        order.setStatus(status);
        return order;
    }

    private static RequestPostProcessor user(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
