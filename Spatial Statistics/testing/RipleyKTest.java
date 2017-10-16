package miatool.modules.spatialstatistics.testing;

import static miatool.core.util.numerics.MIAMatOps.genRandom;
import static org.junit.Assert.assertArrayEquals;
import java.util.Collection;
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
import miatool.core.util.numerics.MIAMatOps;
import miatool.modules.spatialstatistics.RipleyK;

/**
 * Test class of RipleyK
 * 
 * @version 0.1
 * @since 1.0
 * @author Bo Wang
 */
@RunWith(Parameterized.class)
public class RipleyKTest extends ParameterizedTest {

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

		// length of x
		final int lenX = RandomUtils.nextInt(
			MIN_CONTROL_POINTS,
			MAX_CONTROL_POINTS);

		// length of r
		final int lenR = RandomUtils.nextInt(
			MIN_CONTROL_POINTS,
			MAX_CONTROL_POINTS);

		// generate random x, r, roi, and alpha
		final double[][] x = MIAMatOps.copy(genRandom(new double[lenX][2]));

		// x lower bound
		final double xLower = 0;

		// x upper bound
		final double xUpper = MIAMatOps.max(MIAMatOps.getCol(x, 0)) + 0.1;

		// y lower bound
		final double yLower = 0;

		// y upper bound
		final double yUpper = MIAMatOps.max(MIAMatOps.getCol(x, 1)) + 0.1;
		final double[] r = genRandom(new double[lenR]).clone();
		final double[] roi = { xLower, xUpper, yLower, yUpper };
		final double alpha = Math.random() * MIAMatOps.max(r);

		// Calculation of the output from Matlab
		final String dir = System.getProperty("user.dir") + "\\Matlab";
		MatlabUtil.eval("clear all;");
		MatlabUtil.setVariable(String.class, "dir", dir);
		MatlabUtil.eval("cd(dir)");
		MatlabUtil.setVariable(double[][].class, "x", x);
		MatlabUtil.setVariable(double[].class, "r", r);
		MatlabUtil.setVariable(double[].class, "roi", roi);
		MatlabUtil.setVariable(double.class, "alpha", alpha);
		MatlabUtil.eval("K = ripleyk_alpha(x, r, roi, alpha);");
		final double[] expectedK = MatlabUtil.getVariable(double[].class, "K");

		// Calculation of the output from Java
		Parallel.startPool();
		final double[] actualK = RipleyK.ripleyKAlpha(x, r, roi, alpha);
		Parallel.shutdownPool();
		// Test assertions
		assertArrayEquals(
			expectedK,
			actualK,
			TOL);
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-07-25, 0.1, Bo Wang: Created.
 * 
 */
