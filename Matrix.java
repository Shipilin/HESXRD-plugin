/**
 *
 * @author Mikhail Shipilin
 */
public class Matrix {

    private int nrows;
    private int ncols;
    private double[][] data;
 
    public Matrix(double[][] dat) {
        this.data = dat;
        this.nrows = dat.length;
        this.ncols = dat[0].length;
    }

    public Matrix(int nrow, int ncol) {
        this.nrows = nrow;
        this.ncols = ncol;
        data = new double[nrow][ncol];
    }
    
    public int getNcols() {
        return this.ncols;
    }
        
    public int getNrows() {
        return this.nrows;
    }
    
    public boolean isSquare() {
        return this.ncols == this.nrows;
    }
    
    public double getValueAt(int i, int j) {
        return this.data[i][j];
    }
    
    public int size() {
        return (this.ncols > this.nrows) ? this.ncols : this.nrows;
    }
    
    public static int changeSign(int i) {
        if (i % 2 == 0)
            return 1;
        else
            return -1;
    }
    
    protected void setValueAt(int i, int j, double value) {
        this.data[i][j] = value;
    }
    
    public static Matrix transpose(Matrix matrix) {
        Matrix transposedMatrix = new Matrix(matrix.getNcols(), matrix.getNrows());
        for (int i=0;i<matrix.getNrows();i++) {
            for (int j=0;j<matrix.getNcols();j++) {
                transposedMatrix.setValueAt(j, i, matrix.getValueAt(i, j));
            }
        }
        return transposedMatrix;
    }
    
    public static double determinant(Matrix matrix) throws Exception {
        if (!matrix.isSquare())
            throw new Exception("Matrix needs to be square.");

        if (matrix.size()==2) {
            return (matrix.getValueAt(0, 0) * matrix.getValueAt(1, 1)) - ( matrix.getValueAt(0, 1) * matrix.getValueAt(1, 0));
        }
        double sum = 0.0;
        for (int i=0; i<matrix.getNcols(); i++) {
            sum += changeSign(i) * matrix.getValueAt(0, i) * determinant(createSubMatrix(matrix, 0, i));
        }
        return sum;
    }
    
    public static Matrix createSubMatrix(Matrix matrix, int excluding_row, int excluding_col) {
        Matrix mat = new Matrix(matrix.getNrows()-1, matrix.getNcols()-1);
        int r = -1;
        for (int i=0;i<matrix.getNrows();i++) {
            if (i==excluding_row)
                continue;
                r++;
                int c = -1;
            for (int j=0;j<matrix.getNcols();j++) {
                if (j==excluding_col)
                    continue;
                mat.setValueAt(r, ++c, matrix.getValueAt(i, j));
            }
        }
        return mat;
    } 
    
    public static Matrix cofactor(Matrix matrix) throws Exception {
        Matrix mat = new Matrix(matrix.getNrows(), matrix.getNcols());
        for (int i=0;i<matrix.getNrows();i++) {
            for (int j=0; j<matrix.getNcols();j++) {
                mat.setValueAt(i, j, changeSign(i) * changeSign(j) * determinant(createSubMatrix(matrix, i, j)));
            }
        }

        return mat;
    }
    
    public static Matrix inverse(Matrix matrix) throws Exception {
        return (transpose(cofactor(matrix)).multiplyByConstant(1.0/determinant(matrix)));
    }
    
    /**
     * 
     * @param matrixA
     * @param matrixB
     * @return matrixA - matrixB
     * @throws Exception 
     */
    public static Matrix subtract(Matrix matrixA, Matrix matrixB) throws Exception{
        try{
            checkMatrixDimensions(matrixA, matrixB, true);
        }
        catch(Exception e) {
            throw new Exception("Invalid matrix parameters");
        }
        Matrix mat = new Matrix(matrixA.getNrows(), matrixA.getNcols());
        for(int i = 0; i < matrixA.getNrows(); i++) { // A rows
            for(int j = 0; j < matrixA.getNcols(); j++) { // A columns
                mat.setValueAt(i, j, matrixA.getValueAt(i, j) - matrixB.getValueAt(i,j));
            }
        }
        return mat;
    }
    
    protected Matrix multiplyByConstant(double constant) {
        Matrix mat = new Matrix(this.getNrows(), this.getNcols());
        for (int i=0;i<this.getNrows();i++) {
            for (int j=0; j<this.getNcols();j++) {
                mat.setValueAt(i, j, this.getValueAt(i, j)*constant);
            }
        }
        return mat;
    }
    
    /**
     * Multiplies two matrices A and B
     * @return A*B
     */
    public static Matrix multiply(Matrix matrixA, Matrix matrixB) throws Exception{
        try{
            checkMatrixDimensions(matrixA, matrixB, false);
        }
        catch(Exception e) {
            throw new Exception("Invalid matrix parameters");
        }
        Matrix mat = new Matrix(matrixA.getNrows(), matrixB.getNcols());
        double value = 0;
        for(int i = 0; i < matrixA.getNrows(); i++) { // A rows
            for(int j = 0; j < matrixB.getNcols(); j++) { // B columns
                for(int k = 0; k < matrixA.getNcols(); k++) { // A columns
                     value += matrixA.getValueAt(i, k) * matrixB.getValueAt(k,j);
                }
                mat.setValueAt(i, j, value);
                value = 0;
            }
        }
        return mat;
    }
    
    /**
      * Left division, C = A\B
      * @return A\B (inv(A)*B)
      */
    public static Matrix leftDivide(Matrix matrixA, Matrix matrixB) throws Exception{
        return multiply(inverse(matrixA), matrixB);
    }

    /**
     * Checks if number of Acolumns = number of Brows (bothDimensions = false) and
     * checks if all dimensions are equal (bothDimensions = true)
     * @param matrixA
     * @param matrixB
     * @param bothDimensions
     * @throws Exception 
     */
    private static void checkMatrixDimensions(Matrix matrixA, Matrix matrixB, boolean bothDimensions) throws Exception{
        if(bothDimensions){
            if ((matrixA.getNcols() != matrixB.getNcols())||(matrixA.getNrows() != matrixB.getNrows())) {
                throw new Exception("Matrix dimensions must agree.");
            }
        }
        else{
            if (matrixA.getNcols() != matrixB.getNrows()) {
                throw new Exception("Matrix dimensions must agree.");
            }
        }
    }
}
