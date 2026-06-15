package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
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
 * A compact panel that draws a bar chart of the pattern-size distribution, used by the visual pattern viewer
 */
class SizeDistributionHistogram extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int HIST_HEIGHT = 100;
    private static final int BAR_GAP     = 2;
    private static final int LABEL_AREA  = 16;
    private static final int TOP_PAD     = 4;
    private static final int SIDE_PAD    = 8;

    private static final Color BAR_COLOR       = new Color(46, 134, 193);
    private static final Color BAR_HOVER_COLOR = new Color(52, 152, 219);
    private static final Color AXIS_COLOR      = new Color(160, 160, 160);
    private static final Color LABEL_COLOR     = new Color(100, 100, 100);
    private static final Color EMPTY_COLOR     = new Color(150, 150, 150);
    private static final Color COUNT_COLOR     = new Color(60, 60, 60);

    private static final Font FONT_LABEL    = new Font("SansSerif", Font.PLAIN, 9);
    private static final Font FONT_COUNT    = new Font("SansSerif", Font.BOLD, 9);
    private static final Font FONT_EMPTY    = new Font("SansSerif", Font.ITALIC, 10);
    private static final Font FONT_SUBTITLE = new Font("SansSerif", Font.PLAIN, 10);

    private TreeMap<Integer, Integer> distribution = new TreeMap<>();
    private int maxCount = 0, totalPatterns = 0, hoveredBarIndex = -1;
    private double avgSize = 0;
    private int minSize = 0, maxSize = 0;

    SizeDistributionHistogram() {
        setBorder(BorderFactory.createTitledBorder("Size Distribution"));
        int totalH = HIST_HEIGHT + LABEL_AREA + TOP_PAD + 55;
        setPreferredSize(new Dimension(VisualPatternViewer.SIDEBAR_WIDTH - 20, totalH));
        setMinimumSize(new Dimension(VisualPatternViewer.SIDEBAR_WIDTH - 20, totalH));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int h = getBarIndexAt(e.getX(), e.getY());
                if (h != hoveredBarIndex) { hoveredBarIndex = h; repaint(); }
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                if (hoveredBarIndex != -1) { hoveredBarIndex = -1; repaint(); }
            }
        });
    }

    void updateData(PatternsPanel panel) {
        distribution.clear();
        maxCount = 0; totalPatterns = 0; hoveredBarIndex = -1;
        avgSize = 0; minSize = 0; maxSize = 0;
        if (panel == null) { repaint(); return; }
        List<Integer> sizes = panel.getVisiblePatternSizes();
        if (sizes == null || sizes.isEmpty()) { repaint(); return; }
        totalPatterns = sizes.size();
        long sum = 0;
        minSize = Integer.MAX_VALUE; maxSize = Integer.MIN_VALUE;
        for (int sz : sizes) {
            distribution.merge(sz, 1, Integer::sum);
            sum += sz;
            if (sz < minSize) minSize = sz;
            if (sz > maxSize) maxSize = sz;
        }
        avgSize = (double) sum / totalPatterns;
        for (int c : distribution.values()) if (c > maxCount) maxCount = c;
        repaint();
    }

    private int getBarIndexAt(int mx, int my) {
        if (distribution.isEmpty()) return -1;
        Insets ins = getInsets();
        int dl = ins.left + SIDE_PAD, dr = getWidth() - ins.right - SIDE_PAD;
        int dw = dr - dl, sh = 18;
        int bat = ins.top + TOP_PAD + sh, bab = getHeight() - ins.bottom - LABEL_AREA;
        if (dw <= 0 || my < bat || my > bab) return -1;
        int n = distribution.size(), tg = (n - 1) * BAR_GAP;
        int bw = Math.min(Math.max(4, (dw - tg) / n), 28);
        int sx = dl + (dw - (n * bw + tg)) / 2;
        int idx = 0;
        for (Map.Entry<Integer, Integer> e : distribution.entrySet()) {
            int bx = sx + idx * (bw + BAR_GAP);
            if (mx >= bx && mx < bx + bw) return idx;
            idx++;
        }
        return -1;
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        int idx = getBarIndexAt(e.getX(), e.getY());
        if (idx < 0 || idx >= distribution.size()) return null;
        int i = 0;
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            if (i++ == idx) {
                double pct = totalPatterns > 0
                        ? 100.0 * entry.getValue() / totalPatterns : 0;
                return String.format(
                        "<html><b>Size %d:</b> %d pattern%s (%.1f%%)</html>",
                        entry.getKey(), entry.getValue(),
                        entry.getValue() != 1 ? "s" : "", pct);
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Insets ins = getInsets();
        int dl = ins.left + SIDE_PAD, dr = getWidth() - ins.right - SIDE_PAD;
        int dw = dr - dl, sh = 18;

        if (totalPatterns > 0) {
            g2.setFont(FONT_SUBTITLE); g2.setColor(LABEL_COLOR);
            String sub = String.format("min=%d  avg=%.1f  max=%d",
                    minSize, avgSize, maxSize);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(sub, dl + (dw - fm.stringWidth(sub)) / 2,
                    ins.top + TOP_PAD + fm.getAscent());
        }

        int bat = ins.top + TOP_PAD + sh;
        int bab = getHeight() - ins.bottom - LABEL_AREA;
        int bah = bab - bat;

        if (distribution.isEmpty()) {
            g2.setFont(FONT_EMPTY); g2.setColor(EMPTY_COLOR);
            String msg = "No data";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, dl + (dw - fm.stringWidth(msg)) / 2,
                    bat + bah / 2 + fm.getAscent() / 2);
            return;
        }

        int n = distribution.size(), tg = (n - 1) * BAR_GAP;
        int bw = Math.min(Math.max(4, (dw - tg) / n), 28);
        int sx = dl + (dw - (n * bw + tg)) / 2;

        g2.setColor(AXIS_COLOR);
        g2.drawLine(dl, bab, dr, bab);

        int idx = 0;
        for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
            int cnt = entry.getValue();
            int bh = maxCount > 0
                    ? Math.max(2, (int) ((double) cnt / maxCount * bah)) : 2;
            int bx = sx + idx * (bw + BAR_GAP);
            int by = bab - bh;
            g2.setColor(idx == hoveredBarIndex ? BAR_HOVER_COLOR : BAR_COLOR);
            g2.fillRect(bx, by, bw, bh);

            if (bh > 12 || bah > 40) {
                g2.setFont(FONT_COUNT); g2.setColor(COUNT_COLOR);
                FontMetrics fm = g2.getFontMetrics();
                String cs = String.valueOf(cnt);
                int cw = fm.stringWidth(cs), cy = by - 2;
                if (cy >= bat + fm.getAscent())
                    g2.drawString(cs, bx + (bw - cw) / 2, cy);
            }

            g2.setFont(FONT_LABEL); g2.setColor(LABEL_COLOR);
            FontMetrics fl = g2.getFontMetrics();
            String ss = String.valueOf(entry.getKey());
            int lw = fl.stringWidth(ss);
            g2.drawString(ss, bx + (bw - lw) / 2, bab + fl.getAscent() + 1);
            idx++;
        }
    }
}