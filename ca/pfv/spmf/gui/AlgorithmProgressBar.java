package ca.pfv.spmf.gui;

import javax.swing.JProgressBar;
import javax.swing.Timer;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license
 */
/**
 * An indeterminate progress bar that shows how long the current algorithm has
 * been running. Call {@link #startAlgorithm()} when a run starts and
 * {@link #endAlgorithm()} when it finishes.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class AlgorithmProgressBar extends JProgressBar {

    private static final long serialVersionUID = -3266198875763956855L;

    /** Refresh period; shorter than one second so no displayed second is skipped. */
    private static final int TICK_MS = 500;

    private final Timer timer;
    private long startTime;
    private boolean running;

    public AlgorithmProgressBar() {
        super();
        setStringPainted(true);
        setString("");
        timer = new Timer(TICK_MS, e -> updateText());
        timer.setCoalesce(true);
    }

    /** Starts (or restarts) the elapsed-time display. */
    public void startAlgorithm() {
        startTime = System.currentTimeMillis();
        running = true;
        setIndeterminate(true);
        updateText();
        timer.restart();
    }

    /** Stops the elapsed-time display and clears the bar. */
    public void endAlgorithm() {
        timer.stop();
        running = false;
        setIndeterminate(false);
        setValue(0);
        setString("");
    }

    /** @return true between {@link #startAlgorithm()} and {@link #endAlgorithm()} */
    public boolean isRunningAlgorithm() {
        return running;
    }

    /** @return milliseconds since {@link #startAlgorithm()} was called (0 if not running) */
    public long getElapsedMillis() {
        return running ? System.currentTimeMillis() - startTime : 0;
    }

    private void updateText() {
        setString("Running\u2026  " + formatElapsed(getElapsedMillis()));
    }

    /**
     * Formats a duration as "5 s", "1 min 05 s" or "1 h 02 min 03 s".
     *
     * @param millis the duration in milliseconds
     * @return a human-readable string
     */
    public static String formatElapsed(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = seconds / 60;
        seconds %= 60;
        if (minutes < 60) {
            return String.format("%d min %02d s", minutes, seconds);
        }
        long hours = minutes / 60;
        minutes %= 60;
        return String.format("%d h %02d min %02d s", hours, minutes, seconds);
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }
}