package edu.byu.cougarsim.calibration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.router.*;
import org.matsim.core.utils.io.IOUtils;
import sun.nio.ch.IOUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalibrationControlerListener implements StartupListener, IterationEndsListener, ShutdownListener {

    public static final String FILENAME_PURPOSEMODESTATS = "purpose_and_mode.json";
    public static final String FILENAME_COEFFICIENTVALUES = "coefficient_values.csv";
    public File outFile;
    public BufferedWriter constantsOut;
    public String constantsFileName;

    private static final Logger log = Logger.getLogger(CalibrationControlerListener.class);
    HashMap<Integer, HashMap<String, HashMap<String, Integer>>> iterationPurposeCount = new HashMap<>();
    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    
    final private Population population;
    final private ControlerConfigGroup controlerConfigGroup;
    private final Provider<TripRouter> tripRouterFactory;
    private final PlanCalcScoreConfigGroup planCalcScoreConfigGroup;

    private MainModeIdentifier mainModeIdentifier;
    private StageActivityTypes stageActivities;
    private ModeChoiceCoefficientsUpdater modeUpdater;



    @Inject
    public CalibrationControlerListener(ControlerConfigGroup controlerConfigGroup, PlanCalcScoreConfigGroup planCalcScoreConfigGroup,
                                        OutputDirectoryHierarchy controlerIO,
                                        Population population1, Provider<TripRouter> tripRouterFactory) {
        this.controlerConfigGroup = controlerConfigGroup;
        this.population = population1;
        mainModeIdentifier = null;
        this.tripRouterFactory = tripRouterFactory;
        this.outFile = new File(controlerConfigGroup.getOutputDirectory(), FILENAME_PURPOSEMODESTATS);
        this.modeUpdater = new ModeChoiceCoefficientsUpdater();
        this.planCalcScoreConfigGroup = planCalcScoreConfigGroup;

        this.constantsFileName = controlerIO.getOutputFilename(FILENAME_COEFFICIENTVALUES);
        this.constantsOut = IOUtils.getBufferedWriter(constantsFileName);

    }

    /**
     * At startup, need to initialize some of the router-based trip statistics calculators
     * @param startupEvent
     */
    @Override
    public void notifyStartup(StartupEvent startupEvent) {

        TripRouter tripRouter = tripRouterFactory.get();
        this.mainModeIdentifier = tripRouter.getMainModeIdentifier();
        this.stageActivities = tripRouter.getStageActivityTypes();

        // load the mode score parameters
        Collection<String> modes = planCalcScoreConfigGroup.getAllModes();
        for(String mode: modes){
            Double constant = planCalcScoreConfigGroup.getOrCreateModeParams(mode).getConstant();
            modeUpdater.setConstant(mode, constant);
        }

        try {
            this.constantsOut.write("Iteration");
            for(String mode: modes){
                this.constantsOut.write(", " + mode);
            }
            this.constantsOut.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * At the end of each iteration, collect the mode split by trip purpose
     * @param iterationEndsEvent
     */
    @Override
    public void notifyIterationEnds(IterationEndsEvent iterationEndsEvent) {
        Integer iterationNo = iterationEndsEvent.getIteration();

        HashMap<String, HashMap<String, Integer>> tripPurpose =
                collectTripPurposeInfo(iterationEndsEvent);
        this.iterationPurposeCount.put(iterationNo, tripPurpose);

        // Update constants
        Map<String, Double> modelShares = calculateModeShares(tripPurpose.get("hbw"));
        modeUpdater.updateConstants(calculateModeShares(tripPurpose.get("hbw")));
        Map<String, Double> updatedConstants = modeUpdater.getConstants();

        log.info("Updated mode constants: ");
        log.info(gson.toJson(updatedConstants));

        try {
            this.constantsOut.write(iterationNo.toString());
            for(Double entry:updatedConstants.values()) {
                this.constantsOut.write(", " + entry);
            }
            this.constantsOut.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



    private Map<String, Double> calculateModeShares(HashMap<String, Integer> tripsByMode) {
        // get total number of trips
        Integer totalTrips = tripsByMode.values().stream().reduce(0, Integer::sum);
        Map<String, Double> modeShares = new HashMap<>();

        for(Map.Entry<String, Integer> modeTrips:tripsByMode.entrySet()) {
            Double share = (1.0 * modeTrips.getValue() / totalTrips);
            modeShares.putIfAbsent(modeTrips.getKey(), share);
        }
        log.info("Home-Based Work mode split: ");
        log.info(gson.toJson(modeShares));
        return modeShares;
    }

    /**
     * Loop through the population plans at the end of an iteration, get the main mode and the purpose of the
     * activity, and store the counts for each kind of trip in a HashMap. Note that the mode is the "planned" mode,
     * rather than the "executed" mode. As an example, the 'transit_walk' trips are coded as 'pt'.
     * @param iterationEndsEvent
     * @return A map indexed by purpose and mode, with the count of trips in the most recently selected population plans
     */
    private HashMap<String, HashMap<String, Integer>> collectTripPurposeInfo(final IterationEndsEvent iterationEndsEvent){
        HashMap<String, HashMap<String, Integer>> purposeAndModeCount = new HashMap<>();

        //Initialize dictionary
        for(String purpose: new String[] {"hbw", "hbo", "nhb"}){
            purposeAndModeCount.putIfAbsent(purpose, new HashMap<>());
        }

        // loop through population, then through the trips in the selected plan
        for(Person person : this.population.getPersons().values()){
            Plan plan = person.getSelectedPlan();
            List<TripStructureUtils.Trip> trips = TripStructureUtils.getTrips(plan, stageActivities);
            for(TripStructureUtils.Trip trip : trips){
               String mode = this.mainModeIdentifier.identifyMainMode(trip.getTripElements());
               String purpose = getTripPurpose(trip);

               purposeAndModeCount.get(purpose).merge(mode, 1, Integer::sum);
            }
        }

        return purposeAndModeCount;
    }

    /***
     * Given a trip, determine its purpose based on its origin and destination activity types.
     * @param trip
     * @return
     */
    private String getTripPurpose(TripStructureUtils.Trip trip){
        String lastActivity = trip.getOriginActivity().getType();
        String activity = trip.getDestinationActivity().getType();
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

    @Override
    public void notifyShutdown(ShutdownEvent shutdownEvent) {
        try (Writer writer = new FileWriter(outFile)){
            gson.toJson(iterationPurposeCount, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            this.constantsOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }



}
