package ca.pfv.spmf.experimental.datastructures.cache;

public class MainTestStringToIntegerCache {
	public static void main(String[] args) {
		Integer x =  Integer.valueOf("1000").hashCode();
		Integer y =  Integer.valueOf("1000").hashCode();
		Integer z =  Integer.valueOf("1000").hashCode();
		System.out.println(x == y);
		System.out.println(x == z);
		System.out.println(y == z);
		
		System.out.println("=========================");
		Integer xx1 =  1000;
		Integer xx2 =  1000;
		Integer xx3 =  1000;
		System.out.println(xx1 == xx2);
		System.out.println(xx1 == xx3);
		System.out.println(xx2 == xx3);
		
		System.out.println("=========================");
		StringToIntegerCache pool = new StringToIntegerCache(1000);
		Integer x1 =  pool.getInteger("1000");
		Integer x2 =  pool.getInteger("1000");
		Integer x3 =  pool.getInteger("1000");
		System.out.println(x1 == x2);
		System.out.println(x1 == x3);
		System.out.println(x2 == x3);
	}

}
