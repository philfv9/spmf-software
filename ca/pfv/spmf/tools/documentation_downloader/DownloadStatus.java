package ca.pfv.spmf.tools.documentation_downloader;

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
 * Represents the outcome of attempting to download a single documentation
 * resource (in practice, an algorithm "Example" page), used to build the
 * results table shown to the user after a download run completes.
 *
 * @author Philippe Fournier-Viger
 */
public enum DownloadStatus {

    /** The resource was downloaded and saved successfully. */
    SUCCESS,

    /** The download failed (network error, HTTP error, I/O error, etc.). */
    FAILED,

    /**
     * The resource was not downloaded because a local copy already existed
     * and the "skip existing" option was enabled.
     */
    SKIPPED_EXISTING,

    /**
     * The resource was not downloaded because the exact same URL had
     * already been processed earlier in the same run (duplicate link found
     * on the page).
     */
    SKIPPED_DUPLICATE
}