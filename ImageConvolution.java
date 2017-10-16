package simulations;

import java.io.File;
import java.io.IOException;
import miatool.core.util.MIAUtil;
import miatool.core.util.MatrixUtil;
import miatool.core.util.Parallel;
import miatool.core.util.miatiff.MIATIFFWriter;
import simulations.StructureSimulation2D.Shape2D;
import simulations.StructureSimulation3D.Shape3D;
import simulations.StructureSimulation3D.Contour;
import static miatool.core.util.numerics.MIAMatOps.max;
import static miatool.core.util.numerics.MIAMatOps.truncate;
import static miatool.core.util.numerics.MIAMatOps.rescaleI;
import static miatool.core.util.numerics.MIAMatOps.toShort;
import static miatool.core.util.numerics.MIAMatOps.linearize;

/**
 * Convolve image with PSF, by conventional method or fast Fourier
 * transformation. The simulated pixel size is 16 um by 16 um, exposure time is
 * 1 s, magnification is 63 times, step size is 100 nm. The dimension of the
 * simulated object is 12 um x 12 um x 2 um if it is a volume box, or a cylinder
 * with 6 um radius and 2 um height. The photon detection rate of the camera is
 * 2000 photons / s. A simulated labeling dot takes the size of one pixel and is
 * one step size tall in z direction.
 * 
 * @author Bo Wang
 * @version 0.1
 * @since 1.0
 */
public class ImageConvolution {

	public final double[][][] imgMatrix;

	public final double[][][] psfMatrix;

	public double[][][] convolved;

	public static void main(final String[] args) {
		// simulate convolution to a cylinder
		new ImageConvolution(Shape3D.CYLINDER, Contour.SOLID);

		// simulate convolution to a volume box
		new ImageConvolution(Shape3D.BOX, Contour.SOLID);

		// simulate a line
		new ImageConvolution(Shape2D.LINE);

		// simulate a plane
		new ImageConvolution(Shape2D.PLANE);
	}

	/**
	 * Constructor 2D
	 * 
	 * @see #ImageConvolution(Shape3D, Contour)
	 */
	public ImageConvolution(final Shape2D shape) {
		final PSFSimulation psf = new PSFSimulation();
		psf.execute();
		psfMatrix = psf.psfData;
		saveImageSet(psfMatrix, "PSF");

		final StructureSimulation2D struc
			= new StructureSimulation2D(shape);
		struc.execute();
		imgMatrix = struc.strucData;

		final String name = shape.toString() + " ORIGINAL";
		saveImageSet(imgMatrix, name);

		execute2D(shape);
	}

	/**
	 * Constructor 3D
	 * 
	 * @param shape
	 *        The shape of the structure to be convolved
	 * 
	 * @param contour
	 *        The contour of the structure, dotted or solid
	 */
	public ImageConvolution(
			final Shape3D shape,
			final Contour contour) {

		final PSFSimulation psf = new PSFSimulation();
		psf.execute();
		psfMatrix = psf.psfData;
		saveImageSet(psfMatrix, "PSF");

		final StructureSimulation3D struc
			= new StructureSimulation3D(shape, contour);
		struc.execute();
		imgMatrix = struc.strucData;

		final String name = shape.toString() + " ORIGINAL";
		saveImageSet(imgMatrix, name);

		execute3D(shape);
	}

	/** Execute of a 2D structure */
	public void execute2D(Shape2D shape) {
		// start time counting
		long startTime = System.currentTimeMillis();
		System.out.println("Convolution started.");

		Parallel.startPool();

		// make convolution
		final double[][][] paddingMat = padding();
		convolved = MatrixUtil.convolve(paddingMat, psfMatrix);

		Parallel.shutdownPool();

		// save convolved image
		final String name = shape.toString() + " CONVOLVED";
		saveImageSet(convolved, name);

		// show time of run
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println("Convolution is finished in " + totalTime + " ms.");
	}

	/** Execute of a 3D structure */
	public void execute3D(Shape3D shape) {
		// start time counting
		long startTime = System.currentTimeMillis();
		System.out.println("Convolution started.");

		Parallel.startPool();

		// make padding for the image matrix
		final double[][][] paddingMat = padding();
		convolved = MatrixUtil.convolve(paddingMat, psfMatrix);

		Parallel.shutdownPool();

		// save convolved image
		final String name = shape.toString() + " CONVOLVED";
		saveImageSet(convolved, name);

		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println("Convolution is finished in " + totalTime + " ms.");
	}

	/**
	 * Making padding for the imgMatrix for convolution
	 * 
	 * @return The matrix after padding
	 */
	private double[][][] padding() {
		final int nm = imgMatrix.length;
		final int hm = imgMatrix[0].length;
		final int wm = imgMatrix[0][0].length;

		final int np = psfMatrix.length;
		final int hp = psfMatrix[0].length;
		final int wp = psfMatrix[0][0].length;

		// the size of the convolution matrix
		final int nc = nm + (np / 2) * 2;
		final int hc = hm + (hp / 2) * 2;
		final int wc = wm + (wp / 2) * 2;

		final double[][][] result = new double[nc][hc][wc];

		for (int x = 0; x < wm; x++) {
			for (int y = 0; y < hm; y++) {
				for (int z = 0; z < nm; z++) {
					final int xc = x + wp / 2;
					final int yc = y + hp / 2;
					final int zc = z + np / 2;
					result[zc][yc][xc] = imgMatrix[z][y][x];
				}
			}
		}

		return result;
	}

	/**
	 * convert the elements of the input array into 16 bit integer
	 * 
	 * @param input
	 *        The input array
	 * 
	 * @param max
	 *        The maximum value in the 3D image matrix
	 * 
	 * @return An array of 16 bit integers
	 */
	private short[] convertToInt16(final double[] input, final double max) {

		// truncate the input array from 0
		final double[] positive = truncate(input, max, 0);

		/*
		 * if the maximum value of the input array is larger than the upper
		 * bound of short, re-scale the array.
		 */
		final double ub = 0x7FFF;
		final double lb = 0;

		if (max > ub) {

			// re-scale the matrix of the current plane
			final double localMax = max(input);
			final double localUb = ub * localMax / max;
			final double[] rescaled
				= rescaleI(positive.clone(), lb, localUb);
			final short[] result = toShort(rescaled);
			return result;
		}

		return toShort(positive);
	}

	/**
	 * save image set to the path
	 * 
	 * @param input
	 * 
	 * @param seriesName
	 *        The name of the series
	 */
	private void saveImageSet(
			final double[][][] input,
			final String seriesName) {
		// Create new series directories
		final String path = "C:\\Users\\user\\Desktop\\convolution sim";
		final String imgFolder
			= MIAUtil.buildPath(path, seriesName);

		// delete the directory that already existed and creat a new directory
		final File dir = new File(imgFolder);
		if (dir.exists())
			dir.delete();
		new File(imgFolder).mkdirs();

		// Save images to disk
		final double max = max(max(max(input)));
		final int length = input.length;

		for (int i = 0; i < length; i++) {
			final int height = input[i].length;
			final int width = input[i][0].length;

			// linearize data
			double[] linData = linearize(input[i]);
			short[] sData = convertToInt16(linData, max);

			// make file names
			final String fileName = "s1_" + Integer.toString(i + 1) + ".tif";
			final String newImgName = MIAUtil.buildPath(imgFolder, fileName);

			// save files
			final MIATIFFWriter mtw = new MIATIFFWriter(sData, width, height);
			try {
				mtw.write(0, newImgName);
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
		}
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-07-18, Bo Wang, 0.1: Created.
 * 
 */
