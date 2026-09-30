package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 * (license header unchanged – see other files of this package)
 */
/**
 * A titled, collapsible "card" used for each section of the sidebar. Clicking
 * the header collapses / expands the content; the small info label at the
 * right of the title stays visible when collapsed.
 *
 * @author Philippe Fournier-Viger
 */
class SidebarSection extends JPanel {

	private static final long serialVersionUID = 1L;

	private static final int ARC = 10;
	private static final int STRIPE_X = 10;
	private static final String CHEVRON_OPEN = "\u25BE";
	private static final String CHEVRON_CLOSED = "\u25B8";

	private final JPanel header;
	private final JPanel body;
	private final JLabel chevron;
	private final JLabel titleLabel;
	private final JLabel infoLabel;
	private final JComponent content;
	private boolean collapsed;

	SidebarSection(String title, JComponent content) {
		super(new BorderLayout());
		this.content = content;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(0, 0, ViewerStyle.SHADOW_DY, ViewerStyle.SHADOW_DX));

		header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(7, STRIPE_X + 9, 5, 10));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText("Click to collapse or expand this section");

		chevron = new JLabel(CHEVRON_OPEN);
		chevron.setFont(ViewerStyle.sectionTitleFont());
		chevron.setForeground(ViewerStyle.TEXT_SECONDARY);
		chevron.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
		titleLabel = new JLabel(title);
		titleLabel.setFont(ViewerStyle.sectionTitleFont());
		titleLabel.setForeground(ViewerStyle.TEXT_PRIMARY);
		infoLabel = new JLabel();
		infoLabel.setFont(ViewerStyle.smallFont());
		infoLabel.setForeground(ViewerStyle.ACCENT_DARK);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		left.setOpaque(false);
		left.add(chevron);
		left.add(titleLabel);
		header.add(left, BorderLayout.WEST);
		header.add(infoLabel, BorderLayout.EAST);
		add(header, BorderLayout.NORTH);

		body = new JPanel(new BorderLayout());
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));
		body.add(content, BorderLayout.CENTER);
		add(body, BorderLayout.CENTER);

		MouseAdapter toggle = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					setCollapsed(!collapsed);
				}
			}
		};
		for (JComponent c : new JComponent[] { header, left, chevron, titleLabel, infoLabel }) {
			c.addMouseListener(toggle);
		}
	}

	/** Sets the small information text at the right of the title ("" hides it). */
	void setInfo(String text) {
		infoLabel.setText(text == null ? "" : text);
	}

	/** Collapses (hides the content) or expands the section. */
	void setCollapsed(boolean collapsed) {
		if (this.collapsed == collapsed) {
			return;
		}
		this.collapsed = collapsed;
		body.setVisible(!collapsed);
		chevron.setText(collapsed ? CHEVRON_CLOSED : CHEVRON_OPEN);
		revalidate();
		repaint();
	}

	boolean isCollapsed() {
		return collapsed;
	}

	JComponent getContent() {
		return content;
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			ViewerStyle.applyRenderingHints(g2);
			int w = getWidth() - ViewerStyle.SHADOW_DX;
			int h = getHeight() - ViewerStyle.SHADOW_DY;
			g2.setColor(ViewerStyle.CARD_SHADOW);
			g2.fillRoundRect(1, 2, w, h, ARC, ARC);
			g2.setColor(ViewerStyle.CARD_BG);
			g2.fillRoundRect(0, 0, w, h, ARC, ARC);
			g2.setColor(ViewerStyle.CARD_BORDER);
			g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);

			int hy = header.getY();
			int hh = header.getHeight();
			g2.setColor(ViewerStyle.ACCENT);
			g2.fillRoundRect(STRIPE_X, hy + 8, 3, Math.max(4, hh - 14), 3, 3);
			if (!collapsed) {
				g2.setColor(ViewerStyle.SEPARATOR);
				g2.drawLine(STRIPE_X, hy + hh, w - 11, hy + hh);
			}
		} finally {
			g2.dispose();
		}
	}
}