package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * One row of the filter panel: the name of a measure, an operator (≥ / ≤), an
 * editable threshold value, and a {@link MeasureDistributionBar} slider that
 * spans the whole width. The slider at its minimum position means "no filter
 * on this measure". The value field and the slider are kept in sync, and a
 * callback is fired whenever the effective filter changes.
 *
 * @author Philippe Fournier-Viger
 */
class MeasureFilterRow extends JPanel {

	private static final long serialVersionUID = 1L;

	static final String GREATER_OR_EQUAL = "\u2265";
	static final String LESS_OR_EQUAL = "\u2264";

	private final String measure;
	private final double min;
	private final double max;
	private final JLabel nameLabel;
	private final JComboBox<String> operatorCombo;
	private final PlaceholderTextField valueField;
	private final MeasureDistributionBar bar;
	private final Runnable onChange;
	/** True while the row is being reset programmatically */
	private boolean updating;

	/**
	 * @param measure  name of the measure
	 * @param min      minimum value of the measure
	 * @param max      maximum value of the measure
	 * @param onChange called when the filter defined by this row changes
	 */
	MeasureFilterRow(String measure, double min, double max, Runnable onChange) {
		super(new GridBagLayout());
		this.measure = measure;
		this.min = min;
		this.max = max;
		this.onChange = onChange;
		setOpaque(false);

		nameLabel = new JLabel(measure);
		nameLabel.setFont(ViewerStyle.measureNameFont());
		nameLabel.setForeground(ViewerStyle.TEXT_PRIMARY);
		nameLabel.setToolTipText("<html><b>" + ViewerStyle.escapeHtml(measure) + "</b><br>range: "
				+ ViewerStyle.formatNumber(min) + " \u2013 " + ViewerStyle.formatNumber(max) + "</html>");

		operatorCombo = new JComboBox<>(new String[] { GREATER_OR_EQUAL, LESS_OR_EQUAL });
		operatorCombo.setFont(ViewerStyle.measureValueFont());
		operatorCombo.setPreferredSize(new Dimension(52, 22));
		operatorCombo.setToolTipText("Keep patterns whose value is \u2265 or \u2264 the threshold");
		operatorCombo.setFocusable(false);

		valueField = new PlaceholderTextField("any", 7);
		valueField.setFont(ViewerStyle.measureValueFont());
		valueField.setHorizontalAlignment(JTextField.RIGHT);
		valueField.setToolTipText("Threshold \u2013 type a value and press Enter, or drag the slider");

		bar = new MeasureDistributionBar(min, max);

		GridBagConstraints c = new GridBagConstraints();
		c.gridy = 0;
		c.gridx = 0;
		c.weightx = 1;
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		add(nameLabel, c);
		c.gridx = 1;
		c.weightx = 0;
		c.fill = GridBagConstraints.NONE;
		c.insets = new Insets(0, 4, 0, 0);
		add(operatorCombo, c);
		c.gridx = 2;
		add(valueField, c);
		c.gridy = 1;
		c.gridx = 0;
		c.gridwidth = 3;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(2, 0, 0, 0);
		add(bar, c);

		bar.addChangeListener(e -> {
			syncFromBar();
			if (!bar.getValueIsAdjusting()) {
				fire();
			}
		});
		operatorCombo.addActionListener(e -> {
			syncFromBar();
			if (isActive()) {
				fire();
			}
		});
		valueField.addActionListener(e -> applyTypedValue());
		valueField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				applyTypedValue();
			}
		});
		syncFromBar();
	}

	/** @return the measure name */
	String getMeasure() {
		return measure;
	}

	/** @return true if this row currently defines a filter */
	boolean isActive() {
		return bar.getValue() > bar.getMinimum();
	}

	/** @return the current threshold (only meaningful when active) */
	double getThreshold() {
		int range = bar.getMaximum() - bar.getMinimum();
		if (range <= 0) {
			return min;
		}
		double ratio = (bar.getValue() - bar.getMinimum()) / (double) range;
		return min + ratio * (max - min);
	}

	/** @return the selected operator string (≥ or ≤) */
	String getOperator() {
		return (String) operatorCombo.getSelectedItem();
	}

	/** @return the distribution bar (to feed it with fresh values) */
	MeasureDistributionBar getBar() {
		return bar;
	}

	/** Removes the filter of this row without firing the callback. */
	void reset() {
		updating = true;
		try {
			bar.setValue(bar.getMinimum());
			operatorCombo.setSelectedItem(GREATER_OR_EQUAL);
			syncFromBar();
		} finally {
			updating = false;
		}
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private void fire() {
		if (!updating && onChange != null) {
			onChange.run();
		}
	}

	private void syncFromBar() {
		if (isActive()) {
			String v = ViewerStyle.formatNumber(getThreshold());
			valueField.setText(v);
			nameLabel.setForeground(ViewerStyle.ACCENT_DARK);
			bar.setToolTipText(measure + " " + getOperator() + " " + v + "  (drag to adjust)");
		} else {
			valueField.setText("");
			nameLabel.setForeground(ViewerStyle.TEXT_PRIMARY);
			bar.setToolTipText("Drag to set a threshold on " + measure);
		}
	}

	private void applyTypedValue() {
		String text = valueField.getText().trim();
		if (text.isEmpty()) {
			if (isActive()) {
				bar.setValue(bar.getMinimum());
			}
			return;
		}
		Double v = ViewerStyle.parseDouble(text);
		if (v == null) {
			syncFromBar();
			return;
		}
		double clamped = Math.max(min, Math.min(max, v));
		int range = bar.getMaximum() - bar.getMinimum();
		int pos = (max > min) ? bar.getMinimum() + (int) Math.round((clamped - min) / (max - min) * range)
				: bar.getMinimum();
		if (pos == bar.getValue()) {
			syncFromBar();
		} else {
			bar.setValue(pos);
		}
	}
}