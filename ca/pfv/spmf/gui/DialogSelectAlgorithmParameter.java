package ca.pfv.spmf.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

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
 * A modal dialog asking the user for the value of one algorithm parameter.
 * <p>
 * The dialog shows the parameter name, its expected type, whether it is
 * optional and an example, and validates the input against the parameter type
 * before accepting it. The dialog is shown by the constructor and blocks until
 * closed; use {@link #isConfirmed()} and {@link #getUserInput()} afterwards.
 */
public class DialogSelectAlgorithmParameter extends JDialog {

    private static final long serialVersionUID = 1L;

    private static final Color TEXT_SECONDARY = new Color(118, 124, 132);
    private static final Color TEXT_ERROR     = new Color(192, 57, 43);

    private final DescriptionOfParameter parameter;
    private final JTextField textField;
    private final JLabel messageLabel;
    private boolean confirmed;

    /**
     * Creates and shows the dialog (blocks until it is closed).
     *
     * @param paramDescription the parameter to ask a value for
     * @param parent           the parent window (may be null)
     */
    public DialogSelectAlgorithmParameter(DescriptionOfParameter paramDescription, Window parent) {
        super(parent, "Parameter value", ModalityType.APPLICATION_MODAL);
        this.parameter = paramDescription;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        Font fontName  = base.deriveFont(Font.BOLD, base.getSize2D() + 2f);
        Font fontSmall = base.deriveFont(Font.PLAIN, base.getSize2D() - 1f);

        // --- Prompt -----------------------------------------------------------
        JLabel prompt = new JLabel("Enter a value for the parameter:");
        prompt.setForeground(TEXT_SECONDARY);
        prompt.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(paramDescription.getName());
        name.setFont(fontName);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        head.add(prompt);
        head.add(Box.createVerticalStrut(2));
        head.add(name);

        // --- Input ------------------------------------------------------------
        textField = new JTextField(26);
        textField.setAlignmentX(Component.LEFT_ALIGNMENT);
        textField.setMaximumSize(new Dimension(Integer.MAX_VALUE, textField.getPreferredSize().height));
        String example = paramDescription.getExample();
        if (example != null && !example.isBlank()) {
            textField.setToolTipText(example.trim());
        }

        JLabel hint = new JLabel(buildHint());
        hint.setFont(fontSmall);
        hint.setForeground(TEXT_SECONDARY);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        messageLabel = new JLabel(" ");
        messageLabel.setFont(fontSmall);
        messageLabel.setForeground(TEXT_ERROR);
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel middle = new JPanel();
        middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));
        middle.setOpaque(false);
        middle.add(textField);
        middle.add(Box.createVerticalStrut(5));
        middle.add(hint);
        middle.add(Box.createVerticalStrut(4));
        middle.add(messageLabel);

        // Hide the error as soon as the user edits the value
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { messageLabel.setText(" "); }
            @Override public void removeUpdate(DocumentEvent e)  { messageLabel.setText(" "); }
            @Override public void changedUpdate(DocumentEvent e) { messageLabel.setText(" "); }
        });

        // --- Buttons ----------------------------------------------------------
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> confirm());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(ok);
        buttons.add(cancel);

        // --- Assemble ---------------------------------------------------------
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(14, 16, 12, 16));
        root.add(head, BorderLayout.NORTH);
        root.add(middle, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                textField.requestFocusInWindow();
            }
        });

        pack();
        setMinimumSize(new Dimension(Math.max(400, getWidth()), getHeight()));
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    // -------------------------------------------------------------------------
    // Results
    // -------------------------------------------------------------------------

    /**
     * @return true if the user pressed OK with a valid value, false if the
     *         dialog was cancelled or closed
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * @return the value entered by the user, or an empty string if the dialog
     *         was cancelled
     */
    public String getUserInput() {
        return confirmed ? textField.getText() : "";
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void confirm() {
        String error = validateInput(textField.getText().trim());
        if (error != null) {
            messageLabel.setText(error);
            Toolkit.getDefaultToolkit().beep();
            textField.requestFocusInWindow();
            textField.selectAll();
            return;
        }
        confirmed = true;
        dispose();
    }

    /** Returns an error message, or null if the value is acceptable. */
    private String validateInput(String text) {
        if (text.isEmpty()) {
            return parameter.isOptional() ? null : "A value is required for this parameter.";
        }
        Class<?> type = parameter.getParameterType();
        if (type == null) {
            return null;
        }
        // SPMF accepts percentages ("40%") for numeric thresholds
        String numeric = text.endsWith("%") ? text.substring(0, text.length() - 1).trim() : text;
        try {
            if (type == Integer.class || type == int.class) {
                Integer.parseInt(numeric);
            } else if (type == Long.class || type == long.class) {
                Long.parseLong(numeric);
            } else if (type == Double.class || type == double.class
                    || type == Float.class || type == float.class) {
                Double.parseDouble(numeric);
            } else if (type == Boolean.class || type == boolean.class) {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException();
                }
            }
        } catch (RuntimeException e) {
            return "Please enter " + expectation(type) + ".";
        }
        return null;
    }

    private String buildHint() {
        List<String> parts = new ArrayList<>(3);
        Class<?> type = parameter.getParameterType();
        if (type != null) {
            parts.add(typeLabel(type));
        }
        parts.add(parameter.isOptional() ? "optional" : "required");
        String example = parameter.getExample();
        if (example != null && !example.isBlank()) {
            parts.add(example.trim());
        }
        return String.join("  \u00B7  ", parts);
    }

    private static String typeLabel(Class<?> t) {
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class) {
            return "Integer";
        }
        if (t == Double.class || t == double.class || t == Float.class || t == float.class) {
            return "Number";
        }
        if (t == Boolean.class || t == boolean.class) {
            return "true / false";
        }
        if (t == String.class) {
            return "Text";
        }
        return t.getSimpleName();
    }

    private static String expectation(Class<?> t) {
        if (t == Integer.class || t == int.class || t == Long.class || t == long.class) {
            return "an integer (e.g. 5)";
        }
        if (t == Double.class || t == double.class || t == Float.class || t == float.class) {
            return "a number (e.g. 0.5)";
        }
        if (t == Boolean.class || t == boolean.class) {
            return "true or false";
        }
        return "a valid value";
    }
}