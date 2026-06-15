package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2021 Philippe Fournier-Viger
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
 * Do not remove copyright or license information.
 */

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Immutable record of a single algorithm execution. Instances are created by
 * {@link AlgorithmRunnerController} when an algorithm finishes successfully or
 * is stopped by the user/time limit, and are displayed in the
 * {@link RunHistoryPanel}.
 *
 * <p>All fields are set at construction time and are never mutated afterwards,
 * so this class is safe to read from any thread.</p>
 *
 * <p>Only two status values are recorded:</p>
 * <ul>
 *   <li><b>OK</b> — the algorithm completed successfully</li>
 *   <li><b>STOPPED</b> — the algorithm was stopped by the user or by the time
 *       limit before it finished</li>
 * </ul>
 *
 * <p>Runs that fail due to incorrect parameters or other errors are not
 * recorded in the history at all, so the user does not see broken
 * configurations cluttering the table.</p>
 *
 * @author Philippe Fournier-Viger
 */
public final class RunHistoryEntry {

    /** Formatter used for the timestamp column */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Status constant: the algorithm completed successfully */
    public static final String STATUS_OK = "OK";

    /** Status constant: the algorithm was stopped before completion */
    public static final String STATUS_STOPPED = "STOPPED";

    /** The name of the algorithm that was run */
    private final String algorithmName;

    /** The input file path that was used, or an empty string if none */
    private final String inputFile;

    /** The output file path that was used, or an empty string if none */
    private final String outputFile;

    /** The parameter values that were supplied to the algorithm */
    private final String[] parameters;

    /**
     * The status of this run: either {@link #STATUS_OK} if the algorithm
     * completed successfully, or {@link #STATUS_STOPPED} if it was stopped by
     * the user or the time limit.
     */
    private final String status;

    /** The wall-clock date and time at which the run started */
    private final LocalDateTime timestamp;

    /** The duration of the run in milliseconds */
    private final long durationMillis;

    /**
     * Constructs a new history entry with all required fields.
     *
     * @param algorithmName the name of the algorithm
     * @param inputFile     the input file path, or empty string if none
     * @param outputFile    the output file path, or empty string if none
     * @param parameters    the parameter values (a defensive copy is taken)
     * @param status        either {@link #STATUS_OK} or {@link #STATUS_STOPPED}
     * @param timestamp     the date and time at which the run started
     * @param durationMillis the duration of the run in milliseconds
     */
    public RunHistoryEntry(String algorithmName,
                           String inputFile,
                           String outputFile,
                           String[] parameters,
                           String status,
                           LocalDateTime timestamp,
                           long durationMillis) {
        this.algorithmName  = algorithmName  != null ? algorithmName  : "";
        this.inputFile      = inputFile      != null ? inputFile      : "";
        this.outputFile     = outputFile     != null ? outputFile     : "";
        this.parameters     = parameters     != null
                ? Arrays.copyOf(parameters, parameters.length)
                : new String[0];
        this.status         = status         != null ? status         : STATUS_STOPPED;
        this.timestamp      = timestamp      != null ? timestamp      : LocalDateTime.now();
        this.durationMillis = durationMillis;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the name of the algorithm that was run.
     *
     * @return the algorithm name
     */
    public String getAlgorithmName() {
        return algorithmName;
    }

    /**
     * Returns the input file path that was used, or an empty string if none.
     *
     * @return the input file path
     */
    public String getInputFile() {
        return inputFile;
    }

    /**
     * Returns the output file path that was used, or an empty string if none.
     *
     * @return the output file path
     */
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Returns a defensive copy of the parameter values that were supplied.
     *
     * @return a copy of the parameters array
     */
    public String[] getParameters() {
        return Arrays.copyOf(parameters, parameters.length);
    }

    /**
     * Returns the status of this run: either {@link #STATUS_OK} if the
     * algorithm completed successfully, or {@link #STATUS_STOPPED} if it was
     * stopped by the user or the time limit.
     *
     * @return the status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns true if the algorithm completed successfully (status is
     * {@link #STATUS_OK}).
     *
     * @return true on success, false if stopped
     */
    public boolean isSucceeded() {
        return STATUS_OK.equals(status);
    }

    /**
     * Returns the date and time at which the run started.
     *
     * @return the run timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the wall-clock duration of the run in milliseconds.
     *
     * @return the duration in milliseconds
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Returns the timestamp formatted as {@code yyyy-MM-dd HH:mm:ss} for
     * display in the history table.
     *
     * @return the formatted timestamp string
     */
    public String getFormattedTimestamp() {
        return timestamp.format(DISPLAY_FORMATTER);
    }

    /**
     * Returns a human-readable representation of the run duration, e.g.
     * {@code "1.234 s"} or {@code "00:01:05"} for longer runs.
     *
     * @return the formatted duration string
     */
    public String getFormattedDuration() {
        if (durationMillis < 60_000L) {
            // Show as fractional seconds for runs under one minute
            return String.format("%.3f s", durationMillis / 1000.0);
        }
        long totalSeconds = durationMillis / 1000L;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Returns a single-line summary suitable for use as a tooltip or log line.
     *
     * @return a summary string
     */
    @Override
    public String toString() {
        return String.format("[%s] %s  in=%s  out=%s  params=%s  status=%s  dur=%s",
                getFormattedTimestamp(),
                algorithmName,
                inputFile,
                outputFile,
                Arrays.toString(parameters),
                status,
                getFormattedDuration());
    }
}