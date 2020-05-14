/* 
 * The author of this software is Mikhail Shipilin.  Copyright (c) 2012.
 * Permission to use, copy, modify, and distribute this software for any 
 * purpose is hereby granted, provided that this entire notice is included in
 * all copies of any software which is or includes a copy or modification of this 
 * software and in all copies of the supporting documentation for such software.
 * Any for profit use of this software is expressly forbidden without first
 * obtaining the explicit consent of the author. 
 * THIS SOFTWARE IS BEING PROVIDED "AS IS", WITHOUT ANY EXPRESS OR IMPLIED WARRANTY. 
 * IN PARTICULAR, THE AUTHOR DOES NOT MAKE ANY REPRESENTATION OR WARRANTY 
 * OF ANY KIND CONCERNING THE MERCHANTABILITY OF THIS SOFTWARE OR ITS FITNESS FOR ANY 
 * PARTICULAR PURPOSE. 
 */

/* This PlugIn represents a tool for analysis of HESXRD data, i.e. 2D diffraction
 * patterns.
 *      Mikhail Shipilin 
 *      mikhail.shipilin@sljus.lu.se
 */
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.PlugIn;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;


public class HESXRD_ extends JFrame implements PlugIn, ActionListener{
   
    // <editor-fold defaultstate="collapsed" desc="GUI related global variables">
    private final String[] GENERAL_OPTIONS = {      "Load settings",
                                                   "Rod extraction",
                                                     "In-plane cut",
                                                            "Reset",
                                                            "About",
                                                             "Exit"}; 
    
    private final String[] CTR_EXTRACTION_OPTIONS = {  "Extract profile",
                                                       "Extract 3D data",
                                                          "Plot results",
                                                     "Save rod profiles"};
    
    private final String[] GENERAL_BUTTONS_TIPS = {"Load parameters of experiment from file",
                             "Start diffraction rod profile extraction or 3D reconstruction",
                                       "Extract hk-cut at certain l-value or several values",
                                                             "Reset plugin for new data set",
                                                              "Information about the PlugIn",
                                                                               "Exit PlugIn"};

    private final String[] CTR_EXTRACTION_BUTTONS_TIPS = {                        "Extract structure factor values along CTR/SR",
                                                             "Extract 3D segment of reciprocal space containing diffarction rod",
                                                                                       "Plot structure factor values VS L_value",
                                                          "Save in-plane intensity profiles and fitting functions along the rod"};
    private JButton[] buttons;
    private Dimension widgetDimension;//Dimensions of widgets in plugin GUI 
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Data treatment related global variables">
    private ExperimentHandler experiment; //Container for experiment details
    private RodExtractor extractor;       //Container for diffraction rod extraction information
    private HKextractor hk;               //Container for in-plane cut extraction information
    //private ImagePlus hkImp;            //Contains image information from hk projections 
    private JFileChooser fileChooser;     //File manager
    // </editor-fold>
    private ImagePlus imp;                //Container of all our pixels data
    private int pluginMode;               // Variable that shows what mode the plagin is operating in
                                          // 1-initial value, when no choise was done
                                          // 2-rod extraction
                                          // 3-in plane projection
    
//******************************************************************************
//*** METHODS
//******************************************************************************
    /**
     * For testing and debugging
     */
    public static void main(final String... args){
        new ij.ImageJ();
        new HESXRD_().run("");
    }
    
    @Override
    public void run(String arg) {        
        //Bring window style in accordance with operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } 
        catch (Exception e) {
        }  
        setupServiceParameters();
        setupGUIcomponents();
        createAndShowGUI(1);
    } 
    
    private void setupServiceParameters(){
        fileChooser = new JFileChooser(System.getProperty("user"));
        pluginMode = 1;
    }
    
    public void exitPlugin() {
        this.setVisible(false);
        this.experiment = null;
        this.extractor = null;
        this.hk = null;
        this.fileChooser = null;
        try{
            imp.getWindow().dispose();
        }
        catch(Exception e){
            //if the window was closed by user it will throw exception
        }
        this.imp = null;
        // this will make sure WindowListener.windowClosing() et al. will be called.
        WindowEvent wev = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(wev);
        this.dispose();
        IJ.freeMemory();
    }

   
    //Resets main parameters of the plugin except experimental details and
    //filechooser position.
    private void resetPlugin(){
        this.extractor = null;
        this.hk = null;
        File f = this.fileChooser.getCurrentDirectory();
        fileChooser = new JFileChooser(f.getAbsolutePath());
        switch(pluginMode){
            case 1:
                break;
            case 2:                
                if(!IJ.showMessageWithCancel("Reset plugin", "Do you want to continue work with currently opened images?")){
                    try{
                        imp.getWindow().dispose();
                    }
                    catch(Exception e){
                        //if the window was closed by user it will throw exception
                    }
                    this.imp = null;
                    this.dispose();
                    EnableButton("Load settings", false);
                    EnableButton("Rod extraction", true);
                    EnableButton("In-plane cut", true);
                    createAndShowGUI(1);
                }
                else{
                    imp.deleteRoi();
                    this.dispose();
                    setupGUIcomponents();
                    EnableButton("Extract profile", true);//Allow user to press button
                    createAndShowGUI(2);
                }
                break;
            case 3:
                try{
                    imp.getWindow().dispose();
                }
                catch(Exception e){
                    //if the window was closed by user it will throw exception
                }
                this.imp = null;
                EnableButton("In-plane cut", true);
                EnableButton("Rod extraction", true);
                this.dispose();
                createAndShowGUI(1);
                break;
            default:
                break;
        }
    }
   
    private void setupGUIcomponents(){
        buttons = new JButton[GENERAL_OPTIONS.length + CTR_EXTRACTION_OPTIONS.length];
        widgetDimension = new Dimension(170,23);//Dimensions of widgets in plugin GUI
        
        //Define array of buttons that will be used in GUI
        //include general buttons
        for(int i = 0; i < GENERAL_OPTIONS.length; i++){
            buttons[i] = new JButton(GENERAL_OPTIONS[i]);
            buttons[i].setActionCommand(GENERAL_OPTIONS[i]);
            buttons[i].setPreferredSize(widgetDimension);
            buttons[i].setAlignmentX(CENTER_ALIGNMENT);
            buttons[i].addActionListener(this);
            buttons[i].setToolTipText(GENERAL_BUTTONS_TIPS[i]);
            //Some functions are unavailable before preliminary operations, so, corresponding
            //buttons should be unavailable also.
            if(GENERAL_OPTIONS[i].equals("Load settings") ||
                         GENERAL_OPTIONS[i].equals("Reset") ||
                         GENERAL_OPTIONS[i].equals("About") ||
                          GENERAL_OPTIONS[i].equals("Exit")){
                buttons[i].setEnabled(true);
            }
            else{
                buttons[i].setEnabled(false);
            }
        }
        //include optional buttons
        for(int j = GENERAL_OPTIONS.length; j < GENERAL_OPTIONS.length + CTR_EXTRACTION_OPTIONS.length; j++){
            buttons[j] = new JButton(CTR_EXTRACTION_OPTIONS[j-GENERAL_OPTIONS.length]);
            buttons[j].setActionCommand(CTR_EXTRACTION_OPTIONS[j-GENERAL_OPTIONS.length]);
            buttons[j].setPreferredSize(widgetDimension);
            buttons[j].setAlignmentX(CENTER_ALIGNMENT);
            buttons[j].addActionListener(this);
            buttons[j].setToolTipText(CTR_EXTRACTION_OPTIONS[j-GENERAL_OPTIONS.length]);
            buttons[j].setEnabled(false);
        }
    }
    
    // Method for creating initial GUI (parameter stage shows that the method
    // is called either for the first time or for the second)    
    private void createAndShowGUI(int stage){
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.PAGE_AXIS));
        
        Dimension gap = new Dimension(0,10);//Gaps between widgets 
        switch (stage){ 
            case 1:/*First call of createAndShowGUI method*/
               
                //Create panel for loading of details of experiment
                JPanel load_experiment_panel = new JPanel();
                load_experiment_panel.setLayout(new BoxLayout(load_experiment_panel, BoxLayout.PAGE_AXIS));
                JLabel load_experiment_label = new JLabel("Load details of experiment:");
                load_experiment_label.setAlignmentX(CENTER_ALIGNMENT);
                load_experiment_label.setPreferredSize(widgetDimension);
                load_experiment_panel.add(Box.createRigidArea(gap));
                load_experiment_panel.add(load_experiment_label);
                load_experiment_panel.add(Box.createRigidArea(gap));
                load_experiment_panel.add(buttons[0]); //Load settings
                mainPanel.add(load_experiment_panel);
                
                //Create panel for action choosing
                JPanel choose_action_panel = new JPanel();
                choose_action_panel.setLayout(new BoxLayout(choose_action_panel, BoxLayout.PAGE_AXIS));
                JLabel choose_action_label = new JLabel("Choose action:");
                choose_action_label.setAlignmentX(CENTER_ALIGNMENT);
                choose_action_label.setPreferredSize(widgetDimension);
                choose_action_panel.add(Box.createRigidArea(gap));
                choose_action_panel.add(choose_action_label);
                choose_action_panel.add(Box.createRigidArea(gap));
                choose_action_panel.add(buttons[1]); //Rod extraction
                choose_action_panel.add(Box.createRigidArea(gap));
                choose_action_panel.add(buttons[2]); //In-plane cut
                mainPanel.add(choose_action_panel);
                
                break;
            case 2:/*Second call of createAndShowGUI method*/
                
                //Create panel for CTR extraction
                JPanel CTR_extraction_panel = new JPanel();
                CTR_extraction_panel.setLayout(new BoxLayout(CTR_extraction_panel, BoxLayout.PAGE_AXIS));
                JLabel CTR_extraction_label = new JLabel("Choose extraction option:");
                CTR_extraction_label.setAlignmentX(CENTER_ALIGNMENT);
                CTR_extraction_label.setPreferredSize(widgetDimension);
                CTR_extraction_panel.add(Box.createRigidArea(gap));
                CTR_extraction_panel.add(CTR_extraction_label);
                for(int i = GENERAL_OPTIONS.length; i < buttons.length; i++){ 
                    if((buttons[i].getText()).equals("Extract profile")||(buttons[i].getText()).equals("Extract 3D data"))
                        buttons[i].setEnabled(true);
                    CTR_extraction_panel.add(Box.createRigidArea(gap));
                    CTR_extraction_panel.add(buttons[i]);
                }
                mainPanel.add(CTR_extraction_panel);
                
                break;
        }

        //Create service panel
        JPanel service_panel = new JPanel();
        service_panel.setLayout(new BoxLayout(service_panel, BoxLayout.PAGE_AXIS));
        service_panel.add(Box.createRigidArea(gap));
        service_panel.add(buttons[3]); //Reset
        service_panel.add(Box.createRigidArea(gap));
        service_panel.add(buttons[4]); //About
        service_panel.add(Box.createRigidArea(gap));
        service_panel.add(buttons[5]); //Exit
        service_panel.add(Box.createRigidArea(gap));
        mainPanel.add(service_panel);
        
        this.setContentPane(mainPanel);
        this.pack();
        this.setResizable(false);
        this.setAlwaysOnTop(false);
        this.setVisible(true);      
    }    
    
    @Override
    public void actionPerformed(ActionEvent e) {

        String event = e.getActionCommand();
        int optionNumber = 999;
        for(int i = 0; i < buttons.length; i++){
            if((buttons[i].getText()).equals(event)){
                optionNumber = i;
                break;
            }
        }
        
        switch(optionNumber){
            case 0 /*Load settings*/:
                for(;;){    
                    if(!LoadExperimentDetails()){
                        if(IJ.showMessageWithCancel("Warning", "Experiment details were not loaded.\n"+
                                                               "Do you want to try again?")){  
                            continue;
                        }
                        else
                            break;
                    }
                    else{
                        EnableButton("Load settings", false);//Don't allow user to press button again
                                                               //unless the opening process is failed
                        EnableButton("Rod extraction", true);
                        EnableButton("In-plane cut", true);
                        break;
                    }
                }
                break;
            case 1 /*Rod extraction*/:                 
                if(ChooseFiles(true)){ 
                    ImageStack stack = ImageLoader.LoadStack(fileChooser.getSelectedFiles());
                    if(stack == null){
                        break;
                    }
                    imp = new ImagePlus("Stack " + fileChooser.getSelectedFiles()[0].getName() + " - " + 
                            fileChooser.getSelectedFiles()[fileChooser.getSelectedFiles().length - 1].getName(), stack);
                    imp.show();
                    //Repaint GUI with new buttons
                    EnableButton("Extract profile", true);//Allow user to press button
                    pluginMode = 2;//Now plugin nows that user works with rod extraction
                    this.dispose();
                    createAndShowGUI(2);
                }
                break;
            case 2 /*In-plane cut*/:
                EnableButton("Rod extraction", false);
                EnableButton("In-plane cut", false);
                pluginMode = 3;
                for(;;){    
                    if(!ChooseFiles(true)){ 
                        if(IJ.showMessageWithCancel("Warning", "Images were not loaded.\n"+
                                                       "Do you want to try again?")){  
                            continue;
                        }
                        else
                            break;
                    }
                    else{
                        hk = new HKextractor(experiment);
                        if(hk.makeProjections(fileChooser.getSelectedFiles())){
                            String impName = hk.getStackName();
                            imp = new ImagePlus(impName, hk.getImageStack());
                            imp.show();
                            break;
                        }
                        else{
                            IJ.error("The process of calculation of In-plane cuts was failed.");
                            resetPlugin();
                            break;
                        }
                    }
                }
                break; 
            case 3 /*Reset*/:
                resetPlugin();
                break;               
            case 4 /*About*/: 
                showInfo();
                break; 
            case 5 /*Exit*/: 
                exitPlugin();
                break;
            case 6 /*Extract profile*/: 
                EnableButton("Extract profile", false);
                extractor = new RodExtractor(experiment);
                if(!extractor.Extract(imp)){
                    EnableButton("Extract profile", true);
                    break;
                }
                EnableButton("Plot results", true);
                EnableButton("Save rod profiles", true);               
                break;
            case 7 /*Extract 3D*/: 
                break;
            case 8 /*Plot results*/:
                extractor.plotResults();
                break;
            case 9 /*Save rod profiles*/:
                extractor.SaveRodProfiles(fileChooser);
                break;
            default:
                break;
        }

    }  
    
    /**
     * 
     * @param multiSelection - true for multi selection, false for single file choosing
     * @return true if files were found, false if some errors occurred
     */
    private boolean ChooseFiles(boolean multiSelection){
        fileChooser.setMultiSelectionEnabled(multiSelection);
        fileChooser.setDialogTitle("Choose diffraction patterns");
        fileChooser.setFileFilter(new FileFilter()
                {
                   @Override
                   public boolean accept(File file)
                   {
                       if (file.isDirectory()) {
                           return true;
                       }

                       String fileName = file.getName();
                       String extension = fileName.substring(fileName.lastIndexOf('.'),fileName.length());
                       if (extension != null) {
                           if (extension.toUpperCase().equals(".TIFF") ||
                               extension.toUpperCase().equals(".TIF")){
                                   return true;
                           } 
                           else {
                               return false;
                           }
                       }
                       return false;
                   }

                   @Override
                   public String getDescription()
                   {
                      return "'.tiff' or '.tif' files";
                   }
                });
        try{ // try to open chosen files
            int returnVal = fileChooser.showOpenDialog(this);
            if(returnVal==JFileChooser.APPROVE_OPTION){
                //If user didn't choose any files          
                if (fileChooser.getSelectedFiles().length == 0){
                    IJ.showMessage("Files were not loaded.");
                    return false;
                }
                    
            }
            else{
                IJ.showMessage("Files were not loaded.");
                return false;
            }
        }
        catch (Exception ex){
            IJ.error("Fatal error", "Can't open one ore more files\nError: " + ex.getMessage());
            return false;
        }        
        return true;
    }
    
    private void EnableButton(String buttonName, boolean enable){
        for(int i = 0; i < buttons.length; i++){
            if(buttons[i].getText().equals(buttonName)){
                this.buttons[i].setEnabled(enable);
                this.repaint();
            }
        }
    }
    
    // Extracts the transformation parameters from .txt file
    private boolean LoadExperimentDetails(){
        try{ // try to open file
            fileChooser.setMultiSelectionEnabled(false);//Only one file can be chosen at the moment
            int returnVal = fileChooser.showOpenDialog(this);
            if(returnVal==JFileChooser.APPROVE_OPTION){
                experiment = new ExperimentHandler(fileChooser.getSelectedFile());// Actual loading of experiment parameters 
            }
            else
                return false;
        } 
        catch (Exception e){
            IJ.error("Error", "Can't open file\nError: " + e.getMessage());
            return false;
        }
        return true;
    }
    
    private void showInfo(){
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        JFrame infoFrame = new JFrame("About");
        infoFrame.setSize((int)(screenSize.getWidth()/2), (int)(screenSize.getHeight()/2));
        infoFrame.setResizable(true);
        infoFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        
        String infoText = "************************************************\n" +
"* ImageJ PlugIn for HESXRD data treatment v3.0 *\n" +
"************************************************\n" +
"\n" +
"     The author of this software is Mikhail Shipilin.  Copyright (c) 2012.\n" +
"     Permission to use, copy, modify, and distribute this software for any\n" + 
"     purpose is hereby granted, provided that this entire notice is included in\n" +
"     all copies of any software which is or includes a copy or modification of this\n" + 
"     software and in all copies of the supporting documentation for such software.\n" +
"     Any for profit use of this software is expressly forbidden without first\n" +
"     obtaining the explicit consent of the author.\n" + 
"     THIS SOFTWARE IS BEING PROVIDED \"AS IS\", WITHOUT ANY EXPRESS OR IMPLIED WARRANTY.\n" + 
"     IN PARTICULAR, THE AUTHOR DOES NOT MAKE ANY REPRESENTATION OR WARRANTY\n" + 
"     OF ANY KIND CONCERNING THE MERCHANTABILITY OF THIS SOFTWARE OR ITS FITNESS FOR ANY\n" + 
"     PARTICULAR PURPOSE.\n" + 
"\n" +
"     This PlugIn represents a tool for analysis of HESXRD data, i.e. 2D diffraction patterns.\n" +
"        Mikhail Shipilin\n" + 
"        mikhail.shipilin@sljus.lu.se\n" +
"\n" +
"1. Check that you have '.txt' file with preferences for your particular experiment. It must have exactly the same\n" +
"	form as shown below between two dashed lines (dashed lines should not be included):\n" +
"\n" +
"        -------------------------------------------------------------------------------------------\n" +
"        % Preferences for treatment of data obtained during the experiment\n" +
"        % [ev]\n" +
"        PHOTONENERGY 85000\n" +
"        % [fraction of one]\n" +
"        HORIZONTALPOLARIZATION 1\n" +
"        % [degrees] If scan was performed from angular position different than 0, \n" +
"        % you should consider both initial shift and starting value here\n" +
"        % For example: initial shift -29.63 and scan from 6 to 96 result in -23.63\n" +
"        INITIALAZIMUTHALSHIFT -23.63\n" +
"        % [degrees]\n" +
"        INCIDENTANGLE -0.04\n" +
"        % [mm]\n" +
"        DETECTORDISTANCE 1750\n" +
"        % a1 a2 a3[angstrems]\n" +
"        LATTICEPARAMETERS 2.75 2.75 3.89\n" +
"        % a1a2 a2a3 a3a1[degrees]\n" +
"        LATTICEANGLES 90 90 90\n" +
"        % width height[mm]\n" +
"        DETECTORSIZE 410 410\n" +
"        % width height [pixels]\n" +
"        DETECTORRESOLUTION 2048 2048\n" +
"        % x z[pixels from the top left corner]\n" +
"        CENTERPIXELCOORDINATES 1050 1009\n" +
"        -------------------------------------------------------------------------------------------\n" +
"\n" +
"	a)Lines starting with '%' are treated as comments\n" +
"	b)PHOTONENERGY - beam energy in eV\n" +
"	c)HORIZONTALPOLARIZATION - value of horizontal polarization of the beam in fractions of unity\n" +
"		(usually for synchrotrons this value is between 0.8 and 1)\n" +
"	d)INITIALAZIMUTHALSHIFT - the initial angular position (offset) of the sample in degrees\n" +
"		(angle between the beam direction and sample's Y axis (K in reciprocal space)\n" +
"		when the diffractometer Omega (rocking) motor is in it's 0 position)\n" +
"	e)INCIDENTANGLE - the value of sample tilt angle in degrees\n" +
"		(for grazing incidence it is quite small value)\n" +
"	f)DETECTORDISTANCE - distance from sample to detector in mm\n" +
"	g)LATTICEPARAMETERS - values of realspace unit cell vectors in angstrems \n" +
"	h)LATTICEANGLES - angles between realspace unit cell vectors in degrees \n" +
"		(in the order a1a3(alpha), a2a3(beta), a1a2(gamma)) \n" +
"	i)DETECTORSIZE - width and height of the detector in mm\n" +
"       j)DETECTORRESOLUTION - width and height of the detector in pixels\n" +
"	k)CENTERPIXELCOORDINATES - coordinates of the detector's pixel wich lays in the center \n" +
"		of direct incident beam (number of pixels from upper left corner to the right and to the bottom)\n" +
"\n" +
"2. If you plan to work with large amount of data at once you should expand the limits of RAM memory\n" +
"   that ImageJ is working with.\n" +
"   Go to Edit -> Options -> Memory&Threads and enter the value of Maximum Memory that is available\n" +
"   in your computer (8192 MB for 8GB of RAM, for example).\n" +
"\n" +
"3. Press \"Load settings\" button to choose the file with preferences (the one described in step 1).\n" +
"\n" +
"4. Press \"Rod extraction\" button for calculation of diffraction rod profile or 3D reconstruction,\n" +
"   press \"In-plane cut\" button for calculation of In-plane cut.\n" +
"\n" +
"5. In all cases the program will ask you to choose images. It should be raw \".tiff\" images obtained from the detector.\n" +
"   You will be offered to rotate and flip images for processing, choose appropriate values. \n" +
"   Default values are for P07 beamline DESY Hamburg.\n" +
"   For \"In-plane cut\" all images of the scan normally should be chosen.\n" +
"   For \"Rod extraction\" you can choose either the part of the scan that contains one rod or all images\n" +
"   if you want to extract several rods in a row. In latter case don't forget to check the box\n" +
"   \"Use part of scan\" later on to choose the image range that you want to use for extraction of the current rod.\n" +
"\n" +
"6. For rod extraction procedure, choose the rectangular region of interest (ROI) containing single crystal truncation rod(CTR) \n" +
"   or surface rod(SR) using \"rectangular selection\" tool from ImageJ panel. You also can use \n" +
"   \"straight line\" tool following the central axis of the rod, for example if it is not vertical. Press \"Extract\" button. \n" +
"\n" +
"   The dialog where you can change the step of extraction along the rod and the width of treated area \n" +
"   will be shown to you. Smaller step means more extraction points along the rod and longer extraction time. \n" +
"   Don't forget to check the box \"Use part of scan\" if you loaded the whole scan in step 5.\n" +
"\n" +
"   Wait until progress bar will reach 100%. After that you can plot results and save them from \"plot\"-window  \n" +
"   by choosing \"List\" option and \"Save\" option from window menu.\n" +
"\n" +
"   By pressing the \"Save rod profiles\" button you can save the file with in-plane intensity profiles for current\n" +
"   rod and corresponding functions (Gaussian + Linear) that were used to calculate the integrated intensities and \n" +
"   structure factors. When saving write the name of output file with extention (.txt for instance).\n" +
"\n" +
"7. For In-plane cut calculation you are offered two options: either single projection at chosen L-value\n" +
"   or multiple projection (i.e. stack of projections at different L-values). In the last case you need to\n" +
"   specify the L-values interval and step.\n" +
"   You also can choose \"Integrate adjacent slices\" option to sum up number of slices in order to get higher\n"+
"   contrast.";
        
        JTextArea textArea = new JTextArea(infoText);
        textArea.setEditable(false);
        
        JScrollPane infoPane = new JScrollPane(textArea);
        infoFrame.getContentPane().add(infoPane);
        //infoFrame.pack();
        infoFrame.setLocationByPlatform(true);
        infoFrame.setVisible(true);
    }
}
  

