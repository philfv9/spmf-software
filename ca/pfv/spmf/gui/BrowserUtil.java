package ca.pfv.spmf.gui;

import java.awt.Component;
import java.awt.Desktop;
import java.net.URI;

import javax.swing.JOptionPane;
import javax.swing.JTextField;

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
 * Opens web pages in the system browser, with a graceful fallback (a dialog
 * showing the address) when no browser integration is available.
 */
public final class BrowserUtil {

    private BrowserUtil() {
    }

    /**
     * Opens the given URL in the default browser. If that is not possible,
     * shows a dialog from which the user can copy the address.
     *
     * @param parent component used to position the fallback dialog (may be null)
     * @param url    the address to open
     */
    public static void open(Component parent, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception e) {
            System.err.println("Could not open browser: " + e.getMessage());
        }
        JTextField field = new JTextField(url);
        field.setEditable(false);
        JOptionPane.showMessageDialog(parent, field,
                "Open this address in your browser", JOptionPane.INFORMATION_MESSAGE);
    }
}