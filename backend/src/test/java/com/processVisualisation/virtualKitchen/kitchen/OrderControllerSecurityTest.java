package com.processVisualisation.virtualKitchen.kitchen;

import com.processVisualisation.virtualKitchen.auth.service.JwtService;
import com.processVisualisation.virtualKitchen.common.config.SecurityConfig;
import com.processVisualisation.virtualKitchen.kitchen.controller.OrderController;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderCreateRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.OrderResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.service.IOrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shop orders keep working for their owner and are placed/listed only for the authenticated user. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerSecurityTest {

    private static final String BODY = "{\"userId\":%s,\"items\":[{\"itemId\":1,\"itemType\":\"INGREDIENT\",\"itemName\":\"Flour\",\"quantity\":1,\"unit\":\"KG\",\"price\":5}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IOrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shopCheckoutStillWorksForTheOwner() throws Exception {
        when(orderService.createOrder(any())).thenReturn(OrderResponseDTO.builder().orderId(45L).orderCode("SO-000045").userId(7L).build());

        mockMvc.perform(post("/api/v1/orders").with(user(7L)).contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCode").value("SO-000045"));

        ArgumentCaptor<OrderCreateRequestDTO> request = ArgumentCaptor.forClass(OrderCreateRequestDTO.class);
        verify(orderService).createOrder(request.capture());
        assertEquals(7L, request.getValue().getUserId());
    }

    @Test
    void cannotPlaceAnOrderForAnotherUser() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(user(7L)).contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("99")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(orderService);
    }

    @Test
    void ownerSeesTheirOrdersButNotSomeoneElses() throws Exception {
        when(orderService.getOrdersByUser(7L)).thenReturn(List.of(OrderResponseDTO.builder().orderId(45L).orderCode("SO-000045").build()));

        mockMvc.perform(get("/api/v1/orders/user/7").with(user(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].orderCode").value("SO-000045"));
        mockMvc.perform(get("/api/v1/orders/user/8").with(user(7L))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/user/7")).andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor user(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
