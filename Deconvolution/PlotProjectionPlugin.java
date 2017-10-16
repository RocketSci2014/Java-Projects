package miatool.plugins;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import miatool.core.miatooldirectory.MIAToolDirectory;
import miatool.core.setssingles.image.ImageSet;
import miatool.core.setssingles.image.ImageSingle;
import miatool.core.setssingles.intensity.IntensityAdjustSet;
import miatool.core.setssingles.segmentation.SegmentationSet;
import miatool.core.setssingles.segmentation.SegmentationSingle;
import miatool.core.setssingles.segmentation.component.SegmentationComponent;
import miatool.core.util.ImageUtil;
import miatool.core.util.numerics.ImageMatOps;
import miatool.core.util.numerics.MIAMatOps;
import miatool.miamain.MIABrowser;
import miatool.miamain.MIATool;
import miatool.miamain.MIAToolHub;
import miatool.miamain.MIAToolMain;
import miatool.modules.estimation.models.PSFFramework.PSFImage;
import miatool.modules.process.MIAProcessingChain;
import miatool.modules.process.commontasks.PercentileIntensityAutoAdjusterBlock;
import miatool.plugins.MIAPlugin;
import miatool.plugins.PluginUtil;
import miatool.tools.MIAToolDirectoryBrowser;
import miatool.tools.Preview;
import miatool.tools.adjustmenttools.intensitytool.IntensityTool;
import miatool.tools.adjustmenttools.segmentationtool.SegmentationTool;
import miatool.tools.displaytool.ImageSetBrowser;
import miatool.ui.UIUtils;
import miatool.ui.events.MIAPropertyChangeEvent;
import miatool.ui.wizard.objectselection.ObjectSelectionWizardSwing;

/**
 * Plugin to plot yz xz and z projections. User may choose from the maximum
 * projection and cross section projection
 * 
 * @author Bo Wang
 * @version 0.3
 * @since 1.0
 */
public class PlotProjectionPlugin
		implements MIAPlugin, ActionListener {

	public static final String WINDOW_TITLE = "Plot Projection Plugin";
	public static final String MENU_TITLE = "Deconvolution Tools";
	public static final String MAX_PROJ = "Maximum Projection";
	public static final String CROSS_PROJ = "Cross Section Projection";
	public final JMenuItem menuItem = new JMenuItem(WINDOW_TITLE
			+
			"...");
	public ImageSet imgSet;
	public ComboBox<String> projTpCb;
	public ObjectSelectionWizardSwing<ImageSet> imgSetSelector;
	public VBox mainContainer;
	public Label imgSetLbl;
	public Label segSetLbl;
	public Label segComLbl;
	public Label projTpLbl;
	public Label camDimLbl;
	public Label scanDimLbl;

	public TextField imgSetTf;
	public TextField segSetTf;
	public TextField segComTf;
	public TextField camDimTf;
	public TextField scanDimTf;

	public Button imgSetBtn;
	public Button segSetBtn;
	public Button plotBtn;
	public TitledPane imgPane;
	public TitledPane segPane;
	public TitledPane plotPane;
	public GridPane imgGrid;
	public GridPane segGrid;
	public GridPane plotGrid;
	private Stage stage;
	private SegmentationSet segSet = null;
	private int segComInd = 0;
	private String method = MAX_PROJ;
	private MIAToolHub hub;
	private MIAToolMain mtm;
	private MIAToolDirectory mtd;
	private ImageSetBrowser imgb;
	private SegmentationTool segTool;
	private MIABrowser<SegmentationSet, ?> segb;
	private MIAToolDirectoryBrowser mtdb;

	/** buffer data of yz projection */
	private short[] yzBuffer = null;

	/** buffer data of xz projection */
	private short[] xzBuffer = null;

	/** buffer data of z projection */
	private short[] zBuffer = null;

	/** the maximum height of a image in the image set */
	private int maxH;

	/** the maximum width of a image in the image set */
	private int maxW;
	
	public static void main(String[] args) {
		MIATool.main(args);
		PlotProjectionPlugin Tool = new PlotProjectionPlugin();
		PluginUtil.addPlugin(Tool);
		Tool.startup();
	}

	public PlotProjectionPlugin() {
		super();
	}

	public void setupFrame() {
		PlatformImpl.startup(() -> {});
		mainContainer = new VBox(5);
		mainContainer.setPadding(new Insets(5, 5, 5, 5));
		imgSetLbl = new Label("Choose Image Set");

		if (mtd == null)
			imgSetTf = new TextField("-NULL-");
		else {
			hub = MIATool.getMIAToolHub();
			mtm = hub.getMIAToolMain();
			imgb = mtm.getImageSetBrowser();
			imgSet = imgb.getActiveObject();
			imgSetTf = new TextField(imgSet.getFilename());
		}
		imgSetTf.setEditable(false);;

		camDimLbl = new Label("Camera Dimension");
		camDimTf = new TextField("1");
		camDimTf.setDisable(true);
		scanDimLbl = new Label("Scan Dimension");
		scanDimTf = new TextField("2");
		scanDimTf.setDisable(true);
		imgSetBtn = new Button("Load Image Set");
		segSetLbl = new Label("Choose Segmentation Set");
		segSetTf = new TextField("-NULL-");
		segSetTf.setEditable(false);
		segSetBtn = new Button("Segmentation Tool");
		segComLbl = new Label("Choose Segmentation\nComponent");
		segComTf = new TextField("0");
		segComTf.setDisable(true);
		projTpLbl = new Label("Choose Projection Type");
		projTpCb = new ComboBox<String>();
		projTpCb.getItems().addAll(MAX_PROJ, CROSS_PROJ);
		plotBtn = new Button("Plot Projections");

		// step 1
		imgPane = new TitledPane();
		imgGrid = new GridPane();
		imgGrid.setVgap(4);
		imgGrid.setPadding(new Insets(5, 5, 5, 5));
		imgGrid.add(imgSetLbl, 0, 0);
		imgGrid.add(imgSetTf, 0, 1);
		imgGrid.add(imgSetBtn, 0, 2);
		imgGrid.add(camDimLbl, 0, 3);
		imgGrid.add(camDimTf, 0, 4);
		imgGrid.add(scanDimLbl, 0, 5);
		imgGrid.add(scanDimTf, 0, 6);
		imgPane.setCollapsible(false);
		imgPane.setText("Step 1");
		imgPane.setContent(imgGrid);

		// step 2
		segPane = new TitledPane();
		segGrid = new GridPane();
		segGrid.setVgap(4);
		segGrid.setPadding(new Insets(5, 5, 5, 5));
		segGrid.add(segSetLbl, 0, 0);
		segGrid.add(segSetTf, 0, 1);
		segGrid.add(segSetBtn, 0, 2);
		segGrid.add(segComLbl, 0, 3);
		segGrid.add(segComTf, 0, 4);
		segPane.setCollapsible(false);
		segPane.setText("Step 2 - Optional");
		segPane.setContent(segGrid);

		// step 3
		plotPane = new TitledPane();
		plotGrid = new GridPane();
		plotGrid.setVgap(4);
		plotGrid.setPadding(new Insets(5, 5, 5, 5));
		plotGrid.add(projTpLbl, 0, 0);
		plotGrid.add(projTpCb, 0, 1);
		plotGrid.add(plotBtn, 0, 2);
		plotPane.setCollapsible(false);
		plotPane.setText("Step 3");
		plotPane.setContent(plotGrid);
		mainContainer.getChildren().addAll(imgPane, segPane, plotPane);

		Platform.runLater(() -> {
			stage = new Stage();
			stage.setTitle(WINDOW_TITLE);
			final Scene scene = new Scene(mainContainer, 200, 500);
			stage.setScene(scene);
		});
	}

	@Override
	public void processPropertyChange(MIAPropertyChangeEvent<?> e)
			throws IllegalArgumentException {
		final Object src = e.getSource();
		final Object prop = e.getChangedProperty();

		// Callback from ObjectSelectionWizard
		if (prop == ObjectSelectionWizardSwing.Property.SELECTED_OBJECT
				&& src == imgSetSelector) {
			processImgSetSelection();
			if (imgSet != null) {
				hub = MIATool.getMIAToolHub();
				mtm = hub.getMIAToolMain();
				imgb = mtm.getImageSetBrowser();
				imgb.unloadAll();
				imgb.updateFields();
				imgb.setAsLoaded(imgSet);
				intensityAdjust(imgSet);

				if (imgSet.size().length > 1) {
					camDimTf.setDisable(false);
					scanDimTf.setDisable(false);
				}
			}
			return;
		}

		if (prop == MIABrowser.Property.ACTIVE
				&& src == segb
				&& segTool.isShowing()) {

			processMIASetSelection();
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
	public void actionPerformed(ActionEvent e) {
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
		hub = MIATool.getMIAToolHub();
		mtm = hub.getMIAToolMain();
		mtd = hub.getMIAToolDirectory();
		setupFrame();
		
		imgSetBtn.setOnAction((event) -> {
			openImgSetSelector();
		});

		segSetBtn.setOnAction((event) -> {
			if (imgSet != null)
				openSegmentationTool();
		});

		plotBtn.setOnAction((event) -> {
			if (imgSet != null
				|| projTpCb != null
				|| segComTf != null){
			method = projTpCb.getValue();
			segComInd = Integer.valueOf(segComTf.getText());
			plotProjection();
			}
		});
		
		imgb = mtm.getImageSetBrowser();
		imgb.addMIAPropertyChangeListener(this);
		mtdb = hub.getMIAToolMain().getMIAToolDirectoryBrowser();
		mtdb.addMIAPropertyChangeListener(this);
		segTool = hub.getTool(SegmentationTool.class, SegmentationSet.class);
		segb = segTool.getSetBrowser();
		segb.addMIAPropertyChangeListener(this);
	}

	@Override
	public void updateFields() {
		final String imgSetName 
				= imgSet == null ? "-NULL-" : imgSet.getFilename();
		imgSetTf.setText(imgSetName);

		final String segSetName
				= segSet == null ? "-NULL-" : segSet.getFilename();
		segSetTf.setText(segSetName);
	}


	/**
	 * Helper method to open the object selection wizard for the selecting a
	 * image set.
	 */
	private void openImgSetSelector() {
		hub = MIATool.getMIAToolHub();
		mtd = hub.getMIAToolDirectory();
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
		imgSetSelector = new ObjectSelectionWizardSwing<ImageSet>(
				ImageSet.class,
				"Image Set");

		imgSetSelector.setMIAToolDirectory(mtd);
		imgSetSelector.addMIAPropertyChangeListener(this);
		imgSetSelector.show(null);
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
	 * Callback for when a new object set is selected.
	 */
	private void processImgSetSelection() {
		final List<ImageSet> loadedImage;
		loadedImage = imgSetSelector.getSelectedObjects();

		if (loadedImage.isEmpty() || loadedImage.get(0) == null) {
			return;
		}
		segSet = null;
		imgSet = loadedImage.get(0);

		updateFields();
		return;
	}

	/**
	 * Callback for when a new segmentation set is selected.
	 */
	private void processMIASetSelection() {
		segb = segTool.getSetBrowser();
		segSet = segb.getActiveObject();

		if (segSet == null
			|| imgSet == null
			|| imgSetTf == null
			|| segSetTf == null
			|| segComTf == null)

			return;
		else {
			updateFields();

			/*
			 * if more than one components are in each segmentation single,
			 * enable the segComTf
			 */
			if (segSet.get0iR(0).numComponents() > 1)
				segComTf.setDisable(false);

			// keep the current object in the browser
			new Thread(new Runnable() {
				@Override
				public void run() {
					segTool.getSetBrowser().setActiveObject(segSet);
				};
			});
		}
	}

	/**
	 * Plot a stack graph of projections in x-y, y-z and z
	 */
	public void plotProjection() {
		// parameters for plot
		final int scaleWidth = 250;
		final int scaleHeight = 250;
		final int gap = 20;
		final int colWidth = 300;
		final String yzTitle = "YZ Projection";
		final String xzTitle = "XZ Projection";
		final String zTitle = "Z Projection";

		// get the size of the image set
		final int[] imgSetSize = imgSet.size();

		// make a buffered image
		BufferedImage crossImgBi;

		// plot projections
		if (imgSetSize.length == 1) {
			final int len = imgSetSize[0];
			// get image data from each frame
			double[][][] imgData = new double[len][][];
			for (int i = 0; i < len; i++) {
				if (segSet == null) {
					final ImageSingle<?> imgSi = imgSet.get0iR(i);
					imgData[i] = imgSi.get2DImageMatrix();
					imgSi.flush();
				}
				else {
					final SegmentationSingle segSingle = segSet.get0iR(i);
					imgData[i] = getSegmentationData(segSingle);
					if (imgData[i] == null)
						return;
				}
			}

			// get buffer arrays
			makeProjectionData(imgData);
			imgData = null;

			// save data to buffer image and rescale to 250 by 250
			crossImgBi
					= new BufferedImage(300, 900, BufferedImage.TYPE_INT_RGB);
			final Graphics crossImgG = crossImgBi.getGraphics();
			crossImgG.setColor(Color.WHITE);
			crossImgG.fillRect(0, 0, 300, 900);
			final BufferedImage biYz
					= ImageUtil.createGreyU16BufferedImage(yzBuffer, maxH, len);
			final BufferedImage biXz
					= ImageUtil.createGreyU16BufferedImage(xzBuffer, maxW, len);
			final BufferedImage biZ
					= ImageUtil.createGreyU16BufferedImage(zBuffer, maxW, maxH);

			// draw graphs
			crossImgG.drawImage(
					biYz,
					25,
					50,
					scaleWidth,
					scaleHeight,
					null);
			crossImgG.drawImage(
					biXz,
					25,
					50 + scaleHeight + gap,
					scaleWidth,
					scaleHeight,
					null);

			crossImgG.drawImage(
					biZ,
					25,
					50 + 2 * scaleHeight + 2 * gap,
					scaleWidth,
					scaleHeight,
					null);
			
			// Draw title
			crossImgG.setColor(Color.BLACK);
			crossImgG.drawString(yzTitle, 125, 45);
			crossImgG.drawString(xzTitle, 125, 45 + scaleHeight + gap);
			crossImgG.drawString(zTitle, 125, 45 + 2 * scaleHeight + 2 * gap);
		}
		else {
			// get the camDim and scanDim values
			final int camDim = Integer.valueOf(camDimTf.getText());
			final int scanDim = Integer.valueOf(scanDimTf.getText());
			if (camDim > imgSetSize.length
					|| scanDim > imgSetSize.length
					|| camDim < 1
					|| scanDim < 1
					|| camDim == scanDim) {
				JOptionPane.showMessageDialog(
						menuItem,
						"Put the right values of dimensions!",
						"Wrong dimensions!",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			// get the size of the image set and set up the canvas
			final int nPlanes = imgSetSize[camDim - 1];
			final int len = imgSetSize[scanDim - 1];
			crossImgBi
					= new BufferedImage(
							300 * nPlanes,
							900,
							BufferedImage.TYPE_INT_RGB);
			final Graphics crossImgG = crossImgBi.getGraphics();
			crossImgG.setColor(Color.WHITE);
			crossImgG.fillRect(0, 0, 300 * nPlanes, 900);

			for (int i = 0; i < nPlanes; i++) {
				// get image data from each frame in a plane
				double[][][] imgData = new double[len][][];

				for (int j = 0; j < len; j++) {
					// get dimension info
					final int[] imgIndex = new int[imgSetSize.length];
					imgIndex[camDim - 1] = i;
					imgIndex[scanDim - 1] = j;

					// get image data
					if (segSet == null) {
						final ImageSingle<?> imgSi = imgSet.get0iR(imgIndex);
						imgData[j] = imgSi.get2DImageMatrix();
						imgSi.flush();
					}
					else {
						final SegmentationSingle segSingle
								= segSet.get0iR(imgIndex);
						imgData[j] = getSegmentationData(segSingle);
						if (imgData[j] == null)
							return;
					}
				}

				// get buffer arrays
				makeProjectionData(imgData);
				imgData = null;

				// save data to buffer image and rescale to 250 by 250
				final BufferedImage biYz
						= ImageUtil.createGreyU16BufferedImage(
								yzBuffer,
								maxH,
								len);
				final BufferedImage biXz
						= ImageUtil.createGreyU16BufferedImage(
								xzBuffer,
								maxW,
								len);
				final BufferedImage biZ
						= ImageUtil.createGreyU16BufferedImage(
								zBuffer,
								maxW,
								maxH);

				// draw graphs
				crossImgG.drawImage(
						biYz,
						25 + colWidth * i,
						50,
						scaleWidth,
						scaleHeight,
						null);
				
				crossImgG.drawImage(
						biXz,
						25 + colWidth * i,
						50 + scaleHeight + gap,
						scaleWidth,
						scaleHeight,
						null);

				crossImgG.drawImage(
						biZ,
						25 + colWidth * i,
						50 + 2 * scaleHeight + 2 * gap,
						scaleWidth,
						scaleHeight,
						null);

				// Draw title
				crossImgG.setColor(Color.BLACK);
				final String yzPlaneTl
						= String.format("Plane %d: " + yzTitle, i + 1);
				final String xzPlaneTl
						= String.format("Plane %d: " + xzTitle, i + 1);
				final String zPlaneTl
						= String.format("Plane %d: " + zTitle, i + 1);
				crossImgG.drawString(yzPlaneTl, 100 + colWidth * i, 45);
				crossImgG.drawString(
						xzPlaneTl,
						100 + colWidth * i,
						45 + scaleHeight + gap);
				crossImgG.drawString(
						zPlaneTl,
						100 + colWidth * i,
						45 + 2 * scaleHeight + 2 * gap);
			}
		}

		// preview image
		new Preview(crossImgBi);
	}

	/**
	 * Adjust the intensity of a image set and send it to the image set browser
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
		catch (Exception e1) {
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
	 * Get data from each segmentation single
	 */
	private double[][] getSegmentationData(final SegmentationSingle segSingle) {
		final SegmentationComponent segCom;
		if (segSingle.numComponents() > 0) {
			if (segComInd < 0 || segComInd > segSingle.numComponents() - 1) {
				JOptionPane.showMessageDialog(
						menuItem,
						"Put the right index of segmentation component!",
						"Index Out Of Bound!",
						JOptionPane.ERROR_MESSAGE);
				return null;
			}
			else
				segCom = segSingle.get0i(segComInd);
		}
		else
			return null;

		return PSFImage.get2DImage(segCom.getPixels(), segCom.getPixelData());
	}

	/**
	 * Make intensity adjusted buffer data for projections in x-y, y-z and z
	 */
	private void makeProjectionData(final double[][][] imgData) {
		final int len = imgData.length;
		final int[] imgHeight = new int[len];
		final int[] imgWidth = new int[len];
		final double[][] xzData = new double[len][];
		final double[][] yzData = new double[len][];
		final double[][][] zData = MIAMatOps.copy(imgData);

		// get data from the image data
		for (int i = 0; i < len; i++) {
			// get yz and xz projection data
			final double[][] imgDataTrans = MIAMatOps.transpose(imgData[i]);
			final int height = imgData[i].length;
			final int width = imgData[i][0].length;
			final int halfH = (int) Math.round((double) height / 2 - 1);
			final int halfW = (int) Math.round((double) width / 2 - 1);
			imgHeight[i] = height;
			imgWidth[i] = width;
			switch (method) {
				case MAX_PROJ:
					final double[] yzMax = MIAMatOps.max(imgData[i]);
					yzData[i] = yzMax.clone();
					final double[] xzMax = MIAMatOps.max(imgDataTrans);
					xzData[i] = xzMax.clone();
					break;
				case CROSS_PROJ:
					xzData[i] = imgData[i][halfH].clone();
					yzData[i] = imgDataTrans[halfW].clone();
					break;
			}
		}

		// Get the max size of the imgHeight and imgWidth
		maxH = MIAMatOps.max(imgHeight);
		maxW = MIAMatOps.max(imgWidth);

		// make the jagged matrix into regular matrix
		final double[][] yzProjData = MIAMatOps.rectangularize(yzData);
		final double[][] xzProjData = MIAMatOps.rectangularize(xzData);

		// z projection data
		final double[][][] zDataConv
				= MIAMatOps.rectangularize(zData);
		double[][] zProjData = new double[maxH][maxW];
		final int halfL = (int) Math.round((double) len / 2 - 1);
		switch (method) {
			case MAX_PROJ:
				final double[][][] zDataTrans
						= MIAMatOps.xyzToZxy(zDataConv);
				for (int i = 0; i < zDataTrans.length; i++)
					zProjData[i] = MIAMatOps.max(zDataTrans[i]);
				break;
			case CROSS_PROJ:
				zProjData = MIAMatOps.copy(zDataConv[halfL]);
				break;
		}

		// make intensity auto-adjusted buffer arrays
		yzBuffer = ImageMatOps.toIntensityAdjustedShort(yzProjData);
		xzBuffer = ImageMatOps.toIntensityAdjustedShort(xzProjData);
		zBuffer = ImageMatOps.toIntensityAdjustedShort(zProjData);
	}
}
/**
 * REVISION HISTORY
 * 
 * 2017-07-07, 0.3, Bo Wang: Fixed collapse bugs
 * 
 * 2017-06-20, 0.2, Bo Wang: Added dimension selection and multi-dimension image
 * set plot option
 * 
 * 2017-06-03, 0.1, Bo Wang: Created
 * 
 */
