package org.acme.rental.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.billing.Invoice;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class BillingService {

    @Incoming("invoices")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public void processInvoice(Invoice invoice) {
        System.out.println("Processing received invoice: " + invoice);
    }
}
