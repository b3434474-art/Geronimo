"""Geronimo Windows desktop shell with a BLE connection test."""

from __future__ import annotations

import threading
import tkinter as tk
from tkinter import ttk


def main() -> None:
    root = tk.Tk()
    root.title("Geronimo")
    root.geometry("480x340")
    root.minsize(400, 300)

    frame = ttk.Frame(root, padding=24)
    frame.pack(fill="both", expand=True)

    ttk.Label(frame, text="Geronimo", font=("Segoe UI", 22, "bold")).pack(anchor="w")
    ttk.Label(frame, text="Phone companion for Windows").pack(anchor="w", pady=(2, 18))

    status = ttk.Label(frame, text="Phone connection: Not tested", font=("Segoe UI", 11, "bold"))
    status.pack(anchor="w", pady=(0, 12))

    details = ttk.Label(
        frame,
        text="Open Geronimo on your Android phone, grant Bluetooth permissions, and leave it open.",
        wraplength=420,
        justify="left",
    )
    details.pack(anchor="w", pady=(0, 16))

    def run_test() -> None:
        button.configure(state="disabled")
        status.configure(text="Searching for Geronimo over Bluetooth LE...")
        def worker() -> None:
            try:
                from geronimo.bridge import test_connection_sync
                result = test_connection_sync()
            except Exception as exc:
                result = f"Connection test failed: {exc}"
            root.after(0, lambda: finish(result))
        threading.Thread(target=worker, daemon=True).start()

    def finish(result: str) -> None:
        status.configure(text="Bluetooth test finished")
        details.configure(text=result)
        button.configure(state="normal")

    button = ttk.Button(frame, text="Find phone & test Bluetooth", command=run_test)
    button.pack(anchor="w")

    ttk.Separator(frame).pack(fill="x", pady=18)
    ttk.Label(frame, text="Phone call controls", font=("Segoe UI", 13, "bold")).pack(anchor="w")
    ttk.Label(frame, text="Answer is always a deliberate manual action.", wraplength=420).pack(anchor="w", pady=(4, 8))
    number = tk.StringVar()
    ttk.Entry(frame, textvariable=number).pack(fill="x", pady=(0, 8))
    ttk.Label(frame, text="Enter a number to open the phone dialer").pack(anchor="w")

    def send_call_command(command: dict) -> None:
        status.configure(text="Sending " + command.get("command", "command") + "…")
        button.configure(state="disabled")
        def worker() -> None:
            try:
                from geronimo.bridge import send_command_sync
                result = send_command_sync(command)
            except Exception as exc:
                result = f"Command failed: {exc}"
            root.after(0, lambda: finish(result))
        threading.Thread(target=worker, daemon=True).start()

    controls = ttk.Frame(frame)
    controls.pack(anchor="w", pady=(8, 0))
    ttk.Button(controls, text="Answer", command=lambda: send_call_command({"command": "ANSWER"})).pack(side="left", padx=(0, 6))
    ttk.Button(controls, text="Decline", command=lambda: send_call_command({"command": "REJECT"})).pack(side="left", padx=(0, 6))
    ttk.Button(controls, text="Hang up", command=lambda: send_call_command({"command": "DISCONNECT"})).pack(side="left", padx=(0, 6))
    ttk.Button(controls, text="Dial", command=lambda: send_call_command({"command": "DIAL", "number": number.get(), "allowlisted": False})).pack(side="left")

    ttk.Label(frame, text="Two-way call audio and incoming-call alerts back to Windows are still unfinished.", wraplength=420).pack(anchor="w", pady=(18, 0))

    ttk.Label(
        frame,
        text="Incoming calls will always require your approval. Geronimo never auto-answers.",
        wraplength=420,
        justify="left",
    ).pack(side="bottom", anchor="w", pady=(20, 0))

    root.mainloop()


if __name__ == "__main__":
    main()
