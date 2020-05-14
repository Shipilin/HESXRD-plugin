/**
 *
 * @author Mikhail Shipilin
 * Contains information concerning currently treated CTR or SR e.g. 
 * intensity, structure factors, error bars, coordinates etc. 
 */
public class DiffractionRod {
    // <editor-fold defaultstate="collapsed" desc="Class for complete information on single rod">
    private final String[] VALUES = {"H", "K", "L", "INT", "STR", "ERR"};
    private double[] h, k, l; //Reciprocal coordinates along the rod (in rec. units)
    private double[] intIntensities; //Integrated intensities along the rod
    private double[] structureFactors; //Structure factors along the rod
    private double[] errorBars; //Error bars along the rod
    
    protected int[] x, z; // Pixel positions corresponding to each data set along the rod
                        // (from top left corner)
    protected String[] fittingFunctions; //Fitting functions for each profile
    protected double[][] inPlaneRodProfiles; //Current rod profile intensity data:
                                           //Intensity data for different rotational positions.
                                           //2D array will consist of the rows starting with L_value and 
                                           //with "summed line intensities" for every image
    protected double[][] fittedInPlaneRodProfiles; //Those of inPlaneRodProfiles that were succesfully fitted
    
    /**
     * Allocates the object ROD with given number of data points
     */
    public DiffractionRod(int numberOfPoints, int numberOfImages){
        intIntensities = new double[numberOfPoints];
        structureFactors = new double[numberOfPoints];
        errorBars = new double[numberOfPoints];
        h = new double[numberOfPoints];
        k = new double[numberOfPoints];
        l = new double[numberOfPoints];
        x = new int[numberOfPoints];
        z = new int[numberOfPoints];
        fittingFunctions = new String[numberOfPoints];
        inPlaneRodProfiles = new double[numberOfPoints][numberOfImages+1];
        fittedInPlaneRodProfiles = new double[numberOfPoints][numberOfImages+1];
    }
    
    public double getValue(String name, int position){
        int valueNumber = 999;
        for(int i = 0; i < VALUES.length; i++){
            if(VALUES[i].equals(name)){
                valueNumber = i;
                break;
            }
        }
        double value = 0;
        switch(valueNumber){
            case 0 /*H*/:
                value = h[position];
                break;
            case 1 /*K*/:
                value = k[position];
                break;
            case 2 /*L*/:
                value = l[position];
                break;
            case 3 /*INT*/:
                value = intIntensities[position];
                break;
            case 4 /*STR*/:
                value = structureFactors[position];
                break;
            case 5 /*ERR*/:
                value = errorBars[position];
                break;
        }
        return value;
    }
    
    public double[] getValueSet(String name){
        int valueNumber = 999;
        for(int i = 0; i < VALUES.length; i++){
            if(VALUES[i].equals(name)){
                valueNumber = i;
                break;
            }
        }
        double[] valueSet = null;
        switch(valueNumber){
            case 0 /*H*/:
                valueSet = h;
                break;
            case 1 /*K*/:
                valueSet = k;
                break;
            case 2 /*L*/:
                valueSet = l;
                break;
            case 3 /*INT*/:
                valueSet = intIntensities;
                break;
            case 4 /*STR*/:
                valueSet = structureFactors;
                break;
            case 5 /*ERR*/:
                valueSet = errorBars;
                break;
        }
        return valueSet;
    }
    
    /**
     * Sets the corresponding value of ROD object at the specified point
     * To avoid mistakes better to use WriteValues function which 
     * sets the whole bunch of values for one data point
     * @param name - value name (H, K, L, INT, STR, ERR)
     * @param position - number of data point along the rod
     * @param value
     */
    protected void WriteValue(String name, int position, double value) throws Exception{
        int valueNumber = 999;
        for(int i = 0; i < VALUES.length; i++){
            if(VALUES[i].equals(name)){
                valueNumber = i;
                break;
            }
        }
        switch(valueNumber){
            case 0 /*H*/:
                h[position] = value;
                break;
            case 1 /*K*/:
                k[position] = value;
                break;
            case 2 /*L*/:
                l[position] = value;
                break;
            case 3 /*INT*/:
                intIntensities[position] = value;
                break;
            case 4 /*STR*/:
                structureFactors[position] = value;
                break;
            case 5 /*ERR*/:
                errorBars[position] = value;
                break;
            default:
                throw new Exception("Can't write the value " + name + " at position " + position + " to ROD object");
        }
    }
    
    /**
     * Writes the set of data values for the specified point along the rod
     * [H, K, L, IntegratedIntensity, StructureFactor, Error]
     * @param position
     * @param dataSet - values array [H, K, L, INT, STR, ERR]
     * @throws Exception 
     */
    protected void WriteValues(int position, double[] dataSet) throws Exception{
        if(dataSet.length == this.VALUES.length){
            for(int i = 0; i < this.VALUES.length; i++){
                WriteValue(this.VALUES[i], position, dataSet[i]);
            }
        }
        else
            throw new Exception("Wrong number of values in the set for ROD object at position " + position);
    }
}
