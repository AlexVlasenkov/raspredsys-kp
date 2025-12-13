package org.acme.rental.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.acme.rental.entity.Rental;

import java.time.LocalDate;

@ApplicationScoped
public class RentalService {

    @Transactional(Transactional.TxType.REQUIRED)
    public Rental createRental(String userId, Long reservationId, LocalDate reservationDate) {
        Rental rental = new Rental();
        rental.userId = userId;
        rental.reservationId = reservationId;
        rental.startDate = reservationDate;
        rental.active = !reservationDate.isAfter(LocalDate.now());

        rental.persist();

        return rental;
    }
}
