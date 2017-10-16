package simulations.models;

import static miatool.core.util.numerics.MIAUnits.microns;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import miatool.core.MIAConstants;
import miatool.core.util.MIAProfiler;
import miatool.core.util.MIAUtil;
import miatool.core.util.OpenCLUtil;
import miatool.core.util.Parallel;
import miatool.core.util.PixelCoordinates;
import miatool.core.util.miatiff.MIATIFFWriter;
import miatool.core.util.numerics.MIAMatOps;
import miatool.core.util.numerics.MIAUnits;
import miatool.modules.estimation.math.integration.TrapezoidalPixelIntegrator;
import miatool.modules.estimation.math.integration.TrapezoidalPsf3DIntegrator;
import miatool.modules.estimation.models.single.BornWolf3D;
import miatool.modules.estimation.util.Detector;

// TODO: 2015-07-31, Anish V. Abraham: Documentation.
public class MultiPointBornWolf3DImageSimulator {

	// Inputs ------------------------------------------------------------------

	// Image Parameters
	/** the width of the resultant image, in pixels */
	public int width = 512;

	/** the height of the resultant image, in pixels */
	public int height = 512;

	/** the number of simulated spots per image */
	public int nSpotsPerImage = 100;

	/** the number of simulated images that will be saved to disk */
	public int nImages = 20;

	/** The step size in the z transition */
	public double stepSize = 0.1;

	/**
	 * The directory where the image files will be saved (not a
	 * MIAToolDirectory; simply where the files will be saved on disk)
	 */
	public String dirPath
			= "C:\\Users\\user\\Desktop\\Sungyong\\deconvolution image\\image\\";

	// Detector Parameters
	/** the magnification of the image on the detector model */
	public double magnification = 63;

	/** the width of each pixel on the detector, in microns */
	public double pixelWidth = 6.7;

	/** the height of each pixel on the detector, in microns */
	public double pixelHeight = 6.7;

	/**
	 * the length of time that the detector model is exposed to the emitted
	 * photons from the object, in seconds
	 */
	public double exposureTime = 1.0;

	// Model Parameters
	/** The numerical aperture of the objective model */
	final public double NA = 1.4;

	/** The wavelength of light emitted from the point source; in microns */
	final public double lambda = 0.48;

	/**
	 * The alpha value of the system; usually precalculated as:
	 * 
	 * <pre>
	 * 2 * Math.PI * NA / lambda
	 * </pre>
	 */
	final public double alpha = 2 * Math.PI * NA / lambda;

	/** The step size for the model (precision of the model) */
	final public double modelStepSize = 0.01;

	/** The refractive index of the immersion oil */
	final public double refractiveIndex = 1.515;

	// Calculation parameters
	/** The fineness of the pixel gridding for integration */
	final public int pixelGridX = 13;

	/** The fineness of the pixel gridding for integration */
	final public int pixelGridY = 13;

	/**
	 * the background parameter for the simulated spot (in photons / pixel / s
	 */
	public double background = 0;

	/**
	 * The photon detection rate (how many photons were detected in the region
	 * of the spot per second of exposure), in photons / s
	 */
	public double pdr = 50000;

	// SCRIPT SPECIFIC = DO NOT CHANGE ====================================
	/** The model with which to simulate the spots */
	final private BornWolf3D.Model modelObj = new BornWolf3D.Model();

	/** The string format of the name of the images */
	final public String imgFormat = "s1_%d.tif";

	public MultiPointBornWolf3DImageSimulator() {
	}

	public static void main(final String[] args)
			throws IllegalArgumentException,
			IllegalAccessException {
		final MultiPointBornWolf3DImageSimulator simulator;
		simulator = new MultiPointBornWolf3DImageSimulator();
		simulator.execute();
	}

	public Dimension calculateSingleSize(final double sigma) {
		// Calculate sigma width in pixels
		final double pixelSigmaW = sigma * magnification / pixelWidth;
		final double pixelSigmaH = sigma * magnification / pixelHeight;

		// Image should be at least 6 sigma large
		final double imageWidth = pixelSigmaW * 50;
		final double imageHeight = pixelSigmaH * 50;

		final int qImageW = (int) Math.ceil(imageWidth);
		final int qImageH = (int) Math.ceil(imageHeight);

		return new Dimension(qImageW, qImageH);
	}

	public void execute()
			throws IllegalArgumentException,
			IllegalAccessException {

		// make sure that the OpenCL is up and running
		OpenCLUtil.initializeOpenCL();

		// start the parallel pool engine
		Parallel.startPool();

		// set up a timer for the execution
		MIAProfiler.tic();

		// set up general model parameters that will not be changed throughout
		// the simulation
		setupModel(modelObj);

		final double[][] xyLocations = getXYLocations(nSpotsPerImage);
		final double[][] zLocations = getZLocations(nSpotsPerImage, stepSize, nImages);
		// loop through and simulate all images
		for (int i = 0; i < nImages; i++) {
			final double[] zLocation = MIAMatOps.getCol(zLocations, i);
			simulateImage(i, xyLocations, zLocation);
		}

		// retrieve timing information
		final double time = MIAProfiler.toc();
		System.out.println("Elapsed time: " + time + "ms");

		// shut down the parallel engine
		Parallel.shutdownPool();
	}

	/**
	 * Generates a series of spot locations for a particular image in the
	 * sequence.
	 * 
	 * @param nspots
	 *        the number of spots that will be simulated for the image
	 * 
	 * @return The x,y,z location coordinates of all the spots for the image at
	 *         the current index in the series.
	 */
	public double[][] getXYLocations(final int nspots) {

		final double[][] locations = new double[nspots][];

		final double pixelFactor = pixelWidth / magnification;

		for (int i = 0; i < nspots; i++) {
			final double x = Math.random() * width * pixelFactor;
			final double y = Math.random() * height * pixelFactor;
			locations[i] = new double[] { x, y };
		}

		return locations;
	}

	/** Get an array of random z locations 
	 * 
	 * @param stepSize  
	 *        The step size of the scan
	 *        
	 * @param nSteps
	 *        The number of steps
	 */
	public double[][] getZLocations(
			final int nspots,
			final double stepSize,
			final int nsteps) {
		final double range = (double) stepSize * nsteps;
		final double[][] locations = new double[nspots][nsteps];
		for (int i = 0; i < nspots; i++) {
			final double offset = Math.random() - 0.5;
			for (int j = 0; j < nsteps; j++)
				locations[i][j]= (double) (j * stepSize - range / 2 + offset);
		}
		return locations;
	}

	/**
	 * The sequential version of the image simulation
	 * 
	 * @param i
	 *        the index of the image to simulate
	 */
	public void simulateImage(
			final int i,
			final double[][] xyLoci,
			final double[] zLoci) {
		double x0, y0, z0;

		final double[][] image = new double[height][width];

		for (int l = 0; l < xyLoci.length; l++) {
			x0 = xyLoci[l][0];
			y0 = xyLoci[l][1];
			z0 = zLoci[l];

			final Dimension simSize = calculateSingleSize(1.323 / alpha);
			final int simW = simSize.width;
			final int simH = simSize.height;

			// calculate the actual x-y values, scaled by magnification
			final double x1 = x0 * magnification;
			final double y1 = y0 * magnification;

			/*
			 * shift the x and y coordinates, because we do not want to simulate
			 * the entire image for each individual spot; conserve memory and
			 * CPU power
			 */
			final int shiftedPixelX = (int) (x1 / pixelWidth) - simW / 2;
			final int shiftedPixelY = (int) (y1 / pixelHeight) - simH / 2;

			final double[][] modelImage = simulateSingleSpot(
					modelObj,
					x0,
					y0,
					z0,
					1.323 / alpha,
					background,
					pdr,
					simW,
					simH);

			// place the simulated image into the shifted position
			for (int y = 0; y < simH; y++) {
				for (int x = 0; x < simW; x++) {
					final int oX = x + shiftedPixelX;
					final int oY = y + shiftedPixelY;

					if (oX < 0 || oY < 0 || oX >= width || oY >= height) {
						continue;
					}

					image[oY][oX] += modelImage[y][x];
				}
			}

			System.out.println("Simulated [" + l + "]");
		}

		saveImage(image, i);
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
	public final void saveImage(final double[][] image, final int i) {

		// create the folder that will store the images, if it doesn't already
		// exist
		final File dir = new File(dirPath);
		dir.mkdirs();

		// build the path to the single image to be saved
		final String imgName = MIAUtil.buildPath(
				dirPath,
				String.format(imgFormat, i + 1));

		// linearize the data and convert into a short array
		final double[] ddata = MIAMatOps.linearize(image);
		final short[] sdata = MIAMatOps.toShort(ddata);

		// write the data to disk
		final MIATIFFWriter mtw = new MIATIFFWriter(sdata, width, height);
		try {
			mtw.write(0, imgName);
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set the input parameters to the given model
	 * 
	 * @param modelObj
	 *        the model object that will be set up
	 */
	public void setupModel(final BornWolf3D.Model modelObj) {
		// set up detector for the model
		modelObj.detector = new Detector(
				MIAUnits.microns(pixelHeight),
				MIAUnits.microns(pixelWidth),
				MIAUnits.seconds(exposureTime),
				magnification);

		modelObj.psf.alpha = MIAUnits.alpha(alpha);
		modelObj.psf.na = NA;
		modelObj.psf.wavelength = MIAUnits.microns(lambda);
		modelObj.psf.immersionMediumRefractiveIndex = refractiveIndex;

		modelObj.modelIntegrator
				= new TrapezoidalPsf3DIntegrator(modelStepSize);
		modelObj.pixelIntegrator = new TrapezoidalPixelIntegrator(
				pixelGridX,
				pixelGridY,
				modelObj.detector);
	}

	/**
	 * Simulates a single spot image using the model and parameters
	 * 
	 * @param x
	 *        the x position of the spot in microns
	 * @param y
	 *        the y positino of the spot in microns
	 * 
	 * @return a 2D matrix of intensity values, giving the simulated profile of
	 *         the spot
	 */
	public double[][] simulateSingleSpot(
			final BornWolf3D.Model modelObj,
			final double x,
			final double y,
			final double z,
			final double s,
			final double bg,
			final double pdr,
			final int sw,
			final int sh) {

		final int pX = (int) ((x * magnification) / pixelWidth);
		final int pY = (int) ((y * magnification) / pixelWidth);

		final int l = pX - sw / 2;
		final int r = pX + sw / 2;
		final int t = pY - sh / 2;
		final int b = pY + sh / 2;

		final int[] xrange = MIAMatOps.colon(l, 1, r);
		final int[] yrange = MIAMatOps.colon(t, 1, b);

		// Model Parameters
		modelObj.psf.x0 = microns(x);
		modelObj.psf.y0 = microns(y);
		modelObj.psf.z0 = microns(z);

		// Background
		modelObj.backgroundFunction.background = MIAUnits.background(bg);

		// Photon Detection Rate
		modelObj.photonScaling.photonDetectionRate = MIAUnits.pdr(pdr);

		modelObj.imageIndices = new PixelCoordinates(
				MIAConstants.ZERO_INDEXED,
				xrange,
				yrange,
				true);

		// Processing
		modelObj.simulateProfile();

		return modelObj.get2DImage();
	}
}
