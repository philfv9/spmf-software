package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 * See http://www.philippe-fournier-viger.com/spmf for the full license header.
 */
/**
 * Data model for one algorithm node: SPMF metadata, normalised input/output
 * types (used as clustering key), full-text search blob, and layout state
 * (position, velocity, pinned, visible, highlighted).
 */
public class AlgorithmNode {

	public static final String UNCATEGORIZED = "Uncategorized";

	// ---- immutable metadata ----
	private final String name;
	private final DescriptionOfAlgorithm description;
	private final String category;
	private final Color color;
	private final boolean tool;
	private final List<String> inputTypes;
	private final List<String> outputTypes;
	private final String inputKey;
	private final String outputKey;
	private final String searchText;

	// ---- layout / view state ----
	private double x, y, vx, vy;
	private boolean placed;
	private boolean pinned;
	private boolean visible = true;
	private boolean highlighted;

	public AlgorithmNode(String name, DescriptionOfAlgorithm description, String category, Color color) {
		this.name = name == null ? "" : name.trim();
		this.description = description;
		this.category = category == null || category.trim().isEmpty() ? UNCATEGORIZED : category.trim();
		this.color = color == null ? Color.LIGHT_GRAY : color;
		this.inputTypes = normalizeTypes(description == null ? null : description.getInputFileTypes());
		this.outputTypes = normalizeTypes(description == null ? null : description.getOutputFileTypes());
		this.inputKey = String.join("|", lower(inputTypes));
		this.outputKey = String.join("|", lower(outputTypes));
		this.tool = determineIfTool();
		this.searchText = buildSearchText();
	}

	// =====================================================================
	// Derived data
	// =====================================================================

	private static List<String> normalizeTypes(String[] types) {
		if (types == null || types.length == 0) {
			return Collections.emptyList();
		}
		List<String> out = new ArrayList<>();
		for (String t : types) {
			if (t == null) {
				continue;
			}
			String s = t.trim();
			if (!s.isEmpty() && !containsIgnoreCase(out, s)) {
				out.add(s);
			}
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return Collections.unmodifiableList(out);
	}

	private static boolean containsIgnoreCase(List<String> list, String s) {
		for (String e : list) {
			if (e.equalsIgnoreCase(s)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> lower(List<String> in) {
		List<String> out = new ArrayList<>(in.size());
		for (String s : in) {
			out.add(s.toLowerCase(Locale.ROOT));
		}
		return out;
	}

	/**
	 * SPMF marks non-mining algorithms with algorithm types such as
	 * DATA_PROCESSOR, DATA_GENERATOR or DATA_STATS; category and name are used as
	 * a fallback.
	 */
	private boolean determineIfTool() {
		if (description == null) {
			return false;
		}
		try {
			String type = description.getAlgorithmType() == null ? ""
					: description.getAlgorithmType().toString().toLowerCase(Locale.ROOT);
			String cat = description.getAlgorithmCategory() == null ? ""
					: description.getAlgorithmCategory().toLowerCase(Locale.ROOT);
			String n = name.toLowerCase(Locale.ROOT);
			return type.contains("tool") || type.contains("processor") || type.contains("generator")
					|| type.contains("stats") || type.contains("converter") || type.contains("viewer")
					|| cat.contains("tool") || cat.contains("converter") || cat.contains("viewer")
					|| n.contains("converter") || n.contains("viewer");
		} catch (RuntimeException e) {
			return false;
		}
	}

	private String buildSearchText() {
		StringBuilder sb = new StringBuilder(name).append('\n').append(category).append('\n');
		for (String t : inputTypes) {
			sb.append(t).append('\n');
		}
		for (String t : outputTypes) {
			sb.append(t).append('\n');
		}
		if (description != null) {
			try {
				if (description.getAlgorithmType() != null) {
					sb.append(description.getAlgorithmType()).append('\n');
				}
				if (description.getImplementationAuthorNames() != null) {
					sb.append(description.getImplementationAuthorNames()).append('\n');
				}
				if (description.getAlgorithmCategory() != null) {
					sb.append(description.getAlgorithmCategory()).append('\n');
				}
				DescriptionOfParameter[] params = description.getParametersDescription();
				if (params != null) {
					for (DescriptionOfParameter p : params) {
						if (p != null && p.name != null) {
							sb.append(p.name).append('\n');
						}
					}
				}
			} catch (RuntimeException ignored) {
				// metadata is best-effort
			}
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	/**
	 * @param lowerCaseTokens search tokens (already lower-cased); all must occur
	 * @return true if this node matches
	 */
	public boolean matches(String[] lowerCaseTokens) {
		if (lowerCaseTokens == null) {
			return true;
		}
		for (String t : lowerCaseTokens) {
			if (!t.isEmpty() && !searchText.contains(t)) {
				return false;
			}
		}
		return true;
	}

	/** e.g. {@code "in: transaction database  →  out: frequent itemsets"} */
	public String getSignatureLabel() {
		String in = inputTypes.isEmpty() ? "–" : String.join(", ", inputTypes);
		String out = outputTypes.isEmpty() ? "–" : String.join(", ", outputTypes);
		return "in: " + in + "  \u2192  out: " + out;
	}

	/** Evenly distributed, readable colours (golden-ratio hue stepping). */
	public static Color paletteColor(int index) {
		float hue = (index * 0.618033988f) % 1f;
		float sat = 0.55f + 0.2f * ((index / 3) % 2);
		float bri = 0.88f - 0.12f * ((index / 2) % 2);
		return Color.getHSBColor(hue, sat, bri);
	}

	// =====================================================================
	// Geometry helpers
	// =====================================================================

	public void setPosition(double x, double y) {
		this.x = x;
		this.y = y;
		this.placed = true;
	}

	public void setVelocity(double vx, double vy) {
		this.vx = vx;
		this.vy = vy;
	}

	public void stop() {
		vx = 0;
		vy = 0;
	}

	public double distanceSq(double px, double py) {
		double dx = px - x, dy = py - y;
		return dx * dx + dy * dy;
	}

	// =====================================================================
	// Getters / setters
	// =====================================================================

	public String getName() { return name; }
	public DescriptionOfAlgorithm getDescription() { return description; }
	public String getCategory() { return category; }
	public Color getColor() { return color; }
	public boolean isTool() { return tool; }
	public List<String> getInputTypes() { return inputTypes; }
	public List<String> getOutputTypes() { return outputTypes; }
	public String getInputKey() { return inputKey; }
	public String getOutputKey() { return outputKey; }

	public double getX() { return x; }
	public double getY() { return y; }
	public double getVx() { return vx; }
	public double getVy() { return vy; }
	public boolean isPlaced() { return placed; }

	public boolean isPinned() { return pinned; }
	public void setPinned(boolean pinned) { this.pinned = pinned; }
	public boolean isVisible() { return visible; }
	public void setVisible(boolean visible) { this.visible = visible; }
	public boolean isHighlighted() { return highlighted; }
	public void setHighlighted(boolean highlighted) { this.highlighted = highlighted; }

	@Override
	public String toString() { return name; }
}