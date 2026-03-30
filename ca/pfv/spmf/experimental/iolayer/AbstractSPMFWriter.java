package ca.pfv.spmf.experimental.iolayer;

import java.io.IOException;

public abstract class AbstractSPMFWriter extends AbstractIO{

	public AbstractSPMFWriter(IOContext context) {
		super(context);
	}

	public abstract void write(String string) throws IOException;

	public abstract void newLine() throws IOException;

}