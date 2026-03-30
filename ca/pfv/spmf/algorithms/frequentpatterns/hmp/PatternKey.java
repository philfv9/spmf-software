package ca.pfv.spmf.algorithms.frequentpatterns.hmp;
import java.util.Arrays;

public class PatternKey {
    final int[] pattern;

    public PatternKey(int[] pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternKey that = (PatternKey) o;
        return Arrays.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pattern);
    }
}