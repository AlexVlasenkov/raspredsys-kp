package org.acme.rental.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.rental.billing.Invoice;
import org.acme.rental.entity.Rental;
import org.acme.rental.exception.RentalException;
import org.acme.rental.mq.InvoiceProcessingStatus;
import org.acme.rental.mq.ProcessingStatus;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BillingService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Inject
    RentalService rentalService;

    /**
     *
     * Сервис обработки счётов.
     * Сервис отсылает статус платежа при его поступлении
     *
     * @param invoice счёт на оплату
     * @return результат обработки
     */
    @Incoming("invoices")
    @Outgoing("invoice-processing-status")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 100)
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Transactional
    public InvoiceProcessingStatus processInvoice(Invoice invoice) {
        log.info("Processing invoice start: invoice = {}", invoice);
        Rental rental = null;
        try {
            rental = process(invoice);
            InvoiceProcessingStatus processingStatus = new InvoiceProcessingStatus(rental, invoice, ProcessingStatus.OK);
            if (invoice.reservation.carId % 2 == 0) {
                throw new RentalException("Аренду данного автомобиля нельзя оплатить");
            }

            log.info("Processing invoice end: status = {}", processingStatus);
            return processingStatus;
        } catch (RentalException e) {
            log.error("Processing invoice failure", e);
            return new InvoiceProcessingStatus(rental, invoice, ProcessingStatus.FAILURE);
        }
    }

    public Rental process(Invoice invoice) throws RentalException {
        try {
            return rentalService.createRental(invoice.reservation.userId, invoice.reservation.carId, invoice.reservation.startDay);
        } catch (Exception e) {
            throw new RentalException("Error saving invoice", e);
        }
    }
}
