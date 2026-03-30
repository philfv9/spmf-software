package ca.pfv.spmf.datastructures.matrix;

/**
 * A class for testing the IntMatrix class.
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestIntMatrix {

	public static void main(String[] args) {
		System.out.println("(1) Create a first matrix:");
		IntMatrix matrix = new IntMatrix(2, 2);
		matrix.setValue(0, 0, 3);
		matrix.setValue(0, 1, 4);
		matrix.setValue(1, 0, 2);
		matrix.setValue(1, 1, 1);
		matrix.printMatrix();
		System.out.println("number of rows : " + matrix.getRowCount());
		System.out.println("number of columns : " + matrix.getColumnCount());

		System.out.println("(2) Create a second matrix:");
		IntMatrix matrix2 = new IntMatrix(2, 2);
		matrix2.setValue(0, 0, 1);
		matrix2.setValue(0, 1, 5);
		matrix2.setValue(1, 0, 3);
		matrix2.setValue(1, 1, 7);
		matrix2.printMatrix();

		IntMatrix product = matrix.multiply(matrix2);
		System.out.println("(3) The matrix multiplication of the two matrices gives this:");
		product.printMatrix();
		System.out.println("And the expected result is:");
		System.out.println("15	43	" + System.lineSeparator() + "5	17");
		
		IntMatrix add = matrix.add(matrix2);
		System.out.println("(4) The addition of the first and second matrix gives this result:");
		add.printMatrix();
		
		IntMatrix sub = matrix.subtract(matrix2);
		System.out.println("(5) Subtracting the second matrix from the first one gives this result:");
		sub.printMatrix();

		System.out.println("(6) Create a third matrix:");
		IntMatrix matrix3 = new IntMatrix(2, 3);
		matrix3.setValue(0, 0, 1);
		matrix3.setValue(0, 1, 2);
		matrix3.setValue(0, 2, 3);
		matrix3.setValue(1, 0, 4);
		matrix3.setValue(1, 1, 5);
		matrix3.setValue(1, 2, 6);
		matrix3.printMatrix();

		System.out.println("(7) Create a fourth matrix:");
		IntMatrix matrix4 = new IntMatrix(3, 2);
		matrix4.setValue(0, 0, 7);
		matrix4.setValue(0, 1, 8);
		matrix4.setValue(1, 0, 9);
		matrix4.setValue(1, 1, 10);
		matrix4.setValue(2, 0, 11);
		matrix4.setValue(2, 1, 12);
		matrix4.printMatrix();

		IntMatrix dotproduct = matrix3.dotProduct(matrix4);
		System.out.println("(8) The dot product  of the third and fourth matrices gives this:" );
		dotproduct.printMatrix();
		System.out.println("And the expected result is:" + System.lineSeparator() + "(58 64) "+ System.lineSeparator() + "(139 154) ");
		
		System.out.println("The transpose of the fourth matrix is:");
		matrix4.transpose().printMatrix();
	}
}
