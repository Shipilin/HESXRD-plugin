import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Dimension;
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
public class Stack_To_Total_Image implements PlugIn{
    
    private int width = 2048, height = 1050;
    
    @Override
    public void run(String arg) {
        //Bring window style in accordance with operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } 
        catch (Exception e) {
        }      
        File[] chosenFiles; // Array for file paths
        // <editor-fold defaultstate="collapsed" desc="Filling of array by user's chosen files. Provides multiple choice dialog.">
        try{ // try to fill array
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            int returnVal = fileChooser.showOpenDialog(null);
            if(returnVal==JFileChooser.APPROVE_OPTION)
                chosenFiles = fileChooser.getSelectedFiles();
            else
                return;
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
    
        if( createDialog() ){
            float[][] pixels = new float[width][height];
            float pixel = 0;
            ImageProcessor ip = null;
            
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
            
            int percent;//progress
            int filesCounter = 0;
            for(File file : chosenFiles){
                if(filesCounter == chosenFiles.length)
                    percent = 100;
                else
                    percent = Math.round(((float)100/(float)(chosenFiles.length))*(float)(++filesCounter));    
                ip = (new ImagePlus(file.getAbsolutePath())).getProcessor();
                for(int i = 0; i < width; i++){
                    for(int j = 0; j < height; j++){
                        pixel = ip.getPixelValue(j+1, width - i);
                        if(pixel > pixels[i][j])
                            pixels[i][j]=pixel;
                    }
                }
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics()); 
            }
            
            frame.dispose();
            
            FloatProcessor fp = new FloatProcessor(pixels);
            ImageProcessor imp = fp;
            imp.flipHorizontal();
            ImagePlus img = new ImagePlus();
            img.setProcessor(imp);
            img.show();
            
        }
    }
    
    private boolean createDialog() {
        GenericDialog gd = new GenericDialog("Image cropping");
        gd.addMessage("Image will be rotated 90 degrees clockwise and flipped horizontally.\n"
                    + "Bottom part will be removed below specified point: ");
        gd.addNumericField("Y coordinate of image center", height, 0, 4, "pix");
      
        gd.showDialog();
        if (gd.wasCanceled())
            return false;

        height = (int)gd.getNextNumber();
        
        return true; 
    }
}
