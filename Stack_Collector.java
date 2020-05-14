
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Mikhail Shipilin
 */
public class Stack_Collector implements PlugIn{

    protected ImageStack stack;
    private boolean flipHorizontally = true;
    private double rotation = 90;
    private double width = 2048, height = 1050;
    private double scaleFactor = 0.5;
    private static String[] bitDepthChoice = {"8", "16", "32"};
    private double bitDepth = 32;
    
    @Override
    public void run(String arg) {
        //Bring window style in accordance with operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } 
        catch (Exception e) {
        }  
                
        File[] chosenFiles = null; // Array for file paths
        // <editor-fold defaultstate="collapsed" desc="Filling of array by user's chosen files. Provides multiple choice dialog.">
        try{ // try to fill array
            //For different operating systems show different dialogues
            /*
            String os = System.getProperty("os.name");
            if(os.indexOf("win") >= 0){
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setMultiSelectionEnabled(true);
                int returnVal = fileChooser.showOpenDialog(null);
                if(returnVal==JFileChooser.APPROVE_OPTION)
                    chosenFiles = fileChooser.getSelectedFiles();
                else
                    return;
            }
            if(os.indexOf("mac") >= 0){
                JFrame frame = new JFrame();
                System.setProperty("apple.awt.fileDialogForFiles", "true");
                FileDialog fileChooser = new FileDialog(frame);
                fileChooser.setMultipleMode(true);
                fileChooser.setVisible(true);
                chosenFiles = fileChooser.getFiles();
            }
            */
            JFrame frame = new JFrame();
            System.setProperty("apple.awt.fileDialogForFiles", "true");
            FileDialog fileChooser = new FileDialog(frame);
            fileChooser.setMultipleMode(true);
            fileChooser.setVisible(true);
            chosenFiles = fileChooser.getFiles();
        } 
        catch (Exception e){
            IJ.error("Error", "Can't open one ore more files\nError: " + e.getMessage());
            return;
        }        
        //If user didn't choose any files          
        if (chosenFiles.length == 0){
            IJ.noImage();
            return;
        }
        // </editor-fold>
        int percent = 0;
        
        if( createDialog() ){
            JFrame frame = new JFrame("Progress");
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JProgressBar pBar = new JProgressBar(0, 100);
            pBar.setStringPainted(true);
            pBar.setPreferredSize(new Dimension(170,23));
            frame.add(pBar);
            frame.pack();
            frame.setLocationByPlatform(true);            
            frame.setVisible(true);
            
            stack = new ImageStack((int)(width*scaleFactor), (int)(height*scaleFactor));
            ImageProcessor imp;
            for (int i = 0; i < chosenFiles.length; i++ ) {
                if(i == chosenFiles.length)
                    percent = 100;
                else
                    percent = Math.round(((float)100/(float)(chosenFiles.length))*(float)(i));
                
                ImageProcessor ip = (new ImagePlus(chosenFiles[i].getAbsolutePath())).getProcessor();
                ip.rotate(rotation);
                if(flipHorizontally)
                    ip.flipHorizontal();

                //Cropping and reducing resolution
                ip.setRoi(0, 0, (int)width, (int)height);               
                imp = ip.crop();
                if(!(imp.getBitDepth() == (int)bitDepth)){
                    switch((int)bitDepth){
                        case 8:
                            imp = imp.convertToByte(true);
                            break;
                        case 16:
                            imp = imp.convertToShort(true);
                            break;
                        case 32:
                            imp = imp.convertToFloat();
                            break;
                        default:
                            break;
                    }
                }
                imp.setInterpolate(true); 
                if(!((int)scaleFactor == 1))
                    imp = imp.resize(stack.getWidth(), stack.getHeight());
                stack.addSlice(imp);
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics()); 
            }
            frame.dispose();
            ImagePlus img = new ImagePlus("Stack of images " + chosenFiles[0].getName() + " - " + chosenFiles[chosenFiles.length - 1].getName(), stack);
            img.show();
            //IJ.showMessage("If you want to save this stack as a movie, you should apply desired adjustments and save it as AVI file.");
        }
    }
    
    private boolean createDialog() {
        GenericDialog gd = new GenericDialog("Options");
        gd.addNumericField("Rotate clockwise:", rotation, 2);
        gd.addCheckbox("Flip horizontally",flipHorizontally);
        gd.addMessage("Crop images (cut off bottom part):");
        gd.addNumericField("Width", width, 0, 0, "pix");
        gd.addNumericField("Height", height, 0, 0, "pix");
        gd.addMessage("Decrease image resolution by factors:");
        gd.addNumericField("Factor", scaleFactor, 2);
        gd.addMessage("Change bit-depth of image:");
        gd.addChoice("Bit-depth", bitDepthChoice, "32");        
        gd.showDialog();
        if (gd.wasCanceled())
            return false;
        
        rotation = gd.getNextNumber();
        flipHorizontally = gd.getNextBoolean();
        width = gd.getNextNumber();
        height = gd.getNextNumber();
        scaleFactor = gd.getNextNumber();
        bitDepth = Double.parseDouble(bitDepthChoice[gd.getNextChoiceIndex()]);
        
        return true; 
    }
}

