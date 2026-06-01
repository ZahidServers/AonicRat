# GUI Remote (Remote Desktop)

![GUI Remote](../Screenshot_3.png)

A remote desktop viewer that captures and streams the remote machine's screen to the operator in real time.

---

## Capabilities

- Captures the remote machine's screen and streams frames to the controller
- Live display of the remote desktop inside the controller window
- **Ctrl+Alt+Del** — sends the secure attention sequence to the remote machine
- **Screenshot** — captures and saves the current remote screen as an image file
- **FPS control** — adjust the frame streaming rate
- **Quality control** — balance image quality against network bandwidth
- Clear "not connected" state when no device is attached

---

## How It Works

1. Operator connects to a device in the GUI Remote tab
2. The controller sends a `START_SCREEN` command
3. The agent captures the screen using the Java `Robot` API (`createScreenCapture`)
4. Frames are compressed (JPEG) and streamed back over the socket
5. The controller decodes each frame and renders it in the display panel
6. FPS and quality settings control how frequently frames are captured and sent

---

## What You Learn

!!! info "Industry Comparison"
    This demonstrates the core of **VNC (Virtual Network Computing)**, **Microsoft RDP (Remote Desktop Protocol)**, **TeamViewer**, and **AnyDesk**. All of these work by capturing the screen, compressing frames, and streaming them over a network. The fundamental tradeoff between **FPS** (smoothness), **image quality** (clarity), and **bandwidth** (network load) is identical across all remote desktop tools.
