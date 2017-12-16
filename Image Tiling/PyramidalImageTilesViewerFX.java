package miatool.display.fx;

import static javafx.scene.input.MouseEvent.MOUSE_DRAGGED;
import static javafx.scene.input.MouseEvent.MOUSE_PRESSED;
import static javafx.scene.input.ScrollEvent.SCROLL;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import miatool.display.ImageViewer;
import miatool.display.Viewer;
import miatool.display.fx.viewers.ViewerFX;
import miatool.display.registry.DisplayRegistry;
import miatool.tools.graphing.Point;

/**
 * The default viewer of a image pyramid, assume the resolution of one layer is
 * integer times of that of the layer below. To display large images, each image
 * layer is divided by a gird to image tiles, which are specified by the tile
 * size. The resolution of each layer in the unit of tiles is store in
 * {@link ImageTiling#tileInfo}. When an upcoming image is smaller than the
 * window size, the entire image will be added to the node, otherwise, a section
 * will be cropped from the layer to fit the window, in which the tiles will be
 * loaded following the calculated {@link #coord}. All the tile objects in the
 * viewer are stored in {@link #imgViewMap}, which will be iterated whenever the
 * tiled image is refreshed by drag or scroll events.
 * 
 * @author Bo Wang
 * @since 1.0
 * @version 0.1
 */
public class PyramidalImageTilesViewerFX extends ViewerFX implements
		ImageViewer {

	static {
		DisplayRegistry.registerDisplayComponent(
			"JavaFX",
			ImageViewer.class,
			PyramidalImageViewerFX.class);
	}

	/** The tiff reader, only store the tag information in the memory */
	public ImageReader reader = null;

	/** The list to store the height of each layer in pixels */
	public List<Integer> heights = new ArrayList<>();

	/** The list to store the width of each layer in pixels */
	public List<Integer> widths = new ArrayList<>();

	/** The index of the currently displayed layer */
	public int layer = 0;

	/**
	 * The coordinate of current loaded image. the four elements in the array
	 * are the x and y positions of the origin, which is the upper left corner
	 * of the image, the width and the height of the tiled image. These elements
	 * are in the unit of tile size.
	 */
	public int[] coord;

	/**
	 * The object stores the tiling information of the image pyramid
	 * {@link ImageTiling}
	 */
	private ImageTiling imgTiling;

	/** The width of the resolution of the displayed image */
	public double resW;

	/** The height of the resolution of the displayed image */
	public double resH;

	/** The width of the display window */
	public int winW;

	/** The height of the display window */
	public int winH;

	/** The mouse x-position on screen when a button was pressed/dragged */
	private int mouseDownX;

	/** The mouse y-position on screen when a button was pressed/dragged */
	private int mouseDownY;

	/**
	 * The map to store all the image tiles in the parent container. The key of
	 * the map is the tile's coordinate, the value is the tile object, which is
	 * organized as a image view inside a pane, the image data is stored in the
	 * image view.
	 */
	private final Map<List<Integer>, Pane> imgViewMap;

	{
		ImageView imgView = new ImageView();
		Pane imgPane = new Pane(imgView);
		imgViewMap = new HashMap<>();
		imgViewMap.put(Arrays.asList(0, 0), imgPane);
	}

	/** The main container */
	private final Pane pane = new Pane(imgViewMap.get(Arrays.asList(0, 0)));

	/** The definition of the callback for how mouse drag events are handled */
	private EventHandler<MouseEvent> mouseDraggedFilter = e -> {
		try {
			final Pane node = (Pane) e.getSource();

			final boolean onlyMiddleButton = e.isMiddleButtonDown()
				&& !e.isPrimaryButtonDown()
				&& !e.isSecondaryButtonDown();

			if (!onlyMiddleButton)
				return;

			final int x = (int) e.getScreenX();
			final int y = (int) e.getScreenY();

			// Get mouse drag distance
			final int dX = x - mouseDownX;
			final int dY = y - mouseDownY;

			// get the resolution of the current layer
			final int width = widths.get(layer);
			final int height = heights.get(layer);

			// translate the node position by the mouse drag distance
			final int nodeX = (int) node.getTranslateX();
			final int nodeY = (int) node.getTranslateY();
			translateNode(node, nodeX + dX, nodeY + dY);

			// translate all the attached components
//			for (ViewerFX v : locationAttached) {
//				Pane comp = v.getDisplayComponent();
//				translateNode(comp, nodeX + dX, nodeY + dY);
//			}

			// fit the tiled image to the window size
			if (width > winW || height > winH)
				fitToWindowSize(node);

			// refresh mouse position
			mouseDownX = x;
			mouseDownY = y;
		}
		catch (Exception e1) {
			e1.printStackTrace();
		}
	};

	/**
	 * The definition of the callback for how mouse pressed events are handled
	 */
	private EventHandler<MouseEvent> mousePressedFilter = e -> {
		mouseDownX = (int) e.getScreenX();
		mouseDownY = (int) e.getScreenY();
	};

	/** The definition of the callback for how scroll events are handled */
	private EventHandler<ScrollEvent> mouseScrollFilter = e -> {
		try {
			final Pane node = (Pane) e.getSource();

			// Only zoom when control is down
			if (!e.isControlDown())
				return;

			// determine if load a different resolution layer
			final double zoom = e.getDeltaY() < 0 ? 0.8 : 1.25;
			resW *= zoom;
			resH *= zoom;
			final int nextLayer = getNextResolutionVariant(layer);

			// mouse position before rescaling
			final double preScaleX = e.getX();
			final double preScaleY = e.getY();
			final Point2D preScaleScreen
				= node.localToScreen(preScaleX, preScaleY);

			// rescale the image
			double scaleX = node.getScaleX();
			double scaleY = node.getScaleY();
			scaleX *= zoom;
			scaleY *= zoom;
			node.setScaleX(scaleX);
			node.setScaleY(scaleY);

			// compensate the shift caused by the rescaling
			final Point2D postScaleScreen
				= node.localToScreen(preScaleX, preScaleY);

			final double dX
				= postScaleScreen.getX() - preScaleScreen.getX();
			final double dY
				= postScaleScreen.getY() - preScaleScreen.getY();

			double nodeX = node.getTranslateX();
			double nodeY = node.getTranslateY();
			setTranslate(node, nodeX - dX, nodeY - dY);

			// fit the tiled image to the window size
			final int width = widths.get(layer);
			final int height = heights.get(layer);

			if (width > winW || height > winH) {
				fitToWindowSize(node);
			}

			// change resolution layer
			if (nextLayer != layer) {

				// the origin of the node before changing resolution
				Point2D preOrigin = node.localToParent(0, 0);

				// get the resolution of the new layer
				final int nextWidth = widths.get(nextLayer);
				final int nextHeight = heights.get(nextLayer);
				final int lastLayer = layer;
				layer = nextLayer;

				// clear the containers of tiles
				imgViewMap.clear();
				pane.getChildren().clear();

				if (nextWidth <= winW && nextHeight <= winH) {

					/*
					 * if the new layer is smaller than the window, load the
					 * whole image
					 */
					loadImage();
				}
				else {

					/*
					 * if the new layer is larger than the window, crop a tiled
					 * image
					 */
					if (layer > lastLayer)
						coord = imgTiling.toLowerResolution(lastLayer, coord);
					else
						coord = imgTiling.toHigherResolution(lastLayer, coord);

					tileImages();
				}

				// rescale the node
				final double resRatio = imgTiling.resRatio;
				final double scaleFactor
					= layer > lastLayer ? 1 / resRatio : resRatio;
				scaleX /= scaleFactor;
				scaleY /= scaleFactor;
				node.setScaleX(scaleX);
				node.setScaleY(scaleY);

				// the origin of the node after changing resolution
				Point2D postOrigin = node.localToParent(0, 0);

				// compensate the shift induced by the rescaling
				nodeX = node.getTranslateX();
				nodeY = node.getTranslateY();
				final double shiftX
					= postOrigin.getX() - preOrigin.getX();
				final double shiftY
					= postOrigin.getY() - preOrigin.getY();

				setTranslate(node, nodeX - shiftX, nodeY - shiftY);
			}
		}
		catch (Exception e2) {
			e2.printStackTrace();
		}
	};

	static {
		DisplayRegistry.registerDisplayComponent(
			"JavaFX",
			ImageViewer.class,
			PyramidalImageViewerFX.class);
	}

	/**
	 * @param url
	 *        The URL of the input file
	 * @param winH
	 *        The height of the display window
	 * @param winW
	 *        The width of the display window
	 */
	public PyramidalImageTilesViewerFX(
			final URL url,
			final int winH,
			final int winW)
			throws Exception {

		// initialize file stream
		InputStream stream = url.openStream();

		// initialize image reader
		reader = ImageIO.getImageReadersByFormatName("tiff").next();
		ImageInputStream input = ImageIO.createImageInputStream(stream);
		reader.setInput(input);

		// Get the number of layers in the image pyramid
		final int nLayers = reader.getNumImages(true);

		// Get the resolution of each layer
		for (int i = 0; i < nLayers; i++) {
			final int height = reader.getHeight(i);
			final int width = reader.getWidth(i);
			heights.add(height);
			widths.add(width);
		}

		this.winH = winH;
		this.winW = winW;
		imgTiling = new ImageTiling(widths, heights);
		layer = initializeResolutionVariant();
		resH = heights.get(layer);
		resW = widths.get(layer);

		final int[] tiles = imgTiling.getImageSize(layer);
		coord = new int[] { 0, 0, tiles[0], tiles[1] };

		// set the image view
		loadImage();

		pane.addEventFilter(SCROLL, mouseScrollFilter);
		pane.addEventFilter(MOUSE_PRESSED, mousePressedFilter);
		pane.addEventFilter(MOUSE_DRAGGED, mouseDraggedFilter);
	}

	@Override
	public Pane getDisplayComponent() {
		return pane;
	}

	@Override
	public Point getLocationInParent() {
		final double nodeX = pane.getTranslateX();
		final double nodeY = pane.getTranslateY();
		return new Point(nodeX, nodeY, 0);
	}

	@Override
	public Point localToParent(Point p) {
		final Point2D p2d = new Point2D(p.getX(), p.getY());
		final Point2D imgP2d = pane.localToParent(p2d);
		return new Point(imgP2d.getX(), imgP2d.getY(), 0);
	}

	@Override
	public Point localToScreen(Point p) {
		final Point2D p2d = new Point2D(p.getX(), p.getY());
		final Point2D imgP2d = pane.localToScreen(p2d);
		return new Point(imgP2d.getX(), imgP2d.getY(), 0);
	}

	@Override
	public void setImageBuffer(byte[] buffer, int width, int height) {
		// TODO
	}

	@Override
	public void resetImage() {
		// TODO
	}

	/** convert a java.awt.image.Image to a BufferedImage */
	private WritableImage convertToFXImage(Image input) {

		// Set up the rendering of the buffered image
		BufferedImage buffer = new BufferedImage(
			input.getWidth(null),
			input.getHeight(null),
			BufferedImage.TYPE_INT_BGR);

		// Draw the buffered image
		Graphics2D bGr = buffer.createGraphics();
		bGr.drawImage(input, 0, 0, null);
		bGr.dispose();

		WritableImage output
			= SwingFXUtils.toFXImage(buffer, null);

		return output;
	}

	/**
	 * Create an image tile, the image data is stored in a image view, which is
	 * then incorporated into a pane
	 * 
	 * @param key
	 *        The coordinate of the tile
	 */
	private Pane makeImageTile(final List<Integer> key)
			throws Exception {
		final int tileSize = imgTiling.getTileSize();
		final int x = key.get(0) * tileSize;
		final int y = key.get(1) * tileSize;
		Rectangle sourceRectangle = new Rectangle(x, y, tileSize, tileSize);

		// crop the tile from the image layer
		ImageReadParam param = reader.getDefaultReadParam();
		param.setSourceRegion(sourceRectangle);
		Image image = reader.read(layer, param);
		WritableImage buffer = convertToFXImage(image);

		ImageView imgView = new ImageView(buffer);
		Pane imgPane = new Pane(imgView);
		return imgPane;
	}

	/** Fit the tiled image to the window size */
	private void fitToWindowSize(Pane node)
			throws Exception {
		final double scaleX = node.getScaleX();
		final double scaleY = node.getScaleY();
		final int x = coord[0];
		final int y = coord[1];

		final double tileSize = imgTiling.getTileSize();
		final int[] tiles = imgTiling.getImageSize(layer);

		Point2D origin = node.localToParent(0, 0);
		final double oriX = origin.getX() / scaleX / tileSize;
		final double oriY = origin.getY() / scaleY / tileSize;
		final int shiftTileX
			= oriX > 0 ? (int) Math.ceil(oriX) : (int) Math.floor(oriX);
		final int shiftTileY
			= oriY > 0 ? (int) Math.ceil(oriY) : (int) Math.floor(oriY);

		final int startX;
		final int startY;

		if (shiftTileX > 0 && x - shiftTileX >= 0)
			startX = x - shiftTileX;
		else if (shiftTileX < -1 && x - shiftTileX - 1 < tiles[0])
			startX = x - shiftTileX - 1;
		else
			startX = x;

		if (shiftTileY > 0 && y - shiftTileY >= 0)
			startY = y - shiftTileY;
		else if (shiftTileY < -1 && y - shiftTileY - 1 < tiles[1])
			startY = y - shiftTileY - 1;
		else
			startY = y;

		final int width
			= (int) Math.ceil((double) winW / scaleX / tileSize) + 1;
		final int height
			= (int) Math.ceil((double) winH / scaleY / tileSize) + 1;

		coord = imgTiling
			.loadTiles(layer, new int[] { startX, startY, width, height });
		tileImages();

		final double shiftX = (double) (startX - x) * tileSize * scaleX;
		final double shiftY = (double) (startY - y) * tileSize * scaleY;
		final double nodeX = node.getTranslateX();
		final double nodeY = node.getTranslateY();
		setTranslate(node, nodeX + shiftX, nodeY + shiftY);
	}

	/**
	 * Determine whether to change the resolution layer, if so, switch to the
	 * layer above or below the current one
	 * 
	 * @return The index of the layer
	 */
	private int getNextResolutionVariant(final int index) {
		int next;
		int previous;
		final int nLayers = imgTiling.nLayers;

		if (index == 0) {
			next = 1;
			if (resH <= heights.get(next) || resW <= widths.get(next))
				return next;
		}
		else if (index == nLayers - 1) {
			previous = nLayers - 2;
			if (resH >= heights.get(previous)
				|| resW >= widths.get(previous))
				return previous;
		}
		else {
			next = index + 1;
			previous = index - 1;

			if (resH <= heights.get(next) || resW <= widths.get(next))
				return next;

			if (resH >= heights.get(previous)
				|| resW >= widths.get(previous))
				return previous;
		}

		return index;
	}

	/**
	 * Choose the layer closest to the window size
	 * 
	 * @return the index of the layer
	 */
	private int initializeResolutionVariant() {
		final int nLayers = imgTiling.nLayers;

		for (int k = 0; k <= nLayers - 1; k++) {
			final int height = heights.get(k);
			final int width = widths.get(k);

			if (winH >= height && winW >= width) {
				return k;
			}
		}

		return nLayers - 1;
	}

	/**
	 * Get a section from a resolution variant
	 * 
	 * @throws IOException
	 */
	private void loadImage()
			throws Exception {

		Image image = reader.read(layer);
		WritableImage buffer = convertToFXImage(image);
		ImageView imgView;
		Pane imgPane;

		if (imgViewMap.isEmpty()) {
			imgView = new ImageView();
			imgPane = new Pane(imgView);
			imgViewMap.put(Arrays.asList(0, 0), imgPane);
			pane.getChildren().add(imgPane);
		}
		else {
			imgPane = imgViewMap.get(Arrays.asList(0, 0));
			imgView = (ImageView) imgPane.getChildren().get(0);
		}

		imgView.setImage(buffer);
		imgView.setSmooth(false);
	}

	/** Set translation to the node and the all the attached components */
	private void setTranslate(
			final Pane node,
			final double transX,
			final double transY) {
		translateNode(node, transX, transY);

//		for (ViewerFX v : scaleAttached) {
//			v.getDisplayComponent().setScaleX(node.getScaleX());
//			v.getDisplayComponent().setScaleY(node.getScaleY());
//
//			if (locationAttached.contains(v)) {
//				Pane comp = v.getDisplayComponent();
//				translateNode(comp, transX, transY);
//			}
//		}
	}

	/**
	 * Concatenate the image tiles together to make the image section specified
	 * by {@link #coord}
	 */
	private void tileImages()
			throws Exception {

		final int tileSize = imgTiling.getTileSize();
		final int x = coord[0];
		final int y = coord[1];
		final int w = coord[2];
		final int h = coord[3];

		// store the coordinate of tiles that already exist on the pane
		Set<List<Integer>> preRange = new HashSet<>();

		// iterate through the tiles already exist on the pane
		for (Iterator<Entry<List<Integer>, Pane>> it
			= imgViewMap.entrySet().iterator(); it.hasNext();) {

			Entry<List<Integer>, Pane> entry = it.next();
			final List<Integer> key = entry.getKey();
			final Pane value = entry.getValue();

			if (key.get(0) < x
				|| key.get(1) > x + w - 1
				|| key.get(0) < y
				|| key.get(1) > y + h - 1) {

				// remove the unused tiles to save memory space
				it.remove();
				pane.getChildren().remove(value);
			}
			else {

				// add the coordinates of tiles that will be reused in the image
				preRange.add(key);
				final double transX = tileSize * (key.get(0) - x);
				final double transY = tileSize * (key.get(1) - y);
				translateNode(value, transX, transY);
			}
		}

		// store the coordinates of tiles need to be created
		Set<List<Integer>> postRange = new HashSet<>();

		for (int i = x; i < x + w; i++) {
			for (int j = y; j < y + h; j++) {
				final List<Integer> postKey = Arrays.asList(i, j);
				if (!preRange.contains(postKey))
					postRange.add(postKey);
			}
		}

		// create tiles that haven't existed on the pane and add them
		for (Iterator<List<Integer>> it = postRange.iterator(); it.hasNext();) {
			final List<Integer> tileKey = it.next();
			Pane tile = makeImageTile(tileKey);
			imgViewMap.put(tileKey, tile);
			pane.getChildren().add(tile);
			final double transX = tileSize * (tileKey.get(0) - x);
			final double transY = tileSize * (tileKey.get(1) - y);
			translateNode(tile, transX, transY);
		}
	}

	/** Translate a node by tranX on x axis and transY on y axis */
	private void translateNode(
			Pane node,
			final double transX,
			final double transY) {
		node.setTranslateX(transX);
		node.setTranslateY(transY);
	}

	/**
	 * Class to store the tiling information of a image pyramid and make
	 * operations, such as crop a section of a image layer or switch between
	 * layers.
	 */
	public class ImageTiling {

		/**
		 * The tile information of each layer, each element is the width and
		 * height of the corresponding image layer.
		 */
		public final List<int[]> tileInfo = new ArrayList<>();

		/** The number of layers */
		public final int nLayers;

		/**
		 * The resolution ratio of consecutive layers, assuming the upper layer
		 * contains integer times of resolution of the lower layer
		 */
		public final int resRatio;

		/** Tile size, the tile is square shaped. The value can be reset. */
		public int tileSize = 256;

		public ImageTiling(
				final List<Integer> widths,
				final List<Integer> heights) {
			if (widths == null
				|| heights == null
				|| widths.isEmpty()
				|| heights.isEmpty())
				throw new IllegalArgumentException(
					"The input can not be null or empty!");

			if (widths.size() != heights.size())
				throw new IllegalArgumentException(
					"The lengths of the inputs should be the same!");

			if (widths.size() < 2)
				throw new IllegalArgumentException(
					"The number of the layers should be larger than one!");

			nLayers = widths.size();

			resRatio = Math.floorDiv(widths.get(0), widths.get(1));

			// initialize tileInfo
			for (int i = 0; i < nLayers; i++) {
				final int width = widths.get(i);
				final int height = heights.get(i);
				final int nw
					= (int) Math.ceil((double) width / (double) tileSize);
				final int nh
					= (int) Math.ceil((double) height / (double) tileSize);
				tileInfo.add(new int[] { nw, nh });
			}
		}

		/** Get the current tile size */
		public int getTileSize() {
			return tileSize;
		}

		/** The size of the image layer specified by the index */
		public int[] getImageSize(final int layer) {
			return tileInfo.get(layer);
		}

		/**
		 * Load a section concatenated by image tiles
		 * 
		 * @param layer
		 *        The index of the image layer
		 * @param coord
		 *        The coordinate of the tiled image, which contains the origin x
		 *        and y, the width, and the height.
		 * @return A rectangle to crop the image layer, startX and startY are
		 *         the x and y of the origin. All the elements in the array are
		 *         in unit of tile size.
		 */
		public int[] loadTiles(final int layer, final int[] coord) {
			final int x = coord[0];
			final int y = coord[1];
			final int w = coord[2];
			final int h = coord[3];

			if (w <= 0 || h <= 0)
				throw new IllegalArgumentException(
					"The width and height can not be negtive!");

			final int[] tiles = tileInfo.get(layer);

			if (x >= tiles[0] || x < 0 || y >= tiles[1] || y < 0)
				throw new IllegalArgumentException(
					"The start position is out of bound!");

			final int startX = x;
			final int startY = y;
			final int width = startX + w > tiles[0] ? tiles[0] - startX : w;
			final int height = startY + h > tiles[1] ? tiles[1] - startY : h;

			return new int[] { startX, startY, width, height };
		}

		/**
		 * Change to the higher resolution layer
		 * 
		 * @see #loadTiles(int, int[])
		 */
		public int[] toHigherResolution(final int layer, final int[] coord) {
			final int x = coord[0];
			final int y = coord[1];
			final int w = coord[2];
			final int h = coord[3];

			if (w <= 0 || h <= 0)
				throw new IllegalArgumentException(
					"The width and height can not be negtive!");

			final int[] tiles = tileInfo.get(layer);

			if (x > tiles[0] || y > tiles[1])
				throw new IllegalArgumentException(
					"The start position is out of bound!");

			if (layer < 1)
				throw new IllegalArgumentException(
					"The image is on the top layer!");

			final int upperLayer = layer - 1;

			// scale up the coordinates to those in higher resolution layer
			final int startX = x * resRatio;
			final int startY = y * resRatio;
			final int width = w * resRatio;
			final int height = h * resRatio;

			return loadTiles(
				upperLayer,
				new int[] { startX, startY, width, height });
		}

		/** Overload of {@link #toHigherResolution(int, int[])} */
		public int[] toHigherResolution(
				final int layer,
				final int x,
				final int y) {
			final int[] tiles = tileInfo.get(layer);

			if (x > tiles[0] || y > tiles[1])
				throw new IllegalArgumentException(
					"The start position is out of bound!");

			if (layer < 1)
				throw new IllegalArgumentException(
					"The image is on the top layer!");

			final int newX = x * resRatio;
			final int newY = y * resRatio;

			return new int[] { newX, newY };
		}

		/**
		 * Change to the lower resolution layer
		 * 
		 * @see #loadTiles(int, int[])
		 */
		public int[] toLowerResolution(
				final int layer,
				final int[] coord) {
			final int x = coord[0];
			final int y = coord[1];
			final int w = coord[2];
			final int h = coord[3];

			if (w <= 0 || h <= 0)
				throw new IllegalArgumentException(
					"The width and height can not be negtive!");

			final int[] tiles = tileInfo.get(layer);

			if (x > tiles[0] || y > tiles[1])
				throw new IllegalArgumentException(
					"The start position is out of bound!");

			if (layer > nLayers - 2)
				throw new IllegalArgumentException(
					"The image is on the bottom layer!");

			final int upperLayer = layer + 1;

			// scale down the coordinates to those in lower resolution layer
			final int startX = Math.floorDiv(x, resRatio);
			final int startY = Math.floorDiv(y, resRatio);
			final int width = (int) Math.ceil((double) w / (double) resRatio);
			final int height = (int) Math.ceil((double) h / (double) resRatio);

			return loadTiles(
				upperLayer,
				new int[] { startX, startY, width, height });
		}

		/** Overload of {@link #toLowerResolution(int, int[])} */
		public int[] toLowerResolution(
				final int layer,
				final int x,
				final int y) {
			final int[] tiles = tileInfo.get(layer);

			if (x > tiles[0] || y > tiles[1])
				throw new IllegalArgumentException(
					"The start position is out of bound!");

			if (layer > nLayers - 2)
				throw new IllegalArgumentException(
					"The image is on the bottom layer!");
			
			final int newX = Math.floorDiv(x, resRatio);
			final int newY = Math.floorDiv(y, resRatio);
			
			return new int[] {newX, newY};
		}

		/** Set tile size */
		public void setTileSize(final int x, final int winH, final int winW) {
			final int nh = (int) Math.ceil((double) winH / (double) x);
			final int nw = (int) Math.ceil((double) winW / (double) x);

			if (nh * nw > 100)
				throw new IllegalArgumentException(
					"The size of the tile is too small!");

			tileSize = x;
		}
	}

	@Override
	public void addChild(Viewer viewer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean removeChild(Viewer viewer) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setClip(int x, int y, int w, int h) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetClip() {
		// TODO Auto-generated method stub
		
	}
}
/**
 * REVISION HISTORY
 * 
 * 2017-10-26, Bo Wang, 0.1: Created.
 */
