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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Utility class that builds and shows a popup menu of recent file paths.
 *
 * <p>Each entry is numbered (1–9 get a keyboard mnemonic so the user can pick
 * a file with a single key press while the popup is open). Long paths are
 * abbreviated in the label while the full path is shown in the tooltip.
 * Files that no longer exist on disk are shown greyed out in italics.</p>
 *
 * @author Philippe Fournier-Viger
 */
public final class RecentFilesPopup {

    /** Maximum number of characters of a path shown in a menu item label */
    private static final int MAX_LABEL_LENGTH = 70;

    /** Ellipsis character used when a path is abbreviated */
    private static final String ELLIPSIS = "\u2026";

    /** Label shown when there are no recent files */
    private static final String LABEL_EMPTY = "(no recent files)";

    /** Label of the optional "clear" entry */
    private static final String LABEL_CLEAR = "Clear recent files";

    /** Not instantiable */
    private RecentFilesPopup() {
    }

    /**
     * Shows a popup menu anchored below the given component listing the
     * provided recent file paths. When the user clicks an entry, the consumer
     * is called with the chosen (full) path.
     *
     * @param anchor       the component below which the popup appears
     * @param recentFiles  the list of recent file paths to display (may be null or empty)
     * @param onFileChosen consumer called with the selected file path
     */
    public static void show(Component anchor,
                            List<String> recentFiles,
                            Consumer<String> onFileChosen) {
        show(anchor, recentFiles, onFileChosen, null);
    }

    /**
     * Shows a popup menu anchored below the given component listing the
     * provided recent file paths, optionally followed by a "Clear recent
     * files" entry.
     *
     * @param anchor           the component below which the popup appears
     * @param recentFiles      the list of recent file paths to display (may be null or empty)
     * @param onFileChosen     consumer called with the selected file path
     * @param onClearRequested callback invoked when the user picks
     *                         "Clear recent files"; if null, that entry is omitted
     */
    public static void show(Component anchor,
                            List<String> recentFiles,
                            Consumer<String> onFileChosen,
                            Runnable onClearRequested) {
        // JPopupMenu.show() throws if the anchor is not on screen
        if (anchor == null || !anchor.isShowing()) {
            return;
        }

        JPopupMenu popup = new JPopupMenu();
        int count = 0;

        if (recentFiles != null) {
            for (String path : recentFiles) {
                if (path == null || path.trim().isEmpty()) {
                    continue;
                }
                count++;
                popup.add(createFileItem(path, count, onFileChosen));
            }
        }

        if (count == 0) {
            JMenuItem empty = new JMenuItem(LABEL_EMPTY);
            empty.setEnabled(false);
            popup.add(empty);
        } else if (onClearRequested != null) {
            popup.addSeparator();
            JMenuItem clear = new JMenuItem(LABEL_CLEAR);
            clear.setMnemonic(KeyEvent.VK_C);
            clear.addActionListener(e -> onClearRequested.run());
            popup.add(clear);
        }

        popup.show(anchor, 0, anchor.getHeight());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds the menu item for one recent file.
     *
     * @param path         the full file path
     * @param number       the 1-based position in the list
     * @param onFileChosen callback for selection (may be null)
     * @return the configured menu item
     */
    private static JMenuItem createFileItem(String path, int number, Consumer<String> onFileChosen) {
        boolean exists = new File(path).exists();

        // Prefix with the number so the mnemonic has a visible character
        String prefix = number <= 9 ? number + "  " : "    ";
        JMenuItem item = new JMenuItem(prefix + abbreviate(path, MAX_LABEL_LENGTH));

        if (number <= 9) {
            // KeyEvent.VK_1 .. VK_9 have the same values as the chars '1'..'9'
            item.setMnemonic(KeyEvent.VK_0 + number);
        }

        if (exists) {
            item.setToolTipText(path);
        } else {
            item.setToolTipText(path + "  (file not found)");
            item.setFont(item.getFont().deriveFont(Font.ITALIC));
            item.setForeground(Color.GRAY);
        }

        if (onFileChosen != null) {
            item.addActionListener(e -> onFileChosen.accept(path));
        }
        return item;
    }

    /**
     * Shortens a path for display by replacing its middle with an ellipsis.
     * The file name (last path segment) is preserved intact whenever it fits,
     * so that the most meaningful part of the path remains readable.
     *
     * @param path      the full path
     * @param maxLength the maximum length of the returned string
     * @return the original path if short enough, otherwise an abbreviated form
     */
    static String abbreviate(String path, int maxLength) {
        if (path == null || path.length() <= maxLength) {
            return path;
        }
        if (maxLength < 5) {
            return path.substring(0, Math.max(0, maxLength - 1)) + ELLIPSIS;
        }

        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String fileName = lastSep >= 0 ? path.substring(lastSep) : "";

        // Keep the file name whole if we can still show a bit of the directory
        if (!fileName.isEmpty() && fileName.length() <= maxLength - 4) {
            int headLength = maxLength - fileName.length() - ELLIPSIS.length();
            return path.substring(0, headLength) + ELLIPSIS + fileName;
        }

        // Fallback: plain middle ellipsis
        int keep = maxLength - ELLIPSIS.length();
        int head = keep / 2;
        int tail = keep - head;
        return path.substring(0, head) + ELLIPSIS + path.substring(path.length() - tail);
    }
}