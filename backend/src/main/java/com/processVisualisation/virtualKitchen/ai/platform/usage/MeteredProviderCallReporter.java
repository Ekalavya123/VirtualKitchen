package com.processVisualisation.virtualKitchen.ai.platform.usage;

import com.processVisualisation.virtualKitchen.ai.provider.ProviderCallReporter;
import org.springframework.stereotype.Component;

/** Records a provider client's side calls with {@link AiUsageMeter}, against the request running on this thread. */
@Component
public class MeteredProviderCallReporter implements ProviderCallReporter {

    @Override
    public void report(ProviderCall call) {
        AiUsageMeter.recordCall(AiCallRecord.builder()
                .purpose(call.purpose())
                .provider(call.provider())
                .providerModelId(call.providerModelId())
                .startedAt(call.startedAt())
                .endedAt(call.endedAt())
                .durationMs(call.durationMs())
                .success(call.success())
                .inputChars(call.inputChars())
                .totalTokens(call.totalTokens())
                .errorType(call.errorType())
                .errorMessage(call.errorMessage())
                .build());
    }
}
