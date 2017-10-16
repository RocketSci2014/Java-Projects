package miatool.plugins;

import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import miatool.core.setssingles.image.ImageSet;
import miatool.core.util.ImageUtil;
import miatool.core.util.numerics.ImageMatOps;
import miatool.core.util.numerics.MIAMatOps;

/**
 * Viewer of cross sections in X-Y, X-Z, and Y-Z directions
 * 
 * @author Bo Wang
 * @since 1.0
 * @version 0.3
 */
public class CrossSectionViewer {

	public TextField xyTf;
	public TextField xzTf;
	public TextField yzTf;
	public Slider zSli;
	public Slider ySli;
	public Slider xSli;
	public ImageView xyView;
	public ImageView xzView;
	public ImageView yzView;
	public Button showGridBtn;
	public Line vLine;
	public Line hLine;
	public Line xLine;
	public Line yLine;
	public Line xzLine;
	public Line yzLine;

	/** 3D data matrix to store the loaded image data */
	final private double[][][] dataMatrix;

	/** Rotated dataMatrix, coordinates from i,j,k to k,j,i */
	final private double[][][] dataMatRotated;

	/** Indicator to show if the grid is on */
	private boolean isGridOn = false;

	/** Constructor */
	public CrossSectionViewer(final ImageSet imgSet) {
		dataMatrix = ImageMatOps.linearImageSetToMatrix(imgSet);
		dataMatRotated = MIAMatOps.xyzToZyx(dataMatrix);
	}

	/** Setup the frame of the GUI */
	public void setupFrame() {
		// set main container
		final GridPane mainContainer = new GridPane();
		mainContainer.setPadding(new Insets(5));
		mainContainer.setHgap(4);
		mainContainer.setVgap(4);
		
		// The upper limits of sliders
		final int zLim = dataMatrix.length;
		final int yLim = dataMatrix[0].length;
		final int xLim = dataMatrix[0][0].length;

		// set xy viewer
		vLine = new Line(0, 0, 0, 450);
		vLine.setStroke(Color.YELLOW);
		vLine.getStrokeDashArray().addAll(2d);
		vLine.setVisible(false);
		hLine = new Line(0, 0, 450, 0);
		hLine.setStroke(Color.YELLOW);
		hLine.getStrokeDashArray().addAll(2d);
		hLine.setVisible(false);

		xyView = new ImageView();
		xyView.setFitWidth(450);
		xyView.setFitHeight(450);
		xyView.setPreserveRatio(false);
		final Image xyImg = makeCrossSection(0, 0);
		if (xyImg != null)
			xyView.setImage(xyImg);

		final Group xyGroup = new Group(xyView, hLine, vLine);
		final VBox xyViewBox = new VBox(5);
		xyViewBox.getChildren().addAll(new Label("X-Y Cross Section"), xyGroup);
		xyViewBox.setAlignment(Pos.CENTER);
		mainContainer.add(xyViewBox, 0, 0);

		// set z slider
		zSli = new Slider(1, zLim, 1);
		zSli.setOrientation(Orientation.HORIZONTAL);
		zSli.setPrefWidth(430);
		final HBox zSliBox = new HBox(5);
		zSliBox.getChildren().addAll(new Label("Z"), zSli);

		xyTf = new TextField("1");
		xyTf.setPrefColumnCount(3);

		mainContainer.add(zSliBox, 0, 1);
		mainContainer.add(xyTf, 1, 1);
		GridPane.setValignment(zSliBox, VPos.CENTER);

		// set xz viewer
		xLine = new Line(0, 0, 0, 150);
		xLine.setStroke(Color.YELLOW);
		xLine.getStrokeDashArray().addAll(2d);
		xLine.setVisible(false);
		xzLine = new Line(0, 0, 450, 0);
		xzLine.setStroke(Color.YELLOW);
		xzLine.getStrokeDashArray().addAll(2d);
		xzLine.setVisible(false);

		xzView = new ImageView();
		xzView.setFitWidth(450);
		xzView.setFitHeight(150);
		xzView.setPreserveRatio(false);
		final Image xzImg = makeCrossSection(1, 0);
		if (xzImg != null)
			xzView.setImage(xzImg);

		final Group xzGroup = new Group(xzView, xLine, xzLine);
		final VBox xzViewBox = new VBox(5);
		xzViewBox.getChildren().addAll(new Label("X-Z Cross Section"), xzGroup);
		xzViewBox.setAlignment(Pos.CENTER);
		mainContainer.add(xzViewBox, 0, 2);

		// set y slider, slide from top to bottom
		ySli = new Slider(1, yLim, yLim);
		ySli.setOrientation(Orientation.VERTICAL);
		ySli.setPrefHeight(430);
		final Label yTitle = new Label("Y");
		yTitle.setRotate(-90);
		final VBox ySliBox = new VBox(5);
		ySliBox.getChildren().addAll(yTitle, ySli);
		
		xzTf = new TextField("1");
		xzTf.setPrefColumnCount(3);

		mainContainer.add(ySliBox, 3, 0);
		mainContainer.add(xzTf, 3, 1);
		GridPane.setHalignment(ySliBox, HPos.CENTER);

		// set yz viewer
		yLine = new Line(0, 0, 150, 0);
		yLine.setStroke(Color.YELLOW);
		yLine.getStrokeDashArray().addAll(2d);
		yLine.setVisible(false);
		yzLine = new Line(0, 0, 0, 450);
		yzLine.setStroke(Color.YELLOW);
		yzLine.getStrokeDashArray().addAll(2d);
		yzLine.setVisible(false);

		yzView = new ImageView();
		yzView.setFitWidth(150);
		yzView.setFitHeight(450);
		yzView.setPreserveRatio(false);
		final Image yzImg = makeCrossSection(2, 0);
		if (yzImg != null)
			yzView.setImage(yzImg);

		final Group yzGroup = new Group(yzView, yLine, yzLine);
		final VBox yzViewBox = new VBox(5);
		yzViewBox.getChildren().addAll(new Label("Y-Z Cross Section"), yzGroup);
		yzViewBox.setAlignment(Pos.CENTER);
		mainContainer.add(yzViewBox, 2, 0);
		
		// set x slider
		xSli = new Slider(1, xLim, 1);
		xSli.setOrientation(Orientation.HORIZONTAL);
		xSli.setPrefWidth(430);
		final HBox xSliBox = new HBox(5);
		xSliBox.getChildren().addAll(new Label("X"), xSli);

		yzTf = new TextField("1");
		yzTf.setPrefColumnCount(3);

		mainContainer.add(xSliBox, 0, 3);
		mainContainer.add(yzTf, 1, 3);
		GridPane.setValignment(xSliBox, VPos.CENTER);

		// set button
		showGridBtn = new Button("Show Grid");
		mainContainer.add(showGridBtn, 2, 2);
		GridPane.setHalignment(showGridBtn, HPos.CENTER);

		// set stage
		Stage stage = new Stage();
		stage.setTitle("Cross Section Viewer");
		final Scene scene = new Scene(mainContainer, 750, 750);
		stage.setScene(scene);
		stage.show();
	}

	/** Start the application */
	public void startup() {
		// setup frame
		setupFrame();

		// add listener to z slider
		zSli.valueProperty().addListener((obs, oldVal, newVal) -> {
			final int zIndex = (int) zSli.getValue();
			setZLine(zIndex);
			xyTf.setText(String.format("%d", zIndex));
			final Image xyNewImg = makeCrossSection(0, zIndex - 1);

			if (xyNewImg != null)
				xyView.setImage(xyNewImg);
		});

		//add listener to xy text field
		xyTf.setOnKeyPressed((event) -> {
			if (event.getCode().equals(KeyCode.ENTER)) {
				final double xyTfValue = Double.valueOf(xyTf.getText());
				if (xyTfValue < 1 || xyTfValue > zSli.getMax())
					return;
				
				zSli.setValue(xyTfValue);
				final int zIndex = (int) xyTfValue;
				setZLine(zIndex);
				final Image xyNewImg = makeCrossSection(0, zIndex - 1);

				if (xyNewImg != null)
					xyView.setImage(xyNewImg);
			}
		});

		// add listener to y slider
		ySli.valueProperty().addListener((obs, oldVal, newVal) -> {
			final int yIndex = (int) (ySli.getMax() - ySli.getValue() + 1);
			setYLine(yIndex);
			xzTf.setText(String.format("%d", yIndex));
			final Image xzNewImg = makeCrossSection(1, yIndex - 1);

			if (xzNewImg != null)
				xzView.setImage(xzNewImg);
		});

		// add listener to xz text field
		xzTf.setOnKeyPressed((event) -> {
			if (event.getCode().equals(KeyCode.ENTER)) {
				final double xzTfValue = Double.valueOf(xzTf.getText());
				if (xzTfValue < 1 || xzTfValue > ySli.getMax())
					return;
				
				ySli.setValue(ySli.getMax() - xzTfValue + 1);
				final int yIndex = (int) xzTfValue;
				setYLine(yIndex);
				final Image xzNewImg = makeCrossSection(1, yIndex - 1);

				if (xzNewImg != null)
					xzView.setImage(xzNewImg);
			}
		});

		// add listener to x slider
		xSli.valueProperty().addListener((obs, oldVal, newVal) -> {
			final int xIndex = (int) xSli.getValue();
			setXLine(xIndex);
			yzTf.setText(String.format("%d", xIndex));
			final Image yzNewImg = makeCrossSection(2, xIndex - 1);

			if (yzNewImg != null)
				yzView.setImage(yzNewImg);
		});

		// add listener to yz text field
		yzTf.setOnKeyPressed((event) -> {
			if (event.getCode().equals(KeyCode.ENTER)) {
				final double yzTfValue = Double.valueOf(yzTf.getText());
				if (yzTfValue < 1 || yzTfValue > xSli.getMax())
					return;
				
				xSli.setValue(yzTfValue);
				final int xIndex = (int) yzTfValue;
				setXLine(xIndex);
				final Image yzNewImg = makeCrossSection(2, xIndex - 1);

				if (yzNewImg != null)
					yzView.setImage(yzNewImg);
			}
		});

		// add listener to show grid button
		showGridBtn.setOnAction((event) -> {
			if (!isGridOn) {
				showGridBtn.setText("Hide Grid");
				isGridOn = true;
				vLine.setVisible(true);
				hLine.setVisible(true);
				xLine.setVisible(true);
				yLine.setVisible(true);
				xzLine.setVisible(true);
				yzLine.setVisible(true);
			}
			else {
				showGridBtn.setText("Show Grid");
				isGridOn = false;
				vLine.setVisible(false);
				hLine.setVisible(false);
				xLine.setVisible(false);
				yLine.setVisible(false);
				xzLine.setVisible(false);
				yzLine.setVisible(false);
			}
		});
	}

	/**
	 * Make a cross section from the dataMatrix
	 * 
	 * @param dim
	 *        The dimension to take the cross section
	 * 
	 * @param index
	 *        The index by which the cross section is taken
	 * 
	 * @return JavaFX image of the cross section
	 */
	private Image makeCrossSection(final int dim, final int index) {
		if (dim == 0) {
			if (index < dataMatrix.length) {
				final double[][] xyCS = MIAMatOps.copy(dataMatrix[index]);
				return convertToJavaFXImage(xyCS);
			}
			else
				return null;
		}

		if (dim == 1) {
			if (index < dataMatrix[0].length) {
				final double[][] xzCS = new double[dataMatrix.length][];
				for (int i = 0; i < dataMatrix.length; i++)
					xzCS[i] = dataMatrix[i][index].clone();

				return convertToJavaFXImage(xzCS);
			}
			else
				return null;
		}

		if (dim == 2) {
			if (index < dataMatrix[0][0].length) {
				final double[][] yzCS = MIAMatOps.copy(dataMatRotated[index]);
				return convertToJavaFXImage(yzCS);
			}
			else
				return null;
		}

		return null;
	}

	/**
	 * Convert a 2D matrix into a JavaFX image
	 * 
	 * @param input
	 * 
	 * @return a JavaFX image
	 */
	private Image convertToJavaFXImage(final double[][] input) {
		// Adjust intensity
		final short[] intArr
				= ImageMatOps.toIntensityAdjustedShort(input);
		final int height = input.length;
		final int width = input[0].length;

		// Convert data to a JavaFX image
		final BufferedImage buffImg
				= ImageUtil.createGreyU16BufferedImage(intArr, width, height);
		Image result = SwingFXUtils.toFXImage(buffImg, null);
		return result;
	}

	/**
	 * Set the location of the horizontal line in X-Y and Y-Z
	 * 
	 * @param x
	 *        The value of y location
	 */
	private void setYLine(final int x) {
		final int yLim = dataMatrix[0].length;
		final double move = 450 * (double) (x - 1) / (double) (yLim - 1);
		hLine.setStartX(0);
		hLine.setEndX(450);
		hLine.setStartY(move);
		hLine.setEndY(move);

		yLine.setStartX(0);
		yLine.setEndX(150);
		yLine.setStartY(move);
		yLine.setEndY(move);
	}

	/**
	 * Set the location of the vertical line in X-Y and X-Z
	 * 
	 * @param x
	 *        The value of x location
	 */
	private void setXLine(final int x) {
		final int xLim = dataMatrix[0][0].length;
		final double move = 450 * (double) (x - 1) / (double) (xLim - 1);
		vLine.setStartX(move);
		vLine.setEndX(move);
		vLine.setStartY(0);
		vLine.setEndY(450);

		xLine.setStartX(move);
		xLine.setEndX(move);
		xLine.setStartY(0);
		xLine.setEndY(150);
	}

	/**
	 * Set the location of the xzLine and yzLine
	 * 
	 * @param x
	 *        The value of z location
	 */
	private void setZLine(final int x) {
		final int zLim = dataMatrix.length;
		final double move = 150 * (double) (x - 1) / (double) (zLim - 1);

		xzLine.setStartX(0);
		xzLine.setEndX(450);
		xzLine.setStartY(move);
		xzLine.setEndY(move);

		yzLine.setStartX(move);
		yzLine.setEndX(move);
		yzLine.setStartY(0);
		yzLine.setEndY(450);
	}
}
/**
 * REVISION HISTORY
 * 
 * 2017-07-12, 0.3, Bo Wang: fixed bugs
 * 
 * 2017-07-06, 0.2, Bo Wang: Added calibration lines
 * 
 * 2017-07-03, 0.1, Bo Wang: Created it.
 */
