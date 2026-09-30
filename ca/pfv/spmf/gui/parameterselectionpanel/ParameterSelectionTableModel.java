package ca.pfv.spmf.gui.parameterselectionpanel;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * Table model used by {@link ParameterSelectionPanel}.
 *
 * <p>The model holds the raw text typed by the user for each parameter. It
 * also centralises the small amount of "type knowledge" shared by the panel
 * (which types are boolean / integer / decimal, what a syntactically valid
 * value looks like, and how a value is normalised before being handed to an
 * algorithm).</p>
 *
 * <p>Normalisation performed by {@link #getParameterValues()}:</p>
 * <ul>
 *   <li>Boolean values are trimmed and lower-cased.</li>
 *   <li>Numeric values are trimmed.</li>
 *   <li>Decimal values written as a percentage (e.g. {@code "40%"}) are
 *       converted to the equivalent fraction (e.g. {@code "0.4"}).</li>
 *   <li>Other values (typically {@code String}) are returned untouched.</li>
 * </ul>
 *
 * @see ParameterSelectionPanel
 * @author Philippe Fournier-Viger, 2024.
 */
@SuppressWarnings("serial")
public class ParameterSelectionTableModel extends AbstractTableModel {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Index of the "Parameter" column */
    public static final int COL_PARAMETER = 0;

    /** Index of the "Value" column */
    public static final int COL_VALUE = 1;

    /** Index of the "Example" column */
    public static final int COL_EXAMPLE = 2;

    /** Names of the columns */
    private static final String[] COLUMN_NAMES = { "Parameter", "Value", "Example" };

    /** A complete, syntactically valid integer */
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    /**
     * A complete, syntactically valid decimal number, optionally in
     * scientific notation, optionally followed by a {@code %} sign.
     */
    private static final Pattern DECIMAL_PATTERN =
            Pattern.compile("-?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?%?");

    // -----------------------------------------------------------------------
    // Instance fields
    // -----------------------------------------------------------------------

    /** The descriptions of the parameters (never null) */
    private DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];

    /** The raw values typed by the user, parallel to {@link #parameters} */
    private String[] values = new String[0];

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param descriptionOfAlgorithm the description of an algorithm, or
     *                               {@code null} for an empty model
     */
    public ParameterSelectionTableModel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
        setData(descriptionOfAlgorithm == null
                ? null
                : descriptionOfAlgorithm.getParametersDescription());
    }

    // -----------------------------------------------------------------------
    // AbstractTableModel overrides
    // -----------------------------------------------------------------------

    @Override
    public int getRowCount() {
        return parameters.length;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }

    /**
     * Returns the raw content of a cell. The Parameter column returns the
     * plain parameter name; any decoration (such as an "(optional)" marker)
     * is the responsibility of the view's renderer.
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DescriptionOfParameter parameter = parameters[rowIndex];
        switch (columnIndex) {
            case COL_PARAMETER:
                return parameter.getName();
            case COL_EXAMPLE:
                return parameter.example;
            case COL_VALUE:
            default:
                return values[rowIndex];
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == COL_VALUE;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex != COL_VALUE) {
            return;
        }
        String newValue = aValue == null ? null : aValue.toString();
        if (Objects.equals(newValue, values[rowIndex])) {
            return;
        }
        values[rowIndex] = newValue;
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the description of the parameter at the given row.
     *
     * @param row the row index
     * @return the parameter description, or {@code null} if out of range
     */
    public DescriptionOfParameter getParameterAt(int row) {
        return (row < 0 || row >= parameters.length) ? null : parameters[row];
    }

    /**
     * Returns the raw (un-normalised) text currently stored for a row.
     *
     * @param row the row index
     * @return the raw value, possibly {@code null}
     */
    public String getRawValue(int row) {
        return (row < 0 || row >= values.length) ? null : values[row];
    }

    /**
     * Replaces the parameter list with a new one (typically because another
     * algorithm was selected).
     *
     * <p>Previously entered values are carried over when it makes sense:
     * first by parameter <em>name</em> (e.g. a "minsup" value follows the
     * user from one algorithm to the next), then by <em>position</em>. In
     * both cases the old value is only kept if it is syntactically valid for
     * the new parameter's declared type, so that e.g. {@code "true"} never
     * ends up pre-filled in a {@code Double} field.</p>
     *
     * @param newParameters the new parameter descriptions (may be null)
     */
    public void setData(DescriptionOfParameter[] newParameters) {
        if (newParameters == null) {
            newParameters = new DescriptionOfParameter[0];
        }

        DescriptionOfParameter[] oldParameters = parameters;
        String[] oldValues = values;
        String[] newValues = new String[newParameters.length];

        // Index the old non-blank values by parameter name
        Map<String, String> oldValuesByName = new HashMap<>();
        for (int i = 0; i < oldParameters.length; i++) {
            DescriptionOfParameter p = oldParameters[i];
            if (p != null && p.getName() != null && !isBlank(oldValues[i])) {
                oldValuesByName.putIfAbsent(p.getName(), oldValues[i]);
            }
        }

        for (int i = 0; i < newParameters.length; i++) {
            DescriptionOfParameter p = newParameters[i];
            if (p == null) {
                continue;
            }
            Class<?> type = p.getParameterType();

            String byName = oldValuesByName.get(p.getName());
            if (!isBlank(byName) && isValidValueForType(byName, type)) {
                newValues[i] = byName;
                continue;
            }
            if (i < oldValues.length) {
                String byPosition = oldValues[i];
                if (!isBlank(byPosition) && isValidValueForType(byPosition, type)) {
                    newValues[i] = byPosition;
                }
            }
        }

        parameters = newParameters;
        values = newValues;
        fireTableDataChanged();
    }

    /**
     * Pre-populates the Value column (used by the run-history re-run feature).
     * Extra values are ignored; missing values leave the cell untouched.
     *
     * @param parameterValues the values to restore; ignored if null or empty
     */
    public void setParameterValues(String[] parameterValues) {
        if (parameterValues == null || parameterValues.length == 0) {
            return;
        }
        int n = Math.min(values.length, parameterValues.length);
        System.arraycopy(parameterValues, 0, values, 0, n);
        fireTableDataChanged();
    }

    /**
     * Returns the normalised parameter values to pass to the algorithm.
     * Optional parameters left blank are omitted. Required parameters left
     * blank are returned as {@code null} (use
     * {@link ParameterSelectionPanel#hasAllRequiredValuesFilled()} to check
     * beforehand).
     *
     * @return an array of values (never null; elements may be null)
     */
    public String[] getParameterValues() {
        List<String> result = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            DescriptionOfParameter p = parameters[i];
            String raw = values[i];
            if (p.isOptional() && isBlank(raw)) {
                continue;
            }
            result.add(normalizeValue(raw, p.getParameterType()));
        }
        return result.toArray(new String[0]);
    }

    // -----------------------------------------------------------------------
    // Type helpers (shared with the panel)
    // -----------------------------------------------------------------------

    /** @return true if {@code type} is {@code Boolean}/{@code boolean} */
    static boolean isBooleanType(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    /** @return true if {@code type} is an integer-like type */
    static boolean isIntegerType(Class<?> type) {
        return type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Short.class || type == short.class;
    }

    /** @return true if {@code type} is a decimal type */
    static boolean isDecimalType(Class<?> type) {
        return type == Double.class || type == double.class
                || type == Float.class || type == float.class;
    }

    /** @return true if {@code type} is integer-like or decimal */
    static boolean isNumericType(Class<?> type) {
        return isIntegerType(type) || isDecimalType(type);
    }

    /** @return true if {@code s} is null or contains only whitespace */
    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Returns true if {@code value} is a complete, syntactically valid value
     * for the given type. Unknown types always return true.
     *
     * @param value the value to check
     * @param type  the declared parameter type (may be null)
     * @return true if acceptable
     */
    static boolean isValidValueForType(String value, Class<?> type) {
        if (value == null) {
            return false;
        }
        if (type == null) {
            return true;
        }
        String v = value.trim();
        if (isBooleanType(type)) {
            return "true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v);
        }
        if (isIntegerType(type)) {
            return INTEGER_PATTERN.matcher(v).matches();
        }
        if (isDecimalType(type)) {
            return DECIMAL_PATTERN.matcher(v).matches();
        }
        return true;
    }

    /**
     * Normalises a raw value for the given type (see class documentation).
     * Values that cannot be normalised are returned as-is.
     *
     * @param value the raw value (may be null)
     * @param type  the declared parameter type (may be null)
     * @return the normalised value
     */
    static String normalizeValue(String value, Class<?> type) {
        if (value == null || type == null) {
            return value;
        }
        if (isBooleanType(type)) {
            String v = value.trim();
            return ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v))
                    ? v.toLowerCase(Locale.ROOT) : value;
        }
        if (isIntegerType(type)) {
            return value.trim();
        }
        if (isDecimalType(type)) {
            String v = value.trim();
            if (v.endsWith("%")) {
                try {
                    return new BigDecimal(v.substring(0, v.length() - 1).trim())
                            .movePointLeft(2)
                            .stripTrailingZeros()
                            .toPlainString();
                } catch (NumberFormatException e) {
                    return v; // let the algorithm report the problem
                }
            }
            return v;
        }
        return value;
    }
}