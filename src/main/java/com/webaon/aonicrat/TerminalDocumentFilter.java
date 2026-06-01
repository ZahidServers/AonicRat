/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.aonicrat;

/**
 *
 * @author Zahid Wadiwale
 */
import java.awt.Toolkit;
import javax.swing.text.*;

public class TerminalDocumentFilter extends DocumentFilter {
    private int promptPosition;

    public void setPromptPosition(int promptPosition) {
        this.promptPosition = promptPosition;
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
        int docLength = fb.getDocument().getLength();
        if (offset >= promptPosition && offset <= docLength) {
            super.insertString(fb, offset, string, attr);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        int docLength = fb.getDocument().getLength();
        if (offset >= promptPosition && offset <= docLength) {
            super.remove(fb, offset, length);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        int docLength = fb.getDocument().getLength();
        if (offset >= promptPosition && offset <= docLength) {
            super.replace(fb, offset, length, text, attrs);
        } else {
            Toolkit.getDefaultToolkit().beep();
        }
    }

}
