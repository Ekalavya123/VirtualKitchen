package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.credit.AiCreditTransaction;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.credit.UserAiCredit;
import com.processVisualisation.virtualKitchen.ai.dto.AiCreditTransactionResponseDTO;
import com.processVisualisation.virtualKitchen.ai.dto.GrantCreditsRequestDTO;
import com.processVisualisation.virtualKitchen.ai.dto.UserAiCreditResponseDTO;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Self-service AI credit balance/history for the current user, plus an
 * admin-only manual adjustment endpoint. All balance changes go through
 * {@link CreditService} so every adjustment — including this admin one — is
 * ledgered, never a raw write.
 */
@RestController
@RequestMapping("/api/v1")
public class AICreditController {

    private final CreditService creditService;

    public AICreditController(CreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping("/users/me/ai-credits")
    public ApiResponse<UserAiCreditResponseDTO> myCredits() {
        UserAiCredit account = creditService.getOrCreateAccount(currentUserId());
        return ApiResponse.<UserAiCreditResponseDTO>builder()
                .success(true)
                .message("AI credit balance")
                .data(toDto(account))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @GetMapping("/users/me/ai-credits/history")
    public ApiResponse<List<AiCreditTransactionResponseDTO>> myCreditHistory() {
        List<AiCreditTransactionResponseDTO> history = creditService.getHistory(currentUserId()).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.<List<AiCreditTransactionResponseDTO>>builder()
                .success(true)
                .message("AI credit transaction history")
                .data(history)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Manually grants (positive {@code amount}) or deducts (negative
     * {@code amount}) AI credits for a user. Restricted to callers with the
     * {@code ROLE_ADMIN} authority (see {@code auth.model.UserType}).
     */
    @PostMapping("/admin/users/{userId}/ai-credits/grant")
    public ApiResponse<UserAiCreditResponseDTO> grant(
            @PathVariable Long userId,
            @Valid @RequestBody GrantCreditsRequestDTO request
    ) {
        requireAdmin();
        UserAiCredit updated = creditService.adjust(userId, request.getAmount())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Adjustment of " + request.getAmount() + " would push the user's balance negative"));
        return ApiResponse.<UserAiCreditResponseDTO>builder()
                .success(true)
                .message("AI credit balance adjusted")
                .data(toDto(updated))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private UserAiCreditResponseDTO toDto(UserAiCredit account) {
        return UserAiCreditResponseDTO.builder()
                .userId(account.getUserId())
                .monthlyAllocation(account.getMonthlyAllocation())
                .availableBalance(account.getAvailableBalance())
                .reservedBalance(account.getReservedBalance())
                .cycleStart(account.getCycleStart())
                .cycleEnd(account.getCycleEnd())
                .build();
    }

    private AiCreditTransactionResponseDTO toDto(AiCreditTransaction tx) {
        return AiCreditTransactionResponseDTO.builder()
                .id(tx.getId())
                .requestId(tx.getRequestId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .capability(tx.getCapability())
                .modelKey(tx.getModelKey())
                .tier(tx.getTier())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private void requireAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new AuthException("Admin privileges required", HttpStatus.FORBIDDEN);
        }
    }
}
