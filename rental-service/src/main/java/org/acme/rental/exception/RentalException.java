package org.acme.rental.exception;

public class RentalException extends RuntimeException {

    public RentalException(String message) {
        super(message);
    }

    public RentalException(String message, Throwable cause) {
        super(message, cause);
    }
}
