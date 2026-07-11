package com.appsdeveloperblog.core.dto.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentProcessedEvent {
    private UUID orderId;
    private UUID paymentId;
}
