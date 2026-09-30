package ca.pfv.spmf.gui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.algorithmexplorer.AlgorithmExplorer;
import ca.pfv.spmf.gui.developerswindow.DevelopersToolsWindow;
import ca.pfv.spmf.gui.experiments.ExperimenterOneParamVariedWindow;
import ca.pfv.spmf.gui.experiments.ExperimenterScalabilityWindow;
import ca.pfv.spmf.gui.guided_interface.GuidedInterface;
import ca.pfv.spmf.gui.pattern_diff_analyzer.PatternDiffAnalyzer;
import ca.pfv.spmf.gui.patternvizualizer.PatternVizualizer;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.texteditor.SPMFTextEditor;
import ca.pfv.spmf.gui.workflow_editor.WorkflowEditorWindow;

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
 * The SPMF launcher ("welcome") window: four large action cards leading to the
 * main tools, plus a footer with links.
 * <p>
 * Keyboard: Tab / arrow keys move between cards, Enter or Space activates a
 * card, Alt+R / Alt+S / Alt+W / Alt+T are shortcuts for the four cards.
 */
public class LauncherWindow extends JFrame {
    public static final long serialVersionUID = 1L;

    // -------------------------------------------------------------------------
    // Palette
    // -------------------------------------------------------------------------

    private static final Color PRIMARY_COLOR    = new Color(41, 128, 185);
    private static final Color CARD_COLOR       = Color.WHITE;
    private static final Color CARD_HOVER       = new Color(251, 252, 254);
    private static final Color CARD_PRESSED     = new Color(234, 238, 243);
    private static final Color CARD_BORDER      = new Color(214, 220, 226);
    private static final Color CARD_SHADOW      = new Color(44, 62, 80);
    private static final Color TEXT_PRIMARY     = new Color(44, 62, 80);
    private static final Color TEXT_SECONDARY   = new Color(127, 140, 141);
    private static final Color CHEVRON_IDLE     = new Color(190, 196, 202);
    private static final Color BACKGROUND_START = new Color(236, 240, 241);
    private static final Color BACKGROUND_END   = new Color(220, 230, 240);

    private static final Color COLOR_RUN_ALGORITHM    = new Color(39, 174, 96);
    private static final Color COLOR_GUIDED_INTERFACE = new Color(41, 128, 185);
    private static final Color COLOR_WORKFLOW         = new Color(142, 68, 173);
    private static final Color COLOR_OTHER_TOOLS      = new Color(230, 126, 34);

    private static final int       CARD_RADIUS = 12;
    private static final Dimension CARD_SIZE   = new Dimension(300, 92);
    private static final Dimension CARD_MIN    = new Dimension(230, 78);

    // -------------------------------------------------------------------------
    // Fonts (derived from the look & feel so they scale with the system)
    // -------------------------------------------------------------------------

    private final Font fontHeading;
    private final Font fontCardTitle;
    private final Font fontCardDesc;
    private final Font fontSmall;
    private final Font fontMenu;

    // -------------------------------------------------------------------------
    // Components
    // -------------------------------------------------------------------------

    private ActionCard buttonRunAlgorithm;
    private ActionCard buttonGuidedInterface;
    private ActionCard buttonWorkflow;
    private ActionCard buttonOtherTools;

    /** True once the user has used the keyboard; enables the focus ring. */
    private boolean focusRingVisible = false;

    private final KeyEventDispatcher keyDispatcher = e -> {
        if (e.getID() == KeyEvent.KEY_PRESSED && isFocused() && !focusRingVisible) {
            focusRingVisible = true;
            repaint();
        }
        return false;
    };

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public LauncherWindow() throws Exception {
        super("SPMF v." + Main.SPMF_VERSION + " - Data Mining Software");

        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        float s = base.getSize2D();
        fontHeading   = base.deriveFont(Font.BOLD, s + 7f);
        fontCardTitle = base.deriveFont(Font.BOLD, s + 2f);
        fontCardDesc  = base.deriveFont(Font.PLAIN, s - 0.5f);
        fontSmall     = base.deriveFont(Font.PLAIN, s - 1f);
        fontMenu      = base.deriveFont(Font.PLAIN, s + 1f);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        initComponents();
        installKeyboardSupport();

        pack();
//        enforceMinimumSize();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initComponents() {
        JPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(18, 30, 16, 30));

        root.add(createHeaderPanel(), BorderLayout.NORTH);
        root.add(createContentPanel(), BorderLayout.CENTER);
        root.add(createFooterPanel(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // -------------------------------------------------------------------------
    // Header
    // -------------------------------------------------------------------------

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(new EmptyBorder(6, 0, 6, 0));

        header.add(createLogo());
        header.add(Box.createVerticalStrut(10));

        JLabel heading = new JLabel("What would you like to do?");
        heading.setFont(fontHeading);
        heading.setForeground(TEXT_PRIMARY);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(heading);

        header.add(Box.createVerticalStrut(3));

        JLabel subtitle = new JLabel(
                "Version " + Main.SPMF_VERSION + "  \u00B7  Open-source data mining library");
        subtitle.setFont(fontSmall);
        subtitle.setForeground(TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(subtitle);

        return header;
    }

    /** Logo image if available, otherwise a text fallback. Click opens About. */
    private JComponent createLogo() {
        URL url = LauncherWindow.class.getResource("spmf.png");
        JLabel logo;
        if (url != null) {
            logo = new JLabel(new ImageIcon(url));
        } else {
            logo = new JLabel("SPMF");
            logo.setFont(fontHeading.deriveFont(Font.BOLD, 44f));
            logo.setForeground(PRIMARY_COLOR);
        }
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logo.setToolTipText("About SPMF");
        logo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openAboutWindow();
            }
        });
        return logo;
    }

    // -------------------------------------------------------------------------
    // Content – 2 × 2 grid of action cards
    // -------------------------------------------------------------------------

    private JPanel createContentPanel() {
        buttonRunAlgorithm = new ActionCard(
                "Run an Algorithm",
                "Choose an algorithm, set its parameters and run it on a file",
                COLOR_RUN_ALGORITHM, GLYPH_PLAY, false);
        buttonRunAlgorithm.setMnemonic(KeyEvent.VK_R);
        buttonRunAlgorithm.addActionListener(e -> runAction("Run an Algorithm", () -> {
            MainWindow window = new MainWindow(true, true, true, true, true, false);
            window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            window.setVisible(true);
        }));

        buttonGuidedInterface = new ActionCard(
                "Step-by-Step Analysis",
                "Guided: select data, run an algorithm, visualize the results",
                COLOR_GUIDED_INTERFACE, GLYPH_STEPS, false);
        buttonGuidedInterface.setMnemonic(KeyEvent.VK_S);
        buttonGuidedInterface.addActionListener(e -> runAction("Step-by-Step Analysis", () -> {
            GuidedInterface window = new GuidedInterface();
            window.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            window.setVisible(true);
        }));

        buttonWorkflow = new ActionCard(
                "Workflow Editor",
                "Chain several algorithms and tools into a pipeline",
                COLOR_WORKFLOW, GLYPH_WORKFLOW, false);
        buttonWorkflow.setMnemonic(KeyEvent.VK_W);
        buttonWorkflow.addActionListener(e -> runAction("Workflow Editor",
                () -> new WorkflowEditorWindow(false)));

        buttonOtherTools = new ActionCard(
                "Other Tools",
                "Pattern viewer, algorithm explorer, text editor, experiments\u2026",
                COLOR_OTHER_TOOLS, GLYPH_GRID, true);
        buttonOtherTools.setMnemonic(KeyEvent.VK_T);
        buttonOtherTools.addActionListener(e -> showOtherToolsMenu());

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets  = new Insets(7, 7, 7, 7);
        gbc.fill    = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;

        ActionCard[][] grid = {
                {buttonRunAlgorithm, buttonGuidedInterface},
                {buttonWorkflow,     buttonOtherTools}
        };
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                gbc.gridx = c;
                gbc.gridy = r;
                content.add(grid[r][c], gbc);
            }
        }
        installArrowNavigation(grid);
        return content;
    }

    // -------------------------------------------------------------------------
    // Footer
    // -------------------------------------------------------------------------

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(4, 0, 0, 0));

        footer.add(new FadingSeparator());
        footer.add(Box.createVerticalStrut(8));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        links.setOpaque(false);

        JLabel copyright = new JLabel("\u00A9 Philippe Fournier-Viger et al.");
        copyright.setFont(fontSmall);
        copyright.setForeground(TEXT_SECONDARY);

        links.add(copyright);
        links.add(dot());
        links.add(new LinkLabel("Website", "Open the SPMF website",
                () -> openWebsite("http://www.philippe-fournier-viger.com/spmf")));
        links.add(dot());
        links.add(new LinkLabel("Documentation", "Open the online documentation",
                () -> openWebsite("http://www.philippe-fournier-viger.com/spmf/index.php?link=documentation.php")));
        links.add(dot());
        links.add(new LinkLabel("About", "About SPMF", this::openAboutWindow));

        footer.add(links);
        return footer;
    }

    private JLabel dot() {
        JLabel l = new JLabel("\u00B7");
        l.setFont(fontSmall);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    // -------------------------------------------------------------------------
    // Keyboard support
    // -------------------------------------------------------------------------

    private void installKeyboardSupport() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(keyDispatcher);
            }
        });
    }

    /** Lets the arrow keys move focus between the cards of the grid. */
    private static void installArrowNavigation(ActionCard[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                ActionCard card = grid[r][c];
                bindFocusKey(card, "LEFT",  grid[r][(c + cols - 1) % cols]);
                bindFocusKey(card, "RIGHT", grid[r][(c + 1) % cols]);
                bindFocusKey(card, "UP",    grid[(r + rows - 1) % rows][c]);
                bindFocusKey(card, "DOWN",  grid[(r + 1) % rows][c]);
            }
        }
    }

    private static void bindFocusKey(JComponent from, String key, JComponent to) {
        String name = "focus-" + key;
        from.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        from.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                to.requestFocusInWindow();
            }
        });
    }

    // -------------------------------------------------------------------------
    // "Other Tools" dropdown
    // -------------------------------------------------------------------------

    private void showOtherToolsMenu() {
        JPopupMenu menu = new JPopupMenu();
        stylePopup(menu);

        menu.add(menuItem("Algorithm Explorer",
                "Browse and search the algorithms offered by SPMF",
                () -> runAction("Algorithm Explorer", () -> new AlgorithmExplorer(false).setVisible(true))));
        menu.add(menuItem("Pattern Viewer",
                "Visualize and explore patterns found by an algorithm",
                this::actionPatternViewer));
        menu.add(menuItem("Pattern Diff Analyzer",
                "Compare two pattern files",
                () -> runAction("Pattern Diff Analyzer", () -> new PatternDiffAnalyzer(false).setVisible(true))));
        menu.addSeparator();
        menu.add(menuItem("SPMF Text Editor",
                "Open input/output files in the built-in text editor",
                () -> runAction("SPMF Text Editor", () -> new SPMFTextEditor(false))));
        menu.add(menuItem("Memory Viewer",
                "Live chart of the JVM memory usage",
                () -> runAction("Memory Viewer", MemoryViewer::displayMemoryChart)));
        menu.addSeparator();

        JMenu experiments = new JMenu("Experiments");
        experiments.setToolTipText("Compare the performance of several algorithms");
        styleMenuItem(experiments);
        stylePopup(experiments.getPopupMenu());
        experiments.add(menuItem("Vary a Parameter",
                "Compare algorithms while varying one parameter",
                () -> runAction("Parameter Experiment", () -> {
                    ExperimenterOneParamVariedWindow w = new ExperimenterOneParamVariedWindow();
                    w.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    w.setVisible(true);
                })));
        experiments.add(menuItem("Vary Dataset Size (Scalability)",
                "Compare algorithms while varying the dataset size",
                () -> runAction("Scalability Experiment", () -> {
                    ExperimenterScalabilityWindow w = new ExperimenterScalabilityWindow();
                    w.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
                    w.setVisible(true);
                })));
        menu.add(experiments);
        menu.addSeparator();

        // Currently disabled – re-enable when the offline documentation is available
        // menu.add(menuItem("Download Offline Documentation",
        //         "Download a copy of the documentation for offline use",
        //         () -> runAction("Documentation Downloader",
        //                 () -> new DocumentationDownloaderWindow().createAndShowGUI())));
        menu.add(menuItem("Developers' Tools",
                "Tools intended for SPMF developers",
                () -> runAction("Developers' Tools", () -> new DevelopersToolsWindow(false).setVisible(true))));

        // Make it look like a dropdown attached to the card
        Dimension pref = menu.getPreferredSize();
        menu.setPopupSize(new Dimension(Math.max(pref.width, buttonOtherTools.getWidth()), pref.height));
        menu.show(buttonOtherTools, 0, buttonOtherTools.getHeight() + 2);
    }

    private JMenuItem menuItem(String text, String tooltip, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setToolTipText(tooltip);
        item.addActionListener(e -> action.run());
        styleMenuItem(item);
        return item;
    }

    private void styleMenuItem(JMenuItem item) {
        item.setFont(fontMenu);
        item.setForeground(TEXT_PRIMARY);
        item.setBackground(CARD_COLOR);
        item.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 24));
    }

    private static void stylePopup(JPopupMenu menu) {
        menu.setBackground(CARD_COLOR);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Runs an action, reporting any failure to the user in a dialog. */
    private void runAction(String name, ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Could not open " + name + ":\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void actionPatternViewer() {
        runAction("Pattern Viewer", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select a Pattern File");
            chooser.setCurrentDirectory(new File(PreferencesManager.getInstance().getInputFilePath()));
            chooser.setFileFilter(new FileNameExtensionFilter("Text Files (*.txt)", "txt"));
            chooser.addChoosableFileFilter(
                    new FileNameExtensionFilter("All Supported Files (*.txt, *.csv)", "txt", "csv"));
            chooser.setAcceptAllFileFilterUsed(true);

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                new PatternVizualizer(chooser.getSelectedFile().getAbsolutePath()).setVisible(true);
            }
        });
    }

    private void openAboutWindow() {
        runAction("About", () -> new AboutWindow(this).setVisible(true));
    }

    private void openWebsite(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception ex) {
            System.err.println("Could not open browser: " + ex.getMessage());
        }
        // Fallback: let the user copy the address
        JTextField field = new JTextField(url);
        field.setEditable(false);
        JOptionPane.showMessageDialog(this, field,
                "Open this address in your browser", JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Vector icons for the cards
    // -------------------------------------------------------------------------

    @FunctionalInterface
    private interface Glyph {
        /** Paints the glyph inside the square (x, y, s, s) with the current colour. */
        void paint(Graphics2D g2, float x, float y, float s);
    }

    private static final Glyph GLYPH_PLAY = (g2, x, y, s) -> {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x + 0.30f * s, y + 0.16f * s);
        p.lineTo(x + 0.30f * s, y + 0.84f * s);
        p.lineTo(x + 0.86f * s, y + 0.50f * s);
        p.closePath();
        g2.fill(p);
    };

    private static final Glyph GLYPH_STEPS = (g2, x, y, s) -> {
        g2.setStroke(new BasicStroke(s * 0.11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            float cy = y + s * (0.20f + 0.30f * i);
            g2.fill(new Ellipse2D.Float(x + s * 0.08f, cy - s * 0.09f, s * 0.18f, s * 0.18f));
            g2.draw(new Line2D.Float(x + s * 0.40f, cy, x + s * 0.92f, cy));
        }
    };

    private static final Glyph GLYPH_WORKFLOW = (g2, x, y, s) -> {
        g2.setStroke(new BasicStroke(s * 0.10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float n = s * 0.34f;
        float r = s * 0.10f;
        g2.draw(new Line2D.Float(x + s * 0.38f, y + s * 0.21f, x + s * 0.62f, y + s * 0.21f));
        g2.draw(new Line2D.Float(x + s * 0.79f, y + s * 0.38f, x + s * 0.79f, y + s * 0.62f));
        g2.fill(new RoundRectangle2D.Float(x + s * 0.04f, y + s * 0.04f, n, n, r, r));
        g2.fill(new RoundRectangle2D.Float(x + s * 0.62f, y + s * 0.04f, n, n, r, r));
        g2.fill(new RoundRectangle2D.Float(x + s * 0.62f, y + s * 0.62f, n, n, r, r));
    };

    private static final Glyph GLYPH_GRID = (g2, x, y, s) -> {
        float n = s * 0.38f;
        float r = s * 0.12f;
        float[] offsets = {s * 0.08f, s * 0.54f};
        for (float ox : offsets) {
            for (float oy : offsets) {
                g2.fill(new RoundRectangle2D.Float(x + ox, y + oy, n, n, r, r));
            }
        }
    };

    // -------------------------------------------------------------------------
    // Action card
    // -------------------------------------------------------------------------

    /**
     * A large, self-painted card button: accent bar, icon badge, title,
     * word-wrapped description and a chevron (or a caret when the card opens
     * a menu). Supports hover, pressed, disabled and keyboard-focus states.
     */
    private final class ActionCard extends JButton {
        private static final long serialVersionUID = 1L;

        private final String  title;
        private final String  description;
        private final Color   accent;
        private final Glyph   glyph;
        private final boolean opensMenu;

        ActionCard(String title, String description, Color accent, Glyph glyph, boolean opensMenu) {
            this.title       = title;
            this.description = description;
            this.accent      = accent;
            this.glyph       = glyph;
            this.opensMenu   = opensMenu;

            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(CARD_SIZE);
            setMinimumSize(CARD_MIN);

            getAccessibleContext().setAccessibleName(title);
            getAccessibleContext().setAccessibleDescription(description);

            // Enter activates the card (Space already does by default)
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "activate");
            getActionMap().put("activate", new AbstractAction() {
                private static final long serialVersionUID = 1L;
                @Override
                public void actionPerformed(ActionEvent e) {
                    doClick();
                }
            });

            // Mouse use hides the keyboard focus ring
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    focusRingVisible = false;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                int w = getWidth();
                int h = getHeight();
                ButtonModel m = getModel();
                boolean enabled = isEnabled();
                boolean hover   = enabled && m.isRollover();
                boolean pressed = enabled && m.isPressed() && m.isArmed();
                boolean focused = enabled && isFocusOwner() && focusRingVisible;
                float   lift    = (hover && !pressed) ? 1f : 0f;
                Color   ink     = enabled ? accent : new Color(190, 190, 190);

                // Shadow
                g2.setColor(withAlpha(CARD_SHADOW, hover ? 38 : 22));
                g2.fill(new RoundRectangle2D.Float(1, 3, w - 2, h - 4, CARD_RADIUS, CARD_RADIUS));

                // Card
                RoundRectangle2D.Float card =
                        new RoundRectangle2D.Float(1, 1 - lift, w - 2, h - 4, CARD_RADIUS, CARD_RADIUS);
                g2.setColor(!enabled ? new Color(242, 243, 245)
                        : pressed ? CARD_PRESSED : hover ? CARD_HOVER : CARD_COLOR);
                g2.fill(card);

                // Accent bar, clipped to the rounded card
                Shape oldClip = g2.getClip();
                g2.clip(card);
                g2.setColor(ink);
                g2.fill(new Rectangle2D.Float(1, 1 - lift, 5, h - 4));
                g2.setClip(oldClip);

                // Border
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(hover ? withAlpha(accent, 150) : CARD_BORDER);
                g2.draw(card);

                // Keyboard focus ring
                if (focused) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(withAlpha(accent, 210));
                    g2.draw(new RoundRectangle2D.Float(2.5f, 2.5f - lift, w - 5, h - 7,
                            CARD_RADIUS - 2, CARD_RADIUS - 2));
                }

                float centerY = (h - 2) / 2f - lift;

                // Icon badge
                float badge = 40f;
                float bx = 20f;
                float by = centerY - badge / 2f;
                g2.setColor(withAlpha(accent, enabled ? 34 : 14));
                g2.fill(new RoundRectangle2D.Float(bx, by, badge, badge, 11, 11));
                g2.setColor(ink);
                float gs = 20f;
                glyph.paint(g2, bx + (badge - gs) / 2f, by + (badge - gs) / 2f, gs);

                // Chevron / caret
                float cx = w - 22f;
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(hover ? accent : CHEVRON_IDLE);
                Path2D.Float arrow = new Path2D.Float();
                if (opensMenu) {
                    arrow.moveTo(cx - 5, centerY - 2.5f);
                    arrow.lineTo(cx, centerY + 2.5f);
                    arrow.lineTo(cx + 5, centerY - 2.5f);
                } else {
                    arrow.moveTo(cx - 3, centerY - 6);
                    arrow.lineTo(cx + 3, centerY);
                    arrow.lineTo(cx - 3, centerY + 6);
                }
                g2.draw(arrow);

                // Title + description
                float tx = bx + badge + 14f;
                int maxTextW = (int) (cx - 14f - tx);
                FontMetrics fmT = g2.getFontMetrics(fontCardTitle);
                FontMetrics fmD = g2.getFontMetrics(fontCardDesc);
                List<String> lines = wrap(description, fmD, maxTextW, 2);

                int blockH = fmT.getAscent() + fmT.getDescent() + 3 + lines.size() * fmD.getHeight();
                float ty = centerY - blockH / 2f + fmT.getAscent();

                g2.setFont(fontCardTitle);
                g2.setColor(enabled ? TEXT_PRIMARY : TEXT_SECONDARY);
                g2.drawString(ellipsize(title, fmT, maxTextW), tx, ty);

                g2.setFont(fontCardDesc);
                g2.setColor(TEXT_SECONDARY);
                float dy = ty + fmT.getDescent() + 3 + fmD.getAscent();
                for (String line : lines) {
                    g2.drawString(line, tx, dy);
                    dy += fmD.getHeight();
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Small helper components
    // -------------------------------------------------------------------------

    /** A hyperlink-style label that underlines on hover without changing size. */
    private final class LinkLabel extends JLabel {
        private static final long serialVersionUID = 1L;
        private boolean hover;

        LinkLabel(String text, String tooltip, Runnable action) {
            super(text);
            setFont(fontSmall);
            setForeground(PRIMARY_COLOR);
            setToolTipText(tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hover) {
                int y = getBaseline(getWidth(), getHeight()) + 2;
                g.setColor(getForeground());
                g.drawLine(0, y, getFontMetrics(getFont()).stringWidth(getText()), y);
            }
        }
    }

    /** A 1-px horizontal rule that fades out towards both ends. */
    private static final class FadingSeparator extends JComponent {
        private static final long serialVersionUID = 1L;

        FadingSeparator() {
            setPreferredSize(new Dimension(0, 1));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Color line = new Color(160, 170, 180);
                g2.setPaint(new LinearGradientPaint(0, 0, getWidth(), 0,
                        new float[] {0f, 0.5f, 1f},
                        new Color[] {withAlpha(line, 0), withAlpha(line, 140), withAlpha(line, 0)}));
                g2.fillRect(0, 0, getWidth(), 1);
            } finally {
                g2.dispose();
            }
        }
    }

    /** Vertical gradient background for the whole window. */
    private static final class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setPaint(new GradientPaint(0, 0, BACKGROUND_START, 0, getHeight(), BACKGROUND_END));
                g2.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2.dispose();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Text / colour utilities
    // -------------------------------------------------------------------------

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Greedy word wrap into at most {@code maxLines} lines; the last line is ellipsized. */
    private static List<String> wrap(String text, FontMetrics fm, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>(maxLines);
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = line.length() == 0 ? words[i] : line + " " + words[i];
            if (line.length() == 0 || fm.stringWidth(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            if (lines.size() == maxLines - 1) {
                // Last available line: put everything remaining on it, ellipsized
                StringBuilder rest = new StringBuilder(words[i]);
                for (int j = i + 1; j < words.length; j++) {
                    rest.append(' ').append(words[j]);
                }
                lines.add(ellipsize(rest.toString(), fm, maxWidth));
                return lines;
            }
            line.setLength(0);
            line.append(words[i]);
        }
        if (line.length() > 0) {
            lines.add(ellipsize(line.toString(), fm, maxWidth));
        }
        return lines;
    }

    private static String ellipsize(String s, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(s) <= maxWidth) {
            return s;
        }
        final String ellipsis = "\u2026";
        int end = s.length();
        while (end > 0 && fm.stringWidth(s.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return s.substring(0, end).trim() + ellipsis;
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new LauncherWindow();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}