package edu.byu.cougarsim.calibration;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ModeChoiceCoefficientsUpdater {
    private final static Logger log = Logger.getLogger(ModeChoiceCoefficientsUpdater.class);
    Map<String, Double> constants = new HashMap<>();
    Map<String, Double> populationShares = new HashMap<>();

    public ModeChoiceCoefficientsUpdater() {
        this.populationShares.putIfAbsent("pt", 0.2);
        this.populationShares.putIfAbsent("car", 0.8);
    }

    public void setConstant(String mode, Double constant) {
        this.constants.putIfAbsent(mode, constant);
    }

    public void setShares(String mode, Double share){
        populationShares.putIfAbsent(mode, share);
    }

    public Map<String, Double> getConstants() {
        return constants;
    }

    /**
     * Update the constants to match the population shares
     * @param modelShares
     */
    public void updateConstants(Map<String, Double> modelShares) {
        // loop through modes
        for(String key:modelShares.keySet()) {
            Double model = modelShares.get(key);
            Double population = populationShares.get(key);
            log.info("Population share: " + population.toString() + ", Model share: " + model.toString());
            Double constant = constants.get(key);

            // Population bias adjustment
            Double newConstant = constant - Math.log(population / model);
            constants.replace(key, newConstant);
        }
    }
}
