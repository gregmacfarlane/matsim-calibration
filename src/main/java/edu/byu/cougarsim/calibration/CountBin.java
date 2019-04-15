package edu.byu.cougarsim.calibration;

import java.io.BufferedWriter;
import java.io.IOException;

public class CountBin {
    private Integer[] bins;

    public CountBin(Integer size){
        this.bins = new Integer[size];
        for(Integer i = 0; i < bins.length; i++) {
            bins[i] = 0;
        }
    }

    public void updateBin(int index) {
        bins[index]++;
    }

    public Integer[] getBins() {
        return bins;
    }

    public void clearBins() {
        for(int i = 0; i < bins.length; i++) {
            bins[i] = 0;
        }
    }

    public void writeBins(BufferedWriter writer) throws IOException {
        for(Integer i = 0; i < bins.length; i++) {
            writer.write(", " + bins[i]);
        }
        writer.write("\n");

    }
}
