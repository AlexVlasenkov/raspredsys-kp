package org.acme.reservation.mq;

import org.acme.reservation.billing.Invoice;
import org.acme.reservation.rental.Rental;

public class InvoiceProcessingStatus {

    private Rental payload;
    private Invoice previousPayload;
    private ProcessingStatus status;

    public InvoiceProcessingStatus(Rental payload, Invoice previousPayload, ProcessingStatus status) {
        this.payload = payload;
        this.previousPayload = previousPayload;
        this.status = status;
    }

    public Rental getPayload() {
        return payload;
    }

    public ProcessingStatus getStatus() {
        return status;
    }

    public Invoice getPreviousPayload() {
        return previousPayload;
    }

    public void setPreviousPayload(Invoice previousPayload) {
        this.previousPayload = previousPayload;
    }

    public void setStatus(ProcessingStatus status) {
        this.status = status;
    }

    public void setPayload(Rental payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "MessageProcessingStatus{" +
                "payload=" + payload +
                ", previousPayload=" + previousPayload +
                ", status=" + status +
                '}';
    }
}

