package com.example.restaurant_saas.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NavNotificationStatusResponse {
    private boolean kitchen;
    private boolean tablesItemReady;
    private boolean tablesCallWaiter;
    private boolean tablesRequestBill;
    private boolean checkoutRequestBill;
    private boolean checkoutReadyToClose;
}
