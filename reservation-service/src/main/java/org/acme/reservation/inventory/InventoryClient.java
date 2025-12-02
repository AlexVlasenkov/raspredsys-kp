package org.acme.reservation.inventory;

import java.util.List;

public interface InventoryClient {
    // получение всех автомобилей. Служба инвентаризации
    List<Car> allCars();
}
