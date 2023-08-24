package io.wkrzywiec.fooddelivery.bff.controller;

import io.wkrzywiec.fooddelivery.bff.inbox.Inbox;
import io.wkrzywiec.fooddelivery.bff.controller.model.AddTipDTO;
import io.wkrzywiec.fooddelivery.bff.controller.model.CreateOrderDTO;
import io.wkrzywiec.fooddelivery.bff.controller.model.ResponseDTO;
import io.wkrzywiec.fooddelivery.bff.controller.model.CancelOrderDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@RestController
public class OrdersController {

    private final Inbox inbox;
    private static final String ORDERING_INBOX = "ordering-inbox";

    @PostMapping("/orders")
    ResponseEntity<ResponseDTO> createAnOrder(@RequestBody CreateOrderDTO createOrder) {
        log.info("Received request to create an order: {}", createOrder);
        if (createOrder.getId() == null) {
            var id = UUID.randomUUID().toString();
            createOrder.setId(id);
            log.info("Generated {} id for a new order", id);
        }

        inbox.storeMessage(ORDERING_INBOX + ":create", createOrder);

        return ResponseEntity.accepted().body(new ResponseDTO(createOrder.getId()));
    }

    @PatchMapping("/orders/{orderId}/status/cancel")
    ResponseEntity<ResponseDTO> cancelAnOrder(@PathVariable String orderId, @RequestBody CancelOrderDTO cancelOrderD) {
        log.info("Received request to update an '{}' order, update: {}", orderId, cancelOrderD);
        cancelOrderD.setOrderId(orderId);
        inbox.storeMessage(ORDERING_INBOX + ":cancel", cancelOrderD);

        return ResponseEntity.accepted().body(new ResponseDTO(orderId));
    }

    @PostMapping("/orders/{orderId}/tip")
    ResponseEntity<ResponseDTO> addTip(@PathVariable String orderId, @RequestBody AddTipDTO addTip) {
        log.info("Received request to add tip to '{}' an order, value: {}", orderId, addTip);
        addTip.setOrderId(orderId);
        inbox.storeMessage(ORDERING_INBOX + ":tip", addTip);

        return ResponseEntity.accepted().body(new ResponseDTO(orderId));
    }
}
