package ca.pfv.spmf.gui.preferences;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * The main method of this class resets all the input/output file paths and
 * other preferences that are stored in the registry by the GUI version of SPMF.
 * This includes clearing the recent input-file and output-file lists that are
 * maintained by {@link PreferencesManager}.
 *
 * <p>This class is intended for developers only.</p>
 *
 * @author Philippe Fournier-Viger
 */
class ResetPreferences {

    /**
     * Resets all SPMF GUI preferences to their default values, including the
     * recent input-file and output-file lists.
     *
     * @param args command-line arguments (ignored)
     */
    public static void main(String[] args) {
        PreferencesManager.getInstance().resetPreferences();
    }
}