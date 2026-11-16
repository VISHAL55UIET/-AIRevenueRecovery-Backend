
package com.AIRevenueRecovery.service;

import com.AIRevenueRecovery.entity.FailureReason;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeclineCodeMapper {

    private static final Map<String, FailureReason> DECLINE_CODE_MAP =
            Map.ofEntries(

                    Map.entry("insufficient_funds", FailureReason.INSUFFICIENT_FUNDS),
                    Map.entry("card_declined", FailureReason.CARD_DECLINED),
                    Map.entry("expired_card", FailureReason.EXPIRED_CARD),
                    Map.entry("incorrect_cvc", FailureReason.INCORRECT_CVC),
                    Map.entry("do_not_honor", FailureReason.CARD_DECLINED),
                    Map.entry("processing_error", FailureReason.PROCESSING_ERROR),
                    Map.entry("authentication_required", FailureReason.AUTHENTICATION_REQUIRED)
            );

    /**
     * Map gateway decline code to internal failure reason.
     */
    public FailureReason mapDeclineCode(String declineCode) {

        if (declineCode == null || declineCode.isBlank()) {
            return FailureReason.UNKNOWN;
        }

        return DECLINE_CODE_MAP.getOrDefault(
                declineCode.toLowerCase().trim(),
                FailureReason.UNKNOWN
        );
    }
    public boolean isPermanentFailure(String declineCode) {

        if (declineCode == null) {
            return false;
        }

        return switch (declineCode.toLowerCase().trim()) {
            case "expired_card",
                 "lost_card",
                 "stolen_card",
                 "invalid_card_number",
                 "pickup_card" -> true;

            default -> false;
        };
    }

    /**
     * Check whether decline code is retryable.
     */
    public boolean isRetryable(String declineCode) {
        return !isPermanentFailure(declineCode);
    }
}
