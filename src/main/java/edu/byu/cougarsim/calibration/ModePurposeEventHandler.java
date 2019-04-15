package edu.byu.cougarsim.calibration;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ModePurposeEventHandler implements ActivityEndEventHandler, ActivityStartEventHandler,
        PersonDepartureEventHandler {

    private Map<Id<Person>, ActivityEndEvent> lastActivityMap = new HashMap<>();
    private Map<Id<Person>, String> legModeMap = new HashMap<>();
    private Map<String, Map<String, Integer>> purposeModeMap = new HashMap<>();

    private String[] purposes = new String[]{"hbw", "hbo", "nhb"};
    private ArrayList<String> modes = new ArrayList<>();

    @Inject
    public ModePurposeEventHandler () { }

    /**
     * When the person ends their previous activity, stash the end event in a map.
     * @param event
     */
    @Override
    public void handleEvent(ActivityEndEvent event) {
        lastActivityMap.put(event.getPersonId(), event);
    }

    /**
     * When the person leaves, stash their travel mode in another map
     * @param event
     */
    @Override
    public void handleEvent(PersonDepartureEvent event) {
        String mode = event.getLegMode();
        legModeMap.put(event.getPersonId(), mode);
    }

    /**
     * When the person begins their next activity, determine the trip purpose and increment
     * the appropriate purpose-mode bin.
     * @param event
     */
    @Override
    public void handleEvent(ActivityStartEvent event) {
        String purpose = getTripPurpose(lastActivityMap.get(event.getPersonId()), event);
        String mode = legModeMap.get(event.getPersonId());
        if(!modes.contains(mode)) {modes.add(mode);}

        // Update map; if this is our first time seeing the purpose-mode combo, we need to
        // fill with an empty map, and then with a zero value for the purpose-mode key.
        purposeModeMap.putIfAbsent(purpose, new HashMap<>());
        purposeModeMap.get(purpose).putIfAbsent(mode, 0);
        purposeModeMap.get(purpose).merge(mode, 1, Integer::sum); // update by 1

        //remove trip info from lookups
        lastActivityMap.remove(event.getPersonId());
        legModeMap.remove(event.getPersonId());
    }

    /**
     * Get the trip purpose based on the previous activity and the next activity
     * @param endEvent
     * @param startEvent
     * @return
     */
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

    public void writePurposeModeCounts(File file) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write("Purpose, " + String.join(", ", modes) + "\n");
        for(String purpose:purposes) {
            writer.write(purpose);
            for(String mode:modes) {
                writer.write(", " + purposeModeMap.get(purpose).getOrDefault(mode,0));
            }
            writer.write("\n");
        }
        writer.close();
    }


    /**
     * Reset the maps and reinitialize to zero.
     * @param iteration
     */
    public void reset(Integer iteration) {
        lastActivityMap.clear();
        legModeMap.clear();
        for(String purpose:purposes) {
            for(String mode:modes) {
                purposeModeMap.get(purpose).replace(mode, 0);
            }
        }
    }
}
