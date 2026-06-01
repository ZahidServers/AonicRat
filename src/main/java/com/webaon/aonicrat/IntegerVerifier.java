/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.aonicrat;

/**
 *
 * @author Zahid Wadiwale
 */
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JTextField;

public class IntegerVerifier extends InputVerifier {
    @Override
    public boolean verify(JComponent input) {
        String text = ((JTextField) input).getText();
        try {
            Integer.parseInt(text);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
