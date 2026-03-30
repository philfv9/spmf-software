package ca.pfv.spmf.experimental.iolayer;

import java.io.IOException;

public abstract class AbstractIO {

	protected IOContext context;

	public AbstractIO(IOContext context) {
		super();
		this.context = context;
	}

	public void close() throws IOException {
		doClose();
	}

	protected abstract void doClose() throws IOException;

}