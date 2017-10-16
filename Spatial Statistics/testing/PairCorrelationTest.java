package miatool.modules.spatialstatistics.testing;

import static miatool.core.util.numerics.MIAMatOps.genRandom;
import static miatool.core.util.numerics.MIAMatOps.setCol;
import static miatool.core.util.numerics.MIAMatOps.max;
import static miatool.core.util.numerics.MIAMatOps.getCol;
import static miatool.core.util.numerics.MIAMatOps.multiply;
import static org.junit.Assert.assertArrayEquals;
import java.util.Collection;
import java.util.List;
import org.apache.commons.lang3.RandomUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import matlabcontrol.MatlabInvocationException;
import miatool.core.util.MatlabUtil;
import miatool.core.util.Parallel;
import miatool.modules.spatialstatistics.PairCorrelation;
import static miatool.modules.spatialstatistics.PairCorrelation.KernelType;

/**
 * Test class for pair correlation class
 * 
 * @version 0.1
 * @since 1.0
 * @author Bo Wang
 */
@RunWith(Parameterized.class)
public class PairCorrelationTest extends ParameterizedTest {
	private static final int MIN_CONTROL_POINTS = 3;
	private static final int MAX_CONTROL_POINTS = 100;

	/** Tolerance of difference between the actual and expected results. */
	private static final double TOL = 1e-8;

	@Parameters
	public static final Collection<Object[]> data() {
		return getParameters(false);
	}

	@BeforeClass
	public static final void setUp()
			throws MatlabInvocationException {
		MatlabUtil.initialize();
	}

	@AfterClass
	public static final void tearDown() {
		MatlabUtil.disconnect();
	}

	@Test
	/**
	 * Test the decovolution algorithms in the deconvolution plugin, compare the
	 * outcome of them with those of the Matlab code
	 */
	public void testMatlab()
			throws Exception {

		// length of spp
		final int lenSpp = RandomUtils.nextInt(
			MIN_CONTROL_POINTS,
			MAX_CONTROL_POINTS);

		// generate random spp
		final double[][] spp = new double[lenSpp][4];

		// the first column of spp is 1
		setCol(spp, 0, 1);

		// the second column is set to NaN
		setCol(spp, 1, Double.NaN);

		// set the third and the fourth columns
		setCol(spp, 2, genRandom(new double[lenSpp]));
		setCol(spp, 3, genRandom(new double[lenSpp]));

		// x lower bound
		final double xLower = 0;

		// x upper bound
		final double xUpper = max(getCol(spp, 2)) + 0.1;

		// y lower bound
		final double yLower = 0;

		// y upper bound
		final double yUpper = max(getCol(spp, 3)) + 0.1;

		final double[] limits = { xLower, xUpper, yLower, yUpper };

		// the searching distance array, r < min(winX, winY) / 2
		final double range
			= (Math.min(xUpper - xLower, yUpper - yLower) - 0.1) / 2;
		final double[] r = multiply(genRandom(new double[10]), range);

		// the band width
		final double bandWidth = Math.random();
	
		// Calculation of the output from Matlab
		final String dir = System.getProperty("user.dir") + "\\Matlab";
		MatlabUtil.eval("clear all;");
		MatlabUtil.setVariable(String.class, "dir", dir);
		MatlabUtil.eval("cd(dir)");
		MatlabUtil.setVariable(double[][].class, "spp", spp);
		MatlabUtil.setVariable(double[].class, "limits", limits);
		MatlabUtil.setVariable(double[].class, "r", r);
		MatlabUtil.setVariable(double.class, "bandWidth", bandWidth);
		MatlabUtil.eval(
			"[adaptedB, classicB] = Gsthat_isot_mod_parallel(spp, limits, r, 'box', bandWidth);");
		MatlabUtil.eval(
			"[adaptedE, classicE] = Gsthat_isot_mod_parallel(spp, limits, r, 'Epan', bandWidth);");

		final double[][][] expectedAdaptedB
			= MatlabUtil.getVariable(double[][][].class, "adaptedB");
		final double[][][] expectedClassicB
			= MatlabUtil.getVariable(double[][][].class, "classicB");
		final double[][][] expectedAdaptedE
			= MatlabUtil.getVariable(double[][][].class, "adaptedE");
		final double[][][] expectedClassicE
			= MatlabUtil.getVariable(double[][][].class, "classicE");
		
		// Calculation of the output from Java
		Parallel.startPool();
		final List<double[][][]> resultBox = PairCorrelation
			.calculate(spp, limits, r, KernelType.BOX, bandWidth);
		final List<double[][][]> resultEpan = PairCorrelation
			.calculate(spp, limits, r, KernelType.EPAN, bandWidth);
		
		final double[][][] actualAdaptedB = resultBox.get(0);
		final double[][][] actualClassicB = resultBox.get(1);
		final double[][][] actualAdaptedE = resultEpan.get(0);
		final double[][][] actualClassicE = resultEpan.get(1);

		Parallel.shutdownPool();

		// Test assertions		
		for (int i = 0; i < expectedAdaptedB.length; i++) {
			for (int j = 0; j < expectedAdaptedB[0].length; j++) {
				assertArrayEquals(
					expectedAdaptedB[i][j],
					actualAdaptedB[i][j],
					TOL);

				assertArrayEquals(
					expectedClassicB[i][j],
					actualClassicB[i][j],
					TOL);

				assertArrayEquals(
					expectedAdaptedE[i][j],
					actualAdaptedE[i][j],
					TOL);

				assertArrayEquals(
					expectedClassicE[i][j],
					actualClassicE[i][j],
					TOL);
			}
		}
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-08-01, 0.1, Bo Wang: Created.
 * 
 */
