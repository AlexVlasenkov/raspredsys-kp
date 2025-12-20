package org.acme.rental.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.rental.billing.Invoice;
import org.acme.rental.entity.Rental;
import org.acme.rental.exception.RentalException;
import org.acme.rental.mq.InvoiceProcessingStatus;
import org.acme.rental.mq.ProcessingStatus;
import org.acme.rental.mq.ReservationCancelledEvent;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@ApplicationScoped
public class BillingService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    RentalService rentalService;

    @Inject
    @Channel("reservation-cancelled")
    org.eclipse.microprofile.reactive.messaging.Emitter<ReservationCancelledEvent> cancellationEmitter;

    /**
     * Сервис обработки счётов.
     * Сервис отсылает статус платежа при его поступлении
     */
    @Incoming("invoices")
    @Outgoing("invoice-processing-status")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 100)
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Transactional
    public InvoiceProcessingStatus processInvoice(Invoice invoice) {
        String messageId = UUID.randomUUID().toString();
        log.info("Processing invoice start [messageId: {}]: invoice = {}", messageId, invoice);
        
        Rental rental = null;
        Long reservationId = invoice.reservation.carId;
        
        try {
            rental = process(invoice);

            if (invoice.reservation.carId % 2 == 0) {
                throw new RentalException("Аренду автомобилей с четным ID нельзя оплатить");
            }

            InvoiceProcessingStatus processingStatus = 
                new InvoiceProcessingStatus(rental, invoice, ProcessingStatus.OK);
            
            log.info("Processing invoice successful [messageId: {}]: status = {}", 
                     messageId, processingStatus);
            return processingStatus;
            
        } catch (RentalException e) {
            log.error("Processing invoice failed [messageId: {}]: {}", messageId, e.getMessage());
            
            // flag
            if (rental != null) {
                log.info("Compensating: deleting rental {} for failed payment", rental.id);
                rentalService.deleteRental(rental.id);
            }
            
            // событие отмены бронирования
            sendReservationCancelledEvent(reservationId, "PAYMENT_FAILED: " + e.getMessage());
            
            return new InvoiceProcessingStatus(null, invoice, ProcessingStatus.FAILURE);
            
        } catch (Exception e) {
            log.error("Unexpected error processing invoice [messageId: {}]", messageId, e);
            
            // компенсируем
            if (rental != null) {
                log.info("Compensating: deleting rental {} due to unexpected error", rental.id);
                rentalService.deleteRental(rental.id);
            }
            
            sendReservationCancelledEvent(reservationId, "SYSTEM_ERROR: " + e.getMessage());
            
            return new InvoiceProcessingStatus(null, invoice, ProcessingStatus.FAILURE);
        }
    }

    public Rental process(Invoice invoice) throws RentalException {
        try {
            log.info("Processing payment for reservation (carId: {}, userId: {})", 
                     invoice.reservation.carId, invoice.reservation.userId);
            
            Thread.sleep(100);
            
            return rentalService.createRental(
                invoice.reservation.userId, 
                invoice.reservation.carId,
                invoice.reservation.startDay
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RentalException("Payment processing interrupted", e);
        } catch (Exception e) {
            throw new RentalException("Error processing payment and creating rental", e);
        }
    }

    private void sendReservationCancelledEvent(Long reservationId, String reason) {
        try {
            ReservationCancelledEvent event = new ReservationCancelledEvent(
                reservationId, 
                reason,
                System.currentTimeMillis()
            );
            cancellationEmitter.send(event);
            log.info("Sent reservation cancelled event for reservation {}", reservationId);
        } catch (Exception e) {
            log.error("Failed to send reservation cancelled event", e);
        }
    }

    @Incoming("reservation-cancelled")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Transactional
    public void handleReservationCancelled(ReservationCancelledEvent event) {
        log.info("Processing reservation cancelled event: {}", event);
        
        try {
            log.warn("Cannot properly cancel rental: rental stores carId instead of reservationId. " +
                    "Reservation {} cancellation event ignored.", event.getReservationId());
            
        } catch (Exception e) {
            log.error("Failed to process reservation cancelled event", e);
        }
    }
}