package org.acme.reservation.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;

import java.time.LocalDate;

@Entity
public class Reservation extends PanacheEntity {

    public Long carId;
    public String userId;
    public LocalDate startDay;
    public LocalDate endDay;
    public ReservationState state;

    @PrePersist
    public void initialize() {
        state = ReservationState.DRAFT;
    }

    public static Uni<Reservation> findByCarIdAndUserId(Long carId, String userId) {
        return Reservation.find("carId = ?1 and userId = ?2", carId, userId).firstResult();
    }

    /**
     * Check if the given duration overlaps with this reservation
     * @return true if the dates overlap with the reservation, false
     * otherwise
     */
    public boolean isReserved(LocalDate startDay, LocalDate endDay) {
        return (!(this.endDay.isBefore(startDay) ||
            this.startDay.isAfter(endDay)));
    }

    @Override
    public String toString() {
        return "Reservation{" +
            "id=" + id +
            ", carId=" + carId +
            ", userId='" + userId + '\'' +
            ", startDay=" + startDay +
            ", endDay=" + endDay +
            '}';
    }
}
