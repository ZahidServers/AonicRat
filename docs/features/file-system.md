# File System

![File System](../Screenshot_1.png)

A full remote file manager — browse, transfer, and manage files on any connected device.

---

## Capabilities

- Browse the complete remote file system — drives, folders, files
- Navigate directories by double-clicking folders; use `..` to go up
- **Back** and **Forward** navigation buttons
- View Name, Size, Type, and Modified date for every entry
- **Upload** — send files from the operator machine to the remote device
- **Download** — retrieve files from the remote device with a progress bar
- **Delete** — remove files or folders remotely
- **Rename** — rename files or folders remotely
- **Zip** — compress files/folders on the remote device
- **Extract** — decompress ZIP archives on the remote device
- **View** — open text files in a built-in viewer
- **Edit** — edit text files remotely and save changes back

---

## How It Works

1. The operator selects a device and clicks **Connect**
2. The controller sends a `LIST_FILES` command with the current path
3. The agent walks the directory, collects file metadata (name, size, type, modified timestamp), and streams the result back
4. The controller populates the table
5. Upload/download uses a chunked binary transfer over the same socket with progress reporting

---

## What You Learn

!!! info "Industry Comparison"
    This demonstrates exactly how **TeamViewer File Transfer**, **WinSCP**, **FileZilla** (SFTP mode), and enterprise MDM file managers work. File metadata is transferred alongside listings — the same pattern as FTP `LIST`/`MLSD` responses. Chunked binary transfer with progress callbacks mirrors how HTTP multipart uploads work in enterprise deployment tools.
