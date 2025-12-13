package org.acme.rental.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Entity
public class Rental extends PanacheEntity {

    public String userId;
    public Long reservationId;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean active;

    public static Optional<Rental> findByUserAndReservationIdsOptional(
        String userId, Long reservationId) {
        return find("userId = ?1 and reservationId = ?2",
            userId, reservationId)
            .firstResultOptional();
    }

    public static List<Rental> listActive() {
        return list("active", true);
    }

    @Override
    public String toString() {
        return "Rental{" +
            "userId='" + userId + '\'' +
            ", reservationId=" + reservationId +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", active=" + active +
            ", id=" + id +
            '}';
    }
}
