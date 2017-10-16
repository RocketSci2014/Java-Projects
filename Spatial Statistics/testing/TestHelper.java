package miatool.modules.spatialstatistics.testing;

import static miatool.core.util.numerics.MIAMatOps.divideI;
import static miatool.core.util.numerics.MIAMatOps.subtract;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;

/**
 * Provides some stating helper methods for assistance with various tests.<br>
 * <br>
 *
 * For test cases or test suites that need loading of results from MATLAB, the
 * MATLAB </code>.m</code> files and <code>.mat</code> files should following
 * the following convention. If the test file no inner classes, then the
 * corresponding Matlab files should have the same name as the test file. If the
 * test file defines inner test classes and is itself a test suite, then each
 * inner test classes should have its own Matlab files and should be named as
 * <code>TestFileName_InnerClassName</code>
 *
 * @author Anish V. Abraham
 * @since 0.2
 * @version 0.6
 */
public final class TestHelper {

	/** A string for outputting a string for test information */
	private static String tOF = "  [%d] %s ...%s";

	/** The system-dependent newline character for console output */
	public static final String nl = System.getProperty("line.separator");

	public static final Random randomizer = new Random();

	/** A string for outputting a string for parameter information */
	private static String pOF = "    %s: %s%s";

	/** A string for outputting a string for parameter information */
	private static String iOF = "\n%s [%d] : %s";

	/** @return a random printable string of ASCII printable characters */
	public static String buildRandomString(final int nChars) {
		return RandomStringUtils.randomAscii(nChars);
	}

	/**
	 * @param nStrings
	 *        The number of strings to generate.
	 * @param nChars
	 *        The number of characters each string should have.
	 * @return Generates an array of random printable ASCII strings.
	 */
	public static String [] buildRandomStrings(int nStrings, int nChars) {
		String [] strings = new String [nStrings];
		String s;

		for (int i = 0; i < nStrings; i++) {
			s = buildRandomString(nChars);
			strings[i] = s;
		}

		return strings;
	}

	/**
	 * Performs standard tests to determine whether the clone function for a
	 * particular class functions correctly by performing standard tests and
	 * assertions as defined by {@link Object#clone()}.
	 * 
	 * @param original
	 *        Original object that was cloned.
	 * @param clone
	 *        The cloned object.
	 */
	public static void checkClones(
			final Object original,
			final Object clone) {
		assertNotSame(original, clone);
		assertSame(original.getClass(), clone.getClass());
		assertEquals(original, clone);
	}

	/**
	 * Performs the standard tests of equality as defined by
	 * {@link Object#equals(Object)}.
	 * 
	 * @param reference
	 *        The reference against which the object under test should be
	 *        compared.
	 * @param actual
	 *        The object to be compared.
	 * @param nonEqual
	 *        An example of an object that should not be equal to the reference.
	 */
	public static void checkEquals(
			final Object reference,
			final Object actual,
			final Object nonEqual) {
		assertTrue(actual.equals(actual));
		assertTrue(actual.equals(reference));
		assertTrue(reference.equals(actual));
		assertFalse(actual.equals(null));
	}

	/**
	 * Helper method to check the relative difference between the actual and
	 * expected result. The relative difference is meant to approach zero within
	 * some tolerance. If the expected value is zero, the difference between the
	 * expected and actual value is checked rather than the relative difference.
	 * 
	 * @param expected
	 * @param actual
	 * @param relativeTolerance
	 */
	public static void checkRelativeDifference(
			final double expected,
			final double actual,
			final double relativeTolerance) {
		// Avoid a division by zero resulting in NaN
		if (expected == 0) {
			assertEquals(0, expected - actual, relativeTolerance);
		}
		else {
			assertEquals(0, (expected - actual) / expected, relativeTolerance);
		}
	}

	/**
	 * Helper method to check that the relative difference between two sets of
	 * values is within a specified tolerance; i.e. that the two sets of values
	 * match to a specified number of significant digits. The relative
	 * difference will be calculated with respect to the expected values.
	 *
	 * @param expected
	 * @param actual
	 * @param relativeTolerance
	 */
	public static void checkRelativeDifference(double [] expected,
			double [] actual,
			double relativeTolerance) {
		double [] diff = divideI(subtract(expected, actual), expected);
		Assert.assertArrayEquals(new double [diff.length], diff,
				relativeTolerance);
	}// close checkRelativeDifference

	public static void printIteration(final int iter, final String testName) {
		System.out.printf(iOF, testName, iter, nl);
	}

	public static void printParameter(
			final String parameterName,
			final String testName) {
		System.out.printf(pOF, parameterName, testName, nl);
	}

	public static void printTest(final int iter, final String testName) {
		System.out.printf(tOF, iter, testName, nl);
	}

	public static void printTestInfo(final String info) {
		System.out.printf("    %s%s", info, nl);
	}

	// Implementing Fisher–Yates shuffle
	public static void shuffleArray(int [] ar) {
		for (int i = ar.length - 1; i > 0; i--) {
			int index = randomizer.nextInt(i + 1);
			// Simple swap
			int a = ar[index];
			ar[index] = ar[i];
			ar[i] = a;
		}
	}
}// close TestHelper

/**
 * REVISION HISTORY:
 * 
 * 2015-06-01, 0.6, Anish V. Abraham: Checking relative difference corrected for
 * situation where the expected value being zero caused divide-by-zero errors
 * (NaNs).
 *
 * 2015-05-13, 0.5, Anish V. Abraham: Updated documentation.
 *
 * 2014-02-10, 0.4, Anish V. Abraham: Added support to automatically load 2D
 * double arrays.
 *
 * 2014-02-05, 0.3, Anish V. Abraham: Updated to include
 * {@link #loadVariables(Class)}.
 *
 * 2013-11-29, 0.2, Anish V. Abraham: Modified
 * {@link #setUpBeforeClass(boolean, boolean, boolean)} so that the .mat file
 * loading uses full absolute paths. Previously, the MATLAB intialization part
 * took care of changing the working directory to the correct one. However, with
 * test suites, it's better to load the mat file with full file names rather
 * than relying on changes to working directory.
 *
 * 2013-10-03, 0.1, Anish V. Abraham: Created.
 */
