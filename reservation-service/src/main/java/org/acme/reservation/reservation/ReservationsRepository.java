package org.acme.reservation.reservation;

import java.util.List;

// сохранение бронирований авто
public interface ReservationsRepository {

    List<Reservation> findAll();

    Reservation save(Reservation reservation);
}
