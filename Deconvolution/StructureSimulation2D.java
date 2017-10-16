package simulations;

public class StructureSimulation2D {
	
	// parameters in the simulation
	private int imageHeight = 100;
	private int imageWidth = 100;
	private int nImages = 40;
	private Shape2D shape;
	public final double intensity = 300; // photon detection rate 2000/s
	
	/** The matrix to store the keep the data */
	public double[][][] strucData
			= new double[nImages][imageHeight][imageWidth];
	
	public StructureSimulation2D(Shape2D shape){
		this.shape = shape;
	}
	
	public void execute(){
		
		// select the shape of the structure
		switch(shape){
			case LINE:
				
				// make a line of 50 pixels
				for (int i = 0; i < 50; i++){
					strucData[20][50][25 + i] = intensity;
				}
				break;
			case PLANE:
				
				// make a plane of 50 x 50 pixels
				for (int i = 0; i < 50; i++){
					for (int j = 0; j < 50; j++){
						strucData[20][25 + j][25 + i] = intensity;
					}
				}
				break;
		}
	}
	
	public static enum Shape2D {

		/** A line consisted by pixels */
		LINE,

		/** A plane consisted by pixels */
		PLANE;
	}
}
