import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Mikhail Shipilin
 */
public class ExperimentHandler {
    
    private final String[] PARAMETERS = {          "PHOTONENERGY", 
                                         "HORIZONTALPOLARIZATION", 
                                          "INITIALAZIMUTHALSHIFT", 
                                                  "INCIDENTANGLE", 
                                               "DETECTORDISTANCE", 
                                              "LATTICEPARAMETERS",
                                                  "LATTICEANGLES", 
                                                   "DETECTORSIZE",
                                             "DETECTORRESOLUTION",
                                         "CENTERPIXELCOORDINATES"};
    
    // Global variables loaded from .txt file
    private double photonEnergy; // Photon energy in experiment
    private double horizontalPolarization; // Horizontal component of the beam in fractions of unit 
    private double omegaShift; // Initial offset of rotational position due to missalignment
                               // (omega angle is 0 when the beam is parallel to h-axis)
    private double incidentAngle; // Value of incident angle in degrees
    private double detectorDistance; // Distance from sample to detector in mm
    private double[] latticeParameters = new double[3]; // a1, a2, a3 real space crystall lattice parameters
    private double[] latticeAngles = new double[3]; // a1^a3(alpha), a2^a3(beta), a1^a2(gamma) angles between real space lattice vectors
    private double xDetectorSize, zDetectorSize; // Detector size in mm   
    private double xDetectorSizeInPixels, zDetectorSizeInPixels; // Detector size in pixels
    private double xCenter, zCenter; // X and Y coordinates of pattern center in pixels corresponding to (0,0) of detector
       
    // Other global variables
    private double ewaldRadius; // Radius of Ewald sphere
    private double xPixelSize, zPixelSize; // Pixel sizes in mm
    private double zPixelSizeRLU; // Contains the vertical size of a pixel in reciprocal units
    private boolean preferencesLoaded = false; // True, if all necessary preferences were loaded
   
    
    /**
     * Reads the .txt file and constructs the object 
     * ExperimentalHandler with data about experiment properties.
     */ 
    public ExperimentHandler(File preferencesFile) throws Exception {
        List<String> lines = new ArrayList<String>();
        try{
            BufferedReader br = new BufferedReader(new FileReader(preferencesFile));
            String line = br.readLine();
            while (line != null)
            {
                lines.add(line);
                line = br.readLine();
            }
        }
        catch(Exception e){
            throw new Exception("Can't get the information from the preferences file " + preferencesFile.getName());
        }
        for(String line : lines){
            if(line.isEmpty())
                continue; // Skip empty lines
            if((line.trim()).charAt(0) == '%')
                continue; // Skip commented lines
            String[] splits = line.split("\\s+");
            // <editor-fold defaultstate="collapsed" desc="Parsing parameters">
            int parameterNumber = 999;
            for(int i = 0; i < PARAMETERS.length; i++){
                if(PARAMETERS[i].equals((splits[0].trim()).toUpperCase())){
                    parameterNumber = i;
                    break;
                }
            }
            switch(parameterNumber){
                case 0:/*PHOTONENERGY*/
                    if(!(splits.length == 2))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    photonEnergy = Double.parseDouble(splits[1]);
                    break;
                case 1:/*HORIZONTALPOLARIZATION*/
                    if(!(splits.length == 2))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    horizontalPolarization = Double.parseDouble(splits[1]);
                    break;
                case 2:/*AZIMUTHALSHIFT*/
                    if(!(splits.length == 2))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    omegaShift = Double.parseDouble(splits[1]); 
                    break;
                case 3:/*INCIDENTANGLE*/ 
                    if(!(splits.length == 2))
                        throw new Exception("Incorrect number of parameters in preferences file");                
                    incidentAngle = Double.parseDouble(splits[1]);
                    break;
                case 4:/*DETECTORDISTANCE*/
                    if(!(splits.length == 2))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    detectorDistance = Double.parseDouble(splits[1]);
                    break;
                case 5:/*LATTICEPARAMETERS*/
                    if(!(splits.length == 4))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    for(int j = 1; j < splits.length; j++){
                        latticeParameters[j-1] = Double.parseDouble(splits[j]);
                    } 
                    break;
                case 6:/*LATTICEANGLES*/ 
                    if(!(splits.length == 4))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    for(int j = 1; j < splits.length; j++){
                        latticeAngles[j-1] = Double.parseDouble(splits[j]);
                    }
                    break;
                case 7:/*DETECTORSIZE*/
                    if(!(splits.length == 3))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    xDetectorSize = Double.parseDouble(splits[1]);
                    zDetectorSize = Double.parseDouble(splits[2]);
                    break;
                case 8:/*DETECTORRESOLUTION*/
                    if(!(splits.length == 3))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    xDetectorSizeInPixels = Double.parseDouble(splits[1]);
                    zDetectorSizeInPixels = Double.parseDouble(splits[2]); 
                    break;
                case 9:/*CENTERPIXELCOORDINATES*/
                    if(!(splits.length == 3))
                        throw new Exception("Incorrect number of parameters in preferences file");
                    xCenter = Double.parseDouble(splits[1]);
                    zCenter = Double.parseDouble(splits[2]); 
                    break;
                default:
                    throw new Exception("Incorrect name of parameter in preferences file");
            }
            // </editor-fold>
        }
        xPixelSize = xDetectorSize/xDetectorSizeInPixels;
        zPixelSize = zDetectorSize/zDetectorSizeInPixels;
        ewaldRadius = 2*Math.PI/this.getWaveLength();
        
        //Get the reciprocal coordinates of pixel(0;0) to figure out the maximum
        //L-value in order to use it for zPixelSizeRLU calculation
        double[] hkl_00;
        try{
            hkl_00 = this.labToHKL(0, 0, 0);
        }
        catch(Exception e){
            hkl_00 = new double[] {0,0,0};
        }
        zPixelSizeRLU = hkl_00[2]/zCenter;
        
        preferencesLoaded = true;
    }
    
    /**
     * Calculates lambda = lightSpeed*planckConstant/photonEnergy
     * @return Wave length for current photon energy in angstrems 
     */
    public double getWaveLength(){
        return 299792458*0.00004136/photonEnergy;
    }
    
    public double getPixelSizeRLU(){
        return zPixelSizeRLU;
    }
    
    public double[] getDetectorPixelSize(){
        return new double[] { xPixelSize, zPixelSize };
    }
    
    public int getDetectorCenterX(){
        return (int)xCenter;
    }
    
    public int getDetectorCenterZ(){
        return (int)zCenter;
    }
    
    public int getDetectorSizeInPixelsX(){
        return (int)xDetectorSizeInPixels;
    }

    public int getDetectorSizeInPixelsZ(){
        return (int)zDetectorSizeInPixels;
    }    
    
    public double getInitialAngularShift(){
        return omegaShift;
    }
    
        /**
     * @param x horizontal pixel position from top left corner
     * @param imgWidth horizontal resolution of image
     * @return horizontal distance to pixel from the center in mm
     */
    public double xPixTOmm (int x) {
        return (x - xCenter)*xPixelSize;
    }
        
    /**
     * @param z vertical pixel position from top left corner
     * @param imgHeight vertical resolution of image
     * @return vertical distance to pixel from the center in mm
     */
    public double zPixTOmm (int z) {
        return (zCenter - z)*zPixelSize;

    }
    
    /**
     * Calculates the total correction factor for rocking scans in HESXRD experiment:
     * Ctot = Polariz * Lorentz * Crod * Carea * Cdet * Cbeam * Cd * Ci,
     * where Polariz - polarization factor, Lorentz - Lorentz factor,
     * Crod - rod interception, Carea - area correction, Cdet - in-plane detector 
     * acceptance, Cbeam - beam profile correction, Cd - difference in distance to 
     * different pixels, Ci - beam inclination correction for different pixels.
     * In case of open-slits geometry Carea = Cdet = Cbeam = 1.
     * @param x horizontal pixel position from top left corner
     * @param z vertical pixel position from top left corner
     * @return total correction factor
     */
    public double getTotalCorrectionFactor(int x, int z){
        double deltaX = this.xPixTOmm(x); // Horizontal distance to pixel from the center in mm 
        double deltaZ = this.zPixTOmm(z); // Vertical distance to pixel from the center in mm
        // Lorenz factor
        double lorentz = (1/Math.cos(incidentAngle*Math.PI/180))* 
                         Math.sqrt((1+deltaZ*deltaZ/(detectorDistance*detectorDistance))*
                                   (1+detectorDistance*detectorDistance/(deltaX*deltaX)));
        // Polarization factor which consists of two parts depending on vertical and
        // horizontal polarization fractions of the initial beam
        double pVer = (1-horizontalPolarization)*
                      (1 - 1/((1+detectorDistance*detectorDistance/(deltaX*deltaX))*
                              (1+deltaZ*deltaZ/(detectorDistance*detectorDistance))));
        double pHor = horizontalPolarization*
                      (1 - (Math.sin(incidentAngle*Math.PI/180)/
                            Math.sqrt((1+deltaX*deltaX/(detectorDistance*detectorDistance))*
                                 (1+deltaZ*deltaZ/(detectorDistance*detectorDistance)))+ 
                            Math.cos(incidentAngle*Math.PI/180)/
                            Math.sqrt(1+detectorDistance*detectorDistance/(deltaZ*deltaZ)))*
                           (Math.sin(incidentAngle*Math.PI/180)/
                            Math.sqrt((1+deltaX*deltaX/(detectorDistance*detectorDistance))*
                                 (1+deltaZ*deltaZ/(detectorDistance*detectorDistance)))+ 
                            Math.cos(incidentAngle*Math.PI/180)/
                            Math.sqrt(1+detectorDistance*detectorDistance/(deltaZ*deltaZ))));
        double polariz = pVer + pHor;
        // Rod interseption correction factor
        double cRod = 1/Math.sqrt(1+deltaZ*deltaZ/(detectorDistance*detectorDistance));
        double cArea = 1, cDet = 1, cBeam = 1;
        // Distance from the pattern center to different pixels correction
        double cD = (deltaX*deltaX + deltaZ*deltaZ + detectorDistance*detectorDistance)/(detectorDistance*detectorDistance);
        // Beam inclination correction
        double cI = Math.sqrt(1 + (deltaX*deltaX + deltaZ*deltaZ)/(detectorDistance*detectorDistance));
        
        return lorentz*polariz*cRod*cArea*cDet*cBeam*cD*cI;
    }
    
    /**
     * Function transforms detector pixels coordinates to corresponding momentum
     * transfer coordinates 
     * @param x horizontal pixel position from top left corner
     * @param z vertical pixel position from top left corner
     * @param omega current azimuthal angle of sample rotation with respect to 0 starting angle
     * @return [h,k,l] coordinates of momentum transfer vector in reciprocal space
     */
    public double[] labToHKL(int x, int z, double omega) throws Exception {
        double deltaX = this.xPixTOmm(x); // Horizontal distance to pixel from the center in mm 
        double deltaZ = this.zPixTOmm(z); // Vertical distance to pixel from the center in mm
        // Coordinates of pixel in lab coordinates system
        Matrix pixInLab = new Matrix(3, 1);
        pixInLab.setValueAt(0, 0, deltaX);
        pixInLab.setValueAt(1, 0, detectorDistance);
        pixInLab.setValueAt(2, 0, deltaZ);
        // Reciprocal lattice matrix b
        Matrix b = new Matrix(3, 3);
        b.setValueAt(0, 0, 2*Math.PI/latticeParameters[0]);
        b.setValueAt(1, 1, 2*Math.PI/latticeParameters[1]);
        b.setValueAt(2, 2, 2*Math.PI/latticeParameters[2]);
        // Orientation matrix uAngle (accounts the initial azimuthal shift)
        Matrix uAngle = new Matrix(3, 3);
        uAngle.setValueAt(0, 0, Math.cos(omegaShift*Math.PI/180));
        uAngle.setValueAt(0, 1, -1*Math.sin(omegaShift*Math.PI/180));
        uAngle.setValueAt(1, 0, Math.sin(omegaShift*Math.PI/180));
        uAngle.setValueAt(1, 1, Math.cos(omegaShift*Math.PI/180));
        uAngle.setValueAt(2, 2, 1);
        // Rotational matrix sMu (accounts incident angle)
        Matrix sMu = new Matrix(3, 3);
        sMu.setValueAt(0, 0, 1);
        sMu.setValueAt(1, 1, Math.cos(incidentAngle*Math.PI/180));
        sMu.setValueAt(1, 2, -1*Math.sin(incidentAngle*Math.PI/180));
        sMu.setValueAt(2, 1, Math.sin(incidentAngle*Math.PI/180));
        sMu.setValueAt(2, 2, Math.cos(incidentAngle*Math.PI/180));
        // Rotational matrix sOmega (accounts azimuthal angle of rotation)
        Matrix sOmega = new Matrix(3, 3);
        sOmega.setValueAt(0, 0, Math.cos(omega*Math.PI/180));
        sOmega.setValueAt(0, 1, -1*Math.sin(omega*Math.PI/180));
        sOmega.setValueAt(1, 0, Math.sin(omega*Math.PI/180));
        sOmega.setValueAt(1, 1, Math.cos(omega*Math.PI/180));
        sOmega.setValueAt(2, 2, 1);
        // Additional matrix for calculations
        Matrix temp = new Matrix(3, 1);
        //pixInLab.setValueAt(1, 0, 1);
        temp.setValueAt(1, 0, 1);
        
        // Calculation of momentum transfer vector
        Matrix q;
        Matrix matHKL;
        double[] hkl = new double[3];
        try{
            q = (Matrix.subtract(pixInLab.multiplyByConstant(1/Math.sqrt(deltaX*deltaX +
                 detectorDistance*detectorDistance + deltaZ*deltaZ)), temp)).multiplyByConstant(2*Math.PI/this.getWaveLength());
            matHKL = Matrix.leftDivide(Matrix.multiply(sMu, Matrix.multiply(sOmega, Matrix.multiply(uAngle, b))), q);
            for(int i = 0; i < hkl.length; i++){
                hkl[i] = matHKL.getValueAt(i, 0);
            }
        }
        catch(Exception e){
            throw new Exception(e);
        }
        return hkl;         
    }
}
