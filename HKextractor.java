/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.io.File;
import java.text.DecimalFormat;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Mikhail Shipilin
 */
public class HKextractor {
    private double imageRotation = 0; //In case we need to rotate image for further processing
    private boolean imageFlipHoriz = false, 
                    imageFlipVert = false; //In case we need to flip image for further processing
    private double azimuthalStep = 0; //Angular step between images 
    private int firstImage = 1; //The number of first image in treated sequence. We need it because we want to apply corresponding angular shift.
    private boolean singleProjection = true; // Tick in the dialog showing the choice of user
                                             // to calculate only one single in-plane projection
    private boolean integrateSlices = true; // Shows that user wants to get projection(s) that is/are the  
                                            // result of integration of neibouring slices
    private double verticalStep = 0.2; //The vertical step between nearest in-plane projection (in reciprocal units)
    private double integrationInterval = 0.2; //Adjacent slices in the interval (-0.1;+0.1) will be integrated into one
                                              //at '0' position to get higher resolution of the features
                                              //Will be used in case integrateSlices == true and the default value
                                              //can be changed by user
    
    private String[] imageAbsolutePaths;
    //private int imgWidth, imgHeight; //Width and Height of images that are currently being processed
    private double[] boundaryHKLValues = new double[6]; //Boundary values minH maxH minK maxK minL maxL
    private int resolution = 300; //Number of steps in one reciprocal unit
    
    private double minL, maxL, L; // (minL,maxL) - range for in-plane projections
                                  // L - the L-value for single projection

    private JFrame pBarFrame;// Frame for progress bar
    private JProgressBar pBar; //Progress bar 0 - 100% 
    
    private HKprojection[] projections; //Array of in-plane projections for multiple case
    private HKprojection projection; //in-plane projection for singular case
    private ExperimentHandler experiment;
    private ImageStack stack;
    private String stackName; // String with the name of projection(s)
   
    protected HKextractor(ExperimentHandler ex){
        experiment = ex;
        stack = new ImageStack();
    }
    
    /**
     * Returns image information as a stack
     * @return ImagePlus
     */
    protected ImageStack getImageStack(){
        return stack;
    }
    
    protected String getStackName(){
        return stackName;
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
         
        pBar.setValue(0);
        pBar.update(pBar.getGraphics());
    }
    
    private void DisposeGUI(){
        pBar = null;
        pBarFrame.dispose();
    }    
    
    protected boolean makeProjections(File[] files){
        DecimalFormat d2 = new DecimalFormat("#.##");
        imageAbsolutePaths = new String[files.length];
        for(int i = 0; i < files.length; i++){
            imageAbsolutePaths[i] = files[i].getAbsolutePath();  
        }

        if(!showImageProcessingDialog())
            return false;
        
        //Ask boundary coordinates and L-value range
        if(!askBoundaryCoordinates())
            return false;

        CreateAndShowGUI();
        
        if(singleProjection){
            try{
                stackName = "In-plane projection at L = " + d2.format(L);
                projection = calculateHKProjection(L);
                ImageProcessor ip = projection.getProjectionProcessor();
                stack = new ImageStack(ip.getWidth(), ip.getHeight());
                stack.addSlice(ip);
            }
            catch(Exception e){
                IJ.error("The error occured while extracting projection." + e.getMessage());
                return false;
            }
        }
        else{            
            try{
                projections = calculateMultiHKProjection(minL, maxL, verticalStep);
                stackName = "In-plane projections in the L-interval from " + d2.format(minL) + " to " + d2.format(maxL) + " with the step " + d2.format(verticalStep);
                ImageProcessor ip = projections[0].getProjectionProcessor();
                stack = new ImageStack(ip.getWidth(), ip.getHeight());
                stack.addSlice(projections[0].getProjectionProcessor());
                for(int i = 1; i < projections.length; i++){
                    stack.addSlice(projections[i].getProjectionProcessor());
                }
            }
            catch(Exception e){
                IJ.error("The error occured while extracting projections." + e.getMessage());
                return false;
            }
        }
        DisposeGUI();
        return true;
    }
    
    private boolean showImageProcessingDialog(){
        GenericDialog gd = new GenericDialog("Images processing options");
        gd.addNumericField("Rotate clockwise:", 90, 2);
        gd.addCheckbox("Flip horizontally", true);
        gd.addCheckbox("Flip vertically", false);
        gd.addNumericField("Azimuthal step:", 0.1, 1);
        gd.addNumericField("Number of first loaded image", firstImage, 0);
        gd.addCheckbox("Calculate single in-plane projection", singleProjection);

        gd.showDialog();
        if (gd.wasOKed()){
            imageRotation = gd.getNextNumber();
            imageFlipHoriz = gd.getNextBoolean();
            imageFlipVert = gd.getNextBoolean();
            azimuthalStep = gd.getNextNumber();
            firstImage = (int)gd.getNextNumber();
            singleProjection = gd.getNextBoolean();
            return true;
        }
        else 
            return false;
    }     
    
    /**
     * Returns the l value requested by user and maximum l value
     */ 
    private boolean askBoundaryCoordinates(){
        //Check the heightest l-value for current images
        double[] hkl;
        try{            
            //Find L-values range 
            hkl = experiment.labToHKL(0, 0, 0);
            boundaryHKLValues[5] = hkl[2]; //Highest possible L-value (0;0 pixel)
            boundaryHKLValues[4] = 0.01; //Lowest possible L-value
            //Find H,K-values range
            hkl = experiment.labToHKL(0, experiment.getDetectorCenterZ(), -1*experiment.getInitialAngularShift());
            boundaryHKLValues[0] = hkl[0]; //Lowest H
            hkl = experiment.labToHKL(experiment.getDetectorSizeInPixelsX(), experiment.getDetectorCenterZ(), -1*experiment.getInitialAngularShift());
            boundaryHKLValues[1] = hkl[0]; //Highest H
            hkl = experiment.labToHKL(experiment.getDetectorSizeInPixelsX(), experiment.getDetectorCenterZ(), -1*experiment.getInitialAngularShift() + 90);
            boundaryHKLValues[2] = hkl[1]; //Lowest K          
            hkl = experiment.labToHKL(0, experiment.getDetectorCenterZ(), -1*experiment.getInitialAngularShift() + 90);
            boundaryHKLValues[3] = hkl[1]; //Highest K
        }
        catch(Exception e){
            IJ.error(e.getMessage());
        }
        String lMaxLimit = String.format("%.2f", boundaryHKLValues[5]);
        String lMinLimit = String.format("%.2f", boundaryHKLValues[4]);
        if(singleProjection){
            for(;;){
                //Generate dialog for choosing the desired l-value for hk-projection        
                GenericDialog gd = new GenericDialog("Choose L-value");
                gd.addMessage("Choose L-value for hk-projection.\nIt should be within interval " + lMinLimit + " - " + lMaxLimit);
                gd.addNumericField("L-value:", boundaryHKLValues[4], 2);
                gd.addCheckbox("Integrate adjacent slices", integrateSlices);
                gd.showDialog();
                double intInterval = integrationInterval;//We will use this variable for temporary integrationInterval
                if (gd.wasOKed()){
                    L = gd.getNextNumber();
                    integrateSlices = gd.getNextBoolean();
                    if((L < boundaryHKLValues[4]) || (L > boundaryHKLValues[5])) {
                        if(!IJ.showMessageWithCancel("Wrong L-value", "L-value is outside of allowed interval./nDo you want to try again?")){
                            return false;
                        }
                        else
                            continue;
                    }
                    else
                        if(integrateSlices){
                            GenericDialog gd1 = new GenericDialog("Integration interval");
                            gd1.addMessage("Choose L-interval for integration of adjacent in-plane projections.\n"
                                    +     "Resulting in-plane projection will be at the center of interval.");
                            gd1.addNumericField("Choose L-interval:", integrationInterval, 2);
                            gd1.showDialog();
                            if (gd1.wasOKed()){
                               intInterval = gd1.getNextNumber();
                               if((intInterval < experiment.getPixelSizeRLU())||
                                  (intInterval > boundaryHKLValues[5])){
                                   IJ.error("Interval is improperly selected");
                                   continue;//Interval is selected improperly
                               }
                            }
                            else{
                                continue;//The integration interval was canceled 
                            }
                        }
                        integrationInterval = intInterval;//When we are sure that value is entered correctly, we save it 
                        break;
                }
                else{
                    if(!IJ.showMessageWithCancel("L-value was not chosen", "You can't continue without choosing L-value./nDo you want to try again?")){
                            return false;
                    }
                    else
                        continue;
                }
            }            
        }
        else{
            for(;;){
                //Generate dialog for choosing the desired range of l-values for hk-projections        
                GenericDialog gd = new GenericDialog("Choose L-values region");
                gd.addMessage("Choose L-values region for hk-projections.\nIt should be within interval " + lMinLimit + " - " + lMaxLimit);
                gd.addNumericField("Choose min L-value:", boundaryHKLValues[4], 2);
                gd.addNumericField("Choose max L-value:", boundaryHKLValues[5], 2);
                gd.addNumericField("Step between projections (in RLU)", verticalStep, 2);
                gd.addCheckbox("Integrate adjacent slices", integrateSlices);
                gd.showDialog();
                if (gd.wasOKed()){
                    minL = gd.getNextNumber();
                    maxL = gd.getNextNumber();
                    verticalStep = gd.getNextNumber();
                    integrateSlices = gd.getNextBoolean();
                    if((minL < boundaryHKLValues[4]) || (minL > boundaryHKLValues[5]) ||
                       (maxL < boundaryHKLValues[4]) || (maxL > boundaryHKLValues[5])){
                        if(!IJ.showMessageWithCancel("Wrong interval for L-values.", "Do you want to try again?")){
                            return false;//Mistake was done, user doesn't want to try again
                        }
                        else 
                            continue;//Mistake was done, but user wants to try again
                    }
                    else{
                        if(integrateSlices){
                            GenericDialog gd1 = new GenericDialog("Integration interval");
                            gd1.addMessage("Choose L-interval for integration of adjacent in-plane projections.\n"
                                    +     "Resulting in-plane projection will be at the center of interval.");
                            gd1.addNumericField("Choose L-interval:", verticalStep, 2);
                            gd1.showDialog();
                            if (gd1.wasOKed()){
                               integrationInterval = gd1.getNextNumber();
                               if((integrationInterval < experiment.getPixelSizeRLU())||(integrationInterval > verticalStep)){
                                   IJ.error("Interval is improperly selected");
                                   continue;//Interval is selected improperly
                               }
                            }
                            else{
                                continue;//The integration interval was canceled 
                            }
                        }
                        break;//Everything is properly done
                    }
                }
                else{
                    if(!IJ.showMessageWithCancel("Interval for L-values was not chosen", "You can't continue without choosing interval for L-values./nDo you want to try again?")){
                            return false;//Region was not chosen, user doesn't want to try again
                    }
                    else
                        continue;//Region was not chosen, but user wants to try again
                }
            }
        }
        
        return true;
    }
    
    //Calculate hk-projection from chosen images on the base of experiment properties
    private HKprojection calculateHKProjection(double lValue) throws Exception{
        //If user wants to get integrated projection
        if(integrateSlices){
            HKprojection[] projectionsStack;
            if(((lValue - integrationInterval/2) > boundaryHKLValues[4])&&
               ((lValue + integrationInterval/2) < boundaryHKLValues[5])){
                projectionsStack = calculateMultiHKProjection(lValue-integrationInterval/2, lValue+integrationInterval/2, experiment.getPixelSizeRLU());
                return HKprojection.sumUpProjections(projectionsStack, lValue);
            }
            else{
                if(((lValue - integrationInterval/2) <= boundaryHKLValues[4])&&
                   ((lValue + integrationInterval/2) < boundaryHKLValues[5])){
                    projectionsStack = calculateMultiHKProjection(boundaryHKLValues[4], integrationInterval, experiment.getPixelSizeRLU());
                    return HKprojection.sumUpProjections(projectionsStack, lValue);
                }
                if(((lValue - integrationInterval/2) > boundaryHKLValues[4])&&
                   ((lValue + integrationInterval/2) >= boundaryHKLValues[5])){
                    projectionsStack = calculateMultiHKProjection(boundaryHKLValues[5]-integrationInterval, boundaryHKLValues[5], experiment.getPixelSizeRLU());
                    return HKprojection.sumUpProjections(projectionsStack, lValue);
                }
            }
        }        
        
        //If user wants to get just one slice
        //Convert lValue to pixel coordinates
        int pixLValue = (int)(experiment.getDetectorCenterZ() - lValue*experiment.getDetectorCenterZ()/boundaryHKLValues[5]);
        int imageWidth = experiment.getDetectorSizeInPixelsX();
        
        ImageProcessor ip;
        HKprojection hk = new HKprojection(boundaryHKLValues, lValue, resolution);
        double[] hkl; //Arrays that will contain reciprocal coordinates of
                              //pixel(0;0) and pixel(x;z)
        double h,k;//Temporary h-,k-coordinates
        //Array will contain coordinates and intensities for current image (pixels along the chosen l-line)
        double[][] intensityData = new double[imageWidth][3];
        double[] pixels = new double[imageWidth];
        int percent = 0;
        for(int i = 0; i < imageAbsolutePaths.length; i++){
            if(i == imageAbsolutePaths.length - 1)
                percent = 100;
            else
                percent = Math.round(((float)100/(float)(imageAbsolutePaths.length))*(float)(i+1));
            ip = getImageProcessor(imageAbsolutePaths[i]);          
            pixels = ip.getLine(0, pixLValue, imageWidth, pixLValue);
            int cnt = 0;
            if(i == 0){//The first image pixels coordinates must be transformed by labToHKL() method
                for(int j = 0; j < imageWidth; j++){
                    try{
                        hkl = experiment.labToHKL(j, pixLValue, (firstImage-1)*azimuthalStep + i*azimuthalStep);
                    }
                    catch(Exception e){
                        IJ.error("Not all pixels are recalculated due to error: ", e.getMessage());
                        continue;
                    }
                    //hkl = experiment.convertPixelCoordinates(j, pixLValue, hkl_00);
                    intensityData[cnt][0] = hkl[0];
                    intensityData[cnt][1] = hkl[1];
                    intensityData[cnt][2] = pixels[j];
                    cnt++;
                }
            }
            else{ //Each next image pixels coordinates can be transformed based on previous image pixels coordinates
                for(int j = 0; j < imageWidth; j++){
                    //Rotation of pixels coordinates by one azimuthalStep
                    h = intensityData[cnt][0]; 
                    k = intensityData[cnt][1];
                    intensityData[cnt][0] = Math.cos(azimuthalStep*Math.PI/180)*h + Math.sin(azimuthalStep*Math.PI/180)*k;
                    intensityData[cnt][1] = -1*Math.sin(azimuthalStep*Math.PI/180)*h + Math.cos(azimuthalStep*Math.PI/180)*k;
                    intensityData[cnt][2] = pixels[j];
                    cnt++;
                }                
            }
            
            hk.addDataSet(intensityData);
            pBar.setValue(percent);
            pBar.update(pBar.getGraphics());
        }
        return hk;
    }
    
    /**
     * Calculates projections in specified L-interval with specified step
     * @param mnL min L
     * @param mxL max L
     * @param step step in RLU
     * @return projections array
     * @throws Exception 
     */
    private HKprojection[] calculateMultiHKProjection(double mnL, double mxL, double step) throws Exception{      
        int imageWidth = experiment.getDetectorSizeInPixelsX();
        double[] hkl; //Array containing temporary reciprocal coordinates of
                      //currently treated pixel for the first loaded image;
                      //These coordinates are being obtained in LabToHKL() method
                      //for images other than the first coordinates will be obtained
                      //by simpler transformation.
        //Array containing coordinates and intensities for current image (pixels along the chosen l-line)
        double[][] intensityData = new double[imageWidth][3];
        double[] pixels = new double[imageWidth];
        int percent = 0;
        int pixLValue = 0;
        //pixLValue = (pixLValue >= experiment.getDetectorCenterZ())?pixLValue:experiment.getDetectorCenterZ();
        int cnt = 0; 
        
        //If we have the case of multui projection with integration enabled
        if((!singleProjection) && (integrateSlices)){
            double minLValue, maxLValue;
            int[][] projectionsHeights;
            minLValue = (mnL-integrationInterval/2)>boundaryHKLValues[4] ? mnL : boundaryHKLValues[4]; 
            maxLValue = (mxL+integrationInterval/2)<boundaryHKLValues[5] ? mxL : boundaryHKLValues[5];
            projectionsHeights = new int[(int)((maxLValue-minLValue)/step + 1)][(int)(integrationInterval/experiment.getPixelSizeRLU() + 1)];
            
            HKprojection[][] tempProjections = new HKprojection[projectionsHeights.length][projectionsHeights[0].length];
            double[][][] h = new double[tempProjections.length][tempProjections[0].length][imageWidth]; //Arrays containing h- and k- coordinates of corresponding pixels
            double[][][] k = new double[tempProjections.length][tempProjections[0].length][imageWidth]; //at the previously treated image for each in-plane projection
            
            for(int i = 0; i < projectionsHeights.length; i++){
                 for(int j = 0; j < projectionsHeights[0].length; j++){
                     projectionsHeights[i][j] = (int)Math.round(experiment.getDetectorCenterZ() - (minLValue - integrationInterval/2 + step*i)*experiment.getDetectorCenterZ()/boundaryHKLValues[5]) + j;
                     tempProjections[i][j] = new HKprojection(boundaryHKLValues, minLValue - integrationInterval/2 + step*i + experiment.getPixelSizeRLU()*j, resolution);
                 }
            }
            
            for(int i = 0; i < imageAbsolutePaths.length; i++){
                if(i == imageAbsolutePaths.length - 1)
                    percent = 100;
                else
                    percent = Math.round(((float)100/(float)(imageAbsolutePaths.length))*(float)(i+1));

                ImageProcessor imgProc = getImageProcessor(imageAbsolutePaths[i]);
                for(int j = 0; j < tempProjections.length; j++){
                    for(int p = 0; p < tempProjections[0].length; p++){
                        pixLValue = projectionsHeights[j][p];
                        pixels = imgProc.getLine(0, pixLValue, imageWidth, pixLValue);
                        cnt = 0;
                        if(i == 0){//The first image pixels coordinates must be transformed by labToHKL() method
                            for(int q = 0; q < pixels.length; q++){
                                try{
                                    hkl = experiment.labToHKL(q, pixLValue, (firstImage-1)*azimuthalStep + i*azimuthalStep);
                                }
                                catch(Exception e){
                                    IJ.error("Not all pixels are recalculated due to error: ", e.getMessage());
                                    continue;
                                }
                                intensityData[cnt][0] = hkl[0];
                                intensityData[cnt][1] = hkl[1];
                                intensityData[cnt][2] = pixels[q];
                                h[j][p][cnt] = hkl[0];
                                k[j][p][cnt] = hkl[1];
                                cnt++;
                            }
                        }
                        else{ //Each next image pixels coordinates can be transformed based on previous image pixels coordinates
                            for(int q = 0; q < imageWidth; q++){
                                //Rotation of pixels coordinates by one azimuthalStep
                                intensityData[cnt][0] = Math.cos(azimuthalStep*Math.PI/180)*h[j][p][cnt] + Math.sin(azimuthalStep*Math.PI/180)*k[j][p][cnt];
                                intensityData[cnt][1] = -1*Math.sin(azimuthalStep*Math.PI/180)*h[j][p][cnt] + Math.cos(azimuthalStep*Math.PI/180)*k[j][p][cnt];
                                intensityData[cnt][2] = pixels[q];
                                h[j][p][cnt] = intensityData[cnt][0];
                                k[j][p][cnt] = intensityData[cnt][1];
                                cnt++;
                            }                
                        }
                        tempProjections[j][p].addDataSet(intensityData);                        
                    }
                }
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics());  
            }
            HKprojection[] finalProjections = new HKprojection[tempProjections.length];
            for(int i = 0; i < finalProjections.length; i++){
                finalProjections[i] = HKprojection.sumUpProjections(tempProjections[i], minLValue + step*i);;
            }
            return finalProjections;
        }
        else{
            HKprojection[] finalProjections = new HKprojection[(int)((mxL-mnL)/step + 1)];
            double[][] h = new double[finalProjections.length][imageWidth]; //Arrays containing h- and k- coordinates of corresponding pixels
            double[][] k = new double[finalProjections.length][imageWidth]; //at the previously treated image for each in-plane projection

            for(int j = 0; j < finalProjections.length; j++){
                finalProjections[j] = new HKprojection(boundaryHKLValues, mnL + step*j, resolution);
            }

            for(int i = 0; i < imageAbsolutePaths.length; i++){
                if(i == imageAbsolutePaths.length - 1)
                    percent = 100;
                else
                    percent = Math.round(((float)100/(float)(imageAbsolutePaths.length))*(float)(i+1));

                ImageProcessor imgProc = getImageProcessor(imageAbsolutePaths[i]);
                for(int j = 0; j < finalProjections.length; j++){
                    //Convert lValue to pixel coordinates
                    pixLValue = (int)(experiment.getDetectorCenterZ() - (mnL + step*j)*experiment.getDetectorCenterZ()/boundaryHKLValues[5]);                
                    pixels = imgProc.getLine(0, pixLValue, imageWidth, pixLValue);
                    cnt = 0;
                    if(i == 0){//The first image pixels coordinates must be transformed by labToHKL() method
                        for(int q = 0; q < imageWidth; q++){
                            try{
                                hkl = experiment.labToHKL(q, pixLValue, (firstImage-1)*azimuthalStep + i*azimuthalStep);
                            }
                            catch(Exception e){
                                IJ.error("Not all pixels are recalculated due to error: ", e.getMessage());
                                continue;
                            }
                            intensityData[cnt][0] = hkl[0];
                            intensityData[cnt][1] = hkl[1];
                            intensityData[cnt][2] = pixels[q];
                            h[j][cnt] = hkl[0];
                            k[j][cnt] = hkl[1];
                            cnt++;
                        }
                    }
                    else{ //Each next image pixels coordinates can be transformed based on previous image pixels coordinates
                        for(int q = 0; q < imageWidth; q++){
                            //Rotation of pixels coordinates by one azimuthalStep
                            intensityData[cnt][0] = Math.cos(azimuthalStep*Math.PI/180)*h[j][cnt] + Math.sin(azimuthalStep*Math.PI/180)*k[j][cnt];
                            intensityData[cnt][1] = -1*Math.sin(azimuthalStep*Math.PI/180)*h[j][cnt] + Math.cos(azimuthalStep*Math.PI/180)*k[j][cnt];
                            intensityData[cnt][2] = pixels[q];
                            h[j][cnt] = intensityData[cnt][0];
                            k[j][cnt] = intensityData[cnt][1];
                            cnt++;
                        }                
                    }
                    finalProjections[j].addDataSet(intensityData);
                }
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics());  
            }
            return finalProjections;
        } 
    }
    
    /**
     * Calculates projections in specified L-interval with specified step
     * integrating the slices adjacent to each projection in the specified interval
     * @param mnL min L
     * @param mxL max L
     * @param step step in RLU
     * @return projections array
     * @throws Exception 
     */
    private HKprojection[] calculateMultiHKProjectionWithIntegration(double mnL, double mxL, double step) throws Exception{
        HKprojection[][] allProjections = new HKprojection[(int)((mxL-mnL)/step + 1)][(int)Math.round(integrationInterval/experiment.getPixelSizeRLU())];
        int imageWidth = experiment.getDetectorSizeInPixelsX();
        double[] hkl; //Arrays that will contain reciprocal coordinates of
                      //pixel(0;0) and pixel(x;z)
        //Array will contain coordinates and intensities for current image (pixels along the chosen l-line)
        double[][] intensityData = new double[imageWidth][3];
        double[][][] h = new double[allProjections.length][allProjections[0].length][imageWidth]; //Arrays containing h- and k- coordinates of corresponding pixels
        double[][][] k = new double[allProjections.length][allProjections[0].length][imageWidth]; //on the last image for each in-plane projection
        double[] pixels = new double[imageWidth];
        int percent = 0;
        int pixLValue = 0;
        int cnt = 0;
        for(int i = 0; i < allProjections.length; i++){    
            for(int j = 0; j < allProjections[0].length; j++){
                allProjections[i][j] = new HKprojection(boundaryHKLValues, mnL-integrationInterval/2 + experiment.getPixelSizeRLU()*j + step*i, resolution);
            }
        }
        for(int i = 0; i < imageAbsolutePaths.length; i++){
            if(i == imageAbsolutePaths.length - 1)
                percent = 100;
            else
                percent = Math.round(((float)100/(float)(imageAbsolutePaths.length))*(float)(i+1));
            
            ImageProcessor imgProc = getImageProcessor(imageAbsolutePaths[i]);
            for(int j = 0; j < allProjections.length; j++){
                for(int y = 0; y < allProjections.length; y++){
                    //Convert lValue to pixel coordinates
                    pixLValue = (int)(experiment.getDetectorCenterZ() - (mnL + step*j + y*experiment.getPixelSizeRLU() - integrationInterval/2)*experiment.getDetectorCenterZ()/boundaryHKLValues[5]);       
                    if(pixLValue > experiment.getDetectorCenterZ()){ //If the point is below 0 in vertical direction
                        pixLValue = (int)Math.round(experiment.getDetectorCenterZ() - (mnL + step*j + y*experiment.getPixelSizeRLU())*experiment.getDetectorCenterZ()/boundaryHKLValues[5]);
                    }
                    if(pixLValue < 0){ //If the point is above maximum L-value
                        pixLValue = (int)Math.round(experiment.getDetectorCenterZ() - (mnL + step*j + y*experiment.getPixelSizeRLU() - integrationInterval)*experiment.getDetectorCenterZ()/boundaryHKLValues[5]);
                    }
                    
                    pixels = imgProc.getLine(0, pixLValue, imageWidth, pixLValue);
                    cnt = 0;
                    if(i == 0){//The first image pixels coordinates must be transformed by labToHKL() method
                        for(int q = 0; q < imageWidth; q++){
                            try{
                                hkl = experiment.labToHKL(q, pixLValue, (firstImage-1)*azimuthalStep + i*azimuthalStep);
                            }
                            catch(Exception e){
                                IJ.error("Not all pixels are recalculated due to error: ", e.getMessage());
                                continue;
                            }
                            intensityData[cnt][0] = hkl[0];
                            intensityData[cnt][1] = hkl[1];
                            intensityData[cnt][2] = pixels[q];
                            h[j][y][cnt] = hkl[0];
                            k[j][y][cnt] = hkl[1];
                            cnt++;
                        }
                    }
                    else{ //Each next image pixels coordinates can be transformed based on previous image pixels coordinates
                        for(int q = 0; q < imageWidth; q++){
                            //Rotation of pixels coordinates by one azimuthalStep
                            intensityData[cnt][0] = Math.cos(azimuthalStep*Math.PI/180)*h[j][y][cnt] + Math.sin(azimuthalStep*Math.PI/180)*k[j][y][cnt];
                            intensityData[cnt][1] = -1*Math.sin(azimuthalStep*Math.PI/180)*h[j][y][cnt] + Math.cos(azimuthalStep*Math.PI/180)*k[j][y][cnt];
                            intensityData[cnt][2] = pixels[q];
                            h[j][y][cnt] = intensityData[cnt][0];
                            k[j][y][cnt] = intensityData[cnt][1];
                            cnt++;
                        }                
                    }
                    allProjections[j][y].addDataSet(intensityData);
                }
            }
        pBar.setValue(percent);
        pBar.update(pBar.getGraphics());  
        }
        
        HKprojection[] tempProjections = new HKprojection[allProjections.length];
        for(int i= 0; i < tempProjections.length; i++){
            tempProjections[i] = HKprojection.sumUpProjections(allProjections[i], mnL + step*i);
        }
        return tempProjections;        
    }
      
    protected ImageProcessor getImageProcessor(String path){
        ImageProcessor ip = (new ImagePlus(path)).getProcessor();
        ip.rotate(imageRotation);
        if(imageFlipHoriz)
            ip.flipHorizontal();
        if(imageFlipVert)
            ip.flipVertical();
        return ip;    
    }    
}
