package io.github.micha1805.llmgateway.chat;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ProviderRegistry {

    private final Map<ProviderId, ChatProvider> providers;

    public ProviderRegistry(Collection<ChatProvider> providers) {
        Map<ProviderId, ChatProvider> mapped = new EnumMap<>(ProviderId.class);
        for (ChatProvider provider : providers) {
            ChatProvider previous = mapped.put(provider.id(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate provider registration for '" + provider.id().id() + "'.");
            }
        }
        if (mapped.size() != ProviderId.values().length) {
            throw new IllegalStateException("Expected " + ProviderId.values().length
                    + " providers but found " + mapped.size() + ".");
        }
        this.providers = Map.copyOf(mapped);
    }

    public List<ChatProvider> all() {
        return Arrays.stream(ProviderId.values()).map(providers::get).toList();
    }

    public ChatProvider requireAvailable(ProviderId id) {
        ChatProvider provider = providers.get(id);
        if (provider == null || !provider.available()) {
            String reason = provider == null
                    ? "Unknown provider '" + id.id() + "'."
                    : provider.unavailableReason();
            throw new ProviderUnavailableException(id, reason);
        }
        return provider;
    }
}
