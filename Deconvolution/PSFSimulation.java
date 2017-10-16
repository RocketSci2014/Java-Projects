package simulations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import miatool.core.util.OpenCLUtil;
import miatool.core.util.PixelCoordinates;
import miatool.core.util.numerics.MIAMatOps;
import miatool.core.util.numerics.MIAUnits;
import miatool.modules.estimation.math.integration.TrapezoidalPixelIntegrator;
import miatool.modules.estimation.math.integration.TrapezoidalPsf3DIntegrator;
import miatool.modules.estimation.models.single.BornWolf3D;
import miatool.modules.estimation.util.Detector;
import miatool.tools.Preview;

/**
 * Simulate a 3D PSF image with the given conditions
 * 
 * @author Bo Wang
 * @version 0.1
 * @since 1.0
 */
public class PSFSimulation {
	// Imaging parameters
	private double pixelHeight = 16; 	// microns
	private double pixelWidth = 16; 	// microns
	private double exposureTime = 1; 	// seconds
	private double magnification = 63;
	private double na = 1.4;
	private double immersionMediumRefractiveIndex = 1.515;
	private double wavelength = 0.48;	// microns
	private int nImages = 20;
	private double stepSize = 0.1;		// 100 nm

	private int imageHeight = 30; 		// pixels
	private int imageWidth = 30; 		// pixels

	// Model specifications / parameters
	private double x0 = imageWidth * pixelWidth / 2 / magnification;
	private double y0 = imageHeight * pixelHeight / 2 / magnification;
	private double alpha = 2 * Math.PI * na / wavelength;
	private double background = 0;				// photons/pixel/s
	private double photonDetectionRate = 2000;	// photons/s

	// ADVANCED INPUTS ---------------------------------------------------------
	private int trapezoidalXGridding = 11;
	private int trapezoidalYGridding = 11;
	private double modelIntegrationStepSize = 0.001;

	private BornWolf3D.Model modelObj = new BornWolf3D.Model();
	public double[][][] psfData = new double[nImages][][];

	/**
	 * Temporary main, for testing purpose
	 */
	public static void main(final String[] args) {
		final PSFSimulation psfSim = new PSFSimulation();
		psfSim.execute();
		new Preview(psfSim.psfData[0]);
	}

	/**
	 * Constructor
	 */
	public PSFSimulation() {
		OpenCLUtil.initializeOpenCL();
	}

	/**
	 * Execute the simulation
	 */
	public void execute() {

		modelObj.psf.alpha = MIAUnits.alpha(alpha);
		modelObj.psf.na = na;
		modelObj.psf.immersionMediumRefractiveIndex
				= immersionMediumRefractiveIndex;
		modelObj.psf.wavelength = MIAUnits.microns(wavelength);

		// Background
		modelObj.backgroundFunction.background
				= MIAUnits.background(background);

		// Photon Detection Rate
		modelObj.photonScaling.photonDetectionRate
				= MIAUnits.pdr(photonDetectionRate);

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

		// simulate PSF on each plane
		System.out.println("Start PSF simulation.");
		
		List<Integer> tuples = new ArrayList<>();
		for (int i = 0; i < nImages; i++)
			tuples.add(i);
		
		tuples.parallelStream().forEach(tuple -> {
			modelObj.psf.x0 = MIAUnits.microns(x0);
			modelObj.psf.y0 = MIAUnits.microns(y0);
			final double zLoci = tuple * stepSize - nImages * stepSize / 2;
			modelObj.psf.z0 = MIAUnits.microns(zLoci);
			
			// Processing
			modelObj.simulateProfile();
			
			// save image
			psfData[tuple] = MIAMatOps.copy(modelObj.get2DImage());
		});
		
		System.out.println("The simulation is finished.");
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-7-15, 0.1, Bo Wang: Created
 * 
 */
