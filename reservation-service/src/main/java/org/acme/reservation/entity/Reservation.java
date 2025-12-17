package org.acme.reservation.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

@Entity
public class Reservation extends PanacheEntity {

    @NotNull(message = "Car ID cannot be null")
    @Positive(message = "Car ID must be positive")
    public Long carId;

    @NotBlank(message = "User ID cannot be blank")
    public String userId;

    @NotNull(message = "Start day cannot be null")
    @FutureOrPresent(message = "Start day must be in the future or present")
    public LocalDate startDay;

    @NotNull(message = "End day cannot be null")
    @FutureOrPresent(message = "End day must be in the future or present")
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
