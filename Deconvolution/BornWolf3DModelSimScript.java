package simulations.models;

import static miatool.core.util.numerics.MIAMatOps.sum;
import java.io.File;
import java.io.IOException;
import miatool.core.util.*;
import miatool.core.util.miatiff.MIATIFFWriter;
import miatool.core.util.numerics.MIAMatOps;
import miatool.core.util.numerics.MIAUnits;
import miatool.modules.estimation.math.integration.TrapezoidalPixelIntegrator;
import miatool.modules.estimation.math.integration.TrapezoidalPsf3DIntegrator;
import miatool.modules.estimation.models.single.BornWolf3D;
import miatool.modules.estimation.util.Detector;

/**
 * "Script" to simulate a Born-Wolf 3D PSF image.
 * 
 * @author Anish V. Abraham
 * @since 1.0
 * @version 0.1
 */
public class BornWolf3DModelSimScript {

	public String dirPath;
	
	/** The string format of the name of the images */
	final public String imgFormat = "s1_%d.tif";
	// REQUIRED INPUTS ---------------------------------------------------------
	// Imaging parameters
	private double pixelHeight = 6.7; 	// microns
	private double pixelWidth = 6.7; 	// microns
	private double exposureTime = 1; 	// seconds
	private double magnification = 63;
	private double na = 1.4;
	private double immersionMediumRefractiveIndex = 1.515;
	private double wavelength = 0.48;	// microns
	private int nImages = 20;
	private double stepSize = 0.2;

	private int imageHeight = 100; 		// pixels
	private int imageWidth = 100; 		// pixels

	// Model specifications / parameters
	private double x0 = imageWidth * pixelWidth / 2 / magnification;
	private double y0 = imageHeight * pixelHeight / 2 / magnification;
	private double alpha = 2 * Math.PI * na / wavelength;
	private double background = 0;				// photons/pixel/s
	private double photonDetectionRate = 1000;	// photons/s

	// ADVANCED INPUTS ---------------------------------------------------------
	private int trapezoidalXGridding = 11;
	private int trapezoidalYGridding = 11;
	private double modelIntegrationStepSize = 0.001;

	BornWolf3D.Model modelObj = new BornWolf3D.Model();

	public static void main(final String [] args) {
		OpenCLUtil.initializeOpenCL();
		final BornWolf3DModelSimScript script;
		script = new BornWolf3DModelSimScript();
		script.execute();		
		System.out.println(sum(script.modelObj.getSimulatedProfile()));
	}

	// -------------------------------------------------------------------------
	void execute() {
		
		modelObj.psf.alpha = MIAUnits.alpha(alpha);
		modelObj.psf.na = na;
		modelObj.psf.immersionMediumRefractiveIndex = immersionMediumRefractiveIndex;
		modelObj.psf.wavelength = MIAUnits.microns(wavelength);

		// Background
		modelObj.backgroundFunction.background =
				MIAUnits.background(background);

		// Photon Detection Rate
		modelObj.photonScaling.photonDetectionRate =
				MIAUnits.pdr(photonDetectionRate);

		// Detector
		modelObj.detector = new Detector(
				MIAUnits.microns(pixelHeight),
				MIAUnits.microns(pixelWidth),
				MIAUnits.seconds(exposureTime),
				magnification);
		modelObj.imageIndices = new PixelCoordinates(
				imageHeight,
				imageWidth);

		// Integration Parameters
		modelObj.pixelIntegrator = new TrapezoidalPixelIntegrator(
				trapezoidalXGridding,
				trapezoidalYGridding,
				modelObj.detector);
		modelObj.modelIntegrator = new TrapezoidalPsf3DIntegrator(
				modelIntegrationStepSize);

		final double[] zLoci = getZLocations(stepSize, nImages);
		for (int i = 0; i < nImages; i++){
			modelObj.psf.x0 = MIAUnits.microns(x0);
			modelObj.psf.y0 = MIAUnits.microns(y0);
			modelObj.psf.z0 = MIAUnits.microns(zLoci[i]);			
			// Processing
			modelObj.simulateProfile();
			//save image
			saveImage(modelObj.get2DImage(), i);
		}

	}
	
	public double [] getZLocations(final double stepSize, 
				final int nsteps){
		final double range = (double) stepSize * nsteps;
		final double [] locations = new double [nsteps];
		for (int i = 0; i < nsteps; i++){
		locations[i] = (double) i * stepSize - range / 2;
		}
		return locations;
	}

	/**
	 * Writes the provided matrix as an image to disk.
	 * 
	 * @param image
	 *        The matrix containing the raw pixel values.
	 * @param i
	 *        The index of the image within the series of images currently being
	 *        generated.
	 */
	public final void saveImage(final double [][] image, final int i) {
	
		// create the folder that will store the images, if it doesn't already
		// exist
		final File dir = new File(dirPath);
		dir.mkdirs();
	
		// build the path to the single image to be saved
		final String imgName = MIAUtil.buildPath(
				dirPath, String.format(imgFormat, i + 1));
	
		// linearize the data and convert into a short array
		final double [] ddata = MIAMatOps.linearize(image);
		final short [] sdata = MIAMatOps.toShort(ddata);
	
		// write the data to disk
		final MIATIFFWriter mtw = new MIATIFFWriter(sdata, imageHeight, imageWidth);
		try {
			mtw.write(0, imgName);
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2016-08-11, 0.1, Anish V. Abraham: Recreated.
 *
 */
