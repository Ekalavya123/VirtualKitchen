package com.processVisualisation.virtualKitchen.ai.platform.analytics.controller;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.AiAnalyticsRecorder;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsHealth;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill.AiAnalyticsBackfillService;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill.BackfillRun;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.pricing.PricingRegistry;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AiResultResolver;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AnalyticsQueryService;
import com.processVisualisation.virtualKitchen.auth.security.JwtService;
import com.processVisualisation.virtualKitchen.auth.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The analytics API through the real {@link SecurityConfig} chain: admins only, enforced on the server. */
@WebMvcTest(AdminAiAnalyticsController.class)
@Import(SecurityConfig.class)
class AdminAiAnalyticsControllerSecurityTest {

    private static final String BASE = "/api/v1/admin/ai-analytics";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsQueryService queryService;
    @MockitoBean
    private AiResultResolver resultResolver;
    @MockitoBean
    private AiAnalyticsBackfillService backfillService;
    @MockitoBean
    private PricingRegistry pricingRegistry;
    @MockitoBean
    private AiAnalyticsRecorder recorder;
    @MockitoBean
    private AnalyticsHealth health;
    @MockitoBean
    private JwtService jwtService;

    @Test
    void adminCanListRequests() throws Exception {
        when(queryService.list(any(), eq("cost"), eq("desc"), eq(0), eq(25)))
                .thenReturn(new AnalyticsQueryService.Page<>(List.of(), 0, 0, 25));

        mockMvc.perform(get(BASE + "/requests").param("sort", "cost").param("operationContext", "CHAT,RECIPE_FLOW_GENERATION")
                        .with(user(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void adminCanInspectAResult_andAnUnknownRequestIs404() throws Exception {
        when(resultResolver.resolve(eq("missing"), isNull(), anyBoolean(), anyBoolean())).thenReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/requests/missing/result").with(user(1L, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void normalUsersAreForbiddenEverywhere_includingOutputInspection() throws Exception {
        for (String path : List.of("/requests", "/requests/r1", "/requests/r1/result", "/aggregate", "/overview",
                "/compare?operationContext=CHAT", "/pricing", "/backfill/runs", "/health", "/options")) {
            mockMvc.perform(get(BASE + path).with(user(2L, "USER"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post(BASE + "/backfill").with(user(2L, "USER"))).andExpect(status().isForbidden());

        verifyNoInteractions(queryService, resultResolver, backfillService);
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(get(BASE + "/requests")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/requests/r1/result")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/backfill")).andExpect(status().isUnauthorized());
        verifyNoInteractions(queryService, resultResolver, backfillService);
    }

    @Test
    void roleClaimedInParametersIsIgnored() throws Exception {
        mockMvc.perform(get(BASE + "/requests").param("userType", "ADMIN").param("role", "ADMIN").with(user(2L, "USER")))
                .andExpect(status().isForbidden());
        verify(queryService, never()).list(any(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void adminCanRunTheBackfill() throws Exception {
        BackfillRun run = new BackfillRun();
        run.setStatus("COMPLETED");
        when(backfillService.run(1L)).thenReturn(run);

        mockMvc.perform(post(BASE + "/backfill").with(user(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    private static RequestPostProcessor user(Long userId, String userType) {
        return authentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + userType))));
    }
}
