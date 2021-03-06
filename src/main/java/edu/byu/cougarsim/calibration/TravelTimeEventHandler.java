package edu.byu.cougarsim.calibration;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TravelTimeEventHandler implements ActivityEndEventHandler, ActivityStartEventHandler {

    private Map<Id<Person>, ActivityEndEvent> lastActivityMap = new HashMap<>();
    private Map<String, CountBin> travelTimeBins = new HashMap<>();
    private String[] purposes = new String[]{"hbw", "hbo", "nhb"};

    private Integer totalBins = 20;

    @Inject
    public TravelTimeEventHandler() {
        for(String purpose:purposes) {
            travelTimeBins.put(purpose, new CountBin(totalBins));
        }
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {
        lastActivityMap.put(event.getPersonId(), event);
    }

    @Override
    public void handleEvent(ActivityStartEvent startEvent) {
        ActivityEndEvent endEvent = lastActivityMap.get(startEvent.getPersonId());
        String tripPurpose = getTripPurpose(endEvent, startEvent);

        //Get the trip time as a bin in the timeBins vector, top-coded to maximum bin
        double tripTime = (startEvent.getTime() - endEvent.getTime()) / 60.;
        if(tripTime > (totalBins * 5) - 1){tripTime = (totalBins * 5) - 1;}
        int bin = (int) (tripTime / 5);

        CountBin bins = travelTimeBins.get(tripPurpose);
        bins.updateBin(bin);
        travelTimeBins.replace(tripPurpose, bins);
        lastActivityMap.remove(startEvent.getPersonId());
    }

    private String getTripPurpose(ActivityEndEvent endEvent, ActivityStartEvent startEvent){
        String lastActivity = endEvent.getActType();
        String activity = startEvent.getActType();
        String purpose = "nhb";

        if(lastActivity.equals("home") & activity.equals("work")) {
            purpose = "hbw";
        } else if(lastActivity.equals("work") & activity.equals("home")){
            purpose = "hbw";
        } else if(lastActivity.equals("home") | activity.equals("home")){
            purpose = "hbo";
        }
        return purpose;
    }

    public void writeTimeBins(File timeBinFile) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(timeBinFile));
        writer.write("Purpose");

        for(Integer i = 0; i < totalBins; i++) {
           writer.write(", " + i * 5);
        }
        writer.write("\n");

        for(String purpose: purposes){
            writer.write(purpose);
            CountBin bins = travelTimeBins.get(purpose);
            bins.writeBins(writer);
        }
        writer.close();

    }

    public void reset(Integer iteration) {
        lastActivityMap.clear();
        refreshBins();
    }

    private void refreshBins() {
        for(String purpose: purposes){
           CountBin bins = travelTimeBins.get(purpose);
           bins.clearBins();
        }

    }
}
