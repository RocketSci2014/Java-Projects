package miatool.plugins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import com.sun.javafx.application.PlatformImpl;
import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import miatool.core.miatooldirectory.MIAToolDirectory;
import miatool.core.setssingles.image.ImageSet;
import miatool.miamain.MIATool;
import miatool.miamain.MIAToolHub;
import miatool.plugins.MIAPlugin;
import miatool.ui.events.MIAPropertyChangeEvent;
import miatool.ui.wizard.objectselection.ObjectSelectionWizardSwing;

/**
 * Plugin to view cross sections in X-Y, X-Z, and Y-Z directions
 * 
 * @author Bo Wang
 * @since 1.0
 * @version 0.1
 */
public class CrossSectionViewerPlugin
		implements
		MIAPlugin,
		ActionListener {
	public Button getImgSetBtn;
	public Button csViewerBtn;
	public TextField imgSetTf;
	public static final String WINDOW_TITLE = "Cross Section Viewer Plugin";
	public static final String MENU_TITLE = "Deconvolution Tools";
	public final JMenuItem menuItem
			= new JMenuItem(
					WINDOW_TITLE
							+
							"...");
	private Stage stage;
	private ObjectSelectionWizardSwing<ImageSet> imgSetSelector = null;

	/** The image set to be presented */
	private ImageSet imgSet = null;

	public static void main(final String[] args) {
		MIATool.main(args);
		final CrossSectionViewerPlugin tool = new CrossSectionViewerPlugin();
		tool.startup();
	}

	public CrossSectionViewerPlugin() {
		super();
	}

	public void setupFrame() {
		PlatformImpl.startup(() -> {});

		// make main container
		final GridPane mainContainer = new GridPane();
		mainContainer.setPadding(new Insets(5));
		mainContainer.setHgap(4);
		mainContainer.setVgap(4);

		// fill main container
		imgSetTf = new TextField("-NULL-");
		imgSetTf.setEditable(false);
		getImgSetBtn = new Button("Load Image Set");
		csViewerBtn = new Button("Open Cross Section Viewer");
		mainContainer.add(imgSetTf, 0, 0);
		mainContainer.add(getImgSetBtn, 1, 0);
		mainContainer.add(csViewerBtn, 0, 1);
		GridPane.setHalignment(imgSetTf, HPos.RIGHT);
		GridPane.setHalignment(getImgSetBtn, HPos.LEFT);
		GridPane.setHalignment(csViewerBtn, HPos.CENTER);
		
		// setup stage
		Platform.runLater(() -> {
			stage = new Stage();
			stage.setTitle(WINDOW_TITLE);
			final Scene scene = new Scene(mainContainer, 300, 100);
			stage.setScene(scene);
		});
	}

	@Override
	public void processPropertyChange(final MIAPropertyChangeEvent<?> e)
			throws IllegalArgumentException {
		final Object src = e.getSource();
		final Object prop = e.getChangedProperty();

		// Callback from imgSetSelector
		if (prop == ObjectSelectionWizardSwing.Property.SELECTED_OBJECT
				&& src == imgSetSelector) {
			processImgSetSelection();
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

		getImgSetBtn.setOnAction((event) -> {
			openImgSetSelector();
		});

		csViewerBtn.setOnAction((event) -> {
			if (imgSet != null) {	
				// determine if the image set is linear
				if (imgSet.size().length > 1) {
					JOptionPane.showMessageDialog(
							menuItem,
							"The Image Set should be linear!",
							"The Image Set should be linear!",
							JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				final CrossSectionViewer csViewer = new CrossSectionViewer(imgSet);
				csViewer.startup();
			}
		});
	}

	@Override
	public void updateFields() {
		imgSetTf.setText(imgSet == null ? "-NULL-" : imgSet.getFilename());
	}

	/**
	 * Helper method to open the object selection wizard for the selecting a
	 * image set.
	 */
	private void openImgSetSelector() {
		final MIAToolHub hub = MIATool.getMIAToolHub();
		final MIAToolDirectory mtd
				= hub == null ? null : hub.getMIAToolDirectory();

		if (mtd == null) {
			JOptionPane.showMessageDialog(
					menuItem,
					"No MIAToolDirectory loaded!",
					"No MIAToolDirectory!",
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

	/** Callback for when a new object set is selected. */
	private void processImgSetSelection() {
		final List<ImageSet> loadedImage = imgSetSelector.getSelectedObjects();

		if (loadedImage.isEmpty() || loadedImage.get(0) == null) {
			return;
		}

		imgSet = loadedImage.get(0);
		updateFields();
		return;
	}
}
/**
 * REVISION HISTORY
 * 
 * 2017-07-07, 0.1, Bo Wang: Created
 * 
 */
