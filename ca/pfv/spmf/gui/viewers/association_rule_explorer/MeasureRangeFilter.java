package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */

/**
 * One filter row: measure name, editable minimum, range slider, editable
 * maximum.
 * 
 * @author Philippe Fournier-Viger
 */
public class MeasureRangeFilter extends JPanel {

	private static final long serialVersionUID = 1L;

	private final String measure;
	private final double min;
	private final double max;
	private final RangeSlider slider = new RangeSlider();
	private final JTextField minField = new JTextField(6);
	private final JTextField maxField = new JTextField(6);
	private final JLabel nameLabel;
	private final Runnable onChange;
	private boolean updating = false;

	/**
	 * @param measure  the measure name
	 * @param range    {min, max} of the measure over all rules
	 * @param onChange called whenever the selected range changes
	 */
	public MeasureRangeFilter(String measure, double[] range, Runnable onChange) {
		super(new BorderLayout(4, 0));
		this.measure = measure;
		this.min = range[0];
		this.max = range[1];
		this.onChange = onChange;

		nameLabel = new JLabel(MeasureUtils.prettyName(measure), SwingConstants.LEFT);
		nameLabel.setToolTipText("<html>" + MeasureUtils.describe(measure) + "<br><i>Range in this file: "
				+ MeasureUtils.format(min) + " to " + MeasureUtils.format(max) + "</i></html>");
		nameLabel.setPreferredSize(new Dimension(88, 22));

		minField.setToolTipText("Minimum value (press Enter to apply)");
		maxField.setToolTipText("Maximum value (press Enter to apply)");
		minField.setHorizontalAlignment(JTextField.RIGHT);
		maxField.setHorizontalAlignment(JTextField.RIGHT);

		slider.addChangeListener(e -> {
			if (!updating) {
				syncFields();
				onChange.run();
			}
		});
		minField.addActionListener(e -> applyFields());
		maxField.addActionListener(e -> applyFields());
		FocusAdapter focus = new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				applyFields();
			}
		};
		minField.addFocusListener(focus);
		maxField.addFocusListener(focus);

		JPanel center = new JPanel(new BorderLayout(3, 0));
		center.add(minField, BorderLayout.WEST);
		center.add(slider, BorderLayout.CENTER);
		center.add(maxField, BorderLayout.EAST);
		add(nameLabel, BorderLayout.WEST);
		add(center, BorderLayout.CENTER);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		setAlignmentX(LEFT_ALIGNMENT);
		syncFields();
	}

	private double valueOf(int step) {
		return min + (max - min) * step / (double) RangeSlider.MAX;
	}

	private int stepOf(double v) {
		if (max - min < 1e-12) {
			return 0;
		}
		double t = (v - min) / (max - min);
		return (int) Math.round(Math.max(0, Math.min(1, t)) * RangeSlider.MAX);
	}

	private void syncFields() {
		updating = true;
		try {
			minField.setText(MeasureUtils.format(getMin()));
			maxField.setText(MeasureUtils.format(getMax()));
			boolean active = isActive();
			nameLabel.setFont(nameLabel.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
			nameLabel.setForeground(active ? new Color(30, 90, 150) : Color.BLACK);
		} finally {
			updating = false;
		}
	}

	private void applyFields() {
		if (updating) {
			return;
		}
		try {
			double lo = ARuleReader.parseNumber(minField.getText());
			double hi = ARuleReader.parseNumber(maxField.getText());
			slider.setValues(stepOf(lo), stepOf(hi)); // fires change if different
		} catch (NumberFormatException ex) {
			// ignore invalid input
		}
		syncFields();
	}

	public String getMeasure() {
		return measure;
	}

	public double getMin() {
		return slider.getLow() == 0 ? min : valueOf(slider.getLow());
	}

	public double getMax() {
		return slider.getHigh() == RangeSlider.MAX ? max : valueOf(slider.getHigh());
	}

	/** @return true if the filter restricts something */
	public boolean isActive() {
		return !slider.isFullRange();
	}

	public void reset() {
		slider.setValues(0, RangeSlider.MAX);
	}

	public int[] getSliderValues() {
		return new int[] { slider.getLow(), slider.getHigh() };
	}

	public void setSliderValues(int[] values) {
		slider.setValues(values[0], values[1]);
	}
}
