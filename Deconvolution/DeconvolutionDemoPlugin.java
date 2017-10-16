package miatool.plugins;

import static miatool.core.miatooldirectory.MIAToolDirectory.IMAGES_DIR;
import static miatool.core.util.MIAUtil.buildPath;
import static miatool.core.util.numerics.MIAMatOps.linearize;
import static miatool.core.util.numerics.MIAMatOps.max;
import static miatool.plugins.DeconvolutionPlugin.DeconvMethod;
import static javafx.scene.control.Alert.AlertType;
import static miatool.plugins.DeconvolutionDemoPlugin.StructureSimulation.Shape;
import static miatool.core.util.numerics.MIAMatOps.copy;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import miatool.core.miatooldirectory.MIAToolDirectory;
import miatool.core.setssingles.image.GreyU16DiskSingle;
import miatool.core.setssingles.image.ImageSet;
import miatool.core.setssingles.image.ImageSingle;
import miatool.core.util.MatrixUtil;
import miatool.core.util.miatiff.MIATIFFWriter;
import miatool.miamain.MIATool;
import miatool.miamain.MIAToolHub;
import miatool.miamain.MIAToolMain;
import miatool.plugins.MIAPlugin;
import miatool.tools.displaytool.ImageSetBrowser;
import miatool.ui.events.MIAPropertyChangeEvent;
import miatool.plugins.DeconvolutionPlugin;

/**
 * Make a demonstration of how the deconvolution algorithm works. First simulate
 * a 3D structure, then convolve the matrix of the structure with the PSF, and
 * eventually make a deconvolution and recover the original 3D structure.
 * 
 * @version 0.1
 * @since 1.0
 * @author Bo Wang
 */
public class DeconvolutionDemoPlugin
		implements
		MIAPlugin,
		ActionListener {
	public Button genStructBtn;
	public Button convBtn;
	public Button deconvBtn;
	public ComboBox<String> structTypeCb;
	public ComboBox<String> deconvMethodCb;
	public ProgressBar proBar;
	public static final String WINDOW_TITLE = "Deconvolution Demo Plugin";
	public static final String MENU_TITLE = "Deconvolution Tools";
	public final JMenuItem menuItem
		= new JMenuItem(
			WINDOW_TITLE
				+
				"...");

	private Stage stage;

	/** 3D matrix for keeping the psf Data */
	private double[][][] psfMatrix = null;

	/** 3D matrix for keeping the image data */
	private double[][][] structMatrix = null;

	/** 3D matrix for keeping the convolved image data */
	private double[][][] convolved = null;

	/** 3D matrix for keeping the deconvolved image data */
	private double[][][] deconvolved = null;

	/** The shape of the structure */
	private Shape shape;

	public static void main(final String[] args) {
		MIATool.main(args);
		final DeconvolutionDemoPlugin tool = new DeconvolutionDemoPlugin();
		tool.startup();
	}

	public DeconvolutionDemoPlugin() {
		super();
	}

	public void setupFrame() {
		PlatformImpl.startup(() -> {});

		// make main container
		final GridPane mainContainer = new GridPane();
		mainContainer.setPadding(new Insets(5));
		mainContainer.setHgap(10);
		mainContainer.setVgap(10);

		// fill main container
		genStructBtn = new Button("Simulate Structure");
		genStructBtn.setPrefWidth(250);
		convBtn = new Button("Convolution");
		convBtn.setPrefWidth(250);
		deconvBtn = new Button("Deconvolution");
		deconvBtn.setPrefWidth(250);
		structTypeCb = new ComboBox<>();
		structTypeCb.setPromptText("Choose Structure");
		structTypeCb.getItems().addAll(
			Shape.LINE.toString(),
			Shape.PLANE.toString(),
			Shape.BOX.toString(),
			Shape.CYLINDER.toString());
		structTypeCb.setPrefWidth(250);
		deconvMethodCb = new ComboBox<>();
		deconvMethodCb.setPromptText("Choose Deconvolution Method");
		deconvMethodCb.getItems().addAll(
			DeconvMethod.SIMPLE_RLS.getMethod(),
			DeconvMethod.MAT_RLS.getMethod(),
			DeconvMethod.MODIFIED_RLS.getMethod(),
			DeconvMethod.WEIGHTED_RLS.getMethod(),
			DeconvMethod.AGARD.getMethod(),
			DeconvMethod.RICHARDSON_LUCY.getMethod(),
			DeconvMethod.ACCEL_RICHARDSON_LUCY.getMethod());
		deconvMethodCb.setPrefWidth(250);
		proBar = new ProgressBar(0);
		proBar.setPrefWidth(200);
		mainContainer.add(structTypeCb, 0, 0);
		mainContainer.add(genStructBtn, 0, 1);
		mainContainer.add(convBtn, 0, 2);
		mainContainer.add(deconvMethodCb, 0, 3);
		mainContainer.add(deconvBtn, 0, 4);
		mainContainer.add(proBar, 0, 5);
		GridPane.setHalignment(genStructBtn, HPos.CENTER);
		GridPane.setHalignment(structTypeCb, HPos.CENTER);
		GridPane.setHalignment(convBtn, HPos.CENTER);
		GridPane.setHalignment(deconvBtn, HPos.CENTER);
		GridPane.setHalignment(deconvMethodCb, HPos.CENTER);
		GridPane.setHalignment(proBar, HPos.CENTER);

		// setup stage
		Platform.runLater(() -> {
			stage = new Stage();
			stage.setTitle(WINDOW_TITLE);
			final Scene scene = new Scene(mainContainer, 250, 220);
			stage.setScene(scene);
		});
	}

	@Override
	public void processPropertyChange(MIAPropertyChangeEvent<?> e)
			throws IllegalArgumentException {

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
	public Object[] pluginCommand(String cmd, Object... input) {
		return null;
	}

	@Override
	public void setVisible(boolean b) {
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

		genStructBtn.setOnAction(e -> {
			genStructBtnAction();
		});

		convBtn.setOnAction(e -> {
			convBtnAction();
		});

		deconvBtn.setOnAction(e -> {
			deconvBtnAction();
		});
	}

	@Override
	public void updateFields() {

	}

	/** Action event for the generate structure button */
	private void genStructBtnAction() {

		// select structure
		proBar.setProgress(0);
		final String structType = structTypeCb.getValue();

		if (structType == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("Please choose a structure!");
			alert.showAndWait();

			return;
		}

		// load psf data
		loadPSFData();
		proBar.setProgress(0.2);

		// make convolution
		final StructureSimulation structSim;
		shape = Shape.valueOf(structType);

		switch (shape) {
			case LINE:
				structSim = new StructureSimulation(Shape.LINE);
				structSim.execute();
				structMatrix = copy(structSim.strucData);
				break;
			case PLANE:
				structSim = new StructureSimulation(Shape.PLANE);
				structSim.execute();
				structMatrix = copy(structSim.strucData);
				break;
			case BOX:
				structSim = new StructureSimulation(Shape.BOX);
				structSim.execute();
				structMatrix = copy(structSim.strucData);
				break;
			case CYLINDER:
				structSim = new StructureSimulation(Shape.CYLINDER);
				structSim.execute();
				structMatrix = copy(structSim.strucData);
				break;
		}

		proBar.setProgress(0.7);

		new Thread(new Runnable() {

			@Override
			public void run() {

				// save image set
				final ImageSet imgSet = saveImageSet(
					structMatrix,
					structMatrix.length,
					structType);
				proBar.setProgress(0.9);

				// load the convolved image set
				refreshImage(imgSet);
				proBar.setProgress(1.0);
			}
		}).start();
	}

	/** Action event for the convolution button */
	private void convBtnAction() {
		proBar.setProgress(0);

		if (psfMatrix == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("The psf matrix is null!");
			alert.showAndWait();

			return;
		}

		if (structMatrix == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("The structure matrix is null!");
			alert.showAndWait();

			return;
		}

		new Thread(new Runnable() {

			@Override
			public void run() {

				// make convolution
				makeConvolution();
				proBar.setProgress(0.7);

				// save convolved image
				final String name = shape.toString() + " CONVOLVED";
				final ImageSet imgSet
					= saveImageSet(convolved, convolved.length, name);
				proBar.setProgress(0.9);

				// load the convolved image set
				refreshImage(imgSet);
				proBar.setProgress(1.0);
			}
		}).start();
	}

	/** Action event for the deconvolution button */
	private void deconvBtnAction() {
		proBar.setProgress(0);

		if (psfMatrix == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("The psf matrix is null!");
			alert.showAndWait();

			return;
		}

		if (convolved == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("The convolved matrix is null!");
			alert.showAndWait();

			return;
		}

		final String deconvMethod = deconvMethodCb.getValue();

		if (deconvMethod == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("Please choose deconvolution method!");
			alert.showAndWait();

			return;
		}

		final String structType = structTypeCb.getValue();

		if (structType == null) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Warning Dialog");
			alert.setHeaderText(null);
			alert.setContentText("Please choose a structure!");
			alert.showAndWait();

			return;
		}

		// Initialize deconvolution
		final int cn = convolved.length;
		final int pn = psfMatrix.length;
		final int n = cn > pn ? cn : pn;

		final DeconvolutionPlugin deconv = new DeconvolutionPlugin();
		deconv.lenConv = n;
		deconv.imgH = convolved[0].length;
		deconv.imgW = convolved[0][0].length;
		deconv.lenPsf = pn;
		deconv.psfH = psfMatrix[0].length;
		deconv.psfW = psfMatrix[0][0].length;
		proBar.setProgress(0.2);

		// make deconvolution
		DeconvMethod method
			= DeconvMethod.findMethod(deconvMethod);

		switch (method) {
			case SIMPLE_RLS:
				deconvolved = deconv
					.deconvolutionLsq(convolved, psfMatrix, method, 1e-7);
				break;
			case MAT_RLS:
				deconvolved = deconv
					.deconvolutionLsq(convolved, psfMatrix, method, 1e-7);
				break;
			case MODIFIED_RLS:
				deconvolved = deconv
					.deconvolutionLsq(convolved, psfMatrix, method, 1e-7);
				break;
			case WEIGHTED_RLS:
				deconvolved = deconv
					.deconvolutionLsq(convolved, psfMatrix, method, 1e-7);
				break;
			case AGARD:
				deconvolved = deconv
					.deconvolutionRL(convolved, psfMatrix, method, 0, 10);
				break;
			case RICHARDSON_LUCY:
				deconvolved = deconv
					.deconvolutionRL(convolved, psfMatrix, method, 0, 10);
				break;
			case ACCEL_RICHARDSON_LUCY:
				deconvolved = deconv
					.deconvolutionRL(convolved, psfMatrix, method, 0, 10);
				break;
		}
		proBar.setProgress(0.8);

		// save result
		new Thread(new Runnable() {

			@Override
			public void run() {
				final String saveName = structType + " DECONVOLVED";
				final ImageSet imgSet = saveImageSet(deconvolved, cn, saveName);
				proBar.setProgress(0.9);
				refreshImage(imgSet);
				proBar.setProgress(1.0);
			}
		}).start();
	}

	/**
	 * Load the image data of PSF
	 * 
	 * @throws IllegalArgumentException
	 *         If the mtd is null or the image set is null or nonlinear
	 */
	private void loadPSFData() {
		final MIAToolHub hub = MIATool.getMIAToolHub();
		final MIAToolMain mtm = hub.getMIAToolMain();
		final MIAToolDirectory mtd = hub.getMIAToolDirectory();

		if (mtd == null)
			throw new IllegalArgumentException(
				"The MIAToolDirectory is empty!");

		else {

			// load the image set
			final String imgSetName
				= mtm.getImageSetBrowser().getActiveObject().getFilename();

			ImageSet imgSet = null;
			try {
				imgSet = mtd.getObject(mtd, ImageSet.class, imgSetName);
			}
			catch (Exception e) {
				e.printStackTrace();
			}

			// the image set of PSF must be linear
			if (imgSet == null || imgSet.size().length > 1) {
				throw new IllegalArgumentException(
					"The image set of psf should be linear!");
			}
			else {
				final int psfLen = imgSet.size()[0];
				psfMatrix = new double[psfLen][][];

				for (int i = 0; i < psfLen; i++) {
					final ImageSingle<?> imgSi = imgSet.get0iR(i);
					psfMatrix[i] = imgSi.get2DImageMatrix();
				}
			}
		}
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
	 * 
	 * @throws IllegalArgumentException
	 *         If the MIAToolDirectory is null.
	 */
	private ImageSet saveImageSet(
			final double[][][] input,
			final int length,
			final String seriesName) {
		// Create new series directories in the MIAToolDirectory
		final MIAToolHub hub = MIATool.getMIAToolHub();
		final MIAToolDirectory mtd = hub.getMIAToolDirectory();

		if (mtd == null)
			throw new IllegalArgumentException(
				"The MIAToolDirectory is empty!");

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

			// linearize data
			double[] linData = linearize(input[j]);
			short[] sData = DeconvolutionPlugin.convertToInt16(linData, max);

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

		System.out.println("The image set is saved.");

		return result;
	}

	/** Execute of a 3D structure */
	public void makeConvolution() {
		// start time counting
		long startTime = System.currentTimeMillis();
		System.out.println("Convolution started.");

		// make padding for the image matrix
		final double[][][] paddingMat = padding();
		convolved = MatrixUtil.convolve(paddingMat, psfMatrix);

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
		System.out.println("Padding started.");
		final double start = System.currentTimeMillis();
		final int nm = structMatrix.length;
		final int hm = structMatrix[0].length;
		final int wm = structMatrix[0][0].length;

		final int np = psfMatrix.length;
		final int hp = psfMatrix[0].length;
		final int wp = psfMatrix[0][0].length;

		// the size of the convolution matrix
		final int nc = nm + (np / 2) * 2;
		final int hc = hm + (hp / 2) * 2;
		final int wc = wm + (wp / 2) * 2;

		final double[][][] result = new double[nc][hc][wc];
		List<Integer> tuples = new ArrayList<>();
		for (int z = 0; z < nm; z++)
			tuples.add(z);

		tuples.parallelStream().forEachOrdered(tuple -> {
			for (int x = 0; x < wm; x++) {
				for (int y = 0; y < hm; y++) {
					final int xc = x + wp / 2;
					final int yc = y + hp / 2;
					final int zc = tuple + np / 2;
					result[zc][yc][xc] = structMatrix[tuple][y][x];
				}
			}
		});

		final double end = System.currentTimeMillis();
		final double runTime = end - start;
		System.out.println("Padding finished, time " + runTime + " ms.");
		return result;
	}

	/**
	 * Refresh the image set on the image set browser
	 * 
	 * @param input
	 */
	private void refreshImage(final ImageSet input) {
		final MIAToolHub hub = MIATool.getMIAToolHub();
		final MIAToolMain mtm = hub.getMIAToolMain();
		final ImageSetBrowser imgb = mtm.getImageSetBrowser();
		imgb.unloadAll();
		imgb.updateFields();
		imgb.setAsLoaded(input);
	}

	public static class StructureSimulation {

		// parameters in the simulation
		private int imageHeight = 100;
		private int imageWidth = 100;
		private int nImages = 40;
		private int edge = 50;
		private int radius = 25;
		private Shape shape;
		public final double intensity = 300; // photon detection rate 2000/s

		/** The matrix to store the keep the data */
		public double[][][] strucData
			= new double[nImages][imageHeight][imageWidth];

		public StructureSimulation(Shape shape) {
			this.shape = shape;
		}

		public void execute() {

			// select the shape of the structure
			switch (shape) {
				case LINE:

					// make a line of 50 pixels
					for (int i = 0; i < 50; i++) {
						strucData[20][50][25 + i] = intensity;
					}
					break;
				case PLANE:

					// make a plane of 50 x 50 pixels
					for (int i = 0; i < 50; i++) {
						for (int j = 0; j < 50; j++) {
							strucData[20][25 + j][25 + i] = intensity;
						}
					}
					break;
				case CYLINDER:
					for (int i = 10; i < nImages - 10; i++)
						for (int j = 0; j < radius; j++)
							for (int k = 0; k < radius; k++) {
								final double length = Math.sqrt(j * j + k * k);
								if (length <= (double) radius) {
									final int yPos = j + imageHeight / 2;
									final int yNeg = imageHeight / 2 - j - 1;
									final int xPos = k + imageWidth / 2;
									final int xNeg = imageWidth / 2 - k - 1;
									strucData[i][yPos][xPos] = intensity;
									strucData[i][yPos][xNeg] = intensity;
									strucData[i][yNeg][xPos] = intensity;
									strucData[i][yNeg][xNeg] = intensity;
								}

							}
					break;
				case BOX:
					final int yPos = imageHeight / 2 + edge / 2;
					final int yNeg = imageHeight / 2 - edge / 2;
					final int xPos = imageWidth / 2 + edge / 2;
					final int xNeg = imageWidth / 2 - edge / 2;
					for (int i = 10; i < nImages - 10; i++)
						for (int j = yNeg; j < yPos; j++)
							for (int k = xNeg; k < xPos; k++)
								strucData[i][j][k] = intensity;
					break;
			}
		}

		public static enum Shape {

			/** A line consisted by pixels */
			LINE,

			/** A plane consisted by pixels */
			PLANE,

			/** Cylinder */
			CYLINDER,

			/** Volume box */
			BOX;
		}
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-08-09, 0.1, Bo Wang: Created.
 * 
 */