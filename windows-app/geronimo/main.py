"""Minimal Geronimo desktop shell.

Call transport and call control are intentionally not simulated here.
"""

from __future__ import annotations

import tkinter as tk
from tkinter import ttk


def main() -> None:
    root = tk.Tk()
    root.title("Geronimo")
    root.geometry("440x300")
    root.minsize(380, 260)

    frame = ttk.Frame(root, padding=24)
    frame.pack(fill="both", expand=True)

    ttk.Label(frame, text="Geronimo", font=("Segoe UI", 22, "bold")).pack(
        anchor="w"
    )
    ttk.Label(
        frame,
        text="Phone companion for Windows",
        font=("Segoe UI", 10),
    ).pack(anchor="w", pady=(2, 18))

    status = ttk.Label(
        frame,
        text="Phone connection: Not connected",
        foreground="#9a4b00",
        font=("Segoe UI", 11, "bold"),
    )
    status.pack(anchor="w", pady=(0, 12))

    ttk.Label(
        frame,
        text=(
            "The Bluetooth bridge is not implemented yet. "
            "Call controls will stay disabled until a trusted connection "
            "and phone-side call capability are verified."
        ),
        wraplength=380,
        justify="left",
    ).pack(anchor="w")

    ttk.Label(
        frame,
        text="Incoming calls will always require your approval.",
        wraplength=380,
        justify="left",
    ).pack(anchor="w", pady=(18, 0))

    ttk.Label(
        frame,
        text="Build status: starter shell",
        font=("Segoe UI", 9, "italic"),
    ).pack(side="bottom", anchor="w", pady=(20, 0))

    root.mainloop()


if __name__ == "__main__":
    main()
