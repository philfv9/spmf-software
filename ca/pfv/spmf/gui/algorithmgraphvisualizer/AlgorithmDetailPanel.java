package ca.pfv.spmf.gui.algorithmgraphvisualizer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger. Part of SPMF (GPL v3).
 */
/**
 * HTML detail view for the selected algorithm: metadata, parameters,
 * documentation link, clickable list of similar algorithms, and buttons to open
 * the documentation or centre the node in the graph.
 */
public class AlgorithmDetailPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final String ALGO_LINK_PREFIX = "#algo:";
	private static final int MAX_SIMILAR_SHOWN = 40;

	public interface DetailListener {
		/** The user clicked the name of another algorithm. */
		void algorithmChosen(String name);

		/** The user asked to centre the current algorithm in the graph. */
		void revealRequested(AlgorithmNode node);
	}

	private final JEditorPane pane;
	private final JButton docButton, revealButton;
	private DetailListener listener;
	private AlgorithmNode current;

	public AlgorithmDetailPanel() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder("Algorithm details"));

		pane = new JEditorPane("text/html", "");
		pane.setEditable(false);
		pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		pane.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		pane.addHyperlinkListener(e -> {
			if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
				return;
			}
			String desc = e.getDescription();
			if (desc != null && desc.startsWith(ALGO_LINK_PREFIX)) {
				if (listener != null) {
					listener.algorithmChosen(decode(desc.substring(ALGO_LINK_PREFIX.length())));
				}
			} else {
				openInBrowser(this, e.getURL() != null ? e.getURL().toString() : desc);
			}
		});

		JScrollPane scroll = new JScrollPane(pane);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		add(scroll, BorderLayout.CENTER);

		docButton = new JButton("Documentation");
		docButton.setToolTipText("Open the SPMF documentation page of this algorithm in the browser");
		docButton.addActionListener(e -> {
			if (current != null && current.getDescription() != null) {
				openInBrowser(this, current.getDescription().getURLOfDocumentation());
			}
		});
		revealButton = new JButton("Center in graph");
		revealButton.addActionListener(e -> {
			if (current != null && listener != null) {
				listener.revealRequested(current);
			}
		});

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		buttons.add(docButton);
		buttons.add(revealButton);
		add(buttons, BorderLayout.SOUTH);

		clearDetails();
	}

	public void setDetailListener(DetailListener listener) {
		this.listener = listener;
	}

	public void showAlgorithmDetails(AlgorithmNode node, List<AlgorithmNode> similar) {
		if (node == null) {
			clearDetails();
			return;
		}
		current = node;
		DescriptionOfAlgorithm d = node.getDescription();
		String url = d == null ? null : d.getURLOfDocumentation();
		docButton.setEnabled(url != null && !url.trim().isEmpty());
		revealButton.setEnabled(true);

		StringBuilder h = new StringBuilder(
				"<html><body style='font-family:sans-serif;font-size:11pt;margin:4px 8px'>");

		h.append("<h2 style='margin:0 0 4px 0'>").append(esc(node.getName())).append("</h2>");
		h.append("<table cellspacing=0 cellpadding=2><tr><td bgcolor='").append(hex(node.getColor()))
				.append("' width=14>&nbsp;</td><td>").append(esc(node.getCategory())).append("</td></tr></table>");
		h.append("<p style='margin-top:4px'><b>Kind:</b> ")
				.append(node.isTool() ? "Tool / utility" : "Data mining algorithm");
		if (node.isPinned()) {
			h.append(" &nbsp;&middot;&nbsp; <i>pinned</i>");
		}
		h.append("</p>");

		if (d != null) {
			section(h, "Input / output");
			h.append("<table cellpadding=2>");
			row(h, "Input", list(node.getInputTypes()));
			row(h, "Output", list(node.getOutputTypes()));
			h.append("</table>");

			DescriptionOfParameter[] params = safeParams(d);
			if (params.length > 0) {
				section(h, "Parameters (" + params.length + ")");
				h.append("<table cellpadding=3><tr><th align=left>Name</th><th align=left>Type</th>"
						+ "<th align=left>Example</th><th align=left>Optional</th></tr>");
				for (DescriptionOfParameter p : params) {
					String type = p.parameterType == null ? "" : p.parameterType.getSimpleName();
					h.append("<tr><td>").append(esc(p.name)).append("</td><td>").append(esc(type))
							.append("</td><td><code>").append(esc(p.example)).append("</code></td><td>")
							.append(p.isOptional ? "yes" : "").append("</td></tr>");
				}
				h.append("</table>");
			}

			section(h, "Implementation");
			h.append("<table cellpadding=2>");
			if (d.getAlgorithmType() != null) {
				row(h, "Type", esc(String.valueOf(d.getAlgorithmType())));
			}
			if (d.getImplementationAuthorNames() != null) {
				row(h, "Author(s)", esc(d.getImplementationAuthorNames()));
			}
			if (d.getAlgorithmCategory() != null) {
				row(h, "SPMF category", esc(d.getAlgorithmCategory()));
			}
			if (url != null && !url.trim().isEmpty()) {
				row(h, "Documentation", "<a href='" + esc(url) + "'>" + esc(url) + "</a>");
			}
			h.append("</table>");
		}

		section(h, "Similar algorithms (" + similar.size() + ")");
		if (similar.isEmpty()) {
			h.append("<p><i>No other visible algorithm has the same input/output signature "
					+ "(or grouping is disabled).</i></p>");
		} else {
			h.append("<p style='margin:0'>Same signature &ndash; click to select:</p><ul style='margin-top:2px'>");
			int shown = 0;
			for (AlgorithmNode m : similar) {
				if (shown++ == MAX_SIMILAR_SHOWN) {
					h.append("<li><i>\u2026 and ").append(similar.size() - MAX_SIMILAR_SHOWN)
							.append(" more</i></li>");
					break;
				}
				h.append("<li><a href='").append(ALGO_LINK_PREFIX).append(encode(m.getName())).append("'>")
						.append(esc(m.getName())).append("</a></li>");
			}
			h.append("</ul>");
		}

		h.append("</body></html>");
		pane.setText(h.toString());
		pane.setCaretPosition(0);
	}

	public void clearDetails() {
		current = null;
		docButton.setEnabled(false);
		revealButton.setEnabled(false);
		pane.setText("<html><body style='font-family:sans-serif;font-size:11pt;color:#666;margin:12px'>"
				+ "<p>No algorithm selected.</p>"
				+ "<p>Click a node to see its details.<br>Hover a node for its name.<br>"
				+ "Right-click a node for more actions.</p>"
				+ "<p style='font-size:10pt'>Mouse wheel: zoom &middot; drag background: pan &middot; "
				+ "double-click background: fit &middot; drag node: move &amp; pin</p></body></html>");
		pane.setCaretPosition(0);
	}

	private static DescriptionOfParameter[] safeParams(DescriptionOfAlgorithm d) {
		try {
			DescriptionOfParameter[] params = d.getParametersDescription();
			if (params == null) {
				return new DescriptionOfParameter[0];
			}
			int n = 0;
			for (DescriptionOfParameter p : params) {
				if (p != null) {
					n++;
				}
			}
			DescriptionOfParameter[] out = new DescriptionOfParameter[n];
			int i = 0;
			for (DescriptionOfParameter p : params) {
				if (p != null) {
					out[i++] = p;
				}
			}
			return out;
		} catch (RuntimeException e) {
			return new DescriptionOfParameter[0];
		}
	}

	/** Open a URL in the system browser; shows a dialog on failure. */
	public static void openInBrowser(Component parent, String url) {
		if (url == null || url.trim().isEmpty()) {
			return;
		}
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(url.trim()));
				return;
			}
		} catch (Exception ex) {
			// fall through to the message
		}
		JOptionPane.showMessageDialog(parent, "Could not open:\n" + url, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// ---- HTML helpers ----

	private static void section(StringBuilder h, String title) {
		h.append("<h3 style='margin:10px 0 2px 0;color:#2b4c7e'>").append(esc(title)).append("</h3><hr>");
	}

	private static void row(StringBuilder h, String label, String valueHtml) {
		h.append("<tr><td valign=top><b>").append(esc(label)).append(":</b></td><td>").append(valueHtml)
				.append("</td></tr>");
	}

	private static String list(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "<i>none</i>";
		}
		StringBuilder sb = new StringBuilder();
		for (String v : values) {
			if (sb.length() > 0) {
				sb.append("<br>");
			}
			sb.append(esc(v));
		}
		return sb.toString();
	}

	private static String hex(Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static String encode(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (Exception e) {
			return s;
		}
	}

	private static String decode(String s) {
		try {
			return URLDecoder.decode(s, "UTF-8");
		} catch (Exception e) {
			return s;
		}
	}

	public static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
}