package com.appsdeveloperblog.core.dto.commands;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ApproveOrderCommand {
    private UUID orderId;

    public ApproveOrderCommand(UUID orderId) {
        this.orderId = orderId;
    }

    public ApproveOrderCommand() {
    }
}
