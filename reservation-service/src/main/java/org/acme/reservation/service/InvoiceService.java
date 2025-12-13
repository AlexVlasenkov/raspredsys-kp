package org.acme.reservation.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.billing.Invoice;
import org.acme.reservation.constant.InvoiceConstant;
import org.acme.reservation.entity.Reservation;
import org.acme.reservation.entity.ReservationState;
import org.acme.reservation.mq.InvoiceProcessingStatus;
import org.acme.reservation.mq.ProcessingStatus;
import org.acme.reservation.rest.ReservationResource;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class InvoiceService {

    @Inject
    ReservationResource reservationResource;
    private final Logger log = LoggerFactory.getLogger(InvoiceService.class.getName());

    @Inject
    @Channel("invoices")
    MutinyEmitter<Invoice> invoiceEmitter;

    public Uni<Void> sendReservationInvoice(Reservation reservation) {
        return invoiceEmitter.send(
                        new Invoice(reservation, computePrice(reservation)))
                .onFailure()
                .invoke(throwable ->
                        Log.errorf("Couldn't create invoice for %s. %s%n",
                                reservation, throwable.getMessage()));
    }

    @Incoming("invoice-processing-status")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @WithTransaction
    public Uni<Reservation> processInvoicePaymentStatus(InvoiceProcessingStatus processingStatus) {
        return Uni.createFrom().deferred(() -> {
                    log.info("Processing invoice payment status start: paymentStatus = {}", processingStatus);
                    return Reservation.findByCarIdAndUserId(processingStatus.getPreviousPayload().reservation.carId, processingStatus.getPreviousPayload().reservation.userId);
                })
                .onItem().ifNotNull()
                .invoke(reservation -> {
                    if (processingStatus == null || !ProcessingStatus.OK.equals(processingStatus.getStatus())) {
                        reservation.state = ReservationState.DECLINED;
                    } else {
                        reservation.state = ReservationState.ACTIVE;
                    }
                })
                .onFailure()
                .retry().atMost(3)
                .onFailure()
                .invoke(ex -> log.info("Processing invoice payment status: failure", ex))
                .onItem().invoke(reservation -> log.info("Processing invoice payment status end: status = {}", processingStatus));
    }

    private double computePrice(Reservation reservation) {
        return (ChronoUnit.DAYS.between(reservation.startDay,
                reservation.endDay) + 1) * InvoiceConstant.STANDARD_RATE_PER_DAY;
    }
}
