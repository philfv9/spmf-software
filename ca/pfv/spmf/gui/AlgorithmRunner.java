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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class encapsulates the execution of a single data-mining algorithm,
 * either as an internal thread (via {@link CommandProcessor}) or as an
 * external process (via a new JVM running spmf.jar). It also manages optional
 * timeout enforcement through a dedicated killer thread.
 *
 * <p>All mutable execution state (the running thread and the external process)
 * is held as instance fields rather than static fields, so multiple independent
 * instances can coexist safely.</p>
 *
 * <p>Console output is written through the panel's delegation methods rather
 * than by holding a direct reference to a {@link ConsolePanel}, so the runner
 * never bypasses the panel's encapsulation.</p>
 *
 * <p>Thread cancellation uses cooperative cancellation via
 * {@link NotifyingThread#cancel()} rather than the deprecated and removed
 * {@code Thread.stop()} method, with a fallback to {@code stop()} via
 * reflection for older JVM versions. The {@link InvocationTargetException}
 * thrown by the reflection layer is unwrapped so that
 * {@link UnsupportedOperationException} from the JVM is still surfaced to
 * the user as an error dialog, exactly as the original code did.</p>
 *
 * <p>When a run is stopped (either by the user clicking the Stop button or by
 * the time-limit killer thread), {@link #tryToKillProcess(AlgorithmRunnerPanel)}
 * calls {@link AlgorithmRunnerController#notifyWasStopped()} on the registered
 * controller so that {@link AlgorithmRunnerController#notifyOfThreadComplete}
 * can record a history entry with status
 * {@link RunHistoryEntry#STATUS_STOPPED} instead of treating the termination
 * as a plain failure.</p>
 *
 * @author Philippe Fournier-Viger
 */
public class AlgorithmRunner {

    /** The thread currently executing the algorithm, or null if none is running */
    private NotifyingThread currentRunningAlgorithmThread = null;

    /** The external process currently running the algorithm, or null if none */
    private Process currentExternalProcess = null;

    /** The panel used to display algorithm output and reset UI state */
    private final AlgorithmRunnerPanel panel;

    /** Flag set to true when the user manually clicks the Stop button */
    private volatile boolean userRequestedStop = false;

    /**
     * Reference to the controller so that {@link #tryToKillProcess} can call
     * {@link AlgorithmRunnerController#notifyWasStopped()} whenever a run is
     * terminated by the user or the time-limit killer thread. This allows the
     * controller to distinguish a stopped run (which should be recorded in the
     * history with status STOPPED) from a failed run (which should not be
     * recorded at all).
     */
    private AlgorithmRunnerController controller = null;

    /**
     * Constructs an AlgorithmRunner that writes output through the given panel's
     * console-delegation methods.
     *
     * @param panel the {@link AlgorithmRunnerPanel} whose console methods are used
     *              for output and whose UI-reset methods are called after stopping
     */
    public AlgorithmRunner(AlgorithmRunnerPanel panel) {
        this.panel = panel;
    }

    /**
     * Sets the controller reference so that this runner can notify it when a
     * run is stopped by the user or the time-limit killer thread. Must be
     * called once after construction, before any algorithm is run.
     *
     * @param controller the {@link AlgorithmRunnerController} to notify on stop
     */
    public void setController(AlgorithmRunnerController controller) {
        this.controller = controller;
    }

    /**
     * Returns true if either the internal thread or the external process is
     * currently alive.
     *
     * @return true if an algorithm is currently running, false otherwise
     */
    public boolean isRunning() {
        return (currentRunningAlgorithmThread != null
                && currentRunningAlgorithmThread.isAlive())
                || (currentExternalProcess != null
                && currentExternalProcess.isAlive());
    }

    /**
     * Attempts to kill whichever execution is currently running (external
     * process or internal thread). Sets the {@code userRequestedStop} flag
     * so that the killer thread does not call this method again after the
     * user has already clicked Stop manually. Also notifies the controller via
     * {@link AlgorithmRunnerController#notifyWasStopped()} so it can record a
     * STOPPED history entry rather than treating the termination as a failure.
     *
     * <p>For internal threads the strategy is:</p>
     * <ol>
     *   <li>Call {@link NotifyingThread#cancel()} (sets a flag and calls
     *       {@code interrupt()}) and wait up to 100 ms for the thread to
     *       notice and exit cleanly.</li>
     *   <li>If the thread is still alive, attempt to call {@code Thread.stop()}
     *       via reflection so that the code compiles on modern JVMs where the
     *       method has been removed at the source level.</li>
     *   <li>If {@code stop()} does not exist ({@link NoSuchMethodException}),
     *       show a warning dialog explaining that forced termination is not
     *       available.</li>
     *   <li>If {@code stop()} exists but throws when invoked, the reflection
     *       layer wraps the real cause in an {@link InvocationTargetException}.
     *       The cause is unwrapped: an {@link UnsupportedOperationException}
     *       produces the same error dialog that the original code showed; any
     *       other cause is logged to stderr.</li>
     *   <li>After all stop attempts, check if the thread is actually dead. If
     *       it is still alive (because stop is unavailable or failed), do NOT
     *       reset the UI — leave the Stop button visible so the user can retry
     *       or restart the application. If the thread did die, reset the UI
     *       normally.</li>
     * </ol>
     *
     * @param panel the runner panel whose UI should be reset after stopping
     *              (only if the stop actually succeeded)
     * @return true if something was running and a stop was attempted,
     *         false if nothing was running
     */
    public boolean tryToKillProcess(AlgorithmRunnerPanel panel) {
        // Set the flag so the killer thread knows the user clicked Stop manually
        // and does not call tryToKillProcess again, which would show duplicate dialogs
        userRequestedStop = true;

        // If an external process is running, destroy it forcibly
        if (currentExternalProcess != null && currentExternalProcess.isAlive()) {
            currentExternalProcess.destroyForcibly();
            // Notify the controller that this was a deliberate stop so that a
            // STOPPED history entry is created rather than no entry at all
            if (controller != null) {
                controller.notifyWasStopped();
            }
            panel.postStatusMessage("Algorithm stopped. \n");
            panel.resetUIAfterThreadCompletion();
            return true;
        }

        // If a thread is already running (the user clicked the stop button)
        if (currentRunningAlgorithmThread != null
                && currentRunningAlgorithmThread.isAlive()) {

            // Step 1: Try modern cooperative cancellation first.
            // This sets the cancellation flag and calls interrupt().
            currentRunningAlgorithmThread.cancel();

            // Step 2: Give the thread a brief moment to notice the flag and exit cleanly
            try {
                currentRunningAlgorithmThread.join(100); // Wait up to 100 ms
            } catch (InterruptedException e) {
                // Restore interrupt status on the current (EDT) thread
                Thread.currentThread().interrupt();
            }

            // Step 3: If cooperative cancellation didn't work, try the deprecated
            // stop() method as a fallback via reflection so the code compiles on
            // modern JVMs where the method has been removed at the source level.
            if (currentRunningAlgorithmThread.isAlive()) {
                try {
                    Method stopMethod = Thread.class.getMethod("stop");
                    stopMethod.invoke(currentRunningAlgorithmThread);

                } catch (NoSuchMethodException e) {
                    // Thread.stop() has been completely removed from this JVM (Java 21+).
                    // The thread has been interrupted but may keep running if the
                    // algorithm does not check Thread.interrupted().
                    javax.swing.JOptionPane.showMessageDialog(null,
                            "Stopping an algorithm is not supported for Java version "
                                    + System.getProperty("java.version") + ".\n",
                            "Algorithm Stop Warning",
                            javax.swing.JOptionPane.WARNING_MESSAGE);

                } catch (InvocationTargetException e) {
                    // stop() exists in this JVM but threw when called.
                    // Unwrap the real cause from the reflection wrapper.
                    Throwable cause = e.getCause();
                    if (cause instanceof UnsupportedOperationException) {
                        // This is exactly what the original code caught directly.
                        // Preserve the original error dialog message word for word.
                        javax.swing.JOptionPane.showMessageDialog(null,
                                "Stopping an algorithm is not supported for Java version "
                                        + System.getProperty("java.version"),
                                "Error",
                                javax.swing.JOptionPane.ERROR_MESSAGE);
                    } else {
                        // Some other exception was thrown inside stop() itself
                        System.err.println(
                                "Unexpected exception while stopping thread: " + cause);
                    }

                } catch (Exception e) {
                    // Reflection setup failed (e.g. IllegalAccessException).
                    // Log but do not show a dialog since the interrupt was already sent.
                    System.err.println(
                            "Failed to invoke Thread.stop() via reflection: " + e);
                }
            }

            // Step 4: Check if the thread actually died. If stop() was not available
            // or failed, the thread may still be running. In that case we MUST NOT
            // reset the UI or the user will be stuck — they won't be able to run
            // anything new because the next Run click will call tryToKillProcess,
            // find the zombie thread still alive, and abort the new run.
            if (currentRunningAlgorithmThread.isAlive()) {
                // The thread is still running despite our best efforts.
                // Do NOT reset the UI. Leave the Stop button visible.
                // The user can click Stop again or restart the application.
                panel.postStatusMessage(
                        "Warning: The algorithm thread could not be stopped and is still running.\n");
                return true; // We attempted to stop it
            } else {
                // The thread successfully stopped (either via cancel, via stop,
                // or it happened to finish on its own during the 100ms join).
                // Notify the controller that this was a deliberate stop so that a
                // STOPPED history entry is created rather than no entry at all.
                if (controller != null) {
                    controller.notifyWasStopped();
                }
                // Now it is safe to reset the UI.
                panel.postStatusMessage("Algorithm stopped. \n");
                panel.resetUIAfterThreadCompletion();
                return true;
            }
        }

        return false;
    }

    /**
     * Runs the selected algorithm as an internal thread using
     * {@link CommandProcessor}. The thread completion listener and uncaught
     * exception handler are both set to the supplied controller before the
     * thread is started. The {@code userRequestedStop} flag is reset to false.
     *
     * @param choice     the algorithm name
     * @param inputFile  the path to the input file, or null
     * @param outputFile the path to the output file, or null
     * @param parameters the array of parameter values
     * @param listener   the object to notify when the thread finishes
     * @param exHandler  the handler to call if the thread throws
     */
    public void runInternal(final String choice,
                            final String inputFile,
                            final String outputFile,
                            final String[] parameters,
                            ThreadCompleteListener listener,
                            UncaughtExceptionHandler exHandler) {

        // Reset the manual-stop flag for this new run so the killer thread
        // can call tryToKillProcess if the time limit is exceeded
        userRequestedStop = false;

        currentRunningAlgorithmThread = new NotifyingThread() {
            @Override
            public boolean doRun() throws Exception {
                CommandProcessor.runAlgorithm(choice, inputFile, outputFile, parameters);
                return true;
            }
        };
        // The main thread will listen for the completion of the algorithm
        currentRunningAlgorithmThread.addListener(listener);
        // The main thread will also listen for exception generated by the algorithm
        currentRunningAlgorithmThread.setUncaughtExceptionHandler(exHandler);
        // Run the thread
        currentRunningAlgorithmThread.start();

        if (choice.equals("MemoryViewer")) {
            currentRunningAlgorithmThread = null;
        }

        panel.redirectOutputStream();
    }

    /**
     * Runs the selected algorithm as an external JVM process by invoking
     * {@code spmf.jar}. The child process's standard output is piped back to
     * the console panel line by line via the panel's
     * {@link AlgorithmRunnerPanel#appendConsoleLine(String)} method. Thread
     * completion and uncaught-exception handling are delegated to the supplied
     * controller. The {@code userRequestedStop} flag is reset to false.
     *
     * @param choice     the algorithm name
     * @param inputFile  the path to the input file, or null
     * @param outputFile the path to the output file, or null
     * @param parameters the array of parameter values
     * @param listener   the object to notify when the thread finishes
     * @param exHandler  the handler to call if the thread throws
     */
    public void runExternal(final String choice,
                            final String inputFile,
                            final String outputFile,
                            final String[] parameters,
                            ThreadCompleteListener listener,
                            UncaughtExceptionHandler exHandler) {

        // Reset the manual-stop flag for this new run so the killer thread
        // can call tryToKillProcess if the time limit is exceeded
        userRequestedStop = false;

        currentRunningAlgorithmThread = new NotifyingThread() {
            @Override
            public boolean doRun() throws Exception {
                List<String> commandWithParameters = new ArrayList<>(15);
                commandWithParameters.add("java");
                commandWithParameters.add("-jar");
                commandWithParameters.add("spmf.jar");
                commandWithParameters.add("run");
                commandWithParameters.add(choice);
                if (inputFile != null) {
                    commandWithParameters.add(inputFile);
                }
                if (outputFile != null) {
                    commandWithParameters.add(outputFile);
                }
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i] != null && !parameters[i].isEmpty()) {
                        commandWithParameters.add(parameters[i]);
                    }
                }

                // Call the JAR file to run the algorithm
                System.out.println("===== RUN AS EXTERNAL PROGRAM ========");
                StringBuffer singleLineCommand = new StringBuffer(80);
                singleLineCommand.append(" COMMAND: ");
                for (String value : commandWithParameters) {
                    singleLineCommand.append(value);
                    singleLineCommand.append(" ");
                }
                System.out.println(singleLineCommand);

                ProcessBuilder pb = new ProcessBuilder(commandWithParameters);
                pb.redirectOutput(ProcessBuilder.Redirect.PIPE);  //  BUG FIX 2024-5-20
                pb.redirectError(ProcessBuilder.Redirect.PIPE);   //  BUG FIX 2024-5-20

                int exitValue = 1;
                try {
                    currentExternalProcess = pb.start();

                    // Capture the output stream and forward it to the console panel
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    currentExternalProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Forward each output line to the console panel via the
                        // panel's delegation method rather than a raw field access
                        panel.appendConsoleLine(line);
                    }

                    exitValue = currentExternalProcess.waitFor();
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            System.lineSeparator() + System.lineSeparator()
                                    + "I/O Error.");
                }
                return (exitValue == 0);
            }
        };
        // The main thread will listen for the completion of the algorithm
        currentRunningAlgorithmThread.addListener(listener);
        // The main thread will also listen for exception generated by the algorithm
        currentRunningAlgorithmThread.setUncaughtExceptionHandler(exHandler);
        // Run the thread
        currentRunningAlgorithmThread.start();
    }

    /**
     * Starts a background killer thread that monitors the running algorithm and
     * forcibly stops it if it exceeds {@code maxSeconds}. Has no effect if
     * {@code maxSeconds} is not positive. The killer thread checks the
     * {@code userRequestedStop} flag before calling {@code tryToKillProcess}
     * so that duplicate "cannot stop" dialogs do not appear if the user has
     * already clicked Stop manually. When the killer thread does stop the
     * algorithm, {@link #tryToKillProcess(AlgorithmRunnerPanel)} notifies the
     * controller via {@link AlgorithmRunnerController#notifyWasStopped()} so
     * that a STOPPED history entry is recorded.
     *
     * @param maxSeconds the maximum number of seconds to allow before killing
     * @param panel      the runner panel whose UI is reset after killing
     */
    public void startKillerThreadIfNeeded(final int maxSeconds,
                                          final AlgorithmRunnerPanel panel) {
        if (maxSeconds <= 0) {
            return;
        }

        // Create the killer thread
        NotifyingThread killerThread = new NotifyingThread() {
            @Override
            public boolean doRun() throws Exception {
                int secondsElapsed = 0;

                // While the algorithm is still running
                while ((currentRunningAlgorithmThread != null
                        && currentRunningAlgorithmThread.isAlive())
                        || (currentExternalProcess != null
                        && currentExternalProcess.isAlive())) {

                    // Wait one second
                    Thread.sleep(1000);

                    // Increase number of seconds elapsed by 1
                    secondsElapsed++;

                    // If time is up
                    if (secondsElapsed >= maxSeconds) {
                        // Check if the user already clicked Stop manually.
                        // If they did, do not call tryToKillProcess again —
                        // it would show duplicate "cannot stop" dialogs.
                        if (!userRequestedStop) {
                            // Try to kill the algorithm. tryToKillProcess will
                            // call controller.notifyWasStopped() internally so
                            // the history entry is recorded with status STOPPED.
                            boolean killed = tryToKillProcess(panel);
                            if (killed) {
                                System.out.println(
                                        " Stopped because of time limit of "
                                                + maxSeconds + " seconds");
                            }
                        }
                        // Either way, exit the killer thread now — we tried once
                        break;
                    }
                }
                return false;
            }
        };
        // Run the killer thread
        killerThread.start();
    }
}