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
 * Callback invoked by {@link AlgoSPMFDownloadDoc} just before each resource
 * (HTML page, CSS file, or image) is downloaded.
 *
 * <p>The GUI implements this interface to:
 * <ul>
 *   <li>Log the full URL that is about to be fetched.</li>
 *   <li>Update the progress bar.</li>
 *   <li>Return {@code false} when the user presses Stop, which tells the
 *       algorithm to abort immediately.</li>
 *   <li>Block inside the implementation when the user presses Pause, and
 *       unblock when Resume is pressed – all transparent to the algorithm.</li>
 * </ul>
 *
 * @author Philippe Fournier-Viger
 */
@FunctionalInterface
public interface DownloadProgressCallback {

    /**
     * Called before each resource is fetched.
     *
     * @param fullUrl    the URL that is about to be downloaded; may be relative
     *                   if the algorithm has not yet resolved it to an absolute URL
     * @param pageIndex  1-based running count of resources notified so far
     * @param totalPages total number of resources, or {@code 0} if unknown
     *                   (the algorithm discovers pages dynamically)
     * @return {@code true} to continue downloading,
     *         {@code false} to abort the entire download immediately
     */
    boolean onPageDownloading(String fullUrl, int pageIndex, int totalPages);
}