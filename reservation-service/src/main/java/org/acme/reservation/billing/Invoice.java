package org.acme.reservation.billing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.acme.reservation.entity.Reservation;

public class Invoice {

    @NotNull(message = "Reservation cannot be null")
    public Reservation reservation;

    @Positive(message = "Price must be positive")
    public double price;

    public Invoice(Reservation reservation, double price) {
        this.reservation = reservation;
        this.price = price;
    }

    @Override
    public String toString() {
        return "Invoice{" +
            "reservation=" + reservation +
            ", price=" + price +
            '}';
    }
}
