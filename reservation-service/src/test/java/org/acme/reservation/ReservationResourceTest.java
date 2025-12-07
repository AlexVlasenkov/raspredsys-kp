package org.acme.reservation;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.acme.reservation.entity.Reservation;
import org.acme.reservation.rest.ReservationResource;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.time.LocalDate;

@QuarkusTest
public class ReservationResourceTest {

    @TestHTTPEndpoint(ReservationResource.class)
    @TestHTTPResource
    URL reservationResource;

    @TestHTTPEndpoint(ReservationResource.class)
    @TestHTTPResource("availability")
    URL availability;

    @Test
    public void testResourceAuthentication() {
        String startDate = "2025-01-01";
        String endDate = "2026-01-01";

        Reservation reservation = new Reservation();
        reservation.carId = 12345L;
        reservation.startDay = LocalDate
                .parse(startDate);
        reservation.endDay = LocalDate
                .parse(endDate);

        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .body(reservation)
                .when()
                .post(reservationResource)
                .then()
                .statusCode(401);

        RestAssured.given()
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .when().get(availability)
                .then().statusCode(401);

        // Submit the reservation
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(reservation)
                .when().post(reservationResource)
                .then().statusCode(401);

        // Verify that this car doesn't show as available anymore
        RestAssured.given()
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .when().get(availability)
                .then().statusCode(401);
    }

}
