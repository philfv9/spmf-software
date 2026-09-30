package ca.pfv.spmf.gui;

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

import java.awt.Component;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Executes one algorithm, either in a thread inside this JVM (via
 * {@link CommandProcessor}) or in a separate JVM running spmf.jar, and
 * optionally enforces a time limit.
 * <p>
 * All Swing interaction performed by this class is marshalled to the event
 * dispatch thread, so its methods may be called from any thread.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmRunner {

    private volatile NotifyingThread currentRunningAlgorithmThread;
    private volatile Process currentExternalProcess;
    private volatile boolean userRequestedStop;
    private volatile AlgorithmRunnerController controller;

    private final AlgorithmRunnerPanel panel;

    /**
     * @param panel the panel used for console output and UI state changes
     */
    public AlgorithmRunner(AlgorithmRunnerPanel panel) {
        this.panel = panel;
    }

    /**
     * Sets the controller to notify when a run is stopped (by the user or by
     * the time limit), so that a STOPPED history entry can be recorded.
     *
     * @param controller the controller
     */
    public void setController(AlgorithmRunnerController controller) {
        this.controller = controller;
    }

    /** @return true if an algorithm thread or external process is alive */
    public boolean isRunning() {
        NotifyingThread t = currentRunningAlgorithmThread;
        Process p = currentExternalProcess;
        return (t != null && t.isAlive()) || (p != null && p.isAlive());
    }

    // -------------------------------------------------------------------------
    // Stopping
    // -------------------------------------------------------------------------

    /**
     * Stops whatever is currently running. External processes are destroyed;
     * internal threads are cancelled cooperatively, then (if still alive)
     * forcibly via {@code Thread.stop()} where the JVM still allows it.
     *
     * @param panel the panel whose UI should be reset (may be null to use the
     *              panel given at construction)
     * @return true if something was running and a stop was attempted
     */
    public boolean tryToKillProcess(AlgorithmRunnerPanel panel) {
        final AlgorithmRunnerPanel target = (panel != null) ? panel : this.panel;
        userRequestedStop = true;

        Process process = currentExternalProcess;
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            notifyStopped(target);
            return true;
        }

        NotifyingThread thread = currentRunningAlgorithmThread;
        if (thread != null && thread.isAlive()) {
            // 1. Cooperative cancellation (flag + interrupt)
            thread.cancel();
            try {
                thread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // 2. Forced stop where the JVM still supports it
            if (thread.isAlive()) {
                forceStop(thread, target);
            }
            // 3. Only reset the UI if the thread is really gone; otherwise the
            //    Stop button must stay available.
            if (thread.isAlive()) {
                target.postStatusMessage(
                        "Warning: the algorithm could not be stopped and is still running.");
                return true;
            }
            notifyStopped(target);
            return true;
        }
        return false;
    }

    private void notifyStopped(AlgorithmRunnerPanel target) {
        AlgorithmRunnerController c = controller;
        if (c != null) {
            c.notifyWasStopped();
        }
        target.postStatusMessage("Algorithm stopped.");
        onEdt(target::resetUIAfterThreadCompletion);
    }

    /** Calls {@code Thread.stop()} reflectively so this compiles on every JDK. */
    private static void forceStop(Thread thread, Component parent) {
        try {
            Method stop = Thread.class.getMethod("stop");
            stop.invoke(thread);
        } catch (NoSuchMethodException e) {
            showStopUnsupported(parent);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof UnsupportedOperationException) {
                showStopUnsupported(parent);
            } else {
                System.err.println("Unexpected exception while stopping thread: " + e.getCause());
            }
        } catch (Exception e) {
            System.err.println("Failed to invoke Thread.stop(): " + e);
        }
    }

    private static void showStopUnsupported(Component parent) {
        onEdt(() -> JOptionPane.showMessageDialog(parent,
                "Forcibly stopping an algorithm is not supported by Java "
                        + System.getProperty("java.version") + ".\n"
                        + "The algorithm has been asked to stop and may still finish on its own.\n\n"
                        + "Tip: enable \"Run in a separate process\" to be able to stop\n"
                        + "algorithms at any time.",
                "Cannot stop algorithm", JOptionPane.WARNING_MESSAGE));
    }

    private static void onEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    // -------------------------------------------------------------------------
    // Running
    // -------------------------------------------------------------------------

    /**
     * Runs the algorithm in a thread inside this JVM.
     *
     * @param choice     algorithm name
     * @param inputFile  input path or null
     * @param outputFile output path or null
     * @param parameters parameter values
     * @param listener   notified when the thread finishes
     * @param exHandler  handles exceptions thrown by the algorithm
     */
    public void runInternal(final String choice, final String inputFile, final String outputFile,
                            final String[] parameters, ThreadCompleteListener listener,
                            UncaughtExceptionHandler exHandler) {
        userRequestedStop = false;

        NotifyingThread thread = new NotifyingThread() {
            @Override
            public boolean doRun() throws Exception {
                CommandProcessor.runAlgorithm(choice, inputFile, outputFile, parameters);
                return true;
            }
        };
        thread.setName("SPMF-" + choice);
        thread.addListener(listener);
        thread.setUncaughtExceptionHandler(exHandler);

        // Redirect BEFORE starting so the algorithm's first lines are not lost
        panel.redirectOutputStream();

        currentRunningAlgorithmThread = thread;
        thread.start();

        // The memory viewer only opens a window; do not treat it as a running algorithm
        if ("MemoryViewer".equals(choice)) {
            currentRunningAlgorithmThread = null;
        }
    }

    /**
     * Runs the algorithm in a separate JVM (same Java installation as the one
     * running SPMF, using the jar SPMF was loaded from). The child's output is
     * forwarded to the console line by line.
     *
     * @param choice     algorithm name
     * @param inputFile  input path or null
     * @param outputFile output path or null
     * @param parameters parameter values
     * @param listener   notified when the thread finishes
     * @param exHandler  handles exceptions thrown while running
     */
    public void runExternal(final String choice, final String inputFile, final String outputFile,
                            final String[] parameters, ThreadCompleteListener listener,
                            UncaughtExceptionHandler exHandler) {
        userRequestedStop = false;

        NotifyingThread thread = new NotifyingThread() {
            @Override
            public boolean doRun() throws Exception {
                List<String> command = new ArrayList<>(16);
                command.add(javaExecutable());
                command.add("-Dfile.encoding=UTF-8");
                command.add("-Dstdout.encoding=UTF-8");
                command.add("-jar");
                command.add(spmfJarPath());
                command.add("run");
                command.add(choice);
                if (inputFile != null) {
                    command.add(inputFile);
                }
                if (outputFile != null) {
                    command.add(outputFile);
                }
                for (String p : parameters) {
                    if (p != null && !p.isEmpty()) {
                        command.add(p);
                    }
                }

                panel.postStatusMessage("Running in a separate process:");
                panel.postStatusMessage("  " + String.join(" ", command));

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true); // never leave stderr unread (would block the child)

                Process process;
                try {
                    process = pb.start();
                } catch (IOException e) {
                    throw new IllegalArgumentException(System.lineSeparator()
                            + "Could not start the external process: " + e.getMessage()
                            + System.lineSeparator() + "Command: " + String.join(" ", command));
                }
                currentExternalProcess = process;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        panel.appendConsoleLine(line);
                    }
                } catch (IOException e) {
                    if (process.isAlive()) {
                        throw new IllegalArgumentException(System.lineSeparator()
                                + "I/O error while reading the process output: " + e.getMessage());
                    }
                    // stream closed because the process was killed – not an error
                }
                return process.waitFor() == 0;
            }
        };
        thread.setName("SPMF-external-" + choice);
        thread.addListener(listener);
        thread.setUncaughtExceptionHandler(exHandler);
        currentRunningAlgorithmThread = thread;
        thread.start();
    }

    /** Path of the java executable of the JVM running SPMF, or "java" as a fallback. */
    private static String javaExecutable() {
        String home = System.getProperty("java.home");
        if (home != null) {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Path java = Paths.get(home, "bin", windows ? "java.exe" : "java");
            if (Files.isExecutable(java)) {
                return java.toString();
            }
        }
        return "java";
    }

    /** Path of the jar SPMF was loaded from, or "spmf.jar" (working directory) as a fallback. */
    private static String spmfJarPath() {
        try {
            Path location = Paths.get(AlgorithmRunner.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)
                    && location.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return location.toString();
            }
        } catch (Exception ignored) {
            // e.g. running from an IDE (classes directory) – use the default below
        }
        return "spmf.jar";
    }

    // -------------------------------------------------------------------------
    // Time limit
    // -------------------------------------------------------------------------

    /**
     * Starts a daemon thread that stops the current run if it exceeds
     * {@code maxSeconds}. Does nothing if {@code maxSeconds <= 0}.
     *
     * @param maxSeconds the time limit in seconds
     * @param panel      the panel whose UI is reset when the run is stopped
     */
    public void startKillerThreadIfNeeded(final int maxSeconds, final AlgorithmRunnerPanel panel) {
        if (maxSeconds <= 0) {
            return;
        }
        final NotifyingThread watched = currentRunningAlgorithmThread;
        final long deadline = System.currentTimeMillis() + maxSeconds * 1000L;

        Thread killer = new Thread(() -> {
            try {
                while (isRunning() && currentRunningAlgorithmThread == watched) {
                    if (System.currentTimeMillis() >= deadline) {
                        if (!userRequestedStop && tryToKillProcess(panel)) {
                            panel.postStatusMessage(
                                    "Stopped: time limit of " + maxSeconds + " seconds exceeded.");
                        }
                        return;
                    }
                    Thread.sleep(250);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "SPMF-time-limit");
        killer.setDaemon(true);
        killer.start();
    }
}