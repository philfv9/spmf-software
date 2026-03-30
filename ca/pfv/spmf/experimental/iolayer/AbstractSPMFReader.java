package ca.pfv.spmf.experimental.iolayer;

import java.io.IOException;

public abstract class AbstractSPMFReader extends AbstractIO {
	
	public AbstractSPMFReader(IOContext context) {
		super(context);
	}
	
	public abstract String readLine() throws IOException;
	
}