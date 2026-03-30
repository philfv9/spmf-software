package ca.pfv.spmf.tools.documentation_downloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This is a tool to download an offline copy of the SPMF documentation.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class AlgoSPMFDownloadDoc {

	/** The URLs that have been already downloaded */
	Set<String> alreadyDownloaded;

	/** Method to run this algorithm
	 */
	public void runAlgorithm() {
		alreadyDownloaded = new HashSet<String>();
		String mainUrl = "https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php";
		String folderPath = "doc";
		createDirectory(folderPath);
		savePage(mainUrl, folderPath + "/documentation.html", mainUrl);

		BufferedReader br = null;
		try {
			// Download the main documentation page
			URL url = new URI(mainUrl).toURL();
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;
			StringBuilder content = new StringBuilder();
			while ((inputLine = br.readLine()) != null) {
				content.append(inputLine);
				content.append(System.lineSeparator());
			}

			// Replace all .php references with .html in the content
			String updatedContent = content.toString().replaceAll("\\.php", ".html");

			// Save CSS files
			Pattern cssPattern = Pattern.compile("href=\"(.*?\\.css)\"");
			Matcher cssMatcher = cssPattern.matcher(updatedContent);
			while (cssMatcher.find()) {
				String cssLink = cssMatcher.group(1);
				savePage(cssLink, folderPath + "/" + cssLink.substring(cssLink.lastIndexOf('/') + 1), mainUrl);
			}

			// Save pages and images that start with "Example"
			Pattern examplePattern = Pattern.compile("<a href=\"([^\"]+)\">Example");
			Matcher exampleMatcher = examplePattern.matcher(updatedContent);
			while (exampleMatcher.find()) {
				String exampleLink = exampleMatcher.group(1);
				savePage(exampleLink, folderPath + "/" + exampleLink, mainUrl);

			}

		} catch (MalformedURLException e) {
			System.err.println("The URL provided is not valid: " + mainUrl);
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("An I/O error occurred while processing the URL: " + mainUrl);
			e.printStackTrace();
		} catch (URISyntaxException e) {
			System.err.println("The URL provided does not follow proper syntax: " + mainUrl);
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					System.err.println("An error occurred while closing the BufferedReader.");
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Method to create a folder
	 * @param folderPath the path
	 */
	private void createDirectory(String folderPath) {
		try {
			Files.createDirectories(Paths.get(folderPath));
		} catch (IOException e) {
			System.err.println("An error occurred while creating the directory: " + folderPath);
			e.printStackTrace();
		}
	}

	/**
	 * Method to save a webpage
	 * @param urlString the url
	 * @param filePath the filepath where it should be saved
	 * @param baseUri the base URI
	 */
	private void savePage(String urlString, String filePath, String baseUri) {
		if (alreadyDownloaded.contains(urlString)) {
			return;
		}
		alreadyDownloaded.add(urlString);

		BufferedReader reader = null;
		try {
			URL url;
			// Check if the URL is absolute or relative
			if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
				url = new URI(urlString).toURL();
			} else {
				// Convert relative URL to absolute URL
				URI base = new URI(baseUri);
				url = base.resolve(urlString).toURL();
			}

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder contentBuilder = new StringBuilder();
			String inputLine;
			while ((inputLine = reader.readLine()) != null) {
				contentBuilder.append(inputLine);
				contentBuilder.append(System.lineSeparator());
			}

			// Change the file extension from .php to .html
			filePath = filePath.replace(".php", ".html");

			// Update links in the content
			String content = contentBuilder.toString();
			content = content.replaceAll("href=\"([^\"]+).php\"", "href=\"$1.html\"");
			content = content.replaceAll("https://www.philippe-fournier-viger.com/spmf/index.php\\?link=documentation\\.html", "documentation.html");

	        // Find and save images
	        Pattern imgPattern = Pattern.compile("src=\"([^\"]+\\.(png|jpg))\"");
	        Matcher imgMatcher = imgPattern.matcher(content);
	        while (imgMatcher.find()) {
	            String imgLink = imgMatcher.group(1);
	            String imgName = imgLink.substring(imgLink.lastIndexOf('/') + 1);
	            saveImage(imgLink, "doc/" + imgName, baseUri);
	        }
	        
			// Save the updated content to file
			Files.write(Paths.get(filePath), content.getBytes(StandardCharsets.UTF_8));
		} catch (URISyntaxException e) {
			System.err.println("The URI provided is not valid: " + urlString);
			e.printStackTrace();
		} catch (MalformedURLException e) {
			System.err.println("A malformed URL has occurred for the URI: " + urlString);
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("An I/O error occurred while saving the page: " + urlString);
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					System.err.println("An error occurred while closing the BufferedReader.");
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Method to save an image
	 * @param urlString the url
	 * @param filePath the filepath where it should be saved
	 * @param baseUri the base URI
	 */
	private void saveImage(String urlString, String filePath, String baseUri) {
		if (alreadyDownloaded.contains(urlString)) {
			return;
		}
		alreadyDownloaded.add(urlString);
		
	    InputStream in = null;
	    try {
	        URL url;
	        // Check if the URL is absolute or relative
	        if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
	            url = new URI(urlString).toURL();
	        } else {
	            // Convert relative URL to absolute URL
	            URI base = new URI(baseUri);
	            url = base.resolve(urlString).toURL();
	        }
	        
	        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	        in = conn.getInputStream();
	        Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
	    } catch (URISyntaxException e) {
	        System.err.println("The URI provided is not valid: " + urlString);
	        e.printStackTrace();
	    } catch (MalformedURLException e) {
	        System.err.println("A malformed URL has occurred for the URI: " + urlString);
	        e.printStackTrace();
	    } catch (IOException e) {
	        System.err.println("An I/O error occurred while saving the image: " + urlString);
	        e.printStackTrace();
	    } finally {
	        if (in != null) {
	            try {
	                in.close();
	            } catch (IOException e) {
	                System.err.println("An error occurred while closing the InputStream.");
	                e.printStackTrace();
	            }
	        }
	    }
	}

}
