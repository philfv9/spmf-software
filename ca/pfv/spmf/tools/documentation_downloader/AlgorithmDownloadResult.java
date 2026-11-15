package ca.pfv.spmf.tools.documentation_downloader;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */

/**
 * Holds the outcome of downloading a single algorithm "Example" page from
 * the SPMF documentation website, so it can be displayed to the user in a
 * summary {@code JTable} once the download run has completed.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmDownloadResult {

    /** A human-readable name for the algorithm/page (derived from the file name). */
    private final String algorithmName;

    /** The full remote URL that was (or would have been) downloaded. */
    private final String url;

    /** The local file path where the page was (or would have been) saved. */
    private final String filePath;

    /** The outcome of the download attempt. */
    private final DownloadStatus status;

    /** Additional details, e.g. an error message when {@code status == FAILED}. */
    private final String details;

    /**
     * Creates a new immutable download result record.
     *
     * @param algorithmName human-readable name of the algorithm/page
     * @param url           the remote URL that was processed
     * @param filePath      the local path where the file was/would be saved
     * @param status        the outcome of the download attempt
     * @param details       extra details (error message, or a short note)
     */
    public AlgorithmDownloadResult(String algorithmName, String url, String filePath,
            DownloadStatus status, String details) {
        this.algorithmName = algorithmName;
        this.url = url;
        this.filePath = filePath;
        this.status = status;
        this.details = details;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public String getUrl() {
        return url;
    }

    public String getFilePath() {
        return filePath;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "AlgorithmDownloadResult{" +
                "algorithmName='" + algorithmName + '\'' +
                ", url='" + url + '\'' +
                ", filePath='" + filePath + '\'' +
                ", status=" + status +
                ", details='" + details + '\'' +
                '}';
    }
}