package org.acme.reservation.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public class Rental {

    @NotBlank(message = "Rental ID cannot be blank")
    private final String id;

    @NotBlank(message = "User ID cannot be blank")
    private final String userId;

    @NotNull(message = "Reservation ID cannot be null")
    @Positive(message = "Reservation ID must be positive")
    private final Long reservationId;

    @NotNull(message = "Start date cannot be null")
    private final LocalDate startDate;

    public Rental(String id, String userId, Long reservationId,
                  LocalDate startDate) {
        this.id = id;
        this.userId = userId;
        this.reservationId = reservationId;
        this.startDate = startDate;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    @Override
    public String toString() {
        return "Rental{" +
            "id=" + id +
            ", userId='" + userId + '\'' +
            ", reservationId=" + reservationId +
            ", startDate=" + startDate +
            '}';
    }
}
