package org.acme.reservation.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.acme.reservation.billing.Invoice;
import org.acme.reservation.constant.InvoiceConstant;
import org.acme.reservation.entity.Reservation;
import org.acme.reservation.entity.ReservationState;
import org.acme.reservation.mq.InvoiceProcessingStatus;
import org.acme.reservation.mq.ProcessingStatus;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class InvoiceService {

    @Inject
    Validator validator;
    
    private final Logger log = LoggerFactory.getLogger(InvoiceService.class.getName());

    @Inject
    @Channel("invoices")
    MutinyEmitter<Invoice> invoiceEmitter;

    public Uni<Void> sendReservationInvoice(Reservation reservation) {
        Invoice invoice = new Invoice(reservation, computePrice(reservation));
        
        Set<ConstraintViolation<Invoice>> violations = validator.validate(invoice);
        
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
                
            return Uni.createFrom().failure(new IllegalArgumentException(
                "Invoice validation failed: " + errorMessage
            ));
        }
        
        return invoiceEmitter.send(invoice)
                .onFailure()
                .invoke(throwable ->
                        Log.errorf("Couldn't create invoice for %s. %s%n",
                                reservation, throwable.getMessage()));
    }

    @Incoming("invoice-processing-status")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @WithTransaction
    public Uni<Reservation> processInvoicePaymentStatus(InvoiceProcessingStatus processingStatus) {
        // входящее
        Set<ConstraintViolation<InvoiceProcessingStatus>> violations = 
            validator.validate(processingStatus);
        
        if (!violations.isEmpty()) {
            String errorMessage = violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
                
            log.error("Invalid InvoiceProcessingStatus: {}", errorMessage);
            return Uni.createFrom().failure(new IllegalArgumentException(
                "InvoiceProcessingStatus validation failed: " + errorMessage
            ));
        }
        
        log.info("Processing invoice payment status start: paymentStatus = {}", processingStatus);
        
        return Reservation.findByCarIdAndUserId(
                processingStatus.getPreviousPayload().reservation.carId, 
                processingStatus.getPreviousPayload().reservation.userId
            )
            .onItem().ifNotNull()
            .invoke(reservation -> {
                if (!ProcessingStatus.OK.equals(processingStatus.getStatus())) {
                    reservation.state = ReservationState.DECLINED;
                    log.info("Reservation {} declined due to payment failure", reservation.id);
                } else {
                    reservation.state = ReservationState.ACTIVE;
                    log.info("Reservation {} activated", reservation.id);
                }
            })
            .onFailure()
            .retry().atMost(3)
            .onFailure()
            .invoke(ex -> log.error("Processing invoice payment status failed", ex))
            .onItem().invoke(reservation -> 
                log.info("Processing invoice payment status end for reservation {}", reservation.id)
            );
    }

    private double computePrice(Reservation reservation) {
        return (ChronoUnit.DAYS.between(reservation.startDay,
                reservation.endDay) + 1) * InvoiceConstant.STANDARD_RATE_PER_DAY;
    }
}