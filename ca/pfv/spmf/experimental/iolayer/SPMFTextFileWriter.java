package ca.pfv.spmf.experimental.iolayer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class SPMFTextFileWriter extends AbstractSPMFWriter {

	/** writer to write the output file **/
	protected BufferedWriter writer = null;
	

	SPMFTextFileWriter(IOContext context, String output) throws IOException {
		super(context);
		// create a writer object to write results to file
		writer = new BufferedWriter(new FileWriter(output));
	}


	protected void doClose() throws IOException {
		writer.close();
		System.out.println("writer close");
	}

	public void write(String string) throws IOException {
		writer.write(string);
	}

	public void newLine() throws IOException {
		writer.newLine();
	}

}
