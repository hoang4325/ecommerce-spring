package com.example.ecommerce.notificationservice.repository;

import com.example.ecommerce.notificationservice.dto.NotificationSearchCriteria;
import com.example.ecommerce.notificationservice.entity.Notification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> byCriteria(NotificationSearchCriteria criteria) {
        return (root, query, builder) -> {
            if (criteria == null) {
                return builder.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();
            if (criteria.type() != null) {
                predicates.add(builder.equal(root.get("type"), criteria.type()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), criteria.userId()));
            }
            if (criteria.orderId() != null) {
                predicates.add(builder.equal(root.get("orderId"), criteria.orderId()));
            }
            if (criteria.paymentId() != null) {
                predicates.add(builder.equal(root.get("paymentId"), criteria.paymentId()));
            }

            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
