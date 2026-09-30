package ca.pfv.spmf.tools.dataset_converter;

/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* 
* Do not remove the copyright and license information.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
* This class is for converting sequence databases from various formats
* to the SPMF format.
* 
* @see Formats
* @author Philippe Fournier-Viger
*/
public class SequenceDatabaseConverter {
	
	String input;  // the path of the input file
	String output; // the path of the file to be written to disk in SPMF format
	int lineCount =0; // the number of sequences in the input file
	BufferedWriter writer; // to write the output file

	/**
	 * This method converts a sequence database from a given format to the SPMF format.
	 * @param input  the path of the input file
	 * @param charset the charset (encoding) of the input file if it is a text file (e.g "UTF-8")
	 *   or null if you want to use the default text encoding or if it is not a text file.
	 * @param output the path of the file to be written to disk in SPMF format
	 * @param inputFileformat  the format of the input file
	 * @param lineCount  the number of lines from the input file that should be converted
	 * @throws IOException  an exception is thrown if there is an error reading/writing files
	 */
	public void convert(String input, String output, Formats inputFileformat, int lineCount, Charset charset) throws IOException {

	    // we save the parameter in the class fields
	    this.input = input;
	    this.output = output;
	    this.lineCount = lineCount;

	    // Use the default charset if it is null
	    if (charset == null) {
	        charset = Charset.defaultCharset();
	    }

	    if (inputFileformat.equals(Formats.TEXT)) {
	        convertTEXT(false, charset);
	        return;
	    }

	    // For all other formats, we create an object for writing the output file
	    writer = new BufferedWriter(new FileWriter(output));
		writer.write("@FILETYPE=\"Simple sequence database\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

	    // we call the appropriate method for converting a database
	    // according to the format of the input file
	    if (inputFileformat.equals(Formats.IBMGenerator)) {
	        convertIBMGenerator();
	    } else if (inputFileformat.equals(Formats.Kosarak)) {
	        convertKosarak(charset);
	    } else if (inputFileformat.equals(Formats.CSV_INTEGER)) {
	        convertCSV(charset);
	    } else if (inputFileformat.equals(Formats.BMS)) {
	        convertBMS(charset);
	    } else if (inputFileformat.equals(Formats.Snake)) {
	        convertSnake(charset);
	    } else if (inputFileformat.equals(Formats.SPMF_TRANSACTION_DB)) {
	        convertTransactionDB(charset);
	    }

	    // we close the output file
	    writer.close();
	}
	
	/**
	 * This method convert a transaction database in SPMF format to a sequence database in SPMF format
	 * @param charset the charset (encoding) of the input file  (e.g "UTF-8")
	 *   or null if you want to use the default text encoding.
	 */
	private void convertTransactionDB(Charset charset) {
		String thisLine; // variable to read a line
		BufferedReader myInput = null;  
		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin,charset));
			
			int count =0;  // to count the number of line
			
			// we read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata, skip it
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || 
						thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}
				
				// we split the line according to spaces
				String[] split = thisLine.split(" ");
				// for each item on this line
				for(String itemString : split) {
					int item = Integer.parseInt(itemString);
					// we write the item with an itemset separator
					writer.write(item + " -1 "); 
				}
				// we write the end of the line
				writer.write("-2");
				count++; // we increase the number of line that was read until now
				
				// if we have read enough lines, we stop.
				if(count == lineCount){
					break;
				}
				// start a new line
				writer.newLine();
			}
			myInput.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	/**
	 * This method convert a file from the SNAKE format to SPMF format
	 */
	private void convertSnake(Charset charset) {
		String thisLine; // variable to read a line
		BufferedReader myInput = null;  
		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin, charset));
			
			int count =0;  // to count the number of line
			
			// we read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line contains more than 11 elements
				// (we use this to filter smaller lines)
				if(thisLine.length() >= 11){   
					// for each integer on this line, we consider that it is an item
					for(int i=0; i< thisLine.length(); i++){
						// we subtract 65 to get the item number and
						// write the item to the file
						int character = thisLine.toCharArray()[i] - 65;
						// we write an itemset separator
						writer.write(character + " -1 ");   
					}
					// we write the end of the line
					writer.write("-2");
				}
				count++; // we increase the number of line that was read until now
				
				// if we have read enough lines, we stop.
				if(count == lineCount){
					break;
				}
				// start a new line
				writer.newLine();
			}
			myInput.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	/**
	 * This method convert a file from the BMS format to SPMF format
	 * @param charset the charset (encoding) of the input file  (e.g "UTF-8")
	 *   or null if you want to use the default text encoding.
	 */
	private void convertBMS(Charset charset) {
		String thisLine; // variable to read a line
		BufferedReader myInput = null;
		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin, charset));
			
			// In the BMS format, the sequencs of webpage of a user
			// is separated on several lines.
			// We use this variable to remember the id of the current user 
			// that we are reading.
			int lastId = 0; 
			
			int count = 0; // to count the number of line
			
			// we read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {

				// we split the line according to spaces
				String[] split = thisLine.split(" ");
				
				// each line is a user id with a webpage
				int id = Integer.parseInt(split[0]); // id of the user on this line
				int val = Integer.parseInt(split[1]); // webpage viewed by this user
				
				// if the id of the current user is not the same as the previous line
				if(lastId != id){
					// and it is not the first line
					if(lastId!=0 ){ 
						count++; // increase sequence count
						
						// write the end of line
						writer.write("-2");
						writer.newLine();
					}
					lastId = id; // remember the current user id for this line  so that we know it for next line
				}
				// if we have read enough sequences, we stop.
				if(count == lineCount){
					break;
				}
				// after each line we write an itemset separator "-1"
				writer.write(val + " -1 ");   // WRITE
			}
			myInput.close();
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	/**
	 * This method convert a file from the CSV format to SPMF format
	 * @param charset the charset (encoding) of the input file  (e.g "UTF-8")
	 *   or null if you want to use the default text encoding.
	 */
	private void convertCSV(Charset charset) throws IOException {
		String thisLine; // variable to read a line
		BufferedReader myInput = null;
		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin, charset));
			
			int count = 0; // to count the number of line
			
			// we read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				
				// we split the line according to spaces
				String[] split = thisLine.split(",");
				// for each value
				for (String value : split) {
					// we convert to integer and write the item
					Integer item = Integer.parseInt(value);
					writer.write(item + " -1 ");   // write an itemset separator
				}
				writer.write("-2");    // write end of line
				
				count++; // increase the number of sequences
				// if we have read enough sequences, we stop.
				if(count == lineCount){
					break;
				}
				writer.newLine(); // create new line
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
	}

	/**
	 * This method convert a file from the KOSARAK format to SPMF format
	 * @param charset the charset (encoding) of the input file if it is a text file (e.g "UTF-8")
	 *   or null if you want to use the default text encoding or if it is not a text file.
	 */
	private void convertKosarak(Charset charset) throws IOException {
		String thisLine; // variable to read a line
		BufferedReader myInput = null;
		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin, charset));
			
			int count = 0; // to count the number of line
			
			// we read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {
				// we split the line according to spaces
				String[] split = thisLine.split(" ");
				// for each string on this line
				for (String value : split) {
					// we convert to integer and write it to file (it is an item)
					Integer item = Integer.parseInt(value);
					writer.write(item + " -1 ");   // write an itemset separator
				}
				writer.write("-2");  // write end of line
				
				count++;// increase the number of sequences
				// if we have read enough sequences, we stop.
				if(count == lineCount){
					break;
				}
				writer.newLine(); // create new line
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
	}

	/**
	 * This method convert a file from the IBM GENERATOR format to SPMF format
	 * 
	 */
	private void convertIBMGenerator() {
		DataInputStream myInput = null;
		try {
			// Objects to read the input file in binary format
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new DataInputStream(fin);
			
			// Variable to remember if we have written -1 after a group of items or not
			// (because in the binary format, at the end of a line there is no -1 before the -2
			//  but in spmf format there is one).
			boolean lastMinus1 = false; 
			
			int count = 0; // to count the number of line
			
			// we read the file integer by integer until the end of the file
			while (myInput.available() != 0) {
				// we read the first 32 bits and convert to big indian
				int value = INT_little_endian_TO_big_endian(myInput.readInt());
				// if it is "-1", the end of an itemset
				if (value == -1) { 
					// we write the same thing as output
					writer.write("-1 "); 
					lastMinus1 = true; // to remember that we have written -1
				} 
				// if it is "-2", the end of a sequence
				else if (value == -2) { 
					// check if the last "-1" was not written
					if (lastMinus1 == false) {
						writer.write("-1 "); // write "-1"
					}
					writer.write("-2 "); // write end of line
					
					count++;// increase the number of sequences
					
					// if we have read enough sequences, we stop.
					if(count == lineCount){
						break;
					}
					writer.newLine(); // create new line
				}
				// else it is an item
				else {
					// we write the item
					writer.write(value + " ");  
					
					lastMinus1 = false; // to remember that we need to write a -1
				}
			}
			myInput.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 *  This method converts integer values from little indian to big endian
	 * @param i  an integer in little indian
	 * @return  the integer converted to big indian
	 */
	int INT_little_endian_TO_big_endian(int i) {
		return ((i & 0xff) << 24) + ((i & 0xff00) << 8) + ((i & 0xff0000) >> 8)
				+ ((i >> 24) & 0xff);
	}
	
	
	/**
	 * This method convert a text file to a sequence database in SPMF format and
	 * return a map of key = item id  value = corresponding attribute value. This 
	 * method is to be used by the GUI version of SPMF that need to keep the mapping
	 * between item IDs and attribute value in memory to avoid an extra database scan.
	 * @param inputFile the path of the file to be converted
	 * @param outputFile the path for saving the converted file
	 * @param lineCount the number of lines of the input file to be converted
	 * @param charset the charset (encoding) of the input file if it is a text file (e.g "UTF-8")
	 *   or null if you want to use the default text encoding or if it is not a text file.
	 * @return a map of entry (key : itemID, value: attribute-value) if the input format is TEXT
	 * @throws IOException  if an error while reading/writing files
	 */
	public Map<Integer, String> convertTEXTandReturnMap(String inputFile, String outputFile,
			int lineCount, Charset charset) throws IOException {
		// we save the parameter in the class fields
		this.input = inputFile;
		this.output = outputFile;
		this.lineCount = lineCount;
		return convertTEXT(true, charset);
	}
	
	
	/**
	 * This method convert a file from the TEXT format to the SPMF format.
	 * 
	 * @param returnMapItemIDWord
	 * @param charset the charset (encoding) of the input file (e.g "UTF-8")
	 *   or null if you want to use the default text encoding.
	 * @throws IOException  exception if error while reading/writing files.
	 * @return a map where an entry indicates for an item (key), the corresponding attribute value (value).
	 */
	private Map<Integer, String> convertTEXT(boolean returnMapItemIDWord, Charset charset) throws IOException {
	    // Map from item id (key) to word (value). Used only if returnMapItemIDWord = true.
	    Map<Integer, String> mapItemsIDToWords = null;
	    if (returnMapItemIDWord) {
	        mapItemsIDToWords = new HashMap<Integer, String>();
	    }

	    // Map from word (key) to item id (value)
	    Map<String, Integer> mapWordsToItemIDs = new HashMap<String, Integer>();

	    // Writer for the output SPMF file
	    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), charset));
	    writer.write("@CONVERTED_FROM_TEXT");
	    writer.newLine();
		writer.write("@FILETYPE=\"Simple sequence database\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

	    BufferedReader myInput = null;
	    try {
	        FileInputStream fin = new FileInputStream(new File(input));
	        myInput = new BufferedReader(new InputStreamReader(fin, charset));

	        // ------------------------------------------------------------------
	        // STEP 1: Read the ENTIRE file into one big string.
	        // We join lines with a space so that a sentence split across
	        // two lines is still handled correctly.
	        // ------------------------------------------------------------------
	        StringBuilder fullText = new StringBuilder();
	        String thisLine;
	        while ((thisLine = myInput.readLine()) != null) {
	            if (thisLine.length() > 0) {
	                fullText.append(thisLine).append(' ');
	            }
	        }
	        myInput.close();
	        myInput = null;

	        String text = fullText.toString();

	        // ------------------------------------------------------------------
	        // STEP 2: Normalize punctuation that is often glued to words
	        // (dashes, curly quotes, straight quotes) by surrounding them
	        // with spaces. This avoids tokens like "gold?"-holding" hiding
	        // the sentence terminator or merging two different words.
	        // ------------------------------------------------------------------
	        text = text.replaceAll("([\u2013\u2014\u201C\u201D\u2018\u2019\"])", " $1 ");

	        // ------------------------------------------------------------------
	        // STEP 3: Split the WHOLE text into sentences.
	        // A sentence boundary is: a terminator (. ! ? or Chinese equivalents),
	        // optionally followed by closing quotes/brackets, followed by
	        // one or more whitespace characters.
	        // The look-behind (?<=...) keeps the terminator inside the sentence
	        // that is produced.
	        // ------------------------------------------------------------------
	        String[] sentences = text.split(
	            "(?<=[.!?\u3002\uFF01\uFF1F])[\"'\u201C\u201D\u2018\u2019\\)\\]]*\\s+");

	        int nextItemID = 1;
	        int sequenceCount = 0;

	        // ------------------------------------------------------------------
	        // STEP 4: For each sentence, split into words and write it
	        // as one SPMF sequence.
	        // ------------------------------------------------------------------
	        for (String sentence : sentences) {

	            if (sentence.trim().length() == 0) {
	                continue;
	            }

	            String[] words = sentence.trim().split("\\s+");

	            StringBuilder currentSentence = new StringBuilder();
	            boolean isFirstWordOfSentence = true;

	            for (String word : words) {
	                // Remove all characters that are not letters or digits
	                String cleanWord = word.replaceAll("[^\\p{L}\\p{N}]+", "").toLowerCase();

	                if (cleanWord.length() == 0) {
	                    continue; // this token was pure punctuation (a quote, a dash, etc.)
	                }

	                // Get or create the item id for this word
	                Integer item = mapWordsToItemIDs.get(cleanWord);
	                if (item == null) {
	                    item = nextItemID++;
	                    mapWordsToItemIDs.put(cleanWord, item);
	                    if (mapItemsIDToWords != null) {
	                        mapItemsIDToWords.put(item, cleanWord);
	                    }
	                    writer.write("@ITEM=" + item + "=" + cleanWord);
	                    writer.newLine();
	                }

	                if (isFirstWordOfSentence) {
	                    isFirstWordOfSentence = false;
	                } else {
	                    currentSentence.append(" ");
	                }
	                currentSentence.append(item);
	                currentSentence.append(" -1"); // itemset separator
	            }

	            // Only write a sequence if it actually contains at least one word
	            if (currentSentence.length() > 0) {
	                currentSentence.append(" -2"); // end of sequence
	                writer.write(currentSentence.toString());
	                writer.newLine();

	                sequenceCount++;
	                if (sequenceCount == lineCount) {
	                    break;
	                }
	            }
	        }

	        writer.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    } finally {
	        if (myInput != null) {
	            myInput.close();
	        }
	    }

	    return mapItemsIDToWords;
	}
}
