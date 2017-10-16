package miatool.modules.spatialstatistics;

import miatool.core.util.Parallel;
import miatool.core.util.Parallel.Operation;
import static miatool.core.util.numerics.MIAMatOps.isRectangular;
import static miatool.core.util.numerics.MIAMatOps.range;
import static miatool.core.util.numerics.MIAMatOps.generateTuples;

/**
 * Calculate modified Ripley's K function. Translated from ripleyk_alpha.m
 * written by Ed Cowan
 * 
 * @since 1.0
 * @version 0.1
 * @author Bo Wang, Ed Cowan
 */
public class RipleyK {

	/**
	 * Modified Ripley's K function. The calculation is parallelized.
	 * 
	 * @param x
	 *        A N x 2 array of points
	 * 
	 * @param r
	 *        An array of search radii
	 * 
	 * @param roi
	 *        An array of x lower bound, x upper bound, y lower bound, y upper
	 *        bound
	 * 
	 * @param distanceThreshold
	 *        The lower threshold for the distance between two points
	 * 
	 * @return The Ripley's K values
	 * 
	 * @throws IllegalArgumentException
	 *         If the size of x or roi is wrong, or x is out of the bounds of
	 *         roi
	 */
	public static double[] ripleyKAlpha(
			final double[][] x,
			final double[] r,
			final double[] roi,
			final double distanceThreshold) {
		// check legitimation
		if (!isRectangular(x)
			|| x[0].length != 2
			|| roi.length != 4)
			throw new IllegalArgumentException("Wrong size!");

		// check bounds
		for (double[] xi : x) {
			if (xi[0] < roi[0]
				|| xi[0] > roi[1]
				|| xi[1] < roi[2]
				|| xi[1] > roi[3])
				throw new IllegalArgumentException(
					"x should be in the bounds of roi");
		}

		/*
		 * make matrices dx and dy, for the calculation of distance
		 */
		final double[][] dist = new double[x.length][x.length];

		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < x.length; j++) {
				final double xDiff = x[i][0] - x[j][0];
				final double yDiff = x[i][1] - x[j][1];
				dist[i][j] = Math.sqrt((xDiff * xDiff) + (yDiff * yDiff));
			}
		}

		// calculate the top, bottom, left, right values
		final double[] dD = new double[x.length];
		final double[] dU = new double[x.length];
		final double[] dL = new double[x.length];
		final double[] dR = new double[x.length];
		final double[] bLU = new double[x.length];
		final double[] bLD = new double[x.length];
		final double[] bRU = new double[x.length];
		final double[] bRD = new double[x.length];
		final double[] bUL = new double[x.length];
		final double[] bUR = new double[x.length];
		final double[] bDL = new double[x.length];
		final double[] bDR = new double[x.length];

		for (int k = 0; k < x.length; k++) {
			dD[k] = x[k][1] - roi[2];
			dU[k] = roi[3] - x[k][1];
			dL[k] = x[k][0] - roi[0];
			dR[k] = roi[1] - x[k][0];
			bLU[k] = Math.atan2(dU[k], dL[k]);
			bLD[k] = Math.atan2(dD[k], dL[k]);
			bRU[k] = Math.atan2(dU[k], dR[k]);
			bRD[k] = Math.atan2(dD[k], dR[k]);
			bUL[k] = Math.atan2(dL[k], dU[k]);
			bUR[k] = Math.atan2(dR[k], dU[k]);
			bDL[k] = Math.atan2(dL[k], dD[k]);
			bDR[k] = Math.atan2(dR[k], dD[k]);
		}

		// calculate the weights
		final double[][] aL = getOffsetAngles(dL, dist);
		final double[][] aR = getOffsetAngles(dR, dist);
		final double[][] aD = getOffsetAngles(dD, dist);
		final double[][] aU = getOffsetAngles(dU, dist);
		final double[][] weights = new double[x.length][x.length];

		for (int y = 0; y < x.length; y++) {
			for (int z = 0; z < x.length; z++) {
				final double cL
					= Math.min(aL[y][z], bLU[y])
						+ Math.min(aL[y][z], bLD[y]);
				final double cR
					= Math.min(aR[y][z], bRU[y])
						+ Math.min(aR[y][z], bRD[y]);
				final double cD
					= Math.min(aD[y][z], bDL[y])
						+ Math.min(aD[y][z], bDR[y]);
				final double cU
					= Math.min(aU[y][z], bUL[y])
						+ Math.min(aU[y][z], bUR[y]);
				final double ext = cL + cR + cD + cU;
				weights[y][z] = 1 / (1 - ext / (2 * Math.PI));
			}
		}

		// calculate K value
		final double[] ripK = new double[r.length];
		final int[] xRange = range(0, 1, r.length - 1);
		final int[][] tuples = generateTuples(xRange);

		Parallel.parfor(tuples, new Operation<int[]>() {

			@Override
			public void perform(int[] tuple) {
				final int m = tuple[0];
				double usedWeights = 0;

				for (int i = 0; i < x.length; i++) {
					for (int j = 0; j < x.length; j++) {
						if (dist[i][j] < r[m] && dist[i][j] > distanceThreshold)
							usedWeights += weights[i][j];
					}
				}

				ripK[m] = (roi[1] - roi[0])
					* (roi[3] - roi[2])
					/ x.length
					/ (x.length - 1)
					* usedWeights;
			}
		});

		return ripK;
	}

	/**
	 * Calculate the matrix containing the offset values
	 * 
	 * @param d
	 *        An array of offset values, the length of d is the same as the
	 *        height of dist
	 * 
	 * @param dist
	 *        An matrix of distances between points
	 * 
	 * @return An matrix of the same size as dist and contains the angles made
	 *         by the offset values
	 */
	public static double[][] getOffsetAngles(
			final double[] d,
			final double[][] dist) {
		final int h = dist.length;
		final int w = dist[0].length;
		final double[][] result = new double[h][w];

		for (int i = 0; i < h; i++) {
			for (int j = 0; j < w; j++) {
				if (d[i] < dist[i][j])
					result[i][j] = Math.acos(d[i] / dist[i][j]);
			}
		}

		return result;
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-07-24, 0.1, Bo Wang: Created.
 * 
 */
