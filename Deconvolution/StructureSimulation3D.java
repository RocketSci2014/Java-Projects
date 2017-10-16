package simulations;

import miatool.core.util.numerics.MIAMatOps;
import miatool.tools.Preview;

/**
 * Simulate a 3D structure, the shape could be volume box or sphere
 * 
 * @author Bo Wang
 * @version 0.1
 * @since 1.0
 */
public class StructureSimulation3D {

	// parameters in the simulation
	private int imageHeight = 100;
	private int imageWidth = 100;
	private int edge = 50;
	private int radius = 25;
	private int nImages = 40;
	private Shape3D shape;
	private Contour contour;
	public final double intensity = 300; // photon detection rate 2000/s
	
	/** The matrix to store the keep the data */
	public double[][][] strucData
			= new double[nImages][imageHeight][imageWidth];

	/**
	 * Temporary main for testing purpose
	 */
	public static void main(final String[] args) {
		final StructureSimulation3D cylinder
				= new StructureSimulation3D(Shape3D.CYLINDER, Contour.DOTTED);
		cylinder.execute();
		new Preview(cylinder.strucData[1]);

		final StructureSimulation3D box
				= new StructureSimulation3D(Shape3D.BOX, Contour.DOTTED);
		box.execute();
		new Preview(box.strucData[1]);
	}

	public StructureSimulation3D(final Shape3D shape, final Contour contour) {
		this.shape = shape;
		this.contour = contour;
	}

	/**
	 * Execute the simulation
	 */
	public void execute() {

		// switch between solid fill and dotted fill
		switch (contour) {
			case SOLID:
				makeSolidStructure();
				break;
			case DOTTED:
				makeDottedStructure();
				break;
			default:
				break;
		}
	}

	/** Make solid filled structures */
	private void makeSolidStructure() {

		// create the profile of the structure
		switch (this.shape) {
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

	/** Make structures where individual dots distributing inside */
	private void makeDottedStructure() {
		switch (shape) {
			case CYLINDER:

				/*
				 * determine the number of dots in the structure, assume the
				 * percentage of filling is 0.1%
				 */
				final double volumeCyl
						= Math.PI * radius * radius * (nImages - 20);
				final int nCyl = (int) (0.001 * volumeCyl);

				// generate x and y positions
				final int[] posXCyl = new int[nCyl];
				final int[] posYCyl = new int[nCyl];

				for (int i = 0; i < nCyl; i++) {

					// select x-y coordinate inside the cylinder
					double length;
					int xCoor;
					int yCoor;

					do {
						xCoor = MIAMatOps.genRandom(1 - radius, radius);
						yCoor = MIAMatOps.genRandom(1 - radius, radius);
						length = Math.sqrt(xCoor * xCoor + yCoor * yCoor);
					}
					while (length > (double) radius);

					posXCyl[i] = xCoor;
					posYCyl[i] = yCoor;
				}

				// generate z positions
				final int[] posZCyl = new int[nCyl];
				MIAMatOps.genRandom(posZCyl, 10, nImages - 10);

				// fill the structure matrix pixel points
				for (int j = 0; j < nCyl; j++) {
					final int indX
							= posXCyl[j] < 0
									? imageWidth / 2 + posXCyl[j] - 1
									: imageWidth / 2 + posXCyl[j];
					final int indY
							= posYCyl[j] < 0
									? imageHeight / 2 + posYCyl[j] - 1
									: imageHeight / 2 + posYCyl[j];
					final int indZ = posZCyl[j];
					
					strucData[indZ][indY][indX] = intensity;
				}
				break;
			case BOX:
				/*
				 * determine the number of dots in the structure, assume the
				 * percentage of filling is 0.1%
				 */
				final double volume
						= edge * edge * (nImages - 20);
				final int nBox = (int) (0.001 * volume);
				
				final int yPos = imageHeight / 2 + edge / 2;
				final int yNeg = imageHeight / 2 - edge / 2;
				final int xPos = imageWidth / 2 + edge / 2;
				final int xNeg = imageWidth / 2 - edge / 2;
				
				final int[] posZBox = new int[nBox];
				final int[] posYBox = new int[nBox];
				final int[] posXBox = new int[nBox];
				
				// generate x, y, z positions
				MIAMatOps.genRandom(posZBox, 10, nImages - 10);
				MIAMatOps.genRandom(posYBox, yNeg, yPos);
				MIAMatOps.genRandom(posXBox, xNeg, xPos);
				
				// fill the structure matrix with pixel values
				for (int k = 0; k < nBox; k++)
					strucData[posZBox[k]][posYBox[k]][posXBox[k]] = intensity; 
				break;
			default:
				break;
		}
	}

	public static enum Contour {
		/** solid structure */
		SOLID,

		/** Structure containing random located dots */
		DOTTED;
	}

	/**
	 * The shape of the structure
	 */
	public static enum Shape3D {
		/** Cylinder */
		CYLINDER,

		/** Volume box */
		BOX;
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-07-15, 0.1, Bo Wang: Created
 */
