package ca.pfv.spmf.experimental.iolayer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class IOManager {

	private static IOManager instance = new IOManager();

	Map<Object, IOContext> mapObjectToIOContext = new HashMap<Object, IOContext>();

	final boolean DEBUG_MODE = true;

	private IOManager() {

	}

	public static IOManager getInstance() {
		return instance;
	}

	public AbstractSPMFReader getNewReader(Object o, String inputPath) throws IOException {
		IOContext context = getOrCreateContext(o);

		printDebugLine("===== IOManager: Get reader for context === " + o);

		return new SPMFTextFileReader(context, inputPath);
	}

	public AbstractSPMFWriter getNewWriter(Object o, String outputPath) throws IOException {
		IOContext context = getOrCreateContext(o);

		printDebugLine("===== IOManager: Get writer for context === " + o);

		return new SPMFTextFileWriter(context, outputPath);
	}

	public void releaseResources(Object o) {
		mapObjectToIOContext.remove(o);

		printDebugLine("===== IOManager: Release context === " + o);
	}

	private IOContext getOrCreateContext(Object o) {
		IOContext context = mapObjectToIOContext.get(o);
		if (context == null) {
			context = new IOContext();
			mapObjectToIOContext.put(o, context);

			printDebugLine("===== IOManager: Create context === " + o);
		}
		return context;
	}

	private void printDebugLine(String debugLine) {
		if (DEBUG_MODE) {
			System.out.println(debugLine);
		}
	}
	

}
