package com.example.ecommerce.notificationservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import com.example.ecommerce.notificationservice.entity.NotificationStatus;
import com.example.ecommerce.notificationservice.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification_repository;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "eureka.client.enabled=false"
})
class NotificationRepositoryTests {

    @Autowired
    private NotificationRepository repository;

    @Test
    void savesAndFindsById() {
        Notification notification = repository.saveAndFlush(sampleSent(10L, 1000L, 2000L));

        assertThat(notification.getId()).isNotNull();
        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(repository.findById(notification.getId())).contains(notification);
    }

    @Test
    void filtersByTypeStatusUserIdOrderIdAndPaymentId() {
        Notification matching = repository.saveAndFlush(sampleFailed(10L, 1000L, 2000L));
        repository.saveAndFlush(sampleSent(11L, 1001L, 2001L));

        NotificationSearchCriteria criteria = new NotificationSearchCriteria(
            NotificationType.PAYMENT_FAILED,
            NotificationStatus.FAILED,
            10L,
            1000L,
            2000L
        );

        assertThat(repository.findAll(NotificationSpecifications.byCriteria(criteria)))
            .containsExactly(matching);
    }

    @Test
    void treatsNullCriteriaAsNoFilters() {
        Notification first = repository.saveAndFlush(sampleSent(10L, 1000L, 2000L));
        Notification second = repository.saveAndFlush(sampleFailed(11L, 1001L, 2001L));

        assertThat(repository.findAll(NotificationSpecifications.byCriteria(null)))
            .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void sortsNewestFirstByCreatedAt() throws InterruptedException {
        Notification older = repository.saveAndFlush(sampleSent(10L, 1000L, 2000L));
        Thread.sleep(5);
        Notification newer = repository.saveAndFlush(sampleSent(11L, 1001L, 2001L));

        assertThat(repository.findAll(
            NotificationSpecifications.byCriteria(null),
            Sort.by(Sort.Direction.DESC, "createdAt")
        ))
            .containsExactly(newer, older);
    }

    private static Notification sampleSent(Long userId, Long orderId, Long paymentId) {
        return Notification.sentEmail(
            userId,
            orderId,
            paymentId,
            NotificationType.ORDER_COMPLETED,
            "customer@example.com",
            "Order completed",
            "Your order is complete"
        );
    }

    private static Notification sampleFailed(Long userId, Long orderId, Long paymentId) {
        return Notification.failedEmail(
            userId,
            orderId,
            paymentId,
            NotificationType.PAYMENT_FAILED,
            "customer@example.com",
            "Payment failed",
            "Payment could not be completed",
            "SMTP rejected recipient"
        );
    }
}
