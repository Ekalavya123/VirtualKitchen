package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetBatchResponseDTO;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetImageResponseDTO;
import com.processVisualisation.virtualKitchen.auth.service.JwtService;
import com.processVisualisation.virtualKitchen.common.config.SecurityConfig;
import org.junit.jupiter.api.Test;
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
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the admin global-asset API through the real {@link SecurityConfig} filter chain, with
 * identities set the way {@code JwtAuthenticationFilter} sets them (Long user id + ROLE_ authority).
 */
@WebMvcTest(AdminGlobalAssetController.class)
@Import(SecurityConfig.class)
class AdminGlobalAssetControllerSecurityTest {

    private static final String ONION_IMAGE = "/api/v1/admin/global-assets/ingredients/42/image";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GlobalAssetImageService imageService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void adminCanQueueGeneration() throws Exception {
        when(imageService.request(GlobalResourceType.INGREDIENT, 42L, 1L)).thenReturn(result(GlobalAssetImageResponseDTO.Outcome.QUEUED));

        mockMvc.perform(post(ONION_IMAGE).with(user(1L, "ADMIN")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.outcome").value("QUEUED"))
                .andExpect(jsonPath("$.data.resourceId").value(42));
    }

    @Test
    void existingImageAnswersOk() throws Exception {
        when(imageService.request(GlobalResourceType.INGREDIENT, 42L, 1L))
                .thenReturn(result(GlobalAssetImageResponseDTO.Outcome.ALREADY_EXISTS));

        mockMvc.perform(post(ONION_IMAGE).with(user(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("ALREADY_EXISTS"));
    }

    @Test
    void normalUserIsForbidden() throws Exception {
        mockMvc.perform(post(ONION_IMAGE).with(user(2L, "USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/global-assets/ingredients/images/missing").with(user(2L, "USER")))
                .andExpect(status().isForbidden());
        verify(imageService, never()).request(any(), anyLong(), anyLong());
        verify(imageService, never()).requestMissing(any(), anyLong());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post(ONION_IMAGE))
                .andExpect(status().isUnauthorized());
        verify(imageService, never()).request(any(), anyLong(), anyLong());
    }

    @Test
    void roleInRequestBodyOrParamsIsIgnored() throws Exception {
        mockMvc.perform(post(ONION_IMAGE + "?isAdmin=true&role=ADMIN&userId=1")
                        .with(user(2L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userType\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownResourceTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/global-assets/spaceships/42/image").with(user(1L, "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownResourceIsNotFound() throws Exception {
        when(imageService.request(GlobalResourceType.INGREDIENT, 42L, 1L))
                .thenThrow(new NoSuchElementException("No ingredients resource found with id 42"));

        mockMvc.perform(post(ONION_IMAGE).with(user(1L, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanQueueABatchAndPollStatus() throws Exception {
        when(imageService.requestBatch(eq(GlobalResourceType.EQUIPMENT), eq(List.of(3L, 4L)), eq(1L)))
                .thenReturn(GlobalAssetBatchResponseDTO.of(GlobalResourceType.EQUIPMENT, List.of()));
        when(imageService.status(GlobalResourceType.INGREDIENT, 42L)).thenReturn(result(null));

        mockMvc.perform(post("/api/v1/admin/global-assets/equipment/images").with(user(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceIds\":[3,4]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.resourceType").value("EQUIPMENT"));
        mockMvc.perform(get(ONION_IMAGE).with(user(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceId").value(42));
    }

    private static RequestPostProcessor user(Long userId, String userType) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + userType))));
    }

    private static GlobalAssetImageResponseDTO result(GlobalAssetImageResponseDTO.Outcome outcome) {
        return GlobalAssetImageResponseDTO.builder()
                .resourceType(GlobalResourceType.INGREDIENT)
                .resourceId(42L)
                .resourceName("Onion")
                .outcome(outcome)
                .message("ok")
                .build();
    }
}
