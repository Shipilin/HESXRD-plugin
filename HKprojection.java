import ij.ImagePlus;
import ij.gui.NewImage;
import ij.process.ImageProcessor;

/**
 *
 * @author Mikhail Shipilin
 * Contains information concerning currently treated hk-projection 
 * intensity vs hk-coordinates 
 */
public class HKprojection {
    private double lValue; // Vertical coordinate of projection in rec. space
    private int[][] data; //Final array that contains h, k, intensity
    
    private int[] hkLimits = new int[6]; // Boundary values minH maxH minK maxK minL maxL
    private int overmeasure; //Just in case if coordinates will overcome the limits of array
    private int resolution; //Number of steps in one reciprocal unit
    private ImagePlus ip; //Image of projection 
    
    public HKprojection(double[] hklLimits, double l, int res){
        lValue = l;
        resolution = res;
        overmeasure = (int)(resolution*0.1);
        if(overmeasure%2 > 0)
            overmeasure += 1;
        for(int i = 0; i < hklLimits.length; i++){
            hkLimits[i] = (int)Math.round(hklLimits[i]*resolution);
        }
        //For each value of H there are number of k values, for each of which the intensity is defined
        data = new int[Math.abs(hkLimits[0])+hkLimits[1] + overmeasure + 1][Math.abs(hkLimits[2])+hkLimits[3] + overmeasure + 1];
    } 
    
    public double getLValue(){
        return lValue;
    }
    
    protected void addDataSet(double[][] intensityArray){
        int[][] intensity = new int[intensityArray.length][intensityArray[0].length];
        for(int i = 0; i < intensityArray.length; i++){
            intensity[i][0] = (int)Math.round(intensityArray[i][0]*resolution);
            intensity[i][1] = (int)Math.round(intensityArray[i][1]*resolution);
            intensity[i][2] = (int)Math.round(intensityArray[i][2]);
        }
        int h = 0, k = 0;
        for(int j = 0; j < intensity.length; j++){
            h = intensity[j][0] + Math.abs(this.hkLimits[0]) + (int)(this.overmeasure/2);
            k = intensity[j][1] + Math.abs(this.hkLimits[2]) + (int)(this.overmeasure/2);
            if (this.data[h][k] < intensity[j][2]){
                this.data[h][k] = intensity[j][2];
            }
        }
    }
    
    protected double calculateMeanIntensity(){
        double meanIntensity = 0;
        int occupiedPixelCounter = 0;
        for(int i = 0; i < this.data.length; i++){
            for(int j = 0; j < this.data[i].length; j++){
                if(this.data[i][j] > 0){
                    meanIntensity += this.data[i][j];
                    occupiedPixelCounter++;
                }
            }
        }
        return meanIntensity/occupiedPixelCounter;
    }
    
    protected ImageProcessor getProjectionProcessor(){
        String l = String.format("%.2f", this.lValue);
        ip = NewImage.createFloatImage("in-plane projection at L = " + l, 
                                        Math.abs(this.hkLimits[0])+this.hkLimits[1] + this.overmeasure + 1, 
                                        Math.abs(this.hkLimits[2])+this.hkLimits[3] + this.overmeasure + 1, 
                                        1, 
                                        NewImage.FILL_BLACK); 
        ImageProcessor hkProcessor = ip.getProcessor();
        
        double meanInt = this.calculateMeanIntensity();
        for(int i = 0; i < this.data.length; i++){
            int kCounter = 0;
            for(int j = this.data[i].length - 1; j >= 0; j--){
                if(this.data[i][j] < meanInt)
                    hkProcessor.putPixelValue(i, kCounter, 0/*meanInt*/);
                else
                    hkProcessor.putPixelValue(i, kCounter, this.data[i][j]);
                kCounter++;
            }
        }
        return ip.getProcessor();
    } 
    
    public static HKprojection sumUpProjections(HKprojection[] hkStack, double l){
        HKprojection hkRes = hkStack[0];
        hkRes.lValue = l;
        for(int i = 1; i < hkStack.length; i++)
        {
            for(int j = 0; j < hkStack[i].data.length; j++){
                for(int k = 0; k < hkStack[i].data[j].length; k++){
                    hkRes.data[j][k] += hkStack[i].data[j][k];
                    //hkRes.data[j][k] = hkRes.data[j][k]/2;
                }
            }
        }
        return hkRes;
    }
    
}
