package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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

import java.util.List;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * Utility class that builds and shows a popup menu of recent file paths.
 *
 * @author Philippe Fournier-Viger
 */
public class RecentFilesPopup {

    /**
     * Shows a popup menu anchored to the given component listing the provided recent file paths.
     * When the user clicks an entry, the consumer is called with the chosen path.
     *
     * @param anchor        the component below which the popup appears.
     * @param recentFiles   the list of recent file paths to display.
     * @param onFileChosen  consumer called with the selected file path.
     */
    public static void show(java.awt.Component anchor,
                     List<String> recentFiles,
                     Consumer<String> onFileChosen) {
        JPopupMenu popup = new JPopupMenu();

        if (recentFiles == null || recentFiles.isEmpty()) {
            JMenuItem empty = new JMenuItem("(no recent files)");
            empty.setEnabled(false);
            popup.add(empty);
        } else {
            for (String path : recentFiles) {
                JMenuItem item = new JMenuItem(path);
                item.setToolTipText(path);
                item.addActionListener(e -> onFileChosen.accept(path));
                popup.add(item);
            }
        }

        popup.show(anchor, 0, anchor.getHeight());
    }
}