package ca.pfv.spmf.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JProgressBar;
import javax.swing.Timer;

/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * This class is a custom JProgress bar that displays the time that an algorithm has been running in seconds
 * using the intedeterminate types of display. It has two main methods: startAlgorithm and endAlgorithm that
 * should be called when an algorithm starts to run and terminates, respectively.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class AlgorithmProgressBar extends JProgressBar {
    /**
	 * serial UID
	 */
	private static final long serialVersionUID = -3266198875763956855L;
	/** A timer thread */
	private Timer timer;
	/** the start time */
    private long startTime;

    /** The constructor */
    public AlgorithmProgressBar() {
        super();
        setStringPainted(true);
        setString("");
    }

    /**
     *  Call this method to start the timer and progress bar
     */
    public void startAlgorithm() {
        startTime = System.currentTimeMillis();
        setIndeterminate(true); 
        if (timer != null) {
            timer.stop(); // Stop any existing timer
        }
        timer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                setString(formatElapsedTime(elapsedTime) + " s");
            }
        });
        timer.start();
    }

    /**
     * Call this method to stop the timer and progress bar
     */
    public void endAlgorithm() {
        if (timer != null) {
            timer.stop();
            setString("");
        }
        setIndeterminate(false);
    }

    /**
     * Helper method to format the elapsed time in hours, minutes, and seconds
     * @param elapsedTime  the elapsed time
     * @return a string displaying the elapsed time
     */
    private String formatElapsedTime(long elapsedTime) {
        int seconds = (int) (elapsedTime / 1000) % 60 ;
        int minutes = (int) ((elapsedTime / (1000*60)) % 60);
        int hours   = (int) ((elapsedTime / (1000*60*60)) % 24);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}
