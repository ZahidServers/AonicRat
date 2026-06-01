/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.webaon.aonicrat;

import java.net.Socket;

/**
 *
 * @author Zahid Wadiwale
 */
public class RemoteDevice {
    public Socket socket;
    public String osType;
    public String osName;
    public String pcName;
    public String clientName;

    public RemoteDevice(Socket s, String osType, String osName, String pcName, String clientName) {
        this.socket = s;
        this.osType = osType;
        this.osName = osName;
        this.pcName = pcName;
        this.clientName = clientName;
    }

    @Override
    public String toString() {
        return clientName + " (" + pcName + ", " + osType + ")";
    }
}