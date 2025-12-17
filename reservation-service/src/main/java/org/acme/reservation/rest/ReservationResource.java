package org.acme.reservation.rest;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.reservation.entity.Reservation;
import org.acme.reservation.entity.ReservationState;
import org.acme.reservation.inventory.Car;
import org.acme.reservation.inventory.GraphQLInventoryClient;
import org.acme.reservation.inventory.InventoryClient;
import org.acme.reservation.rental.RentalClient;
import org.acme.reservation.service.InvoiceService;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Path("/reservation")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ReservationResource {

    private final InventoryClient inventoryClient;
    private final RentalClient rentalClient;

    @Inject
    jakarta.ws.rs.core.SecurityContext context;

    @Inject
    InvoiceService invoiceService;
    
    @Inject
    Validator validator;

    public ReservationResource(@GraphQLClient("inventory") GraphQLInventoryClient inventoryClient,
                               @RestClient RentalClient rentalClient) {
        this.inventoryClient = inventoryClient;
        this.rentalClient = rentalClient;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("car-rental-reservation-create")
    @WithTransaction
    public Uni<Reservation> make(Reservation reservation) {
        Set<ConstraintViolation<Reservation>> violations = validator.validate(reservation);
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
                
            return Uni.createFrom().failure(new IllegalArgumentException(
                "Reservation validation failed: " + errorMessage
            ));
        }
        
        reservation.userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : "anonymous";

        return reservation.<Reservation>persist().onItem()
                .call(persistedReservation -> {
                    Log.info("Successfully reserved reservation " + persistedReservation);

                    Uni<Void> invoiceUni = invoiceService.sendReservationInvoice(persistedReservation);

                    if (persistedReservation.startDay.equals(LocalDate.now())) {
                        return invoiceUni.chain(() ->
                                rentalClient.start(persistedReservation.userId,
                                                persistedReservation.id)
                                        .onItem().invoke(rental ->
                                                Log.info("Successfully started rental " + rental))
                                        .replaceWith(persistedReservation));
                    }
                    return invoiceUni
                            .replaceWith(persistedReservation);
                });
    }

    @GET
    @Path("/availability")
    @RolesAllowed("car-rental-car-read")
    public Uni<Collection<Car>> availability(@RestQuery LocalDate startDate,
                                             @RestQuery LocalDate endDate) {
        Uni<List<Car>> availableCarsUni = inventoryClient.allCars();
        Uni<List<Reservation>> reservationsUni = Reservation.listAll();

        return Uni.combine().all().unis(availableCarsUni, reservationsUni).with((availableCars, reservations) -> {
            Map<Long, Car> carsById = new HashMap<>();
            for (Car car : availableCars) {
                carsById.put(car.id, car);
            }

            for (Reservation reservation : reservations) {
                if (reservation.isReserved(startDate, endDate)) {
                    carsById.remove(reservation.carId);
                }
            }
            return carsById.values();
        });
    }

    @GET
    @Path("/all")
    @RolesAllowed("car-rental-reservation-read")
    public Uni<List<Reservation>> allReservations() {
        String userId = context.getUserPrincipal() != null ?
                context.getUserPrincipal().getName() : null;
        return Reservation.<Reservation>listAll()
                .onItem().transform(reservations -> reservations.stream()
                        .filter(reservation -> userId == null ||
                                userId.equals(reservation.userId))
                        .collect(Collectors.toList()));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed("car-rental-reservation-create")
    @WithTransaction
    public Uni<Response> extendReservation(@PathParam("id") Long id) {
        String currentUser = getCurrentUserId();
        
        return findReservationByIdAndUser(id, currentUser)
            .onItem().transformToUni(reservation -> {
                if (reservation == null) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("Reservation not found")
                            .build()
                    );
                }
                
                if (reservation.state != ReservationState.DRAFT && 
                    reservation.state != ReservationState.ACTIVE) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.BAD_REQUEST)
                            .entity("Cannot extend reservation in state: " + reservation.state)
                            .build()
                    );
                }
                
                if (reservation.endDay.isBefore(LocalDate.now())) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.BAD_REQUEST)
                            .entity("Cannot extend expired reservation")
                            .build()
                    );
                }
                
                LocalDate newEndDay = reservation.endDay.plusDays(1);
                
                return Reservation.update("endDay = ?1 where id = ?2", newEndDay, reservation.id)
                    .onItem().transform(rowsUpdated -> {
                        if (rowsUpdated == 0) {
                            return Response.status(Response.Status.NOT_FOUND)
                                .entity("Reservation not updated")
                                .build();
                        }
                        
                        Log.infof("Extended reservation %d to %s", reservation.id, newEndDay);
                        
                        reservation.endDay = newEndDay;
                        return Response.ok(reservation).build();
                    });
            });
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed("car-rental-reservation-create")
    @WithTransaction
    public Uni<Response> cancelReservation(@PathParam("id") Long id) {
        String currentUser = getCurrentUserId();
        
        return findReservationByIdAndUser(id, currentUser)
            .onItem().transformToUni(reservation -> {
                if (reservation == null) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.NOT_FOUND)
                            .entity("Reservation not found")
                            .build()
                    );
                }
                
                // отмена бронирования
                if (reservation.state == ReservationState.FINISHED) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.BAD_REQUEST)
                            .entity("Cannot cancel finished reservation")
                            .build()
                    );
                }
                
                if (reservation.state == ReservationState.DECLINED) {
                    return Uni.createFrom().item(
                        Response.status(Response.Status.BAD_REQUEST)
                            .entity("Reservation is already cancelled")
                            .build()
                    );
                }
                
                ReservationState oldState = reservation.state;
                
                return Reservation.update("state = ?1 where id = ?2", ReservationState.DECLINED, reservation.id)
                    .onItem().transform(rowsUpdated -> {
                        if (rowsUpdated == 0) {
                            return Response.status(Response.Status.NOT_FOUND)
                                .entity("Reservation not updated")
                                .build();
                        }
                        
                        Log.infof("Cancelled reservation %d (changed from %s to DECLINED)", 
                            reservation.id, oldState);
                        
                        // аренда началась
                        if (reservation.startDay.isBefore(LocalDate.now().plusDays(1)) || 
                            reservation.startDay.equals(LocalDate.now())) {
                            Log.warnf("Reservation %d cancelled but rental might be active", reservation.id);
                        }
                        
                        reservation.state = ReservationState.DECLINED;
                        return Response.ok(reservation).build();
                    });
            });
    }

    private String getCurrentUserId() {
        return context.getUserPrincipal() != null ? 
               context.getUserPrincipal().getName() : "anonymous";
    }

    private Uni<Reservation> findReservationByIdAndUser(Long id, String userId) {
        return Reservation.find("id = ?1 and userId = ?2", id, userId).firstResult();
    }
}