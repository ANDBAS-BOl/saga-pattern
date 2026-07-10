package com.appsdeveloperblog.core.dto.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ProductReservationFailEvent {
    private UUID productId;
    private UUID orderId;
    private Integer productQuantity;
}
