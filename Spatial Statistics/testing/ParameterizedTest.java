package miatool.modules.spatialstatistics.testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.runners.Parameterized.Parameters;

/**
 * Helper class that provides some static functionality and properties for test
 * classes that involve parameterized testing.
 * 
 * @author Anish V. Abraham
 * @since 0.2
 * @version 0.1
 */
public abstract class ParameterizedTest {

	/** The number of times parameterized tests will run. */
	protected static int nTestRuns;

	/** The length of the string to be generated */
	protected static int stringLength = 100;

	/** The randomization will be done with this object */
	protected static final Random randomizer = TestHelper.randomizer;

	/** The system-dependent newline character for console output */
	protected static final String nl = TestHelper.nl;

	/** Initialize the static values to their defaults. */
	static {
		reset();
	}

	/**
	 * Helper method to provide the parameters for the {@link Parameters}
	 * method.
	 * 
	 * @param provideIterations
	 *        Includes the iteration number in the parameters list if
	 *        <code>true</code>
	 * @return Constructor parameters for each iteration as intended by the
	 *         {@link Parameters} annotation.
	 */
	protected static Collection<Object []> getParameters(
			final boolean provideIterations) {
		final List<Object []> parameters = new ArrayList<Object []>();
		for (int i = 0; i < nTestRuns; i++) {
			if (provideIterations)
				parameters.add(new Object [] { i });
			else
				parameters.add(new Object [] {});
		}
		return parameters;
	}

	/**
	 * Child classes are allowed to temporarily reset some of these static
	 * values. This method allows them to be reset to their defaults.
	 */
	protected static void reset() {
		nTestRuns = 20;
		stringLength = 100;
	}

}

/**
 * REVISION HISTORY:
 * 
 * 2014-06-19, 0.1, Anish V. Abraham: Created.
 * 
 */
