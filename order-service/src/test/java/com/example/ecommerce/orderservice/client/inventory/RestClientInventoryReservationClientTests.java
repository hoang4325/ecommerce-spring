package com.example.ecommerce.orderservice.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ecommerce.orderservice.exception.InventoryServiceUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientInventoryReservationClientTests {

    private MockRestServiceServer server;
    private InventoryReservationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://inventory-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientInventoryReservationClient(builder.build());
    }

    @Test
    void reserveSendsServiceRoleAndReturnsReservationResult() {
        server.expect(once(), requestTo("http://inventory-service/api/inventory/reservations"))
            .andExpect(method(POST))
            .andExpect(header("X-User-Roles", "SERVICE"))
            .andExpect(content().json("""
                {
                  "orderId": 1000,
                  "items": [
                    { "productId": 100, "quantity": 2 },
                    { "productId": 101, "quantity": 1 }
                  ]
                }
                """))
            .andRespond(withSuccess("""
                {
                  "orderId": 1000,
                  "status": "RESERVED",
                  "reservations": [
                    { "productId": 100, "quantity": 2, "status": "RESERVED" }
                  ]
                }
                """, APPLICATION_JSON));

        InventoryReservationResult result = client.reserve(1000L, List.of(
            new InventoryReservationItem(100L, 2),
            new InventoryReservationItem(101L, 1)
        ));

        assertThat(result.orderId()).isEqualTo(1000L);
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RESERVED);
        server.verify();
    }

    @Test
    void releaseSendsServiceRoleAndReturnsReservationResult() {
        server.expect(once(), requestTo("http://inventory-service/api/inventory/reservations/1000/release"))
            .andExpect(method(POST))
            .andExpect(header("X-User-Roles", "SERVICE"))
            .andRespond(withSuccess("""
                { "orderId": 1000, "status": "RELEASED" }
                """, APPLICATION_JSON));

        InventoryReservationResult result = client.release(1000L);

        assertThat(result.orderId()).isEqualTo(1000L);
        assertThat(result.status()).isEqualTo(InventoryReservationStatus.RELEASED);
        server.verify();
    }

    @Test
    void reservationRequestCopiesItemsDefensively() {
        InventoryReservationItem item = new InventoryReservationItem(100L, 2);
        List<InventoryReservationItem> items = new ArrayList<>();
        items.add(item);

        InventoryReservationRequest request = new InventoryReservationRequest(1000L, items);
        items.clear();

        assertThat(request.items()).containsExactly(item);
        assertThatThrownBy(() -> request.items().add(item)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reserveMapsServerErrorToInventoryServiceUnavailableException() {
        server.expect(once(), requestTo("http://inventory-service/api/inventory/reservations"))
            .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.reserve(1000L, List.of(new InventoryReservationItem(100L, 2))))
            .isInstanceOf(InventoryServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void releaseMapsEmptySuccessfulBodyToInventoryServiceUnavailableException() {
        server.expect(once(), requestTo("http://inventory-service/api/inventory/reservations/1000/release"))
            .andRespond(withSuccess("", APPLICATION_JSON));

        assertThatThrownBy(() -> client.release(1000L))
            .isInstanceOf(InventoryServiceUnavailableException.class);
        server.verify();
    }
}
