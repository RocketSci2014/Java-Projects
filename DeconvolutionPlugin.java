package miatool.plugins;

import static miatool.core.miatooldirectory.MIAToolDirectory.IMAGES_DIR;
import static miatool.core.util.MIAUtil.buildPath;
import static miatool.core.util.numerics.MIAMatOps.max;
import static miatool.core.util.numerics.MIAMatOps.rescaleI;
import static miatool.core.util.numerics.MIAMatOps.sum;
import static miatool.core.util.numerics.MIAMatOps.toShort;
import static miatool.core.util.numerics.MIAMatOps.truncate;
import static miatool.core.util.numerics.MIAMatOps.copy;
import static miatool.core.util.numerics.MIAMatOps.multiply;
import static miatool.core.util.numerics.MIAMatOps.add;
import static miatool.core.util.numerics.MIAMatOps.xyzToYzx;
import static miatool.core.util.numerics.MIAMatOps.xyzToZxy;
import static miatool.core.util.numerics.MIAMatOps.divide;
import static miatool.core.util.numerics.MIAMatOps.isSameSize;
import static miatool.core.util.numerics.MIAMatOps.multiplyI;
import static miatool.core.util.numerics.MIAMatOps.addI;
import static miatool.core.util.numerics.MIAMatOps.linearize;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import org.jtransforms.fft.DoubleFFT_3D;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import miatool.core.miatooldirectory.MIAToolDirectory;
import miatool.core.setssingles.image.GreyD64RAMSingle;
import miatool.core.setssingles.image.GreyU16DiskSingle;
import miatool.core.setssingles.image.ImageSet;
import miatool.core.setssingles.image.ImageSingle;
import miatool.core.setssingles.intensity.IntensityAdjustSet;
import miatool.core.setssingles.segmentation.SegmentationSet;
import miatool.core.setssingles.segmentation.SegmentationSingle;
import miatool.core.setssingles.segmentation.component.SegmentationComponent;
import miatool.core.util.FileUtil;
import miatool.core.util.MIAUtil;
import miatool.core.util.PixelCoordinates;
import miatool.core.util.miatiff.MIATIFFWriter;
import miatool.core.util.numerics.ComplexMatrix3D;
import miatool.core.util.numerics.MIAUnits;
import miatool.miamain.MIABrowser;
import miatool.miamain.MIATool;
import miatool.miamain.MIAToolHub;
import miatool.miamain.MIAToolMain;
import miatool.modules.estimation.math.integration.TrapezoidalPixelIntegrator;
import miatool.modules.estimation.math.integration.TrapezoidalPsf3DIntegrator;
import miatool.modules.estimation.models.PSFFramework.PSFImage;
import miatool.modules.estimation.models.single.BornWolf3D;
import miatool.modules.estimation.nonparametric.NonParametricBackgroundMethod;
import miatool.modules.estimation.nonparametric.NonParametricBackgroundMethod.Default.CalculationMethod;
import miatool.modules.estimation.nonparametric.NonParametricBackgroundMethod.Default.ExtractionMethod;
import miatool.modules.estimation.util.Detector;
import miatool.modules.process.MIAProcessingChain;
import miatool.modules.process.commontasks.PercentileIntensityAutoAdjusterBlock;
import miatool.tools.MIAToolDirectoryBrowser;
import miatool.tools.adjustmenttools.intensitytool.IntensityTool;
import miatool.tools.adjustmenttools.segmentationtool.SegmentationTool;
import miatool.tools.displaytool.ImageSetBrowser;
import miatool.ui.UIUtils;
import miatool.ui.components.slider.MassagedIndicesScroller;
import miatool.ui.events.MIAPropertyChangeEvent;
import miatool.ui.wizard.objectselection.ObjectSelectionWizardSwing;

/**
 * Plugin to make deconvolution to a image set and save the deconvolved image to
 * the MIATool directory.
 * 
 * @author Bo Wang
 * @version 0.4
 * @since 0.1
 */

public class DeconvolutionPlugin implements MIAPlugin, ActionListener {

	/** the height of the 3D matrix of the image set */
	public int imgH;

	/** the width of the 3D matrix of the image set */
	public int imgW;

	/** the height of the 3D matrix of the psf set */
	public int psfH;

	/** the width of the 3D matrix of the psf set */
	public int psfW;

	/** the number of planes of the 3D matrix in the deconvolution process */
	public int lenConv;

	/** the number of planes of the 3D matrix of the psf set */
	public int lenPsf;

	/** the minimum value Epsilon from MatLab */
	public static final double EPS = 2.220446049250313e-16;

	public Button deconvBtn;
	public Button getImgSetBtn;
	public Button getPsfSetBtn;
	public Button plotProjBtn;
	public Button getSegSetBtn;
	public Button openPSFSimBtn;
	public Button compareBtn;
	public Button csViewerBtn;

	public TextField regItemTf;
	public TextField psfMtdTf;
	public TextField convTf;
	public TextField psfSetTf;
	public TextField segSetTf;
	public TextField imgSeriesTf;
	public TextField psfSeriesTf;
	public Text completionTf;

	public ComboBox<String> deconvMethodCb;
	public ProgressBar prob;
	public static final String WINDOW_TITLE = "Deconvolution Plugin";
	public static final String MENU_TITLE = "Deconvolution Tools";
	public final JMenuItem menuItem = new JMenuItem(WINDOW_TITLE + "...");
	private Stage stage;
	private ImageSet imgSet = null;
	private ImageSet psfSet = null;
	private SegmentationSet segSet = null;
	private ImageSet segImgSet = null;
	private ImageSet deconvSet = null;
	private ImageSet compareSet = null;
	private ObjectSelectionWizardSwing<ImageSet> imgSetSelector;
	private ObjectSelectionWizardSwing<ImageSet> psfSetSelector;
	private MIAToolDirectory mtd;
	private MIAToolHub hub;
	private MIAToolMain mtm;
	private ImageSetBrowser imgb;
	private MIABrowser<SegmentationSet, ?> segb;
	private SegmentationTool segTool;
	private MIAToolDirectoryBrowser mtdb;

	/** The indicator for updating the psf set and the segmentation set */
	private boolean isUpdate = true;

	/** A 3D matrix to store the image data */
	private double[][][] imgDataIn;

	/** The array to store the lengths of images */
	private int[] imgHArr;

	/** The array to store the widths of images */
	private int[] imgWArr;

	public static void main(final String[] args) {
		MIATool.main(args);
		final DeconvolutionPlugin tool = new DeconvolutionPlugin();
		PluginUtil.addPlugin(tool);
		tool.startup();
	}

	/**
	 * Apply ifftShift the input matrix. The function swaps half-spaces of Y
	 * along each dimension.
	 * 
	 * @param input
	 * 
	 * @return Inverse FFT shifted matrix
	 */
	public static double[][][] ifftShift(final double[][][] input) {
		final int n = input.length;
		final int h = input[0].length;
		final int w = input[0][0].length;

		// The middle of the three dimensions
		final int midN = (int) Math.floor((double) n / 2);
		final int midH = (int) Math.floor((double) h / 2);
		final int midW = (int) Math.floor((double) w / 2);
		final int shiftN = n - midN;
		final int shiftH = h - midH;
		final int shiftW = w - midW;

		final double[][][] result = new double[n][h][w];
		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < n; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					final int indexZ
						= tuple < midN ? tuple + shiftN : tuple - midN;
					final int indexY = y < midH ? y + shiftH : y - midH;
					final int indexX = x < midW ? x + shiftW : x - midW;

					result[indexZ][indexY][indexX] = input[tuple][y][x];
				}
			}
		});

		return result;
	}

	/**
	 * Inverse circular shift to the counter-clockwise by shiftX, shiftY, shiftZ
	 * to each dimension
	 * 
	 * @param input
	 * 
	 * @param shiftX
	 * 
	 * @param shiftY
	 * 
	 * @param shiftZ
	 * 
	 * @return The shifted matrix
	 */
	public static double[][][] iCircShift(
			final double[][][] input,
			final int shiftZ,
			final int shiftY,
			final int shiftX) {
		final int n = input.length;
		final int h = input[0].length;
		final int w = input[0][0].length;

		final double[][][] result = new double[n][h][w];
		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < n; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					final int xIndex;
					final int yIndex;
					final int zIndex;

					if (x < shiftX)
						xIndex = x - shiftX + w;
					else
						xIndex = x - shiftX;

					if (y < shiftY)
						yIndex = y - shiftY + h;
					else
						yIndex = y - shiftY;

					if (tuple < shiftZ)
						zIndex = tuple - shiftZ + n;
					else
						zIndex = tuple - shiftZ;

					result[zIndex][yIndex][xIndex] = input[tuple][y][x];
				}
			}
		});

		return result;
	}

	/**
	 * Estimate background of a image single
	 * 
	 * @param imgSi
	 *        image single
	 * 
	 * @return The background value
	 */
	public static double getBackground(final ImageSingle<?> imgSi) {
		// get pixel data and pixel coordinates
		final int width = imgSi.getWidth();
		final int height = imgSi.getHeight();
		final PixelCoordinates[] pCoordinates = new PixelCoordinates[1];
		final double[][] pData = new double[1][];
		pCoordinates[0] = new PixelCoordinates(height, width);
		pData[0] = imgSi.getPixelValues(pCoordinates[0]);

		// calculate background
		final NonParametricBackgroundMethod.Default npbMethod
			= new NonParametricBackgroundMethod.Default();
		npbMethod.calculationMethod = CalculationMethod.MEDIAN;
		npbMethod.extractionMethod = ExtractionMethod.EDGES;
		final double[] background
			= npbMethod.getBackground(pData, pCoordinates);
		return background[0];
	}

	/**
	 * Subtract background from the image data, if an element in the result is
	 * smaller than 0, set it to 0
	 * 
	 * @param input
	 *        The image data
	 * 
	 * @param background
	 *        The value of the background
	 * 
	 * @return The processed image data
	 */
	public static double[][] subtractBackground(
			final double[][] input,
			final double background) {
		final double[][] result = add(input, -1.0 * background);
		for (int i = 0; i < result.length; i++)
			for (int j = 0; j < result[i].length; j++)
				if (result[i][j] < 0)
					result[i][j] = 0;
		return result;
	}

	/** Constructor */
	public DeconvolutionPlugin() {
		super();
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
	public static short[] convertToInt16(
			final double[] input,
			final double max) {

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
	 * Apply deconvolution using Richardson-Lucy algorithm
	 * 
	 * @param rawImg
	 *        The jagged 3D matrix of the image set
	 * 
	 * @param rawPsf
	 *        The jagged 3D matrix of the psf set
	 * 
	 * @param deconvMethod
	 *        The deconvolution method
	 * 
	 * @param bg
	 *        The background
	 * 
	 * @param n
	 *        The number of iterations
	 * 
	 * @return The 3D matrix of the deconvolved image set
	 */
	public double[][][] deconvolutionRL(
			final double[][][] rawImg,
			final double[][][] rawPsf,
			final DeconvMethod deconvMethod,
			final double bg,
			final int n) {
		/*
		 * make img and psf matrices rectangular and of the size lenConv x imgH
		 * x imgW
		 */
		double[][][] img = makeDeconvSize(rawImg);
		double[][][] psf = makeDeconvSize(rawPsf);

		// rotate the matrices to the size of height x width x n
		final double[][][] imgR = xyzToZxy(img);
		final double[][][] psfR = xyzToZxy(psf);

		// make circshift to the psf matrix and normalize it
		double[][][] psfNorm = processPSF(psfR);

		// make Fourier transform to the psf matrix
		ComplexMatrix3D psfIn
			= new ComplexMatrix3D(psfNorm, new double[imgH][imgW][lenConv]);
		final ComplexMatrix3D psfTrans = JTransforms3DUtil.fft3D(psfIn);

		img = null;
		psf = null;
		psfNorm = null;
		psfIn = null;

		showProgress(0.3);

		// calculate initial value of the image matrix in the iteration
		double[][][] imgRaw = negativeToZero(imgR);

		List<Integer> tuples = new ArrayList<>();
		for (int i = 0; i < imgH; i++)
			tuples.add(i);

		tuples.parallelStream().forEachOrdered(tuple -> {
			addI(imgR[tuple], -1.0 * bg);
		});

		double[][][] imgIterIn = negativeToZero(imgR);

		showProgress(0.4);

		// apply the deconvolution algorithms
		switch (deconvMethod) {
			case AGARD:
				for (int j = 0; j < n; j++)
					agardIteration(imgIterIn, psfTrans, imgRaw);
				break;
			case RICHARDSON_LUCY:
				for (int k = 0; k < n; k++)
					iterationRL(imgIterIn, psfTrans, imgRaw, bg);

				break;
			case ACCEL_RICHARDSON_LUCY:

				// this method require more than three iterations
				if (n < 3)
					break;

				// apply the first iteration, get item gY
				double[][][] imgIterPre = copy(imgIterIn);
				iterationRL(imgIterIn, psfTrans, imgRaw, bg);
				double[][][] gY = add(
					imgIterIn,
					multiply(imgIterPre, -1.0));

				// apply the second iteration, get item gX
				imgIterPre = null;
				imgIterPre = copy(imgIterIn);
				iterationRL(imgIterIn, psfTrans, imgRaw, bg);
				double[][][] gX = add(
					imgIterIn,
					multiply(imgIterPre, -1.0));

				// apply the rest of the iterations
				double[][][] imgMatIn;

				for (int x = 2; x < n; x++) {

					// make the transformed image matrix for the LR iteration
					imgMatIn = makeImageMatrix(
						imgIterIn,
						imgIterPre,
						gX,
						gY);

					imgIterPre = null;
					imgIterPre = copy(imgIterIn);
					imgIterIn = null;
					imgIterIn = copy(imgMatIn);

					// RL iteration
					iterationRL(imgMatIn, psfTrans, imgRaw, bg);

					// refresh variables
					gY = null;
					gY = copy(gX);
					gX = null;
					gX = add(
						imgMatIn,
						multiply(imgIterIn, -1.0));

					imgIterIn = null;
					imgIterIn = copy(imgMatIn);
					imgMatIn = null;
				}
				break;
			default:
				break;
		}

		showProgress(0.7);

		// rotate the matrix back to the size of n x height x width
		final double[][][] result = xyzToYzx(imgIterIn);
		imgIterIn = null;

		return result;
	}

	/**
	 * Apply deconvolution with image data and psf using regularized least
	 * square algorithm
	 * 
	 * @param rawImg
	 *        The jagged 3D matrix of the image set
	 * 
	 * @param rawPsf
	 *        The jagged 3D matrix of the psf set
	 * 
	 * @param deconvMethod
	 *        The deconvolution method
	 * 
	 * @param regItem
	 *        The regularization item
	 * 
	 * @return The 3D matrix of the deconvolved image set
	 */
	public double[][][] deconvolutionLsq(
			final double[][][] rawImg,
			final double[][][] rawPsf,
			final DeconvMethod deconvMethod,
			final double regItem)
			throws IllegalArgumentException {

		/*
		 * make img and psf matrices rectangular and of the size lenConv x imgH
		 * x imgW
		 */
		final double[][][] img = makeDeconvSize(rawImg);
		final double[][][] psf = makeDeconvSize(rawPsf);

		// rotate the matrices to the size of height x width x n
		double[][][] imgR = xyzToZxy(img);
		double[][][] psfR = xyzToZxy(psf);

		// make circshift to the psf matrix and normalize it
		double[][][] psfNorm = processPSF(psfR);

		// make psf and img matrices into complex matrices
		ComplexMatrix3D imgIn
			= new ComplexMatrix3D(imgR, new double[imgH][imgW][lenConv]);
		ComplexMatrix3D psfIn
			= new ComplexMatrix3D(psfNorm, new double[imgH][imgW][lenConv]);

		// Make fft to the image matrix and the psf matrix
		ComplexMatrix3D imgTransMat
			= JTransforms3DUtil.fft3D(imgIn);
		ComplexMatrix3D psfTransMat
			= JTransforms3DUtil.fft3D(psfIn);

		imgR = null;
		psfR = null;
		imgIn = null;
		psfIn = null;
		psfNorm = null;
		showProgress(0.4);

		// make factors in the following calculation
		double[][][] psfAbs = psfTransMat.getAbs();
		final double psfMax
			= max(max(max(psfAbs)));

		ComplexMatrix3D.divideI(psfTransMat, psfMax);
		double[][][] psfNormAbs = psfTransMat.getAbs();
		psfTransMat.getConjugateI();
		ComplexMatrix3D psfNormMat = new ComplexMatrix3D(
			psfNormAbs,
			new double[imgH][imgW][lenConv]);
		ComplexMatrix3D psfPow2 = ComplexMatrix3D.power(psfNormMat, 2);
		ComplexMatrix3D psfPow4 = ComplexMatrix3D.power(psfNormMat, 4);

		psfAbs = null;
		psfNormAbs = null;
		psfNormMat = null;
		showProgress(0.5);

		// apply deconvolution algorithms
		ComplexMatrix3D deconvTrans = null;
		switch (deconvMethod) {
			case SIMPLE_RLS:
				deconvTrans
					= simpleRLS(imgTransMat, psfTransMat, psfPow2, regItem);
				break;
			case MAT_RLS:
				deconvTrans
					= matRLS(imgTransMat, psfTransMat, psfPow2, regItem);
				break;
			case MODIFIED_RLS:
				deconvTrans
					= modifiedRLS(
						imgTransMat,
						psfTransMat,
						psfPow2,
						psfPow4,
						regItem);
				break;
			case WEIGHTED_RLS:
				deconvTrans
					= weightedRLS(
						imgTransMat,
						psfTransMat,
						psfPow2,
						regItem);
				break;
			default:
				break;
		}

		imgTransMat = null;
		psfPow2 = null;
		psfPow4 = null;
		showProgress(0.6);

		// apply inverse fft to the result from the last step
		ComplexMatrix3D deconvResMat
			= JTransforms3DUtil.ifft3D(deconvTrans);
		double[][][] deconvRealR = deconvResMat.getReal();
		showProgress(0.7);

		// rotate the matrix back to the size of n x height x width
		final double[][][] deconvReal = xyzToYzx(deconvRealR);
		deconvTrans = null;
		deconvRealR = null;
		deconvResMat = null;

		return deconvReal;
	}

	public void setupFrame() {
		PlatformImpl.startup(() -> {});
		// make main container
		final VBox mainContainer = new VBox(10);
		mainContainer.setPadding(new Insets(5));

		// choose PSF
		final GridPane psfGrid = new GridPane();
		psfGrid.setPadding(new Insets(5));
		psfGrid.setHgap(4);
		psfGrid.setVgap(4);

		// step1
		hub = MIATool.getMIAToolHub();
		mtd = hub.getMIAToolDirectory();
		if (mtd == null)
			psfMtdTf = new TextField("-NULL-");
		else
			psfMtdTf = new TextField(mtd.getFilename());
		psfSetTf = new TextField("-NULL-");
		psfSetTf.setEditable(false);
		getPsfSetBtn = new Button("Load PSF");
		psfSeriesTf = new TextField("1");
		psfSeriesTf.setDisable(true);
		psfGrid.add(
			new Label("Step 1: Load PSF from the MIAToolDirecotry"),
			0,
			0,
			2,
			1);
		psfGrid.add(psfMtdTf, 0, 1);
		psfGrid.add(new Label("MIAToolDirecotry"), 1, 1);
		psfGrid.add(psfSetTf, 0, 2);
		psfGrid.add(getPsfSetBtn, 1, 2);
		psfGrid.add(psfSeriesTf, 0, 3);
		psfGrid.add(new Label("Series Dimension"), 1, 3);

		// step 2
		openPSFSimBtn = new Button("Simulate PSF");
		psfGrid.add(new Label("Step 2: Simulate PSF - Optional"), 0, 4, 2, 1);
		psfGrid.add(openPSFSimBtn, 0, 5);

		// step 3
		segSetTf = new TextField("-NULL-");
		segSetTf.setEditable(false);
		getSegSetBtn = new Button("PSF Segmentation");
		psfGrid.add(
			new Label("Step 3: Segment a Single PSF - Optional"),
			0,
			6,
			2,
			1);
		psfGrid.add(segSetTf, 0, 7);
		psfGrid.add(getSegSetBtn, 1, 7);

		// step 4
		plotProjBtn = new Button("Plot Projections");
		psfGrid.add(
			new Label("Step 4: Plot 3D PSF Projections - Optional"),
			0,
			8,
			2,
			1);
		psfGrid.add(plotProjBtn, 0, 9);

		final TitledPane psfPane = new TitledPane();
		psfPane.setCollapsible(false);
		psfPane.setText("1. Choose PSF Set");
		psfPane.setContent(psfGrid);

		// choose image set
		final GridPane imgGrid = new GridPane();
		imgGrid.setPadding(new Insets(5));
		imgGrid.setVgap(4);
		imgGrid.setHgap(4);
		convTf = new TextField("-NULL-");
		convTf.setEditable(false);
		getImgSetBtn = new Button("Load Image Set");
		imgSeriesTf = new TextField("1");
		imgSeriesTf.setDisable(true);
		imgGrid.add(convTf, 0, 0);
		imgGrid.add(getImgSetBtn, 1, 0);
		imgGrid.add(imgSeriesTf, 0, 1);
		imgGrid.add(new Label("Series Dimension"), 1, 1);
		final TitledPane imgPane = new TitledPane();
		imgPane.setCollapsible(false);
		imgPane.setText("2. Choose Image Set");
		imgPane.setContent(imgGrid);

		// choose deconvolution parameters
		final GridPane deconvParamGrid = new GridPane();
		deconvParamGrid.setPadding(new Insets(5));
		deconvParamGrid.setVgap(4);
		deconvParamGrid.setHgap(4);
		regItemTf = new TextField("1e-7");
		deconvMethodCb = new ComboBox<String>();
		deconvMethodCb.getItems().addAll(
			DeconvMethod.SIMPLE_RLS.getMethod(),
			DeconvMethod.MAT_RLS.getMethod(),
			DeconvMethod.MODIFIED_RLS.getMethod(),
			DeconvMethod.WEIGHTED_RLS.getMethod(),
			DeconvMethod.AGARD.getMethod(),
			DeconvMethod.RICHARDSON_LUCY.getMethod(),
			DeconvMethod.ACCEL_RICHARDSON_LUCY.getMethod());
		deconvParamGrid.add(regItemTf, 0, 0);
		deconvParamGrid.add(new Label("Regularization Parameter"), 1, 0);
		deconvParamGrid.add(deconvMethodCb, 0, 1);
		deconvParamGrid.add(new Label("Deconvolution Method"), 1, 1);
		final TitledPane deconvParamPane = new TitledPane();
		deconvParamPane.setCollapsible(false);
		deconvParamPane.setText("3. Choose Deconvoluton Parameters");
		deconvParamPane.setContent(deconvParamGrid);

		// make deconvolution
		final GridPane deconvGrid = new GridPane();
		deconvGrid.setPadding(new Insets(5));
		deconvGrid.setHgap(10);
		deconvBtn = new Button("Deconvolution");
		completionTf = new Text(String.format("Complete: %d%%", 0));
		prob = new ProgressBar();
		prob.setProgress(0.0);
		deconvGrid.add(deconvBtn, 0, 0);
		deconvGrid.add(completionTf, 1, 0);
		deconvGrid.add(prob, 2, 0);
		final TitledPane deconvPane = new TitledPane();
		deconvPane.setCollapsible(false);
		deconvPane.setText("4. Implement Deconvolution");
		deconvPane.setContent(deconvGrid);

		// additional functions after the deconvolution
		final GridPane additionalGrid = new GridPane();
		additionalGrid.setPadding(new Insets(5));
		additionalGrid.setHgap(20);
		csViewerBtn = new Button("Open Cross Section Viewer");
		compareBtn = new Button("Compare Results");
		additionalGrid.add(compareBtn, 0, 0);
		additionalGrid.add(csViewerBtn, 1, 0);
		final TitledPane additionalPane = new TitledPane();
		additionalPane.setCollapsible(false);
		additionalPane.setText("5. Additional Functions");
		additionalPane.setContent(additionalGrid);

		// add to mainContainer
		mainContainer.getChildren().addAll(
			psfPane,
			imgPane,
			deconvParamPane,
			deconvPane,
			additionalPane);

		Platform.runLater(() -> {
			stage = new Stage();
			stage.setTitle(WINDOW_TITLE);
			final Scene scene = new Scene(mainContainer, 400, 640);
			stage.setScene(scene);
		});
	}

	@Override
	public void processPropertyChange(final MIAPropertyChangeEvent<?> e)
			throws IllegalArgumentException {
		final Object src = e.getSource();
		final Object prop = e.getChangedProperty();

		// MIAToolDirectory change
		if (prop == MIAToolDirectoryBrowser.Property.MIATOOLDIRECTORY
			&& src == mtdb
			&& isUpdate) {
			mtd = (MIAToolDirectory) e.getNewValue();

			if (mtd == null)
				psfMtdTf.setText("-NULL-");
			else
				psfMtdTf.setText(mtd.getFilename());

			return;
		}

		// Callback from ObjectSelectionWizard
		if (prop == ObjectSelectionWizardSwing.Property.SELECTED_OBJECT
			&& src == imgSetSelector) {
			processImgSetSelection();
			if (imgSet != null) {
				refreshImage(imgSet);
				intensityAdjust(imgSet);
			}
			return;
		}

		if (prop == ObjectSelectionWizardSwing.Property.SELECTED_OBJECT
			&& src == psfSetSelector
			&& isUpdate) {
			processPsfSetSelection();
			if (psfSet != null) {
				refreshImage(psfSet);
				intensityAdjust(psfSet);
			}
			return;
		}

		if (prop == MIABrowser.Property.ACTIVE
			&& src == segb
			&& isUpdate
			&& segTool.isShowing()) {
			processMIASetSelection();
			if (segImgSet != null) {
				refreshImage(segImgSet);
				intensityAdjust(segImgSet);
				isUpdate = false;
			}
			return;
		}
	}

	@Override
	public void setConfigFromPrefs() {

	}

	@Override
	public void saveConfigToPrefs() {

	}

	@Override
	public void setConfigFromSettings() {

	}

	@Override
	public void saveConfigToSettings() {

	}

	@Override
	public void actionPerformed(final ActionEvent e) {
		final Object src = e.getSource();
		if (src == menuItem) {
			setVisible(true);
		}
	}

	@Override
	public void dispose() {

	}

	@Override
	public String[] getMenuHierarchy() {
		return new String[] { MENU_TITLE };
	}

	@Override
	public JMenuItem getMenuItem() {
		menuItem.addActionListener(this);
		return menuItem;
	}

	@Override
	public Object[] pluginCommand(final String cmd, final Object... input) {
		return null;
	}

	@Override
	public void setVisible(final boolean b) {
		Platform.runLater(() -> {
			if (b)
				stage.show();
			else
				stage.hide();
		});
	}

	@Override
	public void startup() {
		setupFrame();

		hub = MIATool.getMIAToolHub();
		mtm = hub.getMIAToolMain();
		imgb = mtm.getImageSetBrowser();
		imgb.addMIAPropertyChangeListener(this);
		mtdb = hub.getMIAToolMain().getMIAToolDirectoryBrowser();
		mtdb.addMIAPropertyChangeListener(this);
		segTool = hub.getTool(SegmentationTool.class, SegmentationSet.class);
		segb = segTool.getSetBrowser();
		segb.addMIAPropertyChangeListener(this);

		openPSFSimBtn.setOnAction((event) -> {
			final BornWolf3DModelSimScript sim = new BornWolf3DModelSimScript();
			sim.startup();
		});

		deconvBtn.setOnAction((event) -> {
			deconvolutionBtnAction();
		});

		getImgSetBtn.setOnAction((event) -> {
			openImgSetSelector();
		});

		getPsfSetBtn.setOnAction((event) -> {
			getPsfSetBtnAction();
		});

		plotProjBtn.setOnAction((event) -> {
			if (segImgSet != null)
				plotProjection(segImgSet);
			else if (psfSet != null)
				plotProjection(psfSet);

		});

		getSegSetBtn.setOnAction((event) -> {
			if (psfSet != null) {
				openSegmentationTool();
			}
		});

		compareBtn.setOnAction((event) -> {
			compareBtnAction();
		});

		csViewerBtn.setOnAction((event) -> {
			if (deconvSet != null) {
				final CrossSectionViewer CSView
					= new CrossSectionViewer(deconvSet);
				CSView.startup();
			}
		});
	}

	@Override
	public void updateFields() {
		convTf.setText(imgSet == null ? "-NULL-" : imgSet.getFilename());
		if (isUpdate) {
			psfSetTf.setText(psfSet == null ? "-NULL-" : psfSet.getFilename());
			segSetTf.setText(segSet == null ? "-NULL-" : segSet.getFilename());
		}
	}

	/** Set up action in the deconvolution button */
	private void deconvolutionBtnAction() {
		prob.setProgress(0.0);
		completionTf.setText(String.format("Complete: %d%%", 0));

		new Thread(new Runnable() {
			@Override
			public void run() {
				processCreate();

				// load the deconvSet into the imgb
				if (deconvSet != null) {
					refreshImage(deconvSet);
					intensityAdjust(deconvSet);
				}
				System.out.println("Deconvolution is finished.");
			}
		}).start();
	}

	/** Set up action in the get psf set button */
	private void getPsfSetBtnAction() {
		final String psfMtdName = psfMtdTf.getText();
		isUpdate = true;
		hub = MIATool.getMIAToolHub();
		mtd = hub == null ? null : hub.getMIAToolDirectory();

		if (mtd == null) {
			// load mtd from the textfield
			if (psfMtdName != "" && psfMtdName != "-NULL-") {
				final String psfPath
					= psfMtdName + "\\MIAToolDirectory.mia";
				final File psfMtdFile = new File(psfMtdName);

				if (psfMtdFile.exists()) {
					try {
						mtd = new MIAToolDirectory(psfPath);
					}
					catch (final Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		else {
			// a different mtd in the textfield
			if (!mtd.getFilename().equals(psfMtdName)) {
				final String psfPath
					= psfMtdName + "\\MIAToolDirectory.mia";
				final File psfMtdFile = new File(psfMtdName);

				if (psfMtdFile.exists()) {
					try {
						mtd = new MIAToolDirectory(psfPath);
					}
					catch (final Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

		openPsfSetSelector();
	}

	/** Set up the action in the make compare set button */
	private void compareBtnAction() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				if (compareSet != null) {
					refreshImage(compareSet);
					intensityAdjust(compareSet);
					final MassagedIndicesScroller mis
						= hub.getIndicesScroller();
					mis.setLocked(0, true);
				}
			}

		}).start();
	}

	/** Callback for when a new object set is selected. */
	private void processImgSetSelection() {
		final List<ImageSet> loadedImage;
		loadedImage = imgSetSelector.getSelectedObjects();

		if (loadedImage.isEmpty() || loadedImage.get(0) == null) {
			return;
		}

		imgSet = loadedImage.get(0);
		if (imgSet.size().length > 1)
			imgSeriesTf.setDisable(false);
		updateFields();
		return;
	}

	/** Callback for when a new object set is selected. */
	private void processPsfSetSelection() {
		final List<ImageSet> loadedPsf;
		loadedPsf = psfSetSelector.getSelectedObjects();

		if (loadedPsf.isEmpty() || loadedPsf.get(0) == null) {
			return;
		}

		psfSet = loadedPsf.get(0);
		if (psfSet.size().length > 1)
			psfSeriesTf.setDisable(false);
		segSet = null;
		segImgSet = null;
		updateFields();
		return;
	}

	/** Callback for when a new segmentation set is selected. */
	private void processMIASetSelection() {
		segb = segTool.getSetBrowser();
		segSet = segb.getActiveObject();
		if (segSet == null || psfSet == null)
			return;

		// get the length of the segmentation series
		final int segLen;
		final int seriesDim;
		if (segSet.size().length > 1) {
			seriesDim = Integer.valueOf(psfSeriesTf.getText()) - 1;
			segLen = segSet.size()[seriesDim];
		}
		else {
			seriesDim = 0;
			segLen = segSet.size()[0];
		}
		// create name for the psf segmentation set
		final String segSetName = "PSF Segmentation";
		mtd = MIATool.getMIAToolHub().getMIAToolDirectory();

		// get segSet data
		final int[] segIndex = new int[segSet.size().length];
		double[][][] segData = new double[segLen][][];
		for (int i = 0; i < segLen; i++) {
			segIndex[seriesDim] = i;
			final SegmentationSingle segSi = segSet.get0iR(segIndex);
			if (segSi.numComponents() > 0) {
				final SegmentationComponent segCom = segSi.get0i(0);
				final double[][] segSiData
					= PSFImage.get2DImage(
						segCom.getPixels(),
						segCom.getPixelData());
				segData[i] = copy(segSiData);
			}
			else
				return;
		}

		// save segImgSet into mtd
		segImgSet = saveImageSet(segData, segLen, segSetName);
		segData = null;
		updateFields();

		// keep the current object in the browser
		new Thread(new Runnable() {
			@Override
			public void run() {
				segTool.getSetBrowser().setActiveObject(segSet);
				System.out.println("Set segSet as active");
			};
		});
	}

	/**
	 * Helper method to open the object selection wizard for the selecting a
	 * image set.
	 */
	private void openImgSetSelector() {
		hub = MIATool.getMIAToolHub();
		mtd = hub == null ? null : hub.getMIAToolDirectory();

		if (mtd == null) {
			JOptionPane.showMessageDialog(
				menuItem,
				"No MIAToolDirectory loaded!",
				"No data!",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (imgSetSelector != null) {
			imgSetSelector.removeMIAPropertyChangeListener(this);
		}
		// new selector is created in case the hierarchy has changed
		imgSetSelector
			= new ObjectSelectionWizardSwing<ImageSet>(
				ImageSet.class,
				"Image Set");

		imgSetSelector.setMIAToolDirectory(mtd);
		imgSetSelector.addMIAPropertyChangeListener(this);
		imgSetSelector.show(null);
	}

	/**
	 * Helper method to open the object selection wizard for the selecting a psf
	 * set.
	 */
	private void openPsfSetSelector() {

		if (mtd == null) {
			JOptionPane.showMessageDialog(
				menuItem,
				"No MIAToolDirectory loaded!",
				"No data!",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (psfSetSelector != null) {
			psfSetSelector.removeMIAPropertyChangeListener(this);
		}
		// new selector is created in case the hierarchy has changed
		psfSetSelector
			= new ObjectSelectionWizardSwing<ImageSet>(
				ImageSet.class,
				"PSF Set");

		psfSetSelector.setMIAToolDirectory(mtd);
		psfSetSelector.addMIAPropertyChangeListener(this);
		psfSetSelector.show(null);
	}

	/**
	 * @return The current instance of the segmentation tool in the application
	 *         environment.
	 */
	private void openSegmentationTool() {
		hub = MIATool.getMIAToolHub();
		segTool = hub.getTool(SegmentationTool.class, SegmentationSet.class);
		segb = segTool.getSetBrowser();
		UIUtils.getParentFrame(segTool).setVisible(true);
	}

	/**
	 * Plot yz xz and z maximum projections
	 */
	private void plotProjection(final ImageSet imgSet) {
		final PlotProjectionPlugin plotProj = new PlotProjectionPlugin();
		plotProj.imgSet = imgSet;
		plotProj.plotProjection();
	}

	/** Process the image set and save the deconvolved image set */
	private void processCreate() {
		/*
		 * get the series dimension and series length of the image set and the
		 * psf set
		 */
		if (imgSet == null || psfSet == null)
			return;

		final int[] seriesInfo = getSeriesInfo();
		final int nImgDim = seriesInfo[0];
		final int imgDim = seriesInfo[1];
		final int imgSize = seriesInfo[2];
		final int nPsfDim = seriesInfo[3];
		final int psfDim = seriesInfo[4];
		final int psfSize = seriesInfo[5];

		/*
		 * The input series only contains 2 serieses take the length of the
		 * longer series for deconvolution
		 */
		lenPsf = psfSize;
		if (imgSize > psfSize) {
			lenConv = imgSize;
		}
		else {
			lenConv = psfSize;
		}

		// make arrays for the image size
		final int[] psfHArr = new int[psfSize];
		final int[] psfWArr = new int[psfSize];

		// make 3D matrices for image sets
		double[][][] psfDataIn = new double[psfSize][][];

		// Load image data to imgDataIn matrix
		loadImageSet(nImgDim, imgDim, imgSize);
		showProgress(0.1);

		// Load psf data into psfDataIn matrix
		for (int j = 0; j < psfSize; j++) {
			if (segImgSet != null) {
				final ImageSingle<?> psfImgSi = segImgSet.get0iR(j);
				double[][] psfData = psfImgSi.get2DImageMatrix();

				psfHArr[j] = psfData.length;
				psfWArr[j] = psfData[0].length;
				psfDataIn[j] = copy(psfData);
				psfData = null;
			}
			else {
				final int[] psfIndex = new int[nPsfDim];
				psfIndex[psfDim] = j;
				final ImageSingle<?> psfSingle = psfSet.get0iR(psfIndex);
				if (!GreyU16DiskSingle.class.isInstance(psfSingle)) {
					JOptionPane.showMessageDialog(
						menuItem,
						"The image single has to be GreyU16DiskSingle",
						"Wrong Image Format!",
						JOptionPane.ERROR_MESSAGE);
					return;
				}

				// subtract the background from the image
				final double psfBg = getBackground(psfSingle);
				double[][] psfData
					= subtractBackground(
						psfSingle.get2DImageMatrix(),
						psfBg);

				psfHArr[j] = psfData.length;
				psfWArr[j] = psfData[0].length;
				psfDataIn[j] = copy(psfData);
				psfSingle.flush();
				psfData = null;
			}
		}

		imgH = max(imgHArr);
		imgW = max(imgWArr);
		psfH = max(psfHArr);
		psfW = max(psfWArr);
		showProgress(0.2);

		if (psfH > imgH || psfW > imgW) {
			JOptionPane.showMessageDialog(
				menuItem,
				"The size of the image should be large than size of the psf.",
				"Wrong Image Size!",
				JOptionPane.ERROR_MESSAGE);
			return;
		}

		// load deconvolution method
		final DeconvMethod deconvMethod
			= DeconvMethod.findMethod(deconvMethodCb.getValue());
		if (deconvMethod == null)
			return;

		double[][][] deconvData;

		if (deconvMethod == DeconvMethod.AGARD
			|| deconvMethod == DeconvMethod.RICHARDSON_LUCY
			|| deconvMethod == DeconvMethod.ACCEL_RICHARDSON_LUCY) {

			// Use Richardson-Lucy algorithm, the number of iterations is 10
			deconvData = deconvolutionRL(
				imgDataIn,
				psfDataIn,
				deconvMethod,
				0,
				10);
		}
		else {

			// Use least square algorithm, load regularization factor
			final String regItem = regItemTf.getText();
			if (regItem == null)
				return;

			final double regItemValue = Double.valueOf(regItem);

			// Apply deconvolution with least square based algorithm
			deconvData = deconvolutionLsq(
				imgDataIn,
				psfDataIn,
				deconvMethod,
				regItemValue);
		}

		imgDataIn = null;
		psfDataIn = null;
		showProgress(0.8);

		/*
		 * save deconvolution set, the length of the deconvolution series
		 * follows the length of img
		 */
		deconvSet = saveImageSet(deconvData, imgSize, "Deconvolution");
		deconvData = null;

		// save comparison image set
		final int compareLen = imgSet.size()[0];
		compareSet = makeCompareSet(imgSet, deconvSet, compareLen, "Compare");
		showProgress(1.0);
	}

	/**
	 * Make the input matrix of the size of deconvoltuon, wich is lenConv x imgH
	 * x imgW
	 * 
	 * @param input
	 * 
	 * @return A 3D matrix of lenConv x imgH x imgW
	 */
	private double[][][] makeDeconvSize(final double[][][] input) {
		final double[][][] result = new double[lenConv][imgH][imgW];

		List<Integer> tuples = new ArrayList<>();
		for (int k = 0; k < input.length; k++)
			tuples.add(k);

		tuples.parallelStream().forEachOrdered(tuple -> {
			final int h = input[tuple].length;
			final int w = input[tuple][0].length;

			for (int j = 0; j < h; j++)
				for (int i = 0; i < w; i++)
					result[tuple][j][i] = input[tuple][j][i];
		});

		return result;
	}

	/**
	 * iCircShift the psf matrix and normalize the matrix
	 * 
	 * @param input
	 *        The psf matrix
	 * 
	 * @return The processed psf matrix
	 */
	private double[][][] processPSF(final double[][][] input) {

		// make circshift to the psf matrix
		final int shiftX = (int) Math.floor(lenPsf / 2);
		final int shiftY = (int) Math.floor(psfW / 2);
		final int shiftZ = (int) Math.floor(psfH / 2);
		double[][][] psfShift = iCircShift(input, shiftZ, shiftY, shiftX);

		// normalize psf matrix
		final double psfSum = sum(sum(sum(psfShift)));
		multiplyI(psfShift, 1 / psfSum);

		return psfShift;
	}

	/**
	 * Dot divide x by y, substitute zeros in y into EPS to x
	 * 
	 * @param x
	 * 
	 * @param y
	 * 
	 * @return The result matrix
	 * 
	 * @throws IllegalArgumentException
	 *         If input arguments have different sizes
	 */
	private double[][][] dotDivide(
			final double[][][] x,
			final double[][][] y) {
		if (!isSameSize(x, y))
			throw new IllegalArgumentException(
				"Input arguments should be of the same size");

		final int n = y.length;
		final int h = y[0].length;
		final int w = y[0][0].length;

		// substitute the elements equal to zero in y to EPS
		final double[][][] result = new double[n][][];

		List<Integer> tuples = new ArrayList<>();
		for (int k = 0; k < n; k++)
			tuples.add(k);

		tuples.parallelStream().forEachOrdered(tuple -> {
			final double[][] denor = copy(y[tuple]);

			for (int i = 0; i < w; i++) {
				for (int j = 0; j < h; j++) {
					if (denor[j][i] == 0)
						denor[j][i] = EPS;
				}
			}

			result[tuple] = copy(divide(x[tuple], denor));
		});

		return result;
	}

	/**
	 * Convert the negative elements in the input matrix to zero
	 * 
	 * @param input
	 * 
	 * @return The converted matrix
	 */
	private double[][][] negativeToZero(final double[][][] input) {
		final int n = input.length;
		final int h = input[0].length;
		final int w = input[0][0].length;

		final double[][][] result = new double[n][h][w];

		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < n; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (input[tuple][y][x] >= 0)
						result[tuple][y][x] = input[tuple][y][x];
				}
			}
		});

		return result;
	}

	/**
	 * Prepare the image matrix data for the Richardson-Lucy iteration in the
	 * accelerated Richardson-Lucy algorithm
	 * 
	 * @param imgIter
	 *        The current value of the image matrix
	 * 
	 * @param imgIterPre
	 *        The previous value of the image matrix
	 * 
	 * @param gX
	 *        The first element in the acceleration factor calculation
	 * 
	 * @param gY
	 *        The second element in the acceleration factor calculation
	 * 
	 * @return The image matrix for the lucyIteration
	 */
	private double[][][] makeImageMatrix(
			final double[][][] imgIter,
			final double[][][] imgIterPre,
			final double[][][] gX,
			final double[][][] gY) {
		// calculate the acceleration factor
		double[] alphaDenor = new double[imgH];
		double[] alphaNumer = new double[imgH];

		for (int i = 0; i < imgH; i++) {
			alphaNumer[i] = sum(
				sum(multiply(gX[i], gY[i])));
			alphaDenor[i] = sum(
				sum(multiply(gY[i], gY[i])));
		}

		final double alpha
			= sum(alphaNumer) / (sum(alphaDenor) + EPS);
		alphaDenor = null;
		alphaNumer = null;

		// calculate the transformed imgIter
		double[][][] imgIterIn;

		if (alpha > 1 || alpha < 0)
			imgIterIn = copy(imgIter);
		else {
			double[][][] imgIterMat = add(
				imgIter,
				multiply(imgIterPre, -1.0));

			double[][][] imgIterItem = multiply(imgIterMat, alpha);
			imgIterIn = negativeToZero(add(imgIter, imgIterItem));
			imgIterMat = null;
			imgIterItem = null;
		}

		return imgIterIn;
	}

	/**
	 * One iteration in Agard algorithm
	 * 
	 * @param imgIter
	 *        The current value of the image matrix
	 * 
	 * @param psfTrans
	 *        The Fourier transformed PSF
	 * 
	 * @param rawImg
	 *        The original value of the image matrix
	 * 
	 * @return The image matrix after the current iteration
	 */
	private double[][][] agardIteration(
			final double[][][] imgIter,
			final ComplexMatrix3D psfTrans,
			final double[][][] rawImg) {

		// calculate iteration factor
		ComplexMatrix3D imgIterMat = new ComplexMatrix3D(
			imgIter,
			new double[imgH][imgW][lenConv]);
		ComplexMatrix3D imgIterTrans
			= JTransforms3DUtil.fft3D(imgIterMat);
		ComplexMatrix3D.multiplyI(imgIterTrans, psfTrans);
		double[][][] prodTrans
			= JTransforms3DUtil.ifft3D(imgIterTrans).getReal();
		final double[][][] iterFactor = dotDivide(rawImg, prodTrans);

		// multiply the imgIter with the iteration factor
		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < imgH; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int x = 0; x < lenConv; x++) {
				for (int y = 0; y < imgW; y++) {
					imgIter[tuple][y][x]
						= imgIter[tuple][y][x] * iterFactor[tuple][y][x];
					if (imgIter[tuple][y][x] < 0)
						imgIter[tuple][y][x] = 0;
				}
			}
		});

		imgIterMat = null;
		imgIterTrans = null;
		prodTrans = null;

		return imgIter;
	}

	/**
	 * One iteration in Richardson-Lucy algorithm
	 * 
	 * @param imgIter
	 *        The current value of the image matrix
	 * 
	 * @param psfTrans
	 *        The Fourier transformed PSF
	 * 
	 * @param rawImg
	 *        The original value of the image matrix
	 * 
	 * @param bg
	 *        The background value
	 * 
	 * @return The image matrix after the current iteration
	 */
	private double[][][] iterationRL(
			final double[][][] imgIter,
			final ComplexMatrix3D psfTrans,
			final double[][][] rawImg,
			final double bg) {

		// calculate transformation factor
		ComplexMatrix3D imgIterMat
			= new ComplexMatrix3D(imgIter, new double[imgH][imgW][lenConv]);
		ComplexMatrix3D imgTrans = JTransforms3DUtil.fft3D(imgIterMat);
		ComplexMatrix3D.multiplyI(imgTrans, psfTrans);
		ComplexMatrix3D prodTrans
			= ComplexMatrix3D.add(JTransforms3DUtil.ifft3D(imgTrans), bg);
		double[][][] iterFactor = dotDivide(rawImg, prodTrans.getReal());
		final ComplexMatrix3D factorMat = new ComplexMatrix3D(
			iterFactor,
			new double[imgH][imgW][lenConv]);

		imgIterMat = null;
		imgTrans = null;
		prodTrans = null;
		iterFactor = null;

		// calculate LR transformed image data
		ComplexMatrix3D factorMatTrans = JTransforms3DUtil.fft3D(factorMat);
		ComplexMatrix3D psfConj = psfTrans.getConjugate();
		ComplexMatrix3D.multiplyI(factorMatTrans, psfConj);
		final double[][][] imgFactor
			= JTransforms3DUtil.ifft3D(factorMatTrans).getReal();

		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < imgH; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int x = 0; x < lenConv; x++) {
				for (int y = 0; y < imgW; y++) {
					imgIter[tuple][y][x]
						= imgIter[tuple][y][x] * imgFactor[tuple][y][x];
					if (imgIter[tuple][y][x] < 0)
						imgIter[tuple][y][x] = 0;
				}
			}
		});

		factorMatTrans = null;
		psfConj = null;

		return imgIter;
	}

	/**
	 * Single regularized least square algorithm
	 * 
	 * @param img
	 * 
	 * @param psf
	 * 
	 * @param psfPow2
	 * 
	 * @param regItem
	 *        Regularization item
	 * 
	 * @return Deconvolution factor
	 */
	private ComplexMatrix3D simpleRLS(
			final ComplexMatrix3D img,
			final ComplexMatrix3D psf,
			final ComplexMatrix3D psfPow2,
			final double regItem) {
		// calculate numerator and denominator
		ComplexMatrix3D numor = ComplexMatrix3D.multiply(psf, img);
		ComplexMatrix3D denor = ComplexMatrix3D.add(psfPow2, regItem);

		// divide denominator to numerator
		ComplexMatrix3D.divideI(numor, denor);
		denor = null;

		return numor;
	}

	/**
	 * The regularized least square algorithm used in MATLAB
	 * 
	 * @param img
	 * 
	 * @param psf
	 * 
	 * @param psfPow2
	 * 
	 * @param regItem
	 *        Regularization item
	 * 
	 * @return Deconvolution factor
	 */
	private ComplexMatrix3D matRLS(
			final ComplexMatrix3D img,
			final ComplexMatrix3D psf,
			final ComplexMatrix3D psfPow2,
			final double regItem) {
		// calculate numerator and denominator
		final ComplexMatrix3D numor = ComplexMatrix3D.multiply(psf, img);
		final ComplexMatrix3D denor = ComplexMatrix3D.add(psfPow2, regItem);

		// process denominator
		final double oTiny = 5.0e-8;
		double[][][] numorAbs = numor.getAbs();
		final double numorMax
			= max(max(max(numorAbs)));
		final double oSmall = numorMax * oTiny;

		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < imgH; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int y = 0; y < imgW; y++) {
				for (int x = 0; x < lenConv; x++) {
					if (denor.real[tuple][y][x] < oSmall)
						if (denor.real[tuple][y][x] > 0)
							denor.real[tuple][y][x] = oSmall;
						else
							denor.real[tuple][y][x] = -1.0 * oSmall;
				}
			}
		});

		// divide denominator to numerator
		ComplexMatrix3D.divideI(numor, denor);
		numorAbs = null;

		return numor;
	}

	/**
	 * Modified regularized least square algorithm
	 * 
	 * @param img
	 * 
	 * @param psf
	 * 
	 * @param psfPow2
	 * 
	 * @param psfPow4
	 * 
	 * @param regItem
	 *        Regularization item
	 * 
	 * @return Deconvolution factor
	 */
	private ComplexMatrix3D modifiedRLS(
			final ComplexMatrix3D img,
			final ComplexMatrix3D psf,
			final ComplexMatrix3D psfPow2,
			final ComplexMatrix3D psfPow4,
			final double regItem) {
		// calculate numerator and denominator
		ComplexMatrix3D item = ComplexMatrix3D.multiply(psf, img);
		ComplexMatrix3D numor = ComplexMatrix3D.multiply(item, psfPow2);
		ComplexMatrix3D denor = ComplexMatrix3D.add(psfPow4, regItem);

		// divide denominator to numerator
		ComplexMatrix3D.divideI(numor, denor);
		denor = null;
		item = null;

		return numor;
	}

	/**
	 * Weighted regularized least square algorithm
	 * 
	 * @param img
	 * 
	 * @param psf
	 * 
	 * @param psfPow2
	 * 
	 * @param regItem
	 *        Regularization item
	 * 
	 * @return Deconvolution factor
	 */
	private ComplexMatrix3D weightedRLS(
			final ComplexMatrix3D img,
			final ComplexMatrix3D psf,
			final ComplexMatrix3D psfPow2,
			final double regItem) {
		// calculate weight factor
		final double[][][] factor = new double[imgH][imgW][lenConv];
		final double factorDenor
			= imgH * imgH + imgW * imgW + lenConv * lenConv;

		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < imgH; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int y = 0; y < imgW; y++) {
				for (int x = 0; x < lenConv; x++) {
					final double itemH
						= (double) tuple + 1 - Math.round((double) imgH / 2);
					final double factorH = itemH * itemH;

					final double itemW
						= (double) y + 1 - Math.round((double) imgW / 2);
					final double factorW = itemW * itemW;

					final double itemConv
						= (double) x + 1 - Math.round((double) lenConv / 2);
					final double factorConv = itemConv * itemConv;

					factor[tuple][y][x]
						= (factorH + factorW + factorConv)
							/ factorDenor
							* 2
							* Math.PI
							* 2
							* regItem;
				}
			}
		});

		// ifftshift factor
		double[][][] factorShift = ifftShift(factor);
		ComplexMatrix3D factorComp
			= new ComplexMatrix3D(
				factorShift,
				new double[imgH][imgW][lenConv]);

		// calculate numerator and denominator
		ComplexMatrix3D numor = ComplexMatrix3D.multiply(psf, img);
		ComplexMatrix3D denor = ComplexMatrix3D.add(psfPow2, factorComp);

		// divide denominator to numerator
		ComplexMatrix3D.divideI(numor, denor);
		denor = null;
		factorShift = null;
		factorComp = null;

		return numor;
	}

	/**
	 * Show the percentage of completion in the progress bar
	 * 
	 * @param input
	 *        The value showed in the progress bar
	 */
	private void showProgress(final double input) {
		if (prob == null || completionTf == null)
			return;

		final double percentile = input * 100;
		prob.setProgress(input);
		completionTf.setText(String.format("Complete: %3.0f%%", percentile));
	}

	/**
	 * get the series dimension and length of image set and psf set
	 * 
	 * @return an array includes number of image set dimensions, image series
	 *         dimension, image series length, number of psf set dimensions, psf
	 *         series dimension, psf series length
	 */
	private int[] getSeriesInfo() {
		final int[] imgSizeArr = imgSet.size();
		final int[] psfSizeArr = psfSet.size();
		final int[] result = new int[6];

		// length of image set
		if (imgSizeArr.length == 1) {
			result[0] = 1;
			result[1] = 0;
			result[2] = imgSizeArr[0];
		}
		else {
			result[0] = imgSizeArr.length;
			result[1] = Integer.valueOf(imgSeriesTf.getText()) - 1;
			result[2] = imgSizeArr[result[1]];
		}

		// length of psf set
		if (psfSizeArr.length == 1) {
			result[3] = 1;
			result[4] = 0;
			result[5] = psfSizeArr[0];
		}
		else {
			result[3] = psfSizeArr.length;
			result[4] = Integer.valueOf(psfSeriesTf.getText()) - 1;
			result[5] = psfSizeArr[result[4]];
		}

		return result;
	}

	/**
	 * Load data from a image set
	 * 
	 * @param nImgDim
	 *        The number of dimensions in the image set
	 * 
	 * @param imgDim
	 *        The dimension of the image series
	 * 
	 * @param imgSize
	 *        The length of the image series
	 */
	private void loadImageSet(
			final int nImgDim,
			final int imgDim,
			final int imgSize) {
		imgDataIn = new double[imgSize][][];
		imgHArr = new int[imgSize];
		imgWArr = new int[imgSize];

		// Load image data to imgDataIn matrix
		for (int i = 0; i < imgSize; i++) {
			final ImageSingle<?> imgSingle;
			final int[] imgIndex = new int[nImgDim];
			imgIndex[imgDim] = i;
			imgSingle = imgSet.get0iR(imgIndex);
			if (!GreyU16DiskSingle.class.isInstance(imgSingle)) {
				JOptionPane.showMessageDialog(
					menuItem,
					"The image single has to be GreyU16DiskSingle",
					"Wrong Image Format!",
					JOptionPane.ERROR_MESSAGE);
				return;
			}

			// subtract background from the image
			final double imgBg = getBackground(imgSingle);
			double[][] imgData
				= subtractBackground(imgSingle.get2DImageMatrix(), imgBg);

			imgHArr[i] = imgData.length;
			imgWArr[i] = imgData[0].length;
			imgDataIn[i] = copy(imgData);
			imgSingle.flush();
			imgData = null;
		}
	}

	/**
	 * Refresh the image set on the image set browser
	 * 
	 * @param input
	 */
	private void refreshImage(final ImageSet input) {
		hub = MIATool.getMIAToolHub();
		mtm = hub.getMIAToolMain();
		imgb = mtm.getImageSetBrowser();
		imgb.unloadAll();
		imgb.updateFields();
		imgb.setAsLoaded(input);
	}

	/**
	 * Adjust the intensity of a image set and send it to the image set browser
	 * 
	 * @param input
	 */
	private void intensityAdjust(final ImageSet input) {
		hub = MIATool.getMIAToolHub();
		mtd = hub.getMIAToolDirectory();
		if (mtd == null)
			return;

		// make a process chain for automatic intensity adjustment
		final MIAProcessingChain proceChain = new MIAProcessingChain();
		final PercentileIntensityAutoAdjusterBlock piaab
			= new PercentileIntensityAutoAdjusterBlock();
		proceChain.addProcessBlock(piaab);
		piaab.imgSetWrapper = input.getTransientObject(mtd);
		piaab.highInPercentile = 99.5;// 99.5%
		piaab.lowInPercentile = 2.5;// 2.5%

		// execute the process chain
		try {
			proceChain.execute();
		}
		catch (final Exception e1) {
			e1.printStackTrace();
		}
		System.out.println("Intensity automatically adjusted");

		// load the intensity set to the image set browser
		final IntensityAdjustSet intSet = piaab.getIntensityAdjustSet();
		intSet.setParent(input);
		final IntensityTool intTool
			= hub.getTool(IntensityTool.class, IntensityAdjustSet.class);
		intTool.getSetBrowser().setAsLoaded(intSet);
	}

	/**
	 * save image set to mtd and path
	 * 
	 * @param input
	 * 
	 * @param length
	 *        The length of the new image set
	 * 
	 * @param seriesName
	 *        The name of the new image set in mtd
	 * 
	 * @return The newly saved image set
	 */
	private ImageSet saveImageSet(
			final double[][][] input,
			final int length,
			final String seriesName) {
		// Create new series directories in the MIAToolDirectory
		final String mtdRoot = mtd.getRootPath();
		final String imgFiles = buildPath(mtdRoot, IMAGES_DIR);
		final String seriesFolder = buildPath(imgFiles, seriesName);

		// delete the directory that already existed and creat a new directory
		final File dir = new File(seriesFolder);
		if (dir.exists())
			dir.delete();
		new File(seriesFolder).mkdirs();

		// Save images to disk
		final ImageSet result = new ImageSet(length);
		final double max = max(max(max(input)));
		for (int j = 0; j < length; j++) {
			final int height = input[j].length;
			final int width = input[j][0].length;

			// subtract backgrouond from the data
			final GreyD64RAMSingle ramImgSi = new GreyD64RAMSingle(input[j]);
			final double ramImgSiBg = getBackground(ramImgSi);
			double[][] ramImgData = subtractBackground(input[j], ramImgSiBg);

			// linearize data
			double[] linData = linearize(ramImgData);
			short[] sData = convertToInt16(linData, max);

			// make file names
			final String fileName = "s1_" + Integer.toString(j + 1) + ".tif";
			final String newImgName = buildPath(seriesFolder, fileName);

			// save files
			final MIATIFFWriter mtw = new MIATIFFWriter(sData, width, height);
			try {
				mtw.write(0, newImgName);
			}
			catch (final IOException e) {
				e.printStackTrace();
			}

			// make a new image set and fill it with the saved images
			final GreyU16DiskSingle imgSingle;
			final String imgName = buildPath(IMAGES_DIR, seriesName, fileName);
			imgSingle = new GreyU16DiskSingle(imgName, true);
			result.set0iR(imgSingle, j);

			// flush memory
			linData = null;
			sData = null;
			ramImgData = null;
			ramImgSi.flush();
			imgSingle.flush();
		}

		// save results to mtd
		result.setFilename(seriesName);
		result.setParent(mtd);
		try {
			mtd.saveObject(null, result);
		}
		catch (final IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	/**
	 * Make a image set in which the first row is the original image set and the
	 * second row is the deconvolution image set
	 * 
	 * @param inputO
	 *        the original image set
	 * 
	 * @param inputR
	 *        the deconvolution image set
	 * 
	 * @return the combined image set for comparison
	 */
	private ImageSet makeCompareSet(
			final ImageSet inputO,
			final ImageSet inputR,
			final int length,
			final String seriesName) {
		// Create new series directories in the MIAToolDirectory
		final String mtdRoot = mtd.getRootPath();
		final String imgFiles = MIAUtil.buildPath(mtdRoot, IMAGES_DIR);
		final String seriesFolderO
			= MIAUtil.buildPath(imgFiles, seriesName, "Series1");
		final String seriesFolderR
			= MIAUtil.buildPath(imgFiles, seriesName, "Series2");

		// Create new directories
		new File(seriesFolderO).mkdirs();
		new File(seriesFolderR).mkdirs();

		// Save images to the disk
		final ImageSet result = new ImageSet(2, length);
		final int[] inputOSize = inputO.size();

		for (int j = 0; j < length; j++) {
			// get image singles from the image set
			final GreyU16DiskSingle oSi;
			if (inputOSize.length == 1)
				oSi = (GreyU16DiskSingle) inputO.get0iR(j);
			else
				oSi = (GreyU16DiskSingle) inputO.get0iR(j, 0, 0);

			final GreyU16DiskSingle rSi = (GreyU16DiskSingle) inputR.get0iR(j);

			// get file location
			final String oSiPath = oSi.getImagePath();
			final String rSiPath = rSi.getImagePath();

			// make destination file names
			final String fileNameO = "s1_" + Integer.toString(j + 1) + ".tif";
			final String fileNameR = "s2_" + Integer.toString(j + 1) + ".tif";
			final String newOSiPath
				= MIAUtil.buildPath(seriesFolderO, fileNameO);
			final String newRSiPath
				= MIAUtil.buildPath(seriesFolderR, fileNameR);

			// copy files to the new destinations
			final File oSiFile = new File(oSiPath);
			final File newOSiFile = new File(newOSiPath);
			final File rSiFile = new File(rSiPath);
			final File newRSiFile = new File(newRSiPath);
			try {
				FileUtil.copyFile(oSiFile, newOSiFile);
				FileUtil.copyFile(rSiFile, newRSiFile);
			}
			catch (final IOException e) {
				e.printStackTrace();
			}

			// make a new image set with the saved images
			final GreyU16DiskSingle imgSingleO;
			final GreyU16DiskSingle imgSingleR;
			final String imgNameO
				= buildPath(IMAGES_DIR, seriesName, "Series1", fileNameO);
			final String imgNameR
				= buildPath(IMAGES_DIR, seriesName, "Series2", fileNameR);
			imgSingleO = new GreyU16DiskSingle(imgNameO, true);
			imgSingleR = new GreyU16DiskSingle(imgNameR, true);
			result.set0iR(imgSingleO, 0, j);
			result.set0iR(imgSingleR, 1, j);

			// flush memory
			oSi.flush();
			rSi.flush();
		}

		// Save the image set to the MIAToolDirectory
		result.setFilename(seriesName);
		result.setParent(mtd);
		try {
			mtd.saveObject(null, result);
		}
		catch (final IOException e) {
			e.printStackTrace();
		}

		return result;
	}

	/** Utility class for the JTransform library */
	public static class JTransforms3DUtil {
		/**
		 * Convert a 3D matrix used by JTransform into an object of
		 * ComplexMatrix3D
		 * 
		 * @param complex
		 *        3D matrix for JTransforms
		 * 
		 * @return ComplexMatrix3D
		 */
		public static ComplexMatrix3D convertToComplexMatrix3D(
				final double[][][] complex) {
			final int n = complex.length;
			final int h = complex[0].length;
			final int w = complex[0][0].length / 2;

			// Initialize elements of the output
			final double[][][] real = new double[n][h][w];
			final double[][][] imaginary = new double[n][h][w];

			/*
			 * loop through the 3D matrix to separate the real part and
			 * imaginary parts
			 */
			List<Integer> tuples = new ArrayList<>();
			for (int z = 0; z < n; z++)
				tuples.add(z);

			tuples.parallelStream().forEachOrdered(tuple -> {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						real[tuple][y][x] = complex[tuple][y][2 * x];
						imaginary[tuple][y][x] = complex[tuple][y][2 * x + 1];
					}
				}
			});

			// Construct the output object
			final ComplexMatrix3D result = new ComplexMatrix3D(real, imaginary);

			return result;
		}

		/**
		 * Convert the object into a 3D complex matrix in which the real part
		 * and the imaginary part of a complex number are stored one by another
		 * 
		 * @param input
		 *        ComplexMatrix3D
		 * 
		 * @return 3D matrix for JTransforms
		 */
		public static double[][][] convertToMatrix(
				final ComplexMatrix3D input) {
			final int n = input.real.length;
			final int h = input.real[0].length;
			final int w = input.real[0][0].length;

			final double[][][] result = new double[n][h][2 * w];

			List<Integer> tuples = new ArrayList<>();
			for (int z = 0; z < n; z++)
				tuples.add(z);

			tuples.parallelStream().forEachOrdered(tuple -> {
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						result[tuple][y][2 * x] = input.real[tuple][y][x];
						result[tuple][y][2 * x + 1]
							= input.imaginary[tuple][y][x];
					}
				}
			});

			return result;
		}

		/**
		 * Make 3D FFT
		 * 
		 * @param input
		 *        matrix for JTransforms
		 * 
		 * @return Fourier transformed matrix
		 */
		public static ComplexMatrix3D fft3D(final ComplexMatrix3D input) {
			double[][][] matrix = convertToMatrix(input);
			final int n = matrix.length;
			final int h = matrix[0].length;
			final int w = matrix[0][0].length / 2;

			// make a new instance of JTransforms and apply FFT
			final DoubleFFT_3D fft = new DoubleFFT_3D(n, h, w);
			fft.complexForward(matrix);

			final ComplexMatrix3D compMat = convertToComplexMatrix3D(matrix);
			matrix = null;

			return compMat;
		}

		/**
		 * Make 3D inverse FFT
		 * 
		 * @param input
		 *        matrix for JTransforms
		 * 
		 * @return Inverse Fourier transformed matrix
		 */
		public static ComplexMatrix3D ifft3D(final ComplexMatrix3D input) {
			double[][][] matrix = convertToMatrix(input);
			final int n = matrix.length;
			final int h = matrix[0].length;
			final int w = matrix[0][0].length / 2;

			/*
			 * make a new instance of JTransforms and apply iFFT, re-scale the
			 * result
			 */
			final DoubleFFT_3D ifft = new DoubleFFT_3D(n, h, w);
			ifft.complexInverse(matrix, true);

			final ComplexMatrix3D compMat = convertToComplexMatrix3D(matrix);
			matrix = null;

			return compMat;
		}
	}

	/** Define the methods of deconvolution */
	public static enum DeconvMethod {
		/** Simple regularied least square algorithm */
		SIMPLE_RLS("Simple regularized least square"),

		/**
		 * The regularized least squared algorithm translated from MATLAB
		 * build-in function
		 */
		MAT_RLS("Matlab regularized least square"),

		/**
		 * Added modifying factor to the numerator of the deconvolution factor
		 */
		MODIFIED_RLS("Modified regularized least square"),

		/** Added a weight to each element in the deconvolution factor */
		WEIGHTED_RLS("Weighted regularized least square"),

		/** Agard algorithm */
		AGARD("Agard algorithm"),

		/** Richardson_Lucy algorithm */
		RICHARDSON_LUCY("Richardson-Lucy algorithm"),

		/** Accelerated Richardson-Lucy algorithm */
		ACCEL_RICHARDSON_LUCY("Accelerated Richardson-Lucy algorithm");

		private final String method;

		DeconvMethod(final String str) {
			this.method = str;
		}

		/**
		 * 
		 * @return the deconvolution method
		 */
		public String getMethod() {
			return this.method;
		}

		/**
		 * find the method for the input string
		 * 
		 * @return the deconvolution method
		 */
		public static DeconvMethod findMethod(final String input) {
			if (input == null)
				return null;

			DeconvMethod method = null;
			if (input.equals(SIMPLE_RLS.getMethod()))
				method = SIMPLE_RLS;
			if (input.equals(MAT_RLS.getMethod()))
				method = MAT_RLS;
			if (input.equals(MODIFIED_RLS.getMethod()))
				method = MODIFIED_RLS;
			if (input.equals(WEIGHTED_RLS.getMethod()))
				method = WEIGHTED_RLS;
			if (input.equals(AGARD.getMethod()))
				method = AGARD;
			if (input.equals(RICHARDSON_LUCY.getMethod()))
				method = RICHARDSON_LUCY;
			if (input.equals(ACCEL_RICHARDSON_LUCY.getMethod()))
				method = ACCEL_RICHARDSON_LUCY;
			return method;
		}
	}

	/**
	 * Inner class to simulate a Born-Wolf 3D PSF image.
	 * 
	 * @author Anish V. Abraham
	 * @since 1.0
	 * @version 0.2
	 */
	public class BornWolf3DModelSimScript {

		public String dirPath = null;

		// The string format of the name of the images
		final public String imgFormat = "s1_%d.tif";
		// Components of the main panel
		public TextField pixHTf;
		public TextField pixWTf;
		public TextField expTimeTf;
		public TextField magTf;
		public TextField naTf;
		public TextField refTf;
		public TextField waveTf;
		public TextField backTf;
		public TextField pdrTf;
		public TextField startTf;
		public TextField stepSizeTf;
		public TextField imgHTf;
		public TextField imgWTf;
		public TextField nImgTf;
		public TextField saveTf;
		public Button genPSFBtn;

		// Imaging parameters
		private double pixelHeight = 6.7; // microns
		private double pixelWidth = 6.7; // microns
		private double exposureTime = 1; // seconds
		private double magnification = 63;
		private double na = 1.4;
		private double immersionMediumRefractiveIndex = 1.515;
		private double wavelength = 0.48; // microns
		private int nImages = 20;
		private double stepSize = 0.1;
		private double start = -1;
		private int imageHeight = 60; // pixels
		private int imageWidth = 60; // pixels

		// Model specifications / parameters
		private final double x0 = imageWidth * pixelWidth / 2 / magnification;
		private final double y0 = imageHeight * pixelHeight / 2 / magnification;
		private final double alpha = 2 * Math.PI * na / wavelength;
		private double background = 0; // photons/pixel/s
		private double photonDetectionRate = 1000; // photons/s

		// Advanced inputs
		private final int trapezoidalXGridding = 11;
		private final int trapezoidalYGridding = 11;
		private final double modelIntegrationStepSize = 0.001;

		BornWolf3D.Model modelObj = new BornWolf3D.Model();

		public BornWolf3DModelSimScript() {

		}

		public void setupFrame() {
			// make the layout of the main panel
			final VBox mainContainer = new VBox(5);
			mainContainer.setPadding(new Insets(5, 5, 5, 5));
			// detector properties
			final GridPane detecGrid = new GridPane();
			detecGrid.setPadding(new Insets(5, 5, 5, 5));
			detecGrid.setHgap(4);
			detecGrid.setVgap(4);
			pixHTf = new TextField(String.valueOf(pixelHeight));
			pixWTf = new TextField(String.valueOf(pixelWidth));
			expTimeTf = new TextField(String.valueOf(exposureTime));
			magTf = new TextField(String.valueOf(magnification));
			detecGrid.add(new Label("Pixel Height (\u00B5m)"), 0, 0);
			detecGrid.add(pixHTf, 0, 1);
			detecGrid.add(new Label("Pixel Width (\u00B5m)"), 1, 0);
			detecGrid.add(pixWTf, 1, 1);
			detecGrid.add(new Label("Exposure Time (s)"), 0, 2);
			detecGrid.add(expTimeTf, 0, 3);
			detecGrid.add(new Label("Magnification"), 1, 2);
			detecGrid.add(magTf, 1, 3);
			final TitledPane detecPane = new TitledPane();
			detecPane.setCollapsible(false);
			detecPane.setText("Detector Properties");
			detecPane.setContent(detecGrid);
			// Imaging System properties
			final GridPane imgGrid = new GridPane();
			imgGrid.setPadding(new Insets(5, 5, 5, 5));
			imgGrid.setHgap(4);
			imgGrid.setVgap(4);
			naTf = new TextField(String.valueOf(na));
			refTf
				= new TextField(
					String.valueOf(immersionMediumRefractiveIndex));
			waveTf = new TextField(String.valueOf(wavelength));
			backTf = new TextField(String.valueOf(background));
			pdrTf = new TextField(String.valueOf(photonDetectionRate));
			imgGrid.add(new Label("N.A."), 0, 0);
			imgGrid.add(naTf, 0, 1);
			imgGrid.add(new Label("Refrative Index"), 1, 0);
			imgGrid.add(refTf, 1, 1);
			imgGrid.add(new Label("Wavelength (\u00B5m)"), 0, 2);
			imgGrid.add(waveTf, 0, 3);
			imgGrid.add(new Label("Background (photons/pixel/s)"), 1, 2);
			imgGrid.add(backTf, 1, 3);
			imgGrid.add(
				new Label("Photon Detection Rate\n" + "(photons/s)"),
				0,
				4);
			imgGrid.add(pdrTf, 0, 5);
			final TitledPane imgPane = new TitledPane();
			imgPane.setCollapsible(false);
			imgPane.setText("Imaging System Properties");
			imgPane.setContent(imgGrid);
			// Simulation Properties
			final GridPane simGrid = new GridPane();
			simGrid.setPadding(new Insets(5, 5, 5, 5));
			simGrid.setHgap(4);
			simGrid.setVgap(4);
			startTf = new TextField(String.valueOf(start));
			stepSizeTf = new TextField(String.valueOf(stepSize));
			imgHTf = new TextField(String.valueOf(imageHeight));
			imgWTf = new TextField(String.valueOf(imageWidth));
			nImgTf = new TextField(String.valueOf(nImages));
			simGrid.add(new Label("Start Position (\u00B5m)"), 0, 0);
			simGrid.add(startTf, 0, 1);
			simGrid.add(new Label("Step Size (\u00B5m)"), 1, 0);
			simGrid.add(stepSizeTf, 1, 1);
			simGrid.add(new Label("Image Height (pixel)"), 0, 2);
			simGrid.add(imgHTf, 0, 3);
			simGrid.add(new Label("Image Width (pixel)"), 1, 2);
			simGrid.add(imgWTf, 1, 3);
			simGrid.add(new Label("Number of Images"), 0, 4);
			simGrid.add(nImgTf, 0, 5);
			final TitledPane simPane = new TitledPane();
			simPane.setCollapsible(false);
			simPane.setText("Simulation Properties");
			simPane.setContent(simGrid);
			// Run Simulation
			final GridPane runGrid = new GridPane();
			runGrid.setPadding(new Insets(5, 5, 5, 5));
			runGrid.setHgap(4);
			runGrid.setVgap(4);
			saveTf = new TextField("Null");
			genPSFBtn = new Button("Generate PSF");
			runGrid.add(new Label("Save Path"), 0, 0);
			runGrid.add(saveTf, 0, 1);
			runGrid.add(new Label("Run Simulation"), 1, 0);
			runGrid.add(genPSFBtn, 1, 1);
			final TitledPane runPane = new TitledPane();
			runPane.setCollapsible(false);
			runPane.setText("Generate And Save PSF");
			runPane.setContent(runGrid);
			mainContainer
				.getChildren()
				.addAll(detecPane, imgPane, simPane, runPane);
			final Stage stage = new Stage();
			stage.setTitle("Simulate PSF");
			final Scene scene = new Scene(mainContainer, 400, 620);
			stage.setScene(scene);
			stage.show();
		}

		public void startup() {
			setupFrame();
			genPSFBtn.setOnAction((event) -> {
				updateField();
				execute();
			});
		}

		public void updateField() {
			pixelHeight = Double.valueOf(pixHTf.getText());
			pixelWidth = Double.valueOf(pixWTf.getText());
			exposureTime = Double.valueOf(expTimeTf.getText());
			magnification = Double.valueOf(magTf.getText());
			na = Double.valueOf(naTf.getText());
			immersionMediumRefractiveIndex = Double.valueOf(refTf.getText());
			wavelength = Double.valueOf(waveTf.getText());
			background = Double.valueOf(backTf.getText());
			photonDetectionRate = Double.valueOf(pdrTf.getText());
			start = Double.valueOf(startTf.getText());
			stepSize = Double.valueOf(stepSizeTf.getText());
			imageHeight = Integer.valueOf(imgHTf.getText());
			imageWidth = Integer.valueOf(imgWTf.getText());
			nImages = Integer.valueOf(nImgTf.getText());
			dirPath = saveTf.getText();
		}

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
			modelObj.detector
				= new Detector(
					MIAUnits.microns(pixelHeight),
					MIAUnits.microns(pixelWidth),
					MIAUnits.seconds(exposureTime),
					magnification);
			modelObj.imageIndices
				= new PixelCoordinates(imageHeight, imageWidth);

			// Integration Parameters
			modelObj.pixelIntegrator
				= new TrapezoidalPixelIntegrator(
					trapezoidalXGridding,
					trapezoidalYGridding,
					modelObj.detector);
			modelObj.modelIntegrator
				= new TrapezoidalPsf3DIntegrator(modelIntegrationStepSize);

			final double[] zLoci = getZLocations(stepSize, nImages);
			for (int i = 0; i < nImages; i++) {
				modelObj.psf.x0 = MIAUnits.microns(x0);
				modelObj.psf.y0 = MIAUnits.microns(y0);
				modelObj.psf.z0 = MIAUnits.microns(zLoci[i]);
				// Processing
				modelObj.simulateProfile();
				// save image
				saveImage(modelObj.get2DImage(), i);
			}
			System.out.println(sum(modelObj.getSimulatedProfile()));
		}

		public double[] getZLocations(final double stepSize, final int nsteps) {

			final double[] locations = new double[nsteps];
			for (int i = 0; i < nsteps; i++) {
				locations[i] = i * stepSize + start;
			}
			return locations;
		}

		/**
		 * Writes the provided matrix as an image to disk.
		 * 
		 * @param image
		 *        The matrix containing the raw pixel values.
		 * @param i
		 *        The index of the image within the series of images currently
		 *        being generated.
		 */
		public final void saveImage(final double[][] image, final int i) {
			// Create new series directories in the MIAToolDirectory
			final String imgFiles = MIAUtil.buildPath(dirPath, IMAGES_DIR);
			final String seriesName = "Series1";
			final String seriesFolder = MIAUtil.buildPath(imgFiles, seriesName);

			// create the folder that will store the images, if it doesn't
			// already
			// exist
			final File dir = new File(seriesFolder);
			if (!dir.exists())
				dir.mkdirs();

			// build the path to the single image to be saved
			final String imgName
				= MIAUtil.buildPath(
					seriesFolder,
					String.format(imgFormat, i + 1));

			// linearize the data and convert into a short array
			final double[] ddata = linearize(image);
			final short[] sdata = toShort(ddata);

			// write the data to disk
			final MIATIFFWriter mtw
				= new MIATIFFWriter(sdata, imageHeight, imageWidth);
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
	 * 2017-06-24, 0.2, Bo Wang: Made it into a stage of the deconvolution
	 * plugin
	 * 
	 * 2016-08-11, 0.1, Anish V. Abraham: Recreated.
	 *
	 */
}

/**
 * REVISION HISTORY
 * 
 * 2017-07-12, 0.4, Bo Wang: Subtract background from the images
 * 
 * 2017-07-06, 0.3, Bo Wang: Added cross section viewer
 * 
 * 2017-06-24, 0.2, Bo Wang: Clean up the code, move methods for complex matrix
 * to ComplexMatrix3D
 * 
 * 2017-05-19, 0.1, Bo Wang: recreate the program from the Matlab version
 * 
 */
