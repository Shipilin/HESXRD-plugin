/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.CurveFitter;
import ij.measure.Minimizer;
import ij.measure.ResultsTable;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Mikhail Shipilin
 */
public class RodExtractor {
    private int[][] lineCoordinates; //Coordinates of pixels along linear ROI
    private int extractionWidth, extractionStep;
            
    //Depending on the sequential order of the parameters fitting differs.
    //We will define the fitting function parameters by the order of following array,
    //putting them to the equation y = [0]*exp(-(x-[1])*(x-[1])/(2*[2]*[2])) + [3]*x + [4]
    //instead of corresponding numbers [..]
    private final String[] alphabet = {"a", "b", "c", "d", "e", "f"};//Parameters in alphabetic order
    private final String[] parameters = {"b", "c", "a", "d", "e"}; //y = b*exp(-(x-c)*(x-c)/(2*a*a)) + d*x + e
    private int fittingParametersNumber; // Number of parameters
    private String fittingFunction; // Function of the form y = b*exp(-(x-c)*(x-c)/(2*a*a)) + d*x + e
    private String operatingError; // Description of error occured durin fitting
    private double fittingError;
    private double[] fittingParametersValues; //Current values of a,b,c,d,e
    
    private DiffractionRod rod;
    private ExperimentHandler experiment;
    private ResultsTable rt; // Table containing results
    
    private JFrame pBarFrame;// Frame for progress bar
    private JProgressBar pBar;//Progress bar 0 - 100%
    
    public RodExtractor(ExperimentHandler ex) {
        fittingParametersNumber = parameters.length;
        experiment = ex;
    }
    
    private void CreateAndShowGUI(){
        //Bring window style in accordance with operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } 
        catch (Exception e) {
        }
        
        //Define parameters of progress bar 
        pBarFrame = new JFrame("Progress");
        pBarFrame.setResizable(false);
        pBarFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); 
        pBar = new JProgressBar(0, 100);//Progress bar 0 - 100%
        pBar.setStringPainted(true);
        pBar.setPreferredSize(new Dimension(170,23)); 
        
        pBarFrame.add(pBar);
        pBarFrame.pack();
        pBarFrame.setLocationByPlatform(true);
        pBarFrame.setVisible(true);
    }
    
    private void DisposeGUI(){
        pBar = null;
        pBarFrame.dispose();
    }
    
    public boolean Extract(ImagePlus imp){
        if(!DefineExtractionRelatedParameters(imp)){
            return false;
        }
        fillIntensityArray(imp);
        defineFittingVariables(parameters);
        if(doFit(imp)){
            return true; 
        }
        return false;
    }
    
    private boolean DefineExtractionRelatedParameters(ImagePlus imp){
        if(imp == null){
            return false;
        }
        Roi roi = imp.getRoi();
        if(roi == null){    
            IJ.showMessage("ROI required.");
            return false;
        }
        //User should decide what is the width of area to be extracted
        //and what step along the ROI to choose
        GenericDialog gd = new GenericDialog("Extraction options");        
        
        if((roi.getType() == 0) || (roi.isLine())){
            //If the roi is polygon selection, we will use it's rectangular bounds
            if(roi.getType() == 0){
                Rectangle r = null;
                try{
                    r = imp.getRoi().getBounds();//We will work with rectangular ROI. If it is not rectangular
                                                 //we will take the bounding rectangle.
                }
                catch(Exception e){
                    IJ.showMessage("ROI error", "Failed while ROI processing");
                    return false;
                }
                Line line = new Line(r.x + Math.round((float)r.width/2), r.y , r.x + Math.round((float)r.width/2), r.y + r.height);
                lineCoordinates = getCoordinatesAlongLine(line);
                gd.addNumericField("Extraction width (in pixels):", r.width, 0);
            }
            else{  //If ROI is linear
                lineCoordinates = getCoordinatesAlongLine(roi);        
                gd.addNumericField("Extraction width (in pixels):", 18, 0);
            }
        }
        else {
            IJ.showMessage("ROI error", "Can't work with such type of ROI\nChoose either linear or rectangular ROI.");            
            return false;
        }

        gd.addNumericField("Extraction step (in pixels):", 10, 0);
        gd.showDialog();
        if (gd.wasOKed()){
            extractionWidth = (int)gd.getNextNumber();
            extractionStep = (int)gd.getNextNumber();
        }
        
        //Number of blocks in ROI to be processed
        int numberOfSteps = Math.round((float)(lineCoordinates.length/extractionStep + 0.5));
        rod = new DiffractionRod(numberOfSteps, imp.getStackSize());
        return true;
    }
    
    private int[][] getCoordinatesAlongLine(Roi r){
        //IJ.run("Fit Spline", "straighten"); 
        FloatPolygon p = r.getFloatPolygon();
        int x1 = 0, y1 = 0, x2 = 0, y2 = 0;
        if(p.ypoints[0] > p.ypoints[1]){
            y1 = (int)p.ypoints[1];
            x1 = (int)p.xpoints[1];
            y2 = (int)p.ypoints[0];
            x2 = (int)p.xpoints[0];
        }
        else{
            y1 = (int)p.ypoints[0];
            x1 = (int)p.xpoints[0];
            y2 = (int)p.ypoints[1];
            x2 = (int)p.xpoints[1];
        }
        
        int[][] coords = new int[y2 - y1 + 1][2];
        for(int i = 0; i < y2 - y1 + 1; i++){
            if(x2 == x1){ //If line is vertical
                coords[i][0] = x1;
                coords[i][1] = y1 + i;
                continue;
            }
            if(x2 < x1){ //If line tilted to the right
                coords[i][1] = y1 + i;
                coords[i][0] = (int) (Math.abs(i*(x1 - x2)/(y2 - y1) + 0.5) + x2);
                continue;
            }
            else{ //If line tilted to the left
                coords[i][1] = y1 + i;
                coords[i][0] = (int) (Math.abs(i*(x2 - x1)/(y2 - y1) + 0.5) + x1);
            }
        }
        return coords;
    }  
    
    private void fillIntensityArray(ImagePlus imp){
        int loopCnt = 0;
        int blockCounter = 0;
        int x1 = 0, x2 = 0, y = 0;
        for(int i = 0; i < lineCoordinates.length; i++){ //For every L value in ROI (for every pixel line in vertical direction)
            for(int j = 1; j <= imp.getStackSize(); j++){ //For every image. Counter of stack starts with 1. 
                ImageProcessor img = imp.getStack().getProcessor(j); //Get image number j from stack
                //Get intensities of pixels in current row of ROI
                //We should also check if the chosen ROI width doesn't overcome the image frame
                y = lineCoordinates[i][1];
                int roiHalfWidth = 0;
                if((Math.round(extractionWidth) & 1) == 0){ //Even width of ROI
                    roiHalfWidth = Math.round((extractionWidth-1)/2);
                    if(lineCoordinates[i][0] - roiHalfWidth < 0 )
                        x1 = 0;
                    else
                        x1 = lineCoordinates[i][0] - roiHalfWidth;
                    if(lineCoordinates[i][0] + roiHalfWidth > img.getWidth())
                        x2 = img.getWidth();
                    else
                        x2 = lineCoordinates[i][0] + roiHalfWidth;
                }
                else{
                    roiHalfWidth = Math.round((extractionWidth)/2);
                    if(lineCoordinates[i][0] - roiHalfWidth < 0 )
                        x1 = 0;
                    else
                        x1 = lineCoordinates[i][0] - roiHalfWidth;
                    if(lineCoordinates[i][0] + roiHalfWidth > img.getWidth())
                        x2 = img.getWidth();
                    else
                        x2 = lineCoordinates[i][0] + roiHalfWidth - 1;//-1, because selection itself takes 1 pixel, and
                                                                      //it should be included in ROI width
                }
                    
                double[] pixels = img.getLine(x1, y, x2, y); 
                double sumIntens = 0; //Sum of pixels intensities for particular image 
                                      //inside the ROI and along the line corresponding to current L value
                                      //"summed line intensity"
                for(double d : pixels) {
                    sumIntens += d;
                }               
                rod.inPlaneRodProfiles[blockCounter][j] += Math.round((float)sumIntens);
            }
            loopCnt++;
            
            if(loopCnt == extractionStep){
                //The y'th coordinate of center of current block (counting from upper left corner)
                rod.x[blockCounter] = lineCoordinates[i - Math.round((float)extractionStep/2)][0];
                rod.z[blockCounter] = lineCoordinates[i - Math.round((float)extractionStep/2)][1];
                double[] hkl = null;
                try{
                    hkl = experiment.labToHKL( rod.x[blockCounter], rod.z[blockCounter], 0);
                }
                catch(Exception e){
                }
                rod.inPlaneRodProfiles[blockCounter][0] = hkl[2];
                blockCounter++;
                loopCnt = 0;
            }
        }        
    }  
    
    private void defineFittingVariables(String[] parameters){
        setFittingFunction(parameters);
        fittingParametersNumber = parameters.length;
        fittingParametersValues = new double[fittingParametersNumber];
    }
    private void setFittingFunction(String[] parameters){      
        fittingFunction = "y = " + parameters[0] + "*exp(-(x-" + parameters[1]
                          + ")*(x-" + parameters[1] + ")/(2*" + parameters[2]
                          + "*" + parameters[2] + ")) + " + parameters[3]
                          + "*x + " + parameters[4];
    }    
    
    private boolean doFit(ImagePlus imp){
        CreateAndShowGUI();
        int cnt = 0;// counter
        //We are not going to treat the last individual detector, because in most cases it's
        //smaller then others and it will give the lover intensity
        for(int i = 0; i < rod.inPlaneRodProfiles.length - 1; i++){
            final int percent;
            if(i == rod.inPlaneRodProfiles.length - 2)
                percent = 100;
            else
                percent = Math.round(((float)100/(float)(rod.inPlaneRodProfiles.length - 1))*(float)(i+1));
                  
            //Gaussian fitting of the curve L vs summed line intensity 
            double[] x = new double[rod.inPlaneRodProfiles[i].length - 1];
            double[] y = new double[rod.inPlaneRodProfiles[i].length - 1];
            for(int j = 1; j < rod.inPlaneRodProfiles[i].length; j++){
                x[j-1] = j; //Image number
                y[j-1] = rod.inPlaneRodProfiles[i][j];
            }
           
            if(!doGaussianFitWithLinearSubtraction(x, y, true, i)){
                IJ.beep();
                if(IJ.showMessageWithCancel("Error", "Line# " + i 
                    + "can't be fitted\n" + operatingError 
                    + "\n\nDo you want to skip and continue?\n"
                    + "Value of intensity will be set to 0 for this line.")){  
                    //If fitting failed but user wants to continue, parameters
                    //for that L_value will be zeroes.
                    continue;//go to the next pixels line
                }
                    else
                        return false;
            }                    
                    
            //If fitting parameters are not reasonable, let's throw this point out.
            if(!CheckResults(imp)){
                String strPercent = Integer.toString(percent);
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics());
                continue;
            }
            
            //Integrating of fitting function
            double param1 = 0, param2 = 0;
            for(int k = 0; k < fittingParametersNumber; k++){
                if(parameters[0].equals(alphabet[k])){
                    param1 = fittingParametersValues[k];
                    continue;
                }
                if(parameters[2].equals(alphabet[k])){
                    param2 = fittingParametersValues[k];
                }                            
            }
            
            //If integrated intensity value equals or smaller than zero, it's most probably misfitting
            double intI = calcIntegratedIntensity(param1, param2);
            if(intI <= 0){
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics());
                continue;
            }
            
            try{
                
                //Getting the l-value for current pixel position. We are not interested in h and k position, 
                //so, azimuthal angle can be defined as 0
                rod.WriteValue("L", cnt, rod.inPlaneRodProfiles[i][0]);
                
                rod.WriteValue("INT", cnt, intI);
                rod.WriteValue("STR", cnt, Math.sqrt(intI/experiment.getTotalCorrectionFactor(rod.x[i], rod.z[i])));
                rod.WriteValue("ERR", cnt, fittingError);
            }                    
            catch(Exception e){
                return false;
            }
            
            rod.fittingFunctions[cnt] = ComposeFitFunction(fittingFunction, fittingParametersValues);
            rod.fittedInPlaneRodProfiles[cnt] = rod.inPlaneRodProfiles[i];
            pBar.setValue(percent);
            pBar.update(pBar.getGraphics());
            cnt++;
        }
        DisposeGUI();
        return true;
    }
    
    private boolean doGaussianFitWithLinearSubtraction(double[] x, double[] y, boolean useInitialGuess, int individualDetectorNum){
        CurveFitter cf = new CurveFitter(x, y);
        if(useInitialGuess)
            cf.doCustomFit(fittingFunction, getInitialGuess(x, y), false);
        else
            cf.doCustomFit(fittingFunction, null, false);

        if (cf.getStatus() == Minimizer.INITIALIZATION_FAILURE) {
            operatingError = "Curve Fitting Error:\n"+cf.getStatusString();
            return false;
        }
        if (Double.isNaN(cf.getSumResidualsSqr())) {
            operatingError = "Error: fit yields Not-a-Number";
            return false;
        }
        
        fittingParametersValues = cf.getParams();
        fittingError = cf.getRSquared();
        return true;
    }
    
   /**
     * Calculates the initial values of fitting parameters
     * sequantial order matters (a -> 1, b -> 2, ...).
     * The 'a' parameter is being varyed in the first turn
     * and 'f' - in the last.
     * Pay attention to defining the fittin function
     */
    private double[] getInitialGuess(double[] x, double[] y){
        double min = y[0], max = y[0];
        double maxPos = x[0]; //x position of y maximum 
        
        //For the case: y = [0]*exp(-(x-[1])*(x-[1])/(2*[2]*[2])) + [3]*x + [4]
        //initial parameters will be: [0] = max(y), [1] = 0.5, [2] = max(x), [3] = 0, [4] = min(y)
        for(int i = 1; i < x.length; i++){
            if(y[i] > max){
                max = y[i];
                maxPos = x[i];
                continue; //If y[i] > max, it can't be < min 
            }
            if(y[i] < min)
                min = y[i];
        }
        
        double[] initialParamsValues = {max, maxPos, 0.5, 0, min};
        return SortParams(initialParamsValues, parameters);
    } 
    
    private double[] SortParams(double[] params, String[] paramsOrder){
        double[] initialParams = new double[fittingParametersNumber];
        for(int j = 0; j < fittingParametersNumber; j ++){
            if("a".equals(paramsOrder[j])){
                initialParams[0] = params[j];
                continue;
            }
            if("b".equals(paramsOrder[j])){
                initialParams[1] = params[j];
                continue;
            }
            if("c".equals(paramsOrder[j])){
                initialParams[2] = params[j];
                continue;
            }
            if("d".equals(paramsOrder[j])){
                initialParams[3] = params[j];
                continue;
            }
            if("e".equals(paramsOrder[j])){
                initialParams[4] = params[j];
                continue;
            }
            if("f".equals(paramsOrder[j])){
                initialParams[5] = params[j];            
                continue;
            }
        }
        return initialParams;
    } 
    
    private boolean CheckResults(ImagePlus imp){
        for(int i = 0; i < parameters.length; i++){
            //Check that intensity is positive
            if(alphabet[i].equals(parameters[0])){
                if(fittingParametersValues[i] <= 0)
                    return false;
                else
                    continue;
            }
            //Check that peak position is reasonable
            if(alphabet[i].equals(parameters[1])){
              if((fittingParametersValues[i] < 1)||(fittingParametersValues[i] > imp.getStackSize()))
                  return false;
              else
                  continue;
            }
            //Check that peak width is reasonable
            if(alphabet[i].equals(parameters[2])){
                if((fittingParametersValues[i] < 0)||(fittingParametersValues[i] > imp.getStackSize()/2))
                    return false;
                else
                    continue;
            }                    
        }
        return true;
    }
    
   /**
     * Creates the String of fitting function with inserted values of fitting parameters
     */
    private String ComposeFitFunction(String formula, double[] values){
        String result = formula;
        String tempStr = "";
        for(int i = 0; i < alphabet.length; i++){
            tempStr = result;
            if(alphabet[i].equals("e")){
                result = tempStr.replaceFirst(alphabet[i], "o");
                tempStr = result;
                result = tempStr.replace(alphabet[i], String.format("%.4f", values[i]));
                tempStr = result;
                result = tempStr.replace("o", "e");
                continue;
            }
            if(result.contains(alphabet[i])){
                result = tempStr.replace(alphabet[i], String.format("%.4f", values[i]));
            }
        }
        return result;
    }
    
   /**
     * Calculates the value of definite integral of Gaussian function in the 
     * range (-Inf, +Inf).
     * Function: f(x) = a*exp(-((x-b)^2)/(2*c^2))
     * Value: Integral(-Inf, +Inf) = sqrt(2*pi)*a*abs(c)
     * param1 = a, param2 = c
     */
    private double calcIntegratedIntensity(double param1, double param2){
        double d;
        try{
            d = Math.sqrt(2*Math.PI)*param1*Math.abs(param2);
        }
        catch(Exception e){
            d = 0;
        }
        return d;
    }
    
    protected void showResults(){
        if(rt == null)
            rt = fillResultsTable(rod);
        rt.show("Results of first fitting with y = b*exp(-(x-c)*(x-c)/(2*a*a)) + d*x + e");
    }    
    
    private ResultsTable fillResultsTable(DiffractionRod r){
        ResultsTable resTab = new ResultsTable();
        String[] headings = {"L_value", "I_integr", "STR_factor", "ERR"};
        double[] l = r.getValueSet("L");
        double[] str = r.getValueSet("STR");
        for(int i = 0; i < l.length; i++){
            //If both L and STR arrays contain zero as the current element, than it's
            //most probably meaningless i.e. unit without information
            if( (Math.abs(l[i]) < 0.0001) && (Math.abs(str[i]) < 0.0001) )
                continue;
            
            //We should remove extremely high and extremely low intensities, 
            //cause they are most probably represent misfitting.
            if( (!(i == 0))&&(!(i == l.length - 1))){
                if( ((str[i] > str[i+1]*10)&&(str[i] > str[i-1]*10)) || 
                    ((10*str[i] < str[i+1])&&(10*str[i] < str[i-1])) ){
                    continue;
                }
            }
            
            resTab.incrementCounter();
            //Add L_value in pixels counted from the center of image
            resTab.addValue(headings[0], l[i]);
            resTab.addValue(headings[1], r.getValue("INT", i));
            resTab.addValue(headings[2], str[i]);
            resTab.addValue(headings[3], r.getValue("ERR", i));
        }
        resTab.showRowNumbers(false);
        return resTab;
    }
    
    protected void plotResults(){
        //User should choose first and last values of L
        double begin = 0, end = 0;
        int lastPos = 0, firstPos = 0; 
        double[] l = rod.getValueSet("L");
        double[] str = rod.getValueSet("STR");
        for(int i = l.length - 1; i >= 0; i--){
            //Find the last non zero element of L_values array
            if( !(Math.abs(l[i]) < 0.0001) ) {
                lastPos = i;
                break;
            }
        }
        for(;;){
            GenericDialog gd = new GenericDialog("L_value range");
            gd.addMessage("Choose the range of L_values you want to plot.");
            gd.addNumericField("First L_value of region", l[0], 3); 
            gd.addNumericField("Last L_value of region", l[lastPos], 3);
            gd.showDialog();
            if (gd.wasCanceled()){
                break;
            }
        
            begin = gd.getNextNumber();
            end = gd.getNextNumber();
            if((begin < l[lastPos])||(end > l[0])||(begin < end)){
                IJ.beep();
                if(IJ.showMessageWithCancel("Error", "Entered numbers are out of range.\n"
                                            + "Do you want to try again?")){
                    continue;
                }  
                else
                    break;
            }
            else{
                for(int i = 0; i <= lastPos; i++){
                    if(l[i] <= begin){
                        firstPos = i;
                        break;
                    }
                }
                for(int i = lastPos; i >= 0; i--){
                    if(l[i] >= end){
                        lastPos = i;
                        break;
                    }
                }
            }
            
            double[] lRes = new double[lastPos-firstPos + 1];
            double[] strRes = new double[lastPos-firstPos + 1];
            int cnt = 0;
            for(int i = firstPos; i <= lastPos; i++){
                //We should remove extremely high and extremely low intensities, 
                //cause they are most probably represent misfitting.
                if( (!(i == firstPos))&&(!(i == lastPos))){
                    if( ((str[i] > str[i+1]*10)&&(str[i] > str[i-1]*10)) || 
                        ((10*str[i] < str[i+1])&&(10*str[i] < str[i-1])) ){
                        continue;
                    }
                }
                lRes[cnt] = l[i];
                strRes[cnt] = str[i];
                cnt++;
            }
            Plot plot = new Plot("Results", "L_value", "Structure factor", lRes, strRes);
            plot.show();
            break;
        }
    } 
    
    protected boolean SaveRodProfiles(){
        JFileChooser fileChooser = new JFileChooser();
        return SaveRodProfiles(fileChooser);
    }
    
    protected boolean SaveRodProfiles(JFileChooser fileChooser){
        File file = null;
        int returnVal = fileChooser.showSaveDialog(null);
        if (returnVal == JFileChooser.APPROVE_OPTION)
            file = fileChooser.getSelectedFile();
        else{
            return false;
        }
        file.setWritable(true);
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(file));
            String str = null;
            //WRITES 1 LINE TO FILE AND CHANGES LINE
            for(int i= 0; i < rod.fittedInPlaneRodProfiles.length; i++){
                if((rod.fittedInPlaneRodProfiles[i][1] == 0)&&(rod.fittedInPlaneRodProfiles[i][2] == 0))
                    break;//This means, that we achieved the meaningless part of array
                for(int j = 0; j < rod.fittedInPlaneRodProfiles[i].length; j++){
                    if(j == 0){
                        str = String.format("%.3f", rod.fittedInPlaneRodProfiles[i][j]);
                        out.write(str);
                        out.newLine();
                        continue;
                    }
                    if (j == rod.fittedInPlaneRodProfiles[i].length - 1){
                        str = String.format("%.3f", rod.fittedInPlaneRodProfiles[i][j]);
                        out.write(str);
                        continue;
                    }
                    str = String.format("%.3f", rod.fittedInPlaneRodProfiles[i][j]) + ", ";
                    out.write(str);
                }
                out.newLine();
                out.write(rod.fittingFunctions[i]);
                out.newLine();
            }
            out.close();
        } 
        catch (Exception e) {
            IJ.error("Fatal error", "Can't write a file: " + e.getMessage());
        }
        return true;
    }    
}
