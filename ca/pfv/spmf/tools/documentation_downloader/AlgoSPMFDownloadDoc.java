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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

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
 *
 * Do not remove the copyright and license information.
 */

/**
 * This is a tool to download an offline copy of the SPMF documentation.
 *
 * <p><b>IMPORTANT FIX #1:</b> The original implementation extracted "Example"
 * page links (and CSS links) from a version of the page content where every
 * occurrence of {@code ".php"} had already been blindly replaced with
 * {@code ".html"}. This corrupted the <em>real</em> URLs (e.g. it turned
 * {@code EFIM.php} into {@code EFIM.html}) before they were used to make the
 * actual HTTP request, causing every "Example" page download to fail (404)
 * since the live server only serves {@code .php} files.</p>
 *
 * <p><b>IMPORTANT FIX #2 (algorithm pages are no longer scraped from the
 * documentation index page):</b> The original implementation tried to
 * discover every algorithm/tool documentation page by scanning the raw HTML
 * of the documentation index page for the word "Example" near an
 * {@code <a href="...">} tag. This was fragile: it depended entirely on the
 * exact wording/layout used on that page, and some entries - most notably
 * "Tools / Data Viewers" pages such as
 * {@code utility_transactions_view.php} - are not phrased as "Example N: ..."
 * at all, so they were silently skipped no matter how many link-layout
 * variants were added to the regex.
 * <br><br>
 * Instead, the list of algorithm/tool documentation pages is now obtained
 * directly from {@link AlgorithmManager#getAlgorithms()}, and each page is
 * downloaded from {@link DescriptionOfAlgorithm#getURLOfDocumentation()}.
 * This is the same URL used everywhere else in SPMF (e.g. the internal
 * documentation browser), so it is guaranteed to be correct and complete for
 * every registered algorithm, regardless of how its documentation page is
 * phrased or linked on the index page.</p>
 *
 * <p>The fix is to:
 * <ol>
 *   <li>Fetch each documentation page using the <b>real, unmodified</b>
 *       {@code .php} URL returned by {@code getURLOfDocumentation()}, so
 *       the actual network request always succeeds.</li>
 *   <li>Only rewrite {@code .php} → {@code .html} when (a) naming the local
 *       file that is saved to disk, and (b) rewriting internal links
 *       <em>inside</em> the saved HTML so that offline browsing works.</li>
 *   <li>Resolve relative/absolute links to a full URL before logging /
 *       requesting them, and extract just the file name (ignoring any
 *       domain or path) when building the local save path — this avoids
 *       building an invalid nested path such as
 *       {@code doc/http://www.example.com/EFIM.php}.</li>
 *   <li>Obtain the list of pages to download from
 *       {@link AlgorithmManager}, not from scraping the index page.</li>
 * </ol>
 * </p>
 *
 * <p><b>Resume / skip-existing support:</b> When enabled, the algorithm
 * checks whether the local file for a given resource (page, CSS, or image)
 * already exists on disk <em>before</em> performing any network I/O. If it
 * does, the resource is skipped entirely, which makes it possible to resume
 * an interrupted download without re-fetching everything from scratch.</p>
 *
 * <p><b>Debugging:</b> Set the (hidden) {@link #verbose} field to
 * {@code true} to print a detailed trace of every step of the algorithm to
 * the console (System.out), including every link discovered, every
 * download attempt, every skip decision, and a final summary. This is
 * useful to diagnose why a particular file was not downloaded. The field is
 * {@code private} on purpose - edit the source directly to toggle it.</p>
 *
 * <p><b>Per-algorithm results:</b> While processing the algorithm/tool
 * documentation pages obtained from {@link AlgorithmManager}, the algorithm
 * records one {@link AlgorithmDownloadResult} per page, capturing whether it
 * succeeded, failed, or was skipped, along with the local file path. After
 * {@link #runAlgorithm(String, DownloadProgressCallback, boolean)} returns,
 * call {@link #getAlgorithmResults()} to retrieve the full list, e.g. to
 * display it in a {@code JTable} for debugging purposes.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoSPMFDownloadDoc {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Entry-point URL for the SPMF documentation. */
    private static final String MAIN_URL =
            "https://philippe-fournier-viger.com/spmf/index.php?link=documentation.php";

    /** Default folder used by the no-argument overload. */
    private static final String DEFAULT_FOLDER = "doc";

    /**
     * Prefix added to the URL passed to {@link DownloadProgressCallback}
     * when a resource is skipped because it already exists locally (only
     * used when the skip-existing option is enabled). The GUI strips this
     * prefix to display a distinct "Skipping…" log message.
     */
    public static final String SKIPPED_PREFIX = "[SKIPPED] ";

    // ── Debugging ────────────────────────────────────────────────────────────

    /**
     * When {@code true}, a detailed trace of every step performed by this
     * class is printed to {@code System.out} (link discovery, downloads,
     * skips, errors, and a final summary). This is a hidden switch meant to
     * be toggled manually in the source code when debugging why a given
     * file was not downloaded. It is intentionally NOT exposed through the
     * public API / GUI.
     */
    private boolean verbose = true;

    // ── Per-run state ─────────────────────────────────────────────────────────

    /** The URLs that have been already downloaded */
    Set<String> alreadyDownloaded;

    /**
     * The active callback, or {@code null} when called from the no-arg overload.
     */
    private DownloadProgressCallback callback;

    /**
     * Running index of the resource being downloaded, used for the callback.
     */
    private int pageIndex;

    /**
     * When {@code true}, resources whose local file already exists are
     * skipped (no network I/O is performed for them).
     */
    private boolean skipExisting;

    /**
     * Details/message associated with the outcome of the most recent
     * {@link #savePage(String, String, String)} call (e.g. an error message
     * on failure, or a short note on success/skip). Used to populate
     * {@link AlgorithmDownloadResult#getDetails()}.
     */
    private String lastOperationMessage;

    /**
     * Collected results, one per algorithm/tool documentation page processed
     * during the run, in the order returned by
     * {@link AlgorithmManager#getAlgorithms()}. Exposed via
     * {@link #getAlgorithmResults()} so callers (e.g. the GUI) can display
     * a summary table after the download finishes.
     */
    private final List<AlgorithmDownloadResult> algorithmResults = new ArrayList<>();

    // ── Run statistics (only used for the verbose summary) ─────────────────────

    private int statPagesDownloaded;
    private int statPagesSkippedExisting;
    private int statPagesSkippedAlreadyProcessed;
    private int statImagesDownloaded;
    private int statImagesSkippedExisting;
    private int statImagesSkippedAlreadyProcessed;
    private int statCssDownloaded;
    private int statErrors;
    private int statAlgorithmPagesFound;

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry points
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs the algorithm with the default output folder and no progress
     * reporting. Preserved so that existing callers are unaffected.
     */
    public void runAlgorithm() {
        runAlgorithm(DEFAULT_FOLDER, null, false);
    }

    /**
     * Runs the algorithm, saving pages to {@code folderPath} and reporting
     * every URL to {@code cb} so the GUI can log it, update the progress bar,
     * and honour Stop / Pause / Resume requests.
     * <p>
     * Preserved overload (skip-existing disabled) so existing callers are
     * unaffected.
     * </p>
     *
     * @param folderPath destination folder; created if it does not exist
     * @param cb         progress/control callback, or {@code null} for none
     */
    public void runAlgorithm(String folderPath, DownloadProgressCallback cb) {
        runAlgorithm(folderPath, cb, false);
    }

    /**
     * Runs the algorithm, saving pages to {@code folderPath} and reporting
     * every URL to {@code cb} so the GUI can log it, update the progress bar,
     * and honour Stop / Pause / Resume requests.
     * <p>
     * The documentation index page and its CSS files are downloaded first
     * (for offline browsing), then every algorithm/tool documentation page
     * is downloaded using the URL returned by
     * {@link DescriptionOfAlgorithm#getURLOfDocumentation()} for each
     * algorithm registered in {@link AlgorithmManager}.
     * </p>
     *
     * @param folderPath   destination folder; created if it does not exist
     * @param cb           progress/control callback, or {@code null} for none
     * @param skipExisting if {@code true}, resources whose local file already
     *                     exists are skipped instead of being re-downloaded
     */
    public void runAlgorithm(String folderPath, DownloadProgressCallback cb,
            boolean skipExisting) {

        alreadyDownloaded = new HashSet<>();
        callback = cb;
        pageIndex = 0;
        this.skipExisting = skipExisting;
        algorithmResults.clear();

        // Reset statistics for this run
        statPagesDownloaded = 0;
        statPagesSkippedExisting = 0;
        statPagesSkippedAlreadyProcessed = 0;
        statImagesDownloaded = 0;
        statImagesSkippedExisting = 0;
        statImagesSkippedAlreadyProcessed = 0;
        statCssDownloaded = 0;
        statErrors = 0;
        statAlgorithmPagesFound = 0;

        long startTime = System.currentTimeMillis();

        debug("========================================================");
        debug("STARTING SPMF documentation download");
        debug("  Output folder : " + folderPath);
        debug("  Skip existing : " + skipExisting);
        debug("  Callback set  : " + (callback != null));
        debug("========================================================");

        createDirectory(folderPath);

        // ── Download the main documentation index page ────────────────────────
        if (!notifyAndCheckStop(MAIN_URL)) {
            debug("ABORTED: callback requested stop before downloading the main page.");
            return;
        }
        debug("Downloading MAIN INDEX PAGE: " + MAIN_URL);
        savePage(MAIN_URL, folderPath + "/documentation.html", MAIN_URL);

        // ── Fetch the RAW page content (no .php→.html corruption!) so the CSS
        // links used by the index page can be found and downloaded too. This
        // is only needed for CSS files: algorithm/tool documentation pages
        // are no longer discovered from this content (see class Javadoc).
        BufferedReader br = null;
        try {
            URL url = new URI(MAIN_URL).toURL();
            debug("Opening connection to fetch raw HTML for CSS link extraction: " + MAIN_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = br.readLine()) != null) {
                content.append(inputLine);
                content.append(System.lineSeparator());
            }

            String rawContent = content.toString();
            debug("Raw HTML fetched successfully. Length = " + rawContent.length() + " characters.");

            // ── CSS files ─────────────────────────────────────────────────────
            Pattern cssPattern = Pattern.compile("href=\"(.*?\\.css)\"");
            Matcher cssMatcher = cssPattern.matcher(rawContent);
            int cssFoundCount = 0;
            while (cssMatcher.find()) {

                if (isCancelled()) {
                    debug("ABORTED: callback requested stop while processing CSS links.");
                    return;
                }

                String cssLink = cssMatcher.group(1);
                cssFoundCount++;

                // Resolve to an absolute URL purely for logging purposes;
                // the actual fetch inside savePage() also resolves it itself.
                String absoluteCssUrl = toAbsoluteUrlSafely(cssLink, MAIN_URL);

                debug("Found CSS link #" + cssFoundCount + ": raw=\"" + cssLink
                        + "\" -> absolute=\"" + absoluteCssUrl + "\"");

                if (!notifyAndCheckStop(absoluteCssUrl)) {
                    debug("ABORTED: callback requested stop before downloading CSS: " + absoluteCssUrl);
                    return;
                }

                String localCssPath = folderPath + "/" + extractFileName(cssLink);
                debug("  -> Will save CSS to: " + localCssPath);
                savePage(cssLink, localCssPath, MAIN_URL);
            }
            debug("Total CSS links found: " + cssFoundCount);

        } catch (MalformedURLException e) {
            statErrors++;
            System.err.println("The URL provided is not valid: " + MAIN_URL);
            e.printStackTrace();
            debug("ERROR: MalformedURLException for MAIN_URL=" + MAIN_URL + " -> " + e.getMessage());
        } catch (IOException e) {
            statErrors++;
            System.err.println("An I/O error occurred while processing the URL: " + MAIN_URL);
            e.printStackTrace();
            debug("ERROR: IOException while processing MAIN_URL=" + MAIN_URL + " -> " + e.getMessage());
        } catch (URISyntaxException e) {
            statErrors++;
            System.err.println("The URL provided does not follow proper syntax: " + MAIN_URL);
            e.printStackTrace();
            debug("ERROR: URISyntaxException for MAIN_URL=" + MAIN_URL + " -> " + e.getMessage());
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

        // ── Algorithm/tool documentation pages, obtained directly from
        // AlgorithmManager instead of being scraped from the documentation
        // index page (see class Javadoc, IMPORTANT FIX #2). Each algorithm's
        // own DescriptionOfAlgorithm.getURLOfDocumentation() is downloaded,
        // which is guaranteed to exist and be correct for every algorithm
        // registered in SPMF, regardless of how (or whether) it is linked
        // from the index page.
        List<DescriptionOfAlgorithm> algorithmList;
        try {
            algorithmList = AlgorithmManager.getInstance().getAlgorithms();
        } catch (Exception e) {
            statErrors++;
            System.err.println("Could not load the list of algorithms from AlgorithmManager.");
            e.printStackTrace();
            debug("ERROR: could not load AlgorithmManager -> " + e.getMessage());
            algorithmList = new ArrayList<>();
        }

        debug("Total algorithms found in AlgorithmManager: " + algorithmList.size());
        statAlgorithmPagesFound = algorithmList.size();

        int algoIndex = 0;
        for (DescriptionOfAlgorithm algorithm : algorithmList) {

            algoIndex++;

            if (isCancelled()) {
                debug("ABORTED: callback requested stop while processing algorithm pages.");
                return;
            }

            String algorithmName = algorithm.getName();
            String docUrl = algorithm.getURLOfDocumentation();

            if (docUrl == null || docUrl.trim().isEmpty()) {
                debug("[" + algoIndex + "/" + algorithmList.size() + "] SKIP \"" + algorithmName
                        + "\": no documentation URL provided.");
                statErrors++;
                algorithmResults.add(new AlgorithmDownloadResult(
                        algorithmName, "", "", DownloadStatus.FAILED,
                        "No documentation URL provided by DescriptionOfAlgorithm"));
                continue;
            }
            docUrl = docUrl.trim();

            // Resolve to an absolute URL for logging (and in case the URL
            // returned by getURLOfDocumentation() was ever relative).
            String absoluteDocUrl = toAbsoluteUrlSafely(docUrl, MAIN_URL);

            debug("[" + algoIndex + "/" + algorithmList.size() + "] Processing algorithm \""
                    + algorithmName + "\": url=\"" + docUrl + "\" -> absolute=\"" + absoluteDocUrl + "\"");

            if (!notifyAndCheckStop(absoluteDocUrl)) {
                debug("ABORTED: callback requested stop before downloading: " + absoluteDocUrl);
                return;
            }

            // Build the LOCAL file name from just the file part of the link
            // (never the full URL) so we don't create an invalid nested path
            // such as "doc/http://www.example.com/EFIM.php".
            String localFileName = extractFileName(docUrl);
            String localFullPath = folderPath + "/" + localFileName;
            String normalizedLocalPath = localFullPath.replace(".php", ".html");

            debug("  -> Local file name: \"" + localFileName + "\" (full path: " + localFullPath + ")");

            // Fetch using the REAL (.php) URL, but save locally as .html.
            // The page's own absolute URL is used as the base for resolving
            // any relative image links found within it.
            DownloadStatus status = savePage(docUrl, localFullPath, absoluteDocUrl);

            // Record the outcome for this algorithm so it can be displayed
            // in a results table once the run completes.
            algorithmResults.add(new AlgorithmDownloadResult(
                    algorithmName, absoluteDocUrl, normalizedLocalPath,
                    status, lastOperationMessage));
        }

        long elapsedMs = System.currentTimeMillis() - startTime;

        int algoSuccess = 0, algoFailed = 0, algoSkippedExisting = 0, algoSkippedDup = 0;
        for (AlgorithmDownloadResult r : algorithmResults) {
            switch (r.getStatus()) {
                case SUCCESS: algoSuccess++; break;
                case FAILED: algoFailed++; break;
                case SKIPPED_EXISTING: algoSkippedExisting++; break;
                case SKIPPED_DUPLICATE: algoSkippedDup++; break;
            }
        }

        debug("========================================================");
        debug("DOWNLOAD RUN SUMMARY (elapsed " + elapsedMs + " ms)");
        debug("  Algorithms found in AlgorithmManager : " + statAlgorithmPagesFound);
        debug("  Pages downloaded                : " + statPagesDownloaded);
        debug("  CSS files downloaded            : " + statCssDownloaded);
        debug("  Images downloaded               : " + statImagesDownloaded);
        debug("  Pages skipped (already exist)   : " + statPagesSkippedExisting);
        debug("  Images skipped (already exist)  : " + statImagesSkippedExisting);
        debug("  Pages skipped (dup. in run)     : " + statPagesSkippedAlreadyProcessed);
        debug("  Images skipped (dup. in run)    : " + statImagesSkippedAlreadyProcessed);
        debug("  Errors encountered              : " + statErrors);
        debug("  --- Algorithm pages breakdown ---");
        debug("  Algorithms SUCCESS              : " + algoSuccess);
        debug("  Algorithms FAILED               : " + algoFailed);
        debug("  Algorithms SKIPPED (existing)   : " + algoSkippedExisting);
        debug("  Algorithms SKIPPED (duplicate)  : " + algoSkippedDup);
        if (algoFailed > 0) {
            debug("  FAILED algorithm pages:");
            for (AlgorithmDownloadResult r : algorithmResults) {
                if (r.getStatus() == DownloadStatus.FAILED) {
                    debug("    - " + r.getAlgorithmName() + "  (" + r.getUrl() + ")  -> "
                            + r.getDetails());
                }
            }
        }
        debug("========================================================");
    }

    /**
     * Returns the list of per-algorithm download results collected during
     * the most recent call to {@link #runAlgorithm(String, DownloadProgressCallback, boolean)}.
     * Each entry corresponds to one algorithm/tool returned by
     * {@link AlgorithmManager#getAlgorithms()}, with its final status
     * (success, failed, or skipped) and the local file path used.
     * <p>
     * Intended to be used after the run completes (e.g. by the GUI) to
     * build a summary {@code JTable} for debugging which files were or
     * were not downloaded.
     * </p>
     *
     * @return an unmodifiable-in-spirit list (callers should not rely on
     *         mutating it) of results, in the order the algorithms were
     *         returned by {@code AlgorithmManager}; empty if
     *         {@code runAlgorithm} has not been called yet
     */
    public List<AlgorithmDownloadResult> getAlgorithmResults() {
        return algorithmResults;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Callback helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Notifies the callback that {@code url} is about to be downloaded and
     * returns whether the download should continue.
     *
     * @param url the full (absolute) URL about to be fetched
     * @return {@code true} to continue, {@code false} to stop
     */
    private boolean notifyAndCheckStop(String url) {
        if (callback == null) {
            return true;
        }
        pageIndex++;
        boolean shouldContinue = callback.onPageDownloading(url, pageIndex, 0);
        if (!shouldContinue) {
            debug("Callback returned FALSE (stop requested) for: " + url);
        }
        return shouldContinue;
    }

    /**
     * Notifies the callback that {@code url} was skipped because a local
     * copy already exists (skip-existing option enabled). The URL is
     * prefixed with {@link #SKIPPED_PREFIX} so the GUI can distinguish this
     * from a real download and log it accordingly.
     *
     * @param url the full (absolute) URL that was skipped
     */
    private void notifySkipped(String url) {
        if (callback == null) {
            return;
        }
        pageIndex++;
        callback.onPageDownloading(SKIPPED_PREFIX + url, pageIndex, 0);
    }

    /**
     * Returns {@code true} if the callback has already signalled a stop.
     * Uses an empty-string sentinel URL so the GUI can distinguish this
     * "probe" call from a real resource notification and skip logging it.
     */
    private boolean isCancelled() {
        if (callback == null) {
            return false;
        }
        return !callback.onPageDownloading("", pageIndex, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL / path helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves {@code link} (which may be relative or already absolute)
     * against {@code baseUri} and returns the resulting absolute URL as a
     * string. If resolution fails for any reason, the original {@code link}
     * is returned unchanged so that logging never throws.
     *
     * @param link    the URL or relative path found in the HTML
     * @param baseUri the page URL that the link was found on
     * @return an absolute URL string, or {@code link} itself on failure
     */
    private String toAbsoluteUrlSafely(String link, String baseUri) {
        try {
            if (link.startsWith("http://") || link.startsWith("https://")) {
                return link;
            }
            URI base = new URI(baseUri);
            return base.resolve(link).toString();
        } catch (URISyntaxException e) {
            debug("WARNING: could not resolve absolute URL for link=\"" + link
                    + "\" against base=\"" + baseUri + "\" -> " + e.getMessage());
            return link;
        }
    }

    /**
     * Extracts just the file name from a URL or relative path, discarding
     * any domain, directory path, or query string.
     *
     * <p>Examples:
     * <pre>
     *   "http://www.philippe-fournier-viger.com/spmf/EFIM.php" → "EFIM.php"
     *   "assets/css/main.css"                                   → "main.css"
     *   "EFIM.php"                                               → "EFIM.php"
     *   "EFIM.php?extra=1"                                       → "EFIM.php"
     * </pre>
     * </p>
     *
     * @param link the URL or relative path
     * @return the file name portion, never {@code null}
     */
    private String extractFileName(String link) {
        String noQuery = link;
        int qIdx = noQuery.indexOf('?');
        if (qIdx >= 0) {
            noQuery = noQuery.substring(0, qIdx);
        }
        int slashIdx = noQuery.lastIndexOf('/');
        return (slashIdx >= 0) ? noQuery.substring(slashIdx + 1) : noQuery;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Original private methods (unchanged core logic, plus skip-existing checks)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Method to create a folder
     * @param folderPath the path
     */
    private void createDirectory(String folderPath) {
        try {
            boolean existedBefore = Files.exists(Paths.get(folderPath));
            Files.createDirectories(Paths.get(folderPath));
            debug("createDirectory(\"" + folderPath + "\") -> "
                    + (existedBefore ? "already existed" : "created"));
        } catch (IOException e) {
            statErrors++;
            System.err.println("An error occurred while creating the directory: " + folderPath);
            e.printStackTrace();
            debug("ERROR: could not create directory \"" + folderPath + "\" -> " + e.getMessage());
        }
    }

    /**
     * Method to save a webpage.
     * <p>
     * {@code urlString} MUST be the real, unmodified URL (i.e. still ending
     * in {@code .php} if that is what the server actually serves). Only the
     * local {@code filePath} and the internal links inside the saved content
     * are rewritten to use {@code .html}.
     * </p>
     * <p>
     * If {@link #skipExisting} is {@code true} and the (extension-normalised)
     * local file already exists, the method returns immediately without
     * performing any network I/O.
     * </p>
     * <p>
     * As a side effect, {@link #lastOperationMessage} is set to a short
     * description of the outcome (used to populate
     * {@link AlgorithmDownloadResult#getDetails()} for the algorithm pages).
     * </p>
     *
     * @param urlString the real URL to fetch (absolute or relative, may end in .php)
     * @param filePath  the filepath where it should be saved (extension will be
     *                  normalised to .html)
     * @param baseUri   the base URI used to resolve relative URLs
     * @return the {@link DownloadStatus} describing how the operation ended
     */
    private DownloadStatus savePage(String urlString, String filePath, String baseUri) {
        if (alreadyDownloaded.contains(urlString)) {
            statPagesSkippedAlreadyProcessed++;
            debug("SKIP page (already processed earlier in this run): " + urlString);
            lastOperationMessage = "Already processed earlier in this run";
            return DownloadStatus.SKIPPED_DUPLICATE;
        }
        alreadyDownloaded.add(urlString);

        // Normalise the local file extension up-front (.php -> .html) so we
        // can check for an existing copy before doing any network I/O.
        String finalFilePath = filePath.replace(".php", ".html");

        if (skipExisting && Files.exists(Paths.get(finalFilePath))) {
            statPagesSkippedExisting++;
            debug("SKIP page (local file already exists): \"" + finalFilePath
                    + "\" for URL: " + urlString);
            notifySkipped(toAbsoluteUrlSafely(urlString, baseUri));
            lastOperationMessage = "Local file already exists: " + finalFilePath;
            return DownloadStatus.SKIPPED_EXISTING;
        }

        debug("DOWNLOAD page: url=\"" + urlString + "\" -> local file=\"" + finalFilePath + "\"");

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
                debug("  Resolved relative URL \"" + urlString + "\" against base \""
                        + baseUri + "\" -> \"" + url + "\"");
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            int responseCode = conn.getResponseCode();
            debug("  HTTP GET " + url + "  -> response code: " + responseCode);

            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder contentBuilder = new StringBuilder();
            String inputLine;
            while ((inputLine = reader.readLine()) != null) {
                contentBuilder.append(inputLine);
                contentBuilder.append(System.lineSeparator());
            }

            // Update links INSIDE the saved content so offline browsing works
            String content = contentBuilder.toString();
            debug("  Downloaded content length: " + content.length() + " characters.");

            content = content.replaceAll("href=\"([^\"]+).php\"", "href=\"$1.html\"");
            content = content.replaceAll(
                    "https://www.philippe-fournier-viger.com/spmf/index.php\\?link=documentation\\.html",
                    "documentation.html");

            // Find and save images
            Pattern imgPattern = Pattern.compile("src=\"([^\"]+\\.(png|jpg))\"");
            Matcher imgMatcher = imgPattern.matcher(content);
            int imgFoundInThisPage = 0;
            while (imgMatcher.find()) {

                if (isCancelled()) {
                    debug("ABORTED: callback requested stop while processing images in " + urlString);
                    lastOperationMessage = "Cancelled while processing embedded images";
                    return DownloadStatus.FAILED;
                }

                String imgLink = imgMatcher.group(1);
                String imgName = imgLink.substring(imgLink.lastIndexOf('/') + 1);
                imgFoundInThisPage++;
                debug("  Found image reference #" + imgFoundInThisPage + " in page: \""
                        + imgLink + "\" -> local name \"" + imgName + "\"");
                saveImage(imgLink, DEFAULT_FOLDER + "/" + imgName, baseUri);
            }
            debug("  Total images referenced in this page: " + imgFoundInThisPage);

            // Save the updated content to file
            Files.write(Paths.get(finalFilePath), content.getBytes(StandardCharsets.UTF_8));
            statPagesDownloaded++;
            if (finalFilePath.toLowerCase().endsWith(".css")) {
                statCssDownloaded++;
            }
            debug("  SUCCESS: saved page to \"" + finalFilePath + "\" ("
                    + content.length() + " chars written).");
            lastOperationMessage = "OK (" + content.length() + " chars, HTTP " + responseCode + ")";
            return DownloadStatus.SUCCESS;

        } catch (URISyntaxException e) {
            statErrors++;
            System.err.println("The URI provided is not valid: " + urlString);
            e.printStackTrace();
            debug("ERROR: URISyntaxException while downloading \"" + urlString
                    + "\" -> " + e.getMessage());
            lastOperationMessage = "Invalid URI: " + e.getMessage();
            return DownloadStatus.FAILED;
        } catch (MalformedURLException e) {
            statErrors++;
            System.err.println("A malformed URL has occurred for the URI: " + urlString);
            e.printStackTrace();
            debug("ERROR: MalformedURLException while downloading \"" + urlString
                    + "\" -> " + e.getMessage());
            lastOperationMessage = "Malformed URL: " + e.getMessage();
            return DownloadStatus.FAILED;
        } catch (IOException e) {
            statErrors++;
            System.err.println("An I/O error occurred while saving the page: " + urlString);
            e.printStackTrace();
            debug("ERROR: IOException while downloading/saving \"" + urlString
                    + "\" -> " + e.getMessage()
                    + "  (This is often the cause of a 'file not downloaded' problem, "
                    + "e.g. HTTP 404 Not Found.)");
            lastOperationMessage = "I/O error: " + e.getMessage();
            return DownloadStatus.FAILED;
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
     * Method to save an image.
     * <p>
     * If {@link #skipExisting} is {@code true} and the local file already
     * exists, the method returns immediately without performing any network
     * I/O.
     * </p>
     *
     * @param urlString the url
     * @param filePath the filepath where it should be saved
     * @param baseUri the base URI
     */
    private void saveImage(String urlString, String filePath, String baseUri) {
        if (alreadyDownloaded.contains(urlString)) {
            statImagesSkippedAlreadyProcessed++;
            debug("SKIP image (already processed earlier in this run): " + urlString);
            return;
        }
        alreadyDownloaded.add(urlString);

        if (skipExisting && Files.exists(Paths.get(filePath))) {
            statImagesSkippedExisting++;
            debug("SKIP image (local file already exists): \"" + filePath
                    + "\" for URL: " + urlString);
            notifySkipped(toAbsoluteUrlSafely(urlString, baseUri));
            return;
        }

        debug("DOWNLOAD image: url=\"" + urlString + "\" -> local file=\"" + filePath + "\"");

        InputStream in = null;
        try {
            URL url;
            if (urlString.startsWith("http://") || urlString.startsWith("https://")) {
                url = new URI(urlString).toURL();
            } else {
                URI base = new URI(baseUri);
                url = base.resolve(urlString).toURL();
                debug("  Resolved relative image URL \"" + urlString + "\" against base \""
                        + baseUri + "\" -> \"" + url + "\"");
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            int responseCode = conn.getResponseCode();
            debug("  HTTP GET " + url + "  -> response code: " + responseCode);

            in = conn.getInputStream();
            long bytesCopied = Files.copy(in, Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);
            statImagesDownloaded++;
            debug("  SUCCESS: saved image to \"" + filePath + "\" (" + bytesCopied + " bytes written).");

        } catch (URISyntaxException e) {
            statErrors++;
            System.err.println("The URI provided is not valid: " + urlString);
            e.printStackTrace();
            debug("ERROR: URISyntaxException while downloading image \"" + urlString
                    + "\" -> " + e.getMessage());
        } catch (MalformedURLException e) {
            statErrors++;
            System.err.println("A malformed URL has occurred for the URI: " + urlString);
            e.printStackTrace();
            debug("ERROR: MalformedURLException while downloading image \"" + urlString
                    + "\" -> " + e.getMessage());
        } catch (IOException e) {
            statErrors++;
            System.err.println("An I/O error occurred while saving the image: " + urlString);
            e.printStackTrace();
            debug("ERROR: IOException while downloading/saving image \"" + urlString
                    + "\" -> " + e.getMessage()
                    + "  (This is often the cause of a 'file not downloaded' problem, "
                    + "e.g. HTTP 404 Not Found.)");
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

    // ─────────────────────────────────────────────────────────────────────────
    // Debug logging helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Prints {@code message} to {@code System.out} if {@link #verbose} is
     * {@code true}. Every line is prefixed with a tag to ease correlation
     * when debugging.
     *
     * @param message the message to print
     */
    private void debug(String message) {
        if (verbose) {
            System.out.println("[SPMF-DOC-DOWNLOADER] " + message);
        }
    }
}