package com.example.ecommerce.orderservice.client.inventory;

import com.example.ecommerce.orderservice.exception.InventoryServiceUnavailableException;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestClientInventoryReservationClient implements InventoryReservationClient {

    private static final String ROLES_HEADER = "X-User-Roles";
    private static final String SERVICE_ROLE = "SERVICE";

    private final RestClient inventoryServiceRestClient;

    public RestClientInventoryReservationClient(
        @Qualifier("inventoryServiceRestClient") RestClient inventoryServiceRestClient
    ) {
        this.inventoryServiceRestClient = inventoryServiceRestClient;
    }

    @Override
    public InventoryReservationResult reserve(Long orderId, List<InventoryReservationItem> items) {
        try {
            return bodyOrUnavailable(inventoryServiceRestClient.post()
                .uri("/api/inventory/reservations")
                .headers(RestClientInventoryReservationClient::applyServiceRole)
                .body(new InventoryReservationRequest(orderId, items))
                .retrieve()
                .body(InventoryReservationResult.class));
        } catch (RestClientException ex) {
            throw new InventoryServiceUnavailableException();
        }
    }

    @Override
    public InventoryReservationResult release(Long orderId) {
        try {
            return bodyOrUnavailable(inventoryServiceRestClient.post()
                .uri("/api/inventory/reservations/{orderId}/release", orderId)
                .headers(RestClientInventoryReservationClient::applyServiceRole)
                .retrieve()
                .body(InventoryReservationResult.class));
        } catch (RestClientException ex) {
            throw new InventoryServiceUnavailableException();
        }
    }

    private static InventoryReservationResult bodyOrUnavailable(InventoryReservationResult result) {
        if (result == null) {
            throw new InventoryServiceUnavailableException();
        }

        return result;
    }

    private static void applyServiceRole(HttpHeaders headers) {
        headers.set(ROLES_HEADER, SERVICE_ROLE);
    }
}
