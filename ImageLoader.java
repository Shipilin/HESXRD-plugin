import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import java.awt.Dimension;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 *
 * @author Mikhail Shipilin
 */
public class ImageLoader {
    
    public static ImageStack LoadStack(File[] files){
        int stackWidth = 0, stackHeight = 0;// Height and width of future stack
        for(int i = 0; i < files.length; i++){
            try{
                ImageProcessor temp_ip = (new ImagePlus(files[i].getAbsolutePath())).getProcessor();   
                stackWidth = temp_ip.getWidth();                  
                stackHeight = temp_ip.getHeight();
                break;
            }
            catch (Exception e){
                IJ.error("Error", "File can't be open as an image: " + e.getMessage());
                return null;
            }
        }
        
        //Bring window style in accordance with operating system
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } 
        catch (Exception e) {
        }
        //Define parameters of progress bar 
        JFrame pBarFrame = new JFrame("Progress");
        pBarFrame.setResizable(false);
        pBarFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); 
        JProgressBar pBar = new JProgressBar(0, 100);//Progress bar 0 - 100%
        pBar.setStringPainted(true);
        pBar.setPreferredSize(new Dimension(170,23)); 
        pBarFrame.add(pBar);
        pBarFrame.pack();
        pBarFrame.setLocationByPlatform(true);
        
        
        GenericDialog gd = new GenericDialog("Stack loading options");
        gd.addNumericField("Rotate clockwise:", 90, 2);
        gd.addCheckbox("Flip horizontally", true);
        //gd.addMessage("Crop images:");
        //gd.addNumericField("Width", stackWidth, 0);
        //gd.addNumericField("Height", stackHeight, 0);
        
        ImageStack stack = null;
        gd.showDialog();
        if (gd.wasOKed()){
            pBarFrame.setVisible(true);
            double rotation = gd.getNextNumber();
            boolean flip = gd.getNextBoolean();
            //stackWidth = (int)gd.getNextNumber();
            //stackHeight = (int)gd.getNextNumber();
            stack = new ImageStack(stackWidth, stackHeight);
            for (int i = 0; i < files.length; i++ ) {
                final int percent;
                if(i == files.length - 1)
                    percent = 100;
                else
                    percent = Math.round(((float)100/(float)files.length)*(float)(i+1));                
                
                ImageProcessor ip = (new ImagePlus(files[i].getAbsolutePath())).getProcessor();
                ip.rotate(rotation);
                if(flip)
                    ip.flipHorizontal();
                ip.setRoi(0, 0, stackWidth, stackHeight);
                ip = ip.crop();
                stack.addSlice(ip);
                pBar.setValue(percent);
                pBar.update(pBar.getGraphics());
            }
            pBarFrame.dispose();
        }
        return stack;
    }
}
