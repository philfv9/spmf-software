package ca.pfv.spmf.datastructures.matrix;

/*
 * Copyright (c) 2023 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * A simple matrix class for storing integers
 * 
 * @author Philippe Fournier-Viger 2023
 */
class IntMatrix {
	/** Storage */
	private double[][] data;

	/** the number of rows */
	private int rowCount;
	/** the number of columns */
	private int columnCount;

	/**
	 * Constructor
	 * 
	 * @param rowCount    number of rows
	 * @param columnCount number of columns
	 */
	public IntMatrix(int rowCount, int columnCount) {
		this.data = new double[rowCount][columnCount];
		this.rowCount = rowCount;
		this.columnCount = columnCount;
	}

	/**
	 * Gets the number of rows
	 * 
	 * @return The number of rows
	 */
	public int getRowCount() {
		return this.rowCount;
	}

	/**
	 * Gets the number of columns
	 * 
	 * @return The number of columns
	 */
	public int getColumnCount() {
		return this.columnCount;
	}

	/**
	 * Get a value
	 * 
	 * @param row    the row index
	 * @param column the column index
	 * @return the value
	 */
	public double getValue(int row, int column) {
		return data[row][column];
	}

	/**
	 * Set a value in the matrix
	 * 
	 * @param row    the row index
	 * @param column the column index
	 * @return value the value
	 */
	public void setValue(int row, int column, double value) {
		data[row][column] = value;
	}

	/**
	 * Print the matrix to the console
	 */
	public void printMatrix() {
		for (int i = 0; i < data.length; i++) {
			for (int j = 0; j < data[i].length; j++) {
				System.out.print(data[i][j] + "\t");
			}
			System.out.println();
		}
	}

	/**
	 * Calculate the multiplication of this matrix with another matrix
	 * 
	 * @param matrix2 The other matrix
	 * @return The result
	 */
	public IntMatrix multiply(IntMatrix matrix2) {
		if (this.columnCount != matrix2.rowCount) {
			throw new IllegalArgumentException("Matrices cannot be multiplied");
		}
		IntMatrix result = new IntMatrix(this.rowCount, matrix2.columnCount);
		for (int i = 0; i < result.rowCount; i++) {
			for (int j = 0; j < result.columnCount; j++) {
				double sum = 0;
				for (int k = 0; k < this.columnCount; k++) {
					sum += this.data[i][k] * matrix2.data[k][j];
				}
				result.data[i][j] = sum;
			}
		}
		return result;
	}

	/**
	 * Calculates the dot product of this matrix and another matrix
	 * 
	 * @param matrix2 The other matrix
	 * @return The dot product of the pair of matrices
	 */
	public IntMatrix dotProduct(IntMatrix matrix2) {
		if (this.columnCount != matrix2.rowCount) {
			throw new IllegalArgumentException(
					"The number of columns in the first matrix must be the same as the number of rows in the second matrix");
		}
		IntMatrix product = new IntMatrix(this.rowCount, matrix2.columnCount);

		for (int i = 0; i < rowCount; i++) {
			for (int j = 0; j < matrix2.columnCount; j++) {
				double sum = 0;
				for (int k = 0; k < matrix2.rowCount; k++) {
					sum += this.data[i][k] * matrix2.data[k][j];
				}
				product.setValue(i, j, sum);
			}
		}
		return product;
	}

	/**
	 * Add this matrix to another matrix
	 * 
	 * @param matrix2 another matrix
	 * @return the result
	 */
	public IntMatrix add(IntMatrix matrix2) {
		IntMatrix result = new IntMatrix(this.rowCount, this.columnCount);
		for (int i = 0; i < this.rowCount; i++) {
			for (int j = 0; j < this.columnCount; j++) {
				result.data[i][j] = this.data[i][j] + matrix2.data[i][j];
			}
		}
		return result;
	}

	/**
	 * Subtract another matrix from this matrix
	 * 
	 * @param matrix2 another matrix
	 * @return the result
	 */
	public IntMatrix subtract(IntMatrix matrix2) {
		IntMatrix result = new IntMatrix(this.rowCount, this.columnCount);
		for (int i = 0; i < this.rowCount; i++) {
			for (int j = 0; j < this.columnCount; j++) {
				result.data[i][j] = this.data[i][j] - matrix2.data[i][j];
			}
		}
		return result;
	}

	/**
	 * Get the transpose of this matrix
	 * @return the transpose
	 */
	public IntMatrix transpose() { 
		IntMatrix result = new IntMatrix(columnCount, rowCount);
	
	    for (int i=0; i < rowCount; i++) {
	        for (int j=0; j < columnCount; j++) {
	            result.data[j][i] = this.data[i][j];
	        }
	    }
	    return result;
    }

}