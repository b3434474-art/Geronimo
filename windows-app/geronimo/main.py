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

    ttk.Label(
        frame,
        text="Transport test only — cellular call controls and two-way call audio are not implemented.",
        wraplength=420,
        justify="left",
    ).pack(anchor="w", pady=(22, 0))

    ttk.Label(
        frame,
        text="Incoming calls will always require your approval. Geronimo never auto-answers.",
        wraplength=420,
        justify="left",
    ).pack(side="bottom", anchor="w", pady=(20, 0))

    root.mainloop()


if __name__ == "__main__":
    main()
