package miatool.modules.spatialstatistics;

import java.util.ArrayList;
import java.util.List;
import miatool.core.util.MIAUtil;
import miatool.core.util.Parallel;
import miatool.core.util.Parallel.Operation;
import miatool.core.util.numerics.HistogramAnalysis;
import static miatool.core.util.numerics.MIAMatOps.getCol;
import static miatool.core.util.numerics.MIAMatOps.unique;
import static miatool.core.util.numerics.MIAMatOps.multiply;
import static miatool.core.util.numerics.MIAMatOps.multiplyI;
import static miatool.core.util.numerics.MIAMatOps.divideI;
import static miatool.core.util.numerics.MIAMatOps.divide;
import static miatool.core.util.numerics.MIAMatOps.addI;
import static miatool.core.util.numerics.MIAMatOps.add;
import static miatool.core.util.numerics.MIAMatOps.generateTuples;
import static miatool.core.util.numerics.MIAMatOps.range;
import static miatool.core.util.numerics.MIAMatOps.isNan;
import static miatool.core.util.numerics.MIAMatOps.setI;
import static miatool.core.util.numerics.MIAMatOps.copy;
import static miatool.core.util.numerics.MIAMatOps.cumSum;
import static miatool.core.util.numerics.MIAMatOps.transpose;
import static miatool.core.util.numerics.MIAMatOps.sum;
import static miatool.core.util.numerics.MIAMatOps.xyzToZxy;

/**
 * Calculate a matrix of pair correlation functions for the multivariate spatial
 * point pattern in SPP
 * 
 * [1] Illian, J.B. et al, 2008, Statistical Analysis and Modelling of Spatial
 * Point Patterns
 * 
 * @version 0.1
 * @since 1.0
 * @author Bo Wang, Illian, J. B. et al
 */
public class PairCorrelation {

	public final static double EPS = 2.220446049250313e-16;

	public static void main(final String[] args) {
		final double a = 1;

		System.out.println(-a);
	}

	/**
	 * Main function in PairCorrelation, calculate the matrix of pair
	 * correlation
	 * 
	 * @param spp
	 *        Contains the spatial point pattern (spp) of interest. This is an n
	 *        by 4 matrix. The first column contains the variable labels, the
	 * @param limits
	 *        The limits of the observation window, which are lower x bound,
	 *        upper x bound, lower y bound, upper y bound.
	 * @param searchDist
	 *        A vector of search distances at which to estimate the pair
	 *        correlation function
	 * @param type
	 *        The type of kernel used for the pair correlation function
	 *        estimation
	 * @param bandWidth
	 *        The band width parameter for the kernel function. The kernel is
	 *        scaled such that the band width is the standard deviation
	 * @return The adapted intensity matrix and the classic intensity matrix
	 * @throws IllegalArgumentException
	 *         If searchDist is beyond its threshold.
	 */
	public static List<double[][][]> calculate(
			final double[][] spp,
			final double[] limits,
			final double[] searchDist,
			final KernelType type,
			final double bandWidth) {

		/*
		 * find the number of unique labels and the length of pair correlation
		 * function estimator
		 */
		final double[] labels = getCol(spp, 0);
		final double[] uniLabels = unique(labels);
		final int numLabels = uniLabels.length;
		final int lenPCFE = searchDist.length;

		// estimate the dimensions of the observation window
		final double winX = limits[1] - limits[0];
		final double winY = limits[3] - limits[2];
		final double distThreshold = Math.min(winX, winY) / 2;

		for (int i = 0; i < lenPCFE; i++) {
			if (searchDist[i] >= distThreshold)
				throw new IllegalArgumentException(
					"The search distance can't exceed threshold!");
		}

		// sort spp data by the third column, then by the first column
		final double[][] sppSorted = sortRows(sortRows(spp, 2), 0);

		// calculate the distances between data points
		final double[] xVals = getCol(sppSorted, 2);
		final double[] yVals = getCol(sppSorted, 3);
		final double[][] dist = new double[xVals.length][xVals.length];
		final double[][] xDist = new double[xVals.length][xVals.length];
		final double[][] yDist = new double[xVals.length][xVals.length];

		for (int i = 0; i < xVals.length; i++) {
			for (int j = 0; j < xVals.length; j++) {
				final double xDiff = xVals[j] - xVals[i];
				final double yDiff = yVals[j] - yVals[i];
				dist[i][j] = Math.sqrt(xDiff * xDiff + yDiff * yDiff);
				xDist[i][j] = Math.abs(xDiff);
				yDist[i][j] = Math.abs(yDiff);
			}
		}

		/*
		 * carry out edge correction via the translation method; divide the
		 * scaled kernel by the area of the overlap created by translating the
		 * observation window by the separation vector
		 */
		addI(xDist, -winX);
		addI(yDist, -winY);
		final double[][] edgeCorrection = multiply(xDist, yDist);

		// calculate classic intensity
		final double winArea = winX * winY;
		final int[] estimatorArr
			= HistogramAnalysis.histCount(labels, uniLabels);
		final double[][] classicIntensity = new double[numLabels][numLabels];

		for (int i = 0; i < numLabels; i++) {
			for (int j = 0; j < numLabels; j++) {
				classicIntensity[i][j]
					=  estimatorArr[i] * estimatorArr[j] / (winArea * winArea);

				// multiple correction factor to the elements on the diagonal
				if (i == j) {
					final double adjFactor = (estimatorArr[i] - 1) / (double) estimatorArr[i];
					classicIntensity[i][j] *= adjFactor;
				}
			}
		}

		// calculate the outputs of the function, using parallel
		final int[] xRange = range(0, 1, lenPCFE);
		final int[][] tuples = generateTuples(xRange);
		final double[][][] gsthatsAdapInt = new double[lenPCFE][][];
		final double[][][] gsthatsClaInt = new double[lenPCFE][][];

		Parallel.parfor(tuples, new Operation<int[]>() {

			@Override
			public void perform(int[] tuple) {
				final int x = tuple[0];
				final double r = searchDist[x];

				// estimate matrix for each estimator
				final double[][] estMat
					= new double[xVals.length][xVals.length];
				switch (type) {
					case BOX:
						for (int i = 0; i < xVals.length; i++) {
							for (int j = 0; j < xVals.length; j++) {
								final double factorB
									= Math.sqrt(12) * bandWidth;
								if (i != j
									&& Math.abs(r - dist[i][j])
										/ factorB <= 0.5) {
									estMat[i][j] = 1 / factorB;
								}
							}
						}
						break;
					case EPAN:
						for (int i = 0; i < xVals.length; i++) {
							for (int j = 0; j < xVals.length; j++) {
								if (i != j) {
									final double factorE
										= Math.sqrt(5) * bandWidth;
									final double offsetE
										= (r - dist[i][j]) / factorE;
									final double estMatE = 0.75
										/ factorE
										* (1 - offsetE * offsetE);

									if (estMatE >= 0)
										estMat[i][j] = estMatE;
								}
							}
						}
						break;
				}
				multiplyI(estMat, 1 / (2 * Math.PI * r));
				divideI(estMat, edgeCorrection);

				// calculate the numerator of the Gsthats factor
				final double[][] numor = sumSubMatrices(estMat, estimatorArr);

				// calculate the edge correction factor
				final double[] vetX
					= { limits[0], limits[1], limits[1], limits[0] };
				final double[] vetY
					= { limits[2], limits[2], limits[3], limits[3] };

				final double[][] edgeCorrFactors = getEdgeCorrelationFactors(
					sppSorted,
					vetX,
					vetY,
					r);

				// calculate the isotropised set covariance
				final double isoCov;
				final double sWinEdge = Math.min(winX, winY);
				final double lWinEdge = Math.max(winX, winY);
				if (r <= sWinEdge) {
					isoCov = sWinEdge * lWinEdge
						- 2 * r * (sWinEdge + lWinEdge) / Math.PI
						+ r * r / Math.PI;
				}
				else {
					final double ratio = r / sWinEdge;
					isoCov = sWinEdge
						* lWinEdge
						* (2 * Math.asin(1 / ratio)
							- sWinEdge / lWinEdge
							- 2 * (ratio - Math.sqrt(ratio * ratio - 1)))
						/ Math.PI;
				}

				// estimate the surface-adapted intensity
				final double[] adaptedInt = new double[estimatorArr.length];
				for (int i = 0, k = 0; i < estimatorArr.length; i++) {
					double sum = 0;
					for (int j = 0; j < estimatorArr[i]; j++) {
						sum += edgeCorrFactors[0][k + j];
					}
					adaptedInt[i] = sum / isoCov;
					k += estimatorArr[i];
				}

				// calculate results
				 setI(adaptedInt, isNan(adaptedInt), 0);
				final double denor = Math.pow(sum(adaptedInt), 2);

				gsthatsAdapInt[x] = multiply(numor, 1 / denor);
				gsthatsClaInt[x] = divide(numor, classicIntensity);
			}

		});

		 final List<double[][][]> result = new ArrayList<>();
		 result.add(xyzToZxy(gsthatsAdapInt));
		 result.add(xyzToZxy(gsthatsClaInt));
		return result;
	}

	/**
	 * Calculate the sum of the specified sub-matrices of a given matrix input
	 * with a summed area table (the matrix analog of a cumulative sum). The
	 * summed area table should have a symmetric sub-matrix structure.
	 * 
	 * @param matrix
	 *        The matrix of summed area table, the matrix could contain NaN
	 *        values
	 * @param splitArr
	 *        The split of elements in each dimension of the full matrix
	 * @return The sum of specified sub-matrices
	 */
	public static double[][] sumSubMatrices(
			final double[][] matrix,
			final int[] splitArr) {

		// cumulative sum the summed area table by column, then by rows
		final double[][] nonNanMat = setI(copy(matrix), isNan(matrix), 0);
		final double[][] cumColMat = new double[nonNanMat.length][];

		for (int i = 0; i < nonNanMat.length; i++) {
			cumColMat[i] = cumSum(nonNanMat[i]).clone();
		}

		final double[][] transMat = transpose(cumColMat);
		final double[][] cumTransMat = new double[transMat.length][];

		for (int j = 0; j < transMat.length; j++) {
			cumTransMat[j] = cumSum(transMat[j]).clone();
		}

		final double[][] cumRowMat = transpose(cumTransMat);

		// cumulative sum the searchDist
		final int[] cumDist = cumSum(splitArr);
		final List<Integer> nonZeroDist = new ArrayList<>();

		for (int element : cumDist) {
			if (element != 0)
				nonZeroDist.add(element);
		}

		final int nZero = cumDist.length - nonZeroDist.size();

		// sum the specified matrices
		final double[][] result = new double[cumDist.length][cumDist.length];

		for (int i = 0; i < nonZeroDist.size(); i++) {
			for (int j = 0; j < nonZeroDist.size(); j++) {

				// get factor A
				final int ax = nonZeroDist.get(i) - 1;
				final int ay = nonZeroDist.get(j) - 1;
				final double factorA = cumRowMat[ay][ax];

				// get factor B
				final double factorB;
				if (i > 0 && j > 0) {
					final int bx = nonZeroDist.get(i - 1) - 1;
					final int by = nonZeroDist.get(j - 1) - 1;
					factorB = cumRowMat[by][bx];
				}
				else {
					factorB = 0;
				}

				// get factor C
				final double factorC;
				if (j > 0) {
					final int cx = nonZeroDist.get(i) - 1;
					final int cy = nonZeroDist.get(j - 1) - 1;
					factorC = cumRowMat[cy][cx];
				}
				else {
					factorC = 0;
				}

				// get factor D
				final double factorD;
				if (i > 0) {
					final int dx = nonZeroDist.get(i - 1) - 1;
					final int dy = nonZeroDist.get(j) - 1;
					factorD = cumRowMat[dy][dx];
				}
				else {
					factorD = 0;
				}

				// sum A, B, C, D factors
				final double factor = factorA + factorB - factorC - factorD;
				result[j + nZero][i + nZero] = factor > EPS ? factor : 0;
			}
		}

		return result;
	}

	/**
	 * Calculate two matrices containing different disk-based edge correction
	 * factors for a point pattern in a rectangular observation window. The
	 * elements of each out array contain the proportion of the boundary/area of
	 * a disk, centered at a given point of a particular radius that intersects
	 * with the observation window.
	 * 
	 * @param pattern
	 *        The point pattern of study
	 * @param vetX
	 *        The x coordinates of the vertices of the observation window.
	 * @param vetY
	 *        The y coordinates of the vertices of the observation window.
	 * @param r
	 *        The fixed distance value to compare with the distance between a
	 *        point and the edges or corners of a observation window.
	 * @return A matrix of 2 rows. The first row gives the proportion of the
	 *         circumference of a disk centered at a given focal point,
	 *         potentially with varying radii. The second row gives the
	 *         proportion of the area of a disk centered at a given focal point,
	 *         potentially with varying radii. Each column in the matrix
	 *         corresponds to a distinct focal point.
	 */
	public static double[][] getEdgeCorrelationFactors(
			final double[][] pattern,
			final double[] vetX,
			final double[] vetY,
			final double r) {
		/*
		 * calculate the distance between the point and the left, right, bottom,
		 * top of the window
		 */
		final double[] leftDist = add(getCol(pattern, 2), -vetX[0]);
		final double[] rightDist
			= add(multiply(getCol(pattern, 2), -1), vetX[1]);
		final double[] bottomDist = add(getCol(pattern, 3), -vetY[0]);
		final double[] topDist = add(multiply(getCol(pattern, 3), -1), vetY[3]);

		// calculate the result
		final double[][] result = new double[4][pattern.length];

		for (int i = 0; i < pattern.length; i++) {

			// assume the point is fully contained within the window
			double boundOverlap = 2 * Math.PI * r;
			double areaOverlap = Math.PI * r * r;

			// distance between the point of the edges of the window
			final double left = leftDist[i];
			final double right = rightDist[i];
			final double bottom = bottomDist[i];
			final double top = topDist[i];

			/*
			 * If the distance between the point and the left edge is within r
			 * but is further than r to the bottom left corner or top left
			 * corner, subtract the section corresponding to the subtended angle
			 * and then add on the corresponding triangle section.
			 */
			if (left <= r) {
				final double thetaL = Math.acos(left / r);

				if (left * left + bottom * bottom > r * r) {
					boundOverlap -= thetaL * r;
					areaOverlap -= thetaL * r * r / 2
						- left * r * Math.sin(thetaL) / 2;
				}

				if (left * left + top * top > r * r) {
					boundOverlap -= thetaL * r;
					areaOverlap -= thetaL * r * r / 2
						- left * r * Math.sin(thetaL) / 2;
				}
			}

			/*
			 * The distance between the point and the right edge is within r,
			 * but those to the bottom right corner and the top right corner are
			 * not.
			 */
			if (right <= r) {
				final double thetaR = Math.acos(right / r);

				if (right * right + bottom * bottom > r * r) {
					boundOverlap -= thetaR * r;
					areaOverlap -= thetaR * r * r / 2
						- right * r * Math.sin(thetaR) / 2;
				}

				if (right * right + top * top > r * r) {
					boundOverlap -= thetaR * r;
					areaOverlap -= thetaR * r * r / 2
						- right * r * Math.sin(thetaR) / 2;
				}
			}

			/*
			 * The distance between the point and the bottom edge is within r,
			 * but those to the bottom left corner and the bottom right corner
			 * are not.
			 */
			if (bottom <= r) {
				final double thetaB = Math.acos(bottom / r);

				if (left * left + bottom * bottom > r * r) {
					boundOverlap -= thetaB * r;
					areaOverlap -= thetaB * r * r / 2
						- bottom * r * Math.sin(thetaB) / 2;
				}

				if (right * right + bottom * bottom > r * r) {
					boundOverlap -= thetaB * r;
					areaOverlap -= thetaB * r * r / 2
						- bottom * r * Math.sin(thetaB) / 2;
				}
			}

			/*
			 * The distance between the point and the top edge is within r, but
			 * those to the top left corner and the top right corner are not.
			 */
			if (top <= r) {
				final double thetaT = Math.acos(top / r);

				if (left * left + top * top > r * r) {
					boundOverlap -= thetaT * r;
					areaOverlap -= thetaT * r * r / 2
						- top * r * Math.sin(thetaT) / 2;
				}

				if (right * right + top * top > r * r) {
					boundOverlap -= thetaT * r;
					areaOverlap -= thetaT * r * r / 2
						- top * r * Math.sin(thetaT) / 2;
				}
			}

			/*
			 * If the point is within r of one of the corners, a quarter of the
			 * boundary of the r-disk is removed, and similarly a quarter of the
			 * area of the r-disk is removed. Then, the corresponding square
			 * contained within the intersect of the quarter-circle and the
			 * window is add to the area.
			 */
			if (left * left + bottom * bottom <= r * r) {
				boundOverlap -= Math.PI * r / 2;
				areaOverlap -= Math.PI * r * r / 4 - left * bottom;
			}

			if (left * left + top * top <= r * r) {
				boundOverlap -= Math.PI * r / 2;
				areaOverlap -= Math.PI * r * r / 4 - left * top;
			}

			if (right * right + bottom * bottom <= r * r) {
				boundOverlap -= Math.PI * r / 2;
				areaOverlap -= Math.PI * r * r / 4 - right * bottom;
			}

			if (right * right + top * top <= r * r) {
				boundOverlap -= Math.PI * r / 2;
				areaOverlap -= Math.PI * r * r / 4 - right * top;
			}

			result[0][i] = boundOverlap / (2 * Math.PI * r);
			result[1][i] = areaOverlap / (Math.PI * r * r);
		}

		return result;
	}

	/**
	 * Sort the matrix by the values in a column
	 * 
	 * @param x
	 *        The input matrix
	 * 
	 * @param index
	 *        The index of the column to be sorted
	 * 
	 * @return The sorted matrix
	 * 
	 */
	public static double[][] sortRows(final double[][] x, final int index) {

		// get sorted indices
		final double[] col = getCol(x, index);
		final int[] sortedIndices = MIAUtil.getSortOrder(col);

		// re-arrange rows in the matrix
		final double[][] result = new double[x.length][];

		for (int i = 0; i < x.length; i++) {
			final int j = sortedIndices[i];
			result[i] = x[j].clone();
		}

		return result;
	}

	/** The type of the kernel */
	public static enum KernelType {

		/** Box kernel */
		BOX,

		/** Epanechnikov kernel */
		EPAN;
	}
}
/**
 * REVISION HISTORY:
 * 
 * 2017-07-31, 0.1, Bo Wang: Created.
 * 
 */
