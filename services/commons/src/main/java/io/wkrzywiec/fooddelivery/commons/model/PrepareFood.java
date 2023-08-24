package io.wkrzywiec.fooddelivery.commons.model;

import io.wkrzywiec.fooddelivery.commons.event.DomainMessageBody;

public record PrepareFood(String orderId, int version) implements DomainMessageBody {
}
