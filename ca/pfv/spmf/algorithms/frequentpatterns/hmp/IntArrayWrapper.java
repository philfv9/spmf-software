package ca.pfv.spmf.algorithms.frequentpatterns.hmp;
import java.util.Arrays;

public class IntArrayWrapper {
    private final int[] array;

    public IntArrayWrapper(int[] array) {
        this.array = array;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntArrayWrapper that = (IntArrayWrapper) o;
        return Arrays.equals(array, that.array);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(array);
    }

    public int[] getArray() {
        return array;
    }
}