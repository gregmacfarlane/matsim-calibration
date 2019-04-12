package edu.byu.cougarsim.calibration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.router.Transit;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class TransitBoardingsEventHandler implements PersonEntersVehicleEventHandler {

    TransitSchedule transitSchedule;
    Vehicles transitVehicles;
    Integer totalBoardings = 0;

    private Map<Id<Vehicle>, Id<TransitLine>> vehicleLineMap = new HashMap<>();
    private Map<Id<TransitRoute>, Id<TransitLine>> routeLineMap = new HashMap<>();
    private Map<Id<TransitLine>, Integer> transitLineBoardings = new HashMap<>();

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * The class constructor initializes the lookup maps and counter.
     * @param scenario
     */
    @Inject
    public TransitBoardingsEventHandler(Scenario scenario) {
        this.transitSchedule = scenario.getTransitSchedule();
        this.transitVehicles = scenario.getTransitVehicles();

        // Loop through all the transit lines, initializing the boardings file
        for(Map.Entry<Id<TransitLine>, TransitLine> line:transitSchedule.getTransitLines().entrySet()){
           //Loop through all of the routes on the line, and fill the route-line lookup table
           for(Map.Entry<Id<TransitRoute>, TransitRoute> route:line.getValue().getRoutes().entrySet()) {
               routeLineMap.putIfAbsent(route.getKey(), line.getKey());
               TransitRoute transitRoute = route.getValue();

               for(Map.Entry<Id<Departure>, Departure> departure : transitRoute.getDepartures().entrySet()) {
                   vehicleLineMap.put(departure.getValue().getVehicleId(), line.getKey());
               }
           }
        }
    }

    /**
     * When a person enters a vehicle, lookup to see if the vehicle is a transit vehicle. If it is,
     * then add a boarding to that transit line.
     * @param event
     */
    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        Id<Vehicle> vehicleId = event.getVehicleId();
        Id<Person> personId = event.getPersonId();
        if(transitVehicles.getVehicles().containsKey(vehicleId) & // are they boarding transit
                !personId.toString().contains("pt_") ){ // and are they not a transit driver?
            Id<TransitLine> lineId = vehicleLineMap.get(vehicleId);
            transitLineBoardings.merge(lineId, 1, Integer::sum);
            totalBoardings++;
        }
    }

    @Override
    public void reset(int iteration) {
        totalBoardings = 0;
        transitLineBoardings.clear();
    }


    public void writeLineBoardings(File boardingsFile) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(boardingsFile));
        writer.write("TransitLine, Boardings\n");
        transitLineBoardings.forEach((key, value) -> {
            try {
                writer.write(key + ", " + value + "\n");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        writer.close();
    }

}
