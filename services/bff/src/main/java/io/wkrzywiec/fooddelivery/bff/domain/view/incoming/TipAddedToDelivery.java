package io.wkrzywiec.fooddelivery.bff.domain.view.incoming;

import io.wkrzywiec.fooddelivery.commons.event.IntegrationMessageBody;

import java.math.BigDecimal;

public record TipAddedToDelivery(String orderId, int version, BigDecimal tip, BigDecimal total) implements IntegrationMessageBody {
}
