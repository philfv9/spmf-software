package ca.pfv.spmf.gui;

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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;

/**
 * Modal "About" dialog: logo, version, algorithm/tool counts, license and
 * project information, system information (for bug reports) and links to the
 * website, documentation and contributors page.
 *
 * @author Philippe Fournier-Viger
 */
public class AboutWindow extends JDialog {

    private static final long serialVersionUID = 6173164103462475327L;

    private static final String URL_WEBSITE =
            "http://www.philippe-fournier-viger.com/spmf/";
    private static final String URL_DOCUMENTATION =
            "http://philippe-fournier-viger.com/spmf/index.php?link=documentation.php";
    private static final String URL_CONTRIBUTORS =
            "http://philippe-fournier-viger.com/spmf/index.php?link=contributors.php";
    private static final String URL_LICENSE = "http://www.gnu.org/licenses/";

    /** Width of the wrapped description text, in pixels. */
    private static final int TEXT_WIDTH = 440;

    private static final Color CARD_BG        = Color.WHITE;
    private static final Color CARD_BORDER    = new Color(214, 220, 226);
    private static final Color TEXT_PRIMARY   = new Color(44, 62, 80);
    private static final Color TEXT_SECONDARY = new Color(118, 124, 132);

    private final Font fontBase;
    private final Font fontSmall;
    private final Font fontSmallBold;

    /**
     * Builds the About dialog (call {@code setVisible(true)} to show it).
     *
     * @param owner the parent window used for positioning (may be null)
     * @throws Exception kept for compatibility; failures to count algorithms
     *                   are handled internally
     */
    public AboutWindow(Window owner) throws Exception {
        super(owner, "About SPMF", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        URL iconUrl = AboutWindow.class.getResource("/ca/pfv/spmf/gui/icons/About24.gif");
        if (iconUrl != null) {
            setIconImage(new ImageIcon(iconUrl).getImage());
        }

        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        fontBase      = base.deriveFont(Font.PLAIN, base.getSize2D() + 0.5f);
        fontSmall     = base.deriveFont(Font.PLAIN, base.getSize2D() - 1f);
        fontSmallBold = fontSmall.deriveFont(Font.BOLD);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(16, 20, 14, 20));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(owner);
    }

    // -------------------------------------------------------------------------
    // Sections
    // -------------------------------------------------------------------------

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        URL logoUrl = AboutWindow.class.getResource("/ca/pfv/spmf/gui/spmf.png");
        JLabel logo = (logoUrl != null) ? new JLabel(new ImageIcon(logoUrl)) : new JLabel("SPMF");
        if (logoUrl == null) {
            logo.setFont(fontBase.deriveFont(Font.BOLD, 40f));
            logo.setForeground(new Color(41, 128, 185));
        }
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(logo);
        header.add(Box.createVerticalStrut(6));

        int[] counts = countAlgorithmsAndTools();
        JLabel version = new JLabel("Version " + Main.SPMF_VERSION
                + "  \u00B7  " + fmt(counts[0]) + " algorithms"
                + "  \u00B7  " + fmt(counts[1]) + " tools");
        version.setFont(fontSmall);
        version.setForeground(TEXT_SECONDARY);
        version.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(version);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(buildDescriptionCard(), BorderLayout.CENTER);
        body.add(buildSystemCard(), BorderLayout.SOUTH);
        return body;
    }

    private JComponent buildDescriptionCard() {
        String html = "<html><body style='margin:0'>"
                + "<p style='margin-top:0'>Thank you for using <b>SPMF</b>, an open-source "
                + "data mining library specialized in pattern mining.</p>"
                + "<p>SPMF was founded in 2008 by <b>Philippe Fournier-Viger</b>. Many people "
                + "have contributed to the project since then &mdash; see the "
                + "<a href='" + URL_CONTRIBUTORS + "'>contributors page</a>.</p>"
                + "<p>SPMF is distributed under the <b>GNU General Public License, version 3</b>. "
                + "The license text is available at "
                + "<a href='" + URL_LICENSE + "'>www.gnu.org/licenses</a>.</p>"
                + "<p style='margin-bottom:0'>If you use SPMF in your research, please cite it. "
                + "Citation details are given on the <a href='" + URL_WEBSITE + "'>website</a>.</p>"
                + "</body></html>";

        JEditorPane pane = new JEditorPane();
        pane.setContentType("text/html");
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(fontBase);
        pane.setForeground(TEXT_PRIMARY);
        pane.setText(html);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setFocusable(false);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                BrowserUtil.open(this, e.getURL() != null ? e.getURL().toString() : e.getDescription());
            }
        });
        // Give the HTML view a width so it can compute its wrapped height
        pane.setSize(new Dimension(TEXT_WIDTH, Short.MAX_VALUE));
        pane.setPreferredSize(new Dimension(TEXT_WIDTH, pane.getPreferredSize().height));

        return card(pane);
    }

    private JComponent buildSystemCard() {
        JLabel title = new JLabel("System");
        title.setFont(fontSmallBold);
        title.setForeground(TEXT_SECONDARY);

        String info = systemInfo();

        JButton copy = new JButton("Copy");
        copy.setFont(fontSmall);
        copy.setMargin(new Insets(1, 8, 1, 8));
        copy.setFocusPainted(false);
        copy.setToolTipText("Copy this information to the clipboard (useful when reporting a bug)");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(info), null);
            copy.setText("Copied");
            copy.setEnabled(false);
            Timer t = new Timer(1500, ev -> {
                copy.setText("Copy");
                copy.setEnabled(true);
            });
            t.setRepeats(false);
            t.start();
        });

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        top.add(copy, BorderLayout.EAST);

        JTextArea area = new JTextArea(info);
        area.setEditable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFocusable(false);
        area.setFont(fontSmall);
        area.setForeground(TEXT_PRIMARY);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        content.add(area, BorderLayout.CENTER);
        return card(content);
    }

    private JComponent buildButtons() {
        JButton website = new JButton("Website");
        website.setToolTipText("Open the SPMF website in your browser");
        website.addActionListener(e -> BrowserUtil.open(this, URL_WEBSITE));

        JButton docs = new JButton("Documentation");
        docs.setToolTipText("Open the SPMF documentation in your browser");
        docs.addActionListener(e -> BrowserUtil.open(this, URL_DOCUMENTATION));

        JButton contributors = new JButton("Contributors");
        contributors.setToolTipText("Open the list of contributors in your browser");
        contributors.addActionListener(e -> BrowserUtil.open(this, URL_CONTRIBUTORS));

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(close);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(website);
        left.add(docs);
        left.add(contributors);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(2, 0, 0, 0));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static JPanel card(JComponent content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                new EmptyBorder(12, 14, 12, 14)));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    /** Returns {algorithmCount, toolCount}; -1 for a value that could not be determined. */
    private static int[] countAlgorithmsAndTools() {
        int algorithms = -1;
        int tools = -1;
        try {
            AlgorithmManager am = AlgorithmManager.getInstance();
            algorithms = am.getListOfAlgorithmsAsString(false, false, false, true, false).size();
            tools      = am.getListOfAlgorithmsAsString(true, true, true, false, false).size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new int[] {algorithms, tools};
    }

    private static String fmt(int count) {
        return count < 0 ? "?" : Integer.toString(count);
    }

    private static String systemInfo() {
        String nl = System.lineSeparator();
        return "SPMF " + Main.SPMF_VERSION + nl
                + "Java " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")" + nl
                + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")" + nl
                + "Max heap: " + (Runtime.getRuntime().maxMemory() >> 20) + " MB";
    }
}