"""Geronimo Windows app: original assistant UI plus its phone companion."""
from __future__ import annotations

import base64
import json
import lzma
import os
import threading
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, simpledialog, ttk

# Restore the exact supplied original assistant source for source checkouts.
_SOURCE = Path(__file__).with_name("original_assistant.py")
_PAYLOAD = Path(__file__).with_name("original_assistant.py.xz.b64")
if not _SOURCE.exists() and _PAYLOAD.exists():
    _SOURCE.write_bytes(lzma.decompress(base64.b64decode(_PAYLOAD.read_bytes())))

from geronimo.original_assistant import GeronimoApp as OriginalGeronimoApp


class GeronimoApp(OriginalGeronimoApp):
    """Keep the original orb-and-tools UI, adding a separate phone strip."""

    def __init__(self, root: tk.Tk):
        self.phone_bridge = None
        self.phone_status = tk.StringVar(value="PHONE NOT CONNECTED")
        self.current_call = {"state": "IDLE", "number": "", "name": ""}
        self.call_popup = None
        self.muted = False
        self.speaker = False
        self.contacts_path = Path(os.environ.get("APPDATA", str(Path.home()))) / "Geronimo" / "phone_contacts.json"
        self.contacts = self._load_contacts()
        super().__init__(root)
        root.protocol("WM_DELETE_WINDOW", self.shutdown)

    def _build(self):
        super()._build()
        children = self.root.winfo_children()
        before = children[-1] if children else None
        bar = tk.Frame(self.root, bg="#071426")
        if before is not None:
            bar.pack(fill="x", padx=28, pady=(0, 8), before=before)
        else:
            bar.pack(fill="x", padx=28, pady=(0, 8))
        tk.Label(bar, text="PHONE LINK", bg="#071426", fg="#58A9FF",
                 font=("Segoe UI", 9, "bold")).pack(side="left", padx=(0, 12))
        tk.Label(bar, textvariable=self.phone_status, bg="#071426", fg="#9DB5D0",
                 font=("Segoe UI", 9)).pack(side="left", fill="x", expand=True)
        self._phone_button(bar, "Connect", self.toggle_phone_connection)
        self._phone_button(bar, "Calls", self.phone_call_panel)
        self._phone_button(bar, "Contacts", self.manage_contacts)

    @staticmethod
    def _phone_button(parent, label, command):
        tk.Button(parent, text=label, command=command, bg="#10233D", fg="#EAF4FF",
                  activebackground="#1B3657", activeforeground="#EAF4FF",
                  relief="flat", padx=12, pady=6, font=("Segoe UI", 9, "bold")).pack(side="left", padx=4)

    def _load_contacts(self):
        try:
            data = json.loads(self.contacts_path.read_text(encoding="utf-8"))
            return data if isinstance(data, list) else []
        except Exception:
            return []

    def _save_contacts(self):
        self.contacts_path.parent.mkdir(parents=True, exist_ok=True)
        self.contacts_path.write_text(json.dumps(self.contacts, indent=2, ensure_ascii=False), encoding="utf-8")

    def toggle_phone_connection(self):
        if self.phone_bridge is not None:
            self.phone_bridge.stop()
            self.phone_bridge = None
            self.phone_status.set("PHONE DISCONNECTED")
            return
        try:
            from geronimo.bridge import GeronimoPhoneBridge
            self.phone_bridge = GeronimoPhoneBridge(self._on_phone_status, self._on_phone_event)
            self.phone_bridge.start()
            self.phone_status.set("SEARCHING FOR GERONIMO PHONE…")
        except Exception as exc:
            messagebox.showerror("Bluetooth unavailable", f"Install the app requirements first.\n\n{exc}", parent=self.root)

    def _on_phone_status(self, message: str):
        self.root.after(0, lambda: self.phone_status.set(message.upper()[:100]))

    def _on_phone_event(self, event: dict):
        self.root.after(0, lambda payload=event: self._apply_phone_event(payload))

    def _apply_phone_event(self, event: dict):
        kind = str(event.get("event", "")).upper()
        if kind in ("CALL_RINGING", "CALL_DIALING", "CALL_ACTIVE"):
            self.current_call = {
                "state": kind,
                "number": str(event.get("number", "Unknown number")),
                "name": str(event.get("name", "")).strip(),
            }
            title = self.current_call["name"] or self.current_call["number"]
            self.phone_status.set(f"{kind.replace('CALL_', '')} · {title}".upper()[:100])
            self._show_call_popup()
            if kind == "CALL_RINGING":
                self._speak(f"Incoming call from {title}. Please answer or decline.")
        elif kind == "CALL_DIAL_REQUESTED":
            self.phone_status.set(
                "TRUSTED NUMBER: STARTING CALL ON PHONE"
                if event.get("direct")
                else "PHONE DIALER OPENED · CONFIRM THE CALL ON PHONE"
            )
        elif kind == "CALL_ENDED":
            self.current_call = {"state": "IDLE", "number": "", "name": ""}
            self.phone_status.set("CALL ENDED · PHONE LINK READY")
            if self.call_popup and self.call_popup.winfo_exists():
                self.call_popup.destroy()
                self.call_popup = None
        elif kind == "CALL_ERROR":
            messagebox.showerror("Phone call", str(event.get("message", "The phone reported an error.")), parent=self.root)
        elif kind == "PHONE_READY":
            self.phone_status.set("PHONE READY · BLUETOOTH CONNECTED")
            for contact in self.contacts:
                self._send_phone_command({
                    "command": "ALLOWLIST_ADD",
                    "name": contact.get("name", ""),
                    "number": contact.get("number", ""),
                }, quiet=True)

    def _send_phone_command(self, command: dict, quiet: bool = False):
        bridge = self.phone_bridge
        if bridge is None or not bridge.connected:
            if not quiet:
                messagebox.showinfo("Phone not connected", "Connect your Android phone first.", parent=self.root)
            return

        def worker():
            try:
                result = bridge.send_command(command)
            except Exception as exc:
                result = f"Phone command failed: {exc}"
            if not quiet:
                self.root.after(0, lambda: self.phone_status.set(result.upper()[:100]))

        threading.Thread(target=worker, daemon=True).start()

    def manage_contacts(self):
        win = tk.Toplevel(self.root)
        win.title("Geronimo · Trusted outgoing contacts")
        win.configure(bg="#071426")
        win.geometry("500x410")
        tk.Label(win, text="TRUSTED OUTGOING CONTACTS", bg="#071426", fg="#EAF4FF",
                 font=("Segoe UI", 15, "bold")).pack(anchor="w", padx=18, pady=(16, 5))
        tk.Label(win, text="Saved numbers are synced to the phone. Only the phone-side list can authorize direct dialing. Incoming calls are never auto-answered.",
                 bg="#071426", fg="#9DB5D0", wraplength=460, justify="left").pack(anchor="w", padx=18, pady=(0, 10))
        listing = tk.Listbox(win, bg="#10233D", fg="#EAF4FF", selectbackground="#245B91", relief="flat", height=10)
        listing.pack(fill="both", expand=True, padx=18, pady=6)

        def refresh():
            listing.delete(0, "end")
            for contact in self.contacts:
                listing.insert("end", f"{contact.get('name', 'Contact')}  ·  {contact.get('number', '')}")

        def add():
            name = simpledialog.askstring("Contact name", "Name for this contact:", parent=win)
            if not name or not name.strip():
                return
            number = simpledialog.askstring("Phone number", "Number to trust for outgoing calls:", parent=win)
            if not number:
                return
            clean = "".join(ch for ch in number if ch.isdigit() or ch in "+*#")
            if len(clean) < 3:
                messagebox.showerror("Invalid number", "Enter a valid phone number.", parent=win)
                return
            self.contacts = [item for item in self.contacts if item.get("number") != clean]
            self.contacts.append({"name": name.strip()[:60], "number": clean})
            self._save_contacts()
            self._send_phone_command({"command": "ALLOWLIST_ADD", "name": name.strip(), "number": clean}, quiet=True)
            refresh()

        def remove():
            chosen = listing.curselection()
            if not chosen:
                return
            contact = self.contacts.pop(chosen[0])
            self._save_contacts()
            self._send_phone_command({"command": "ALLOWLIST_REMOVE", "number": contact.get("number", "")}, quiet=True)
            refresh()

        actions = tk.Frame(win, bg="#071426")
        actions.pack(fill="x", padx=18, pady=12)
        for label, action in (("Add contact", add), ("Remove selected", remove), ("Close", win.destroy)):
            tk.Button(actions, text=label, command=action, bg="#243A55", fg="#EAF4FF",
                      relief="flat", padx=12, pady=8).pack(side="left", padx=(0, 8))
        refresh()

    def phone_call_panel(self):
        win = tk.Toplevel(self.root)
        win.title("Geronimo · Phone controls")
        win.configure(bg="#071426")
        win.geometry("510x390")
        tk.Label(win, text="PHONE COMPANION", bg="#071426", fg="#58A9FF",
                 font=("Segoe UI", 12, "bold")).pack(anchor="w", padx=18, pady=(16, 4))
        tk.Label(win, text="Call controls use Bluetooth. Incoming calls still require you to press Answer.",
                 bg="#071426", fg="#9DB5D0", wraplength=470, justify="left").pack(anchor="w", padx=18, pady=(0, 10))
        number = tk.StringVar()
        tk.Entry(win, textvariable=number, bg="#10233D", fg="#EAF4FF", insertbackground="#EAF4FF",
                 relief="flat", font=("Segoe UI", 14)).pack(fill="x", padx=18, pady=5)
        choices = [f"{c.get('name', 'Contact')} · {c.get('number', '')}" for c in self.contacts]
        selected = tk.StringVar(value=choices[0] if choices else "No saved contacts")
        picker = ttk.Combobox(win, textvariable=selected, values=choices, state="readonly")
        picker.pack(fill="x", padx=18, pady=5)

        def choose(_event=None):
            index = picker.current()
            if 0 <= index < len(self.contacts):
                number.set(self.contacts[index].get("number", ""))

        picker.bind("<<ComboboxSelected>>", choose)

        def dial():
            clean = "".join(ch for ch in number.get() if ch.isdigit() or ch in "+*#")
            if len(clean) < 3:
                messagebox.showerror("Invalid number", "Enter a phone number or choose a saved contact.", parent=win)
                return
            match = next((c for c in self.contacts if c.get("number") == clean), {})
            self._send_phone_command({"command": "DIAL", "number": clean, "name": match.get("name", "")})

        tk.Button(win, text="Dial", command=dial, bg="#1B5C96", fg="#EAF4FF", relief="flat",
                  padx=18, pady=8).pack(anchor="w", padx=18, pady=(8, 12))
        controls = tk.Frame(win, bg="#071426")
        controls.pack(anchor="w", padx=18, pady=4)
        for label, command in (("Answer", "ANSWER"), ("Decline", "REJECT"), ("Hang up", "DISCONNECT")):
            tk.Button(controls, text=label, command=lambda cmd=command: self._send_phone_command({"command": cmd}),
                      bg="#243A55", fg="#EAF4FF", relief="flat", padx=12, pady=8).pack(side="left", padx=(0, 7))
        toggles = tk.Frame(win, bg="#071426")
        toggles.pack(anchor="w", padx=18, pady=8)

        def toggle_mute():
            self.muted = not self.muted
            self._send_phone_command({"command": "MUTE", "enabled": self.muted})

        def toggle_speaker():
            self.speaker = not self.speaker
            self._send_phone_command({"command": "SPEAKER", "enabled": self.speaker})

        tk.Button(toggles, text="Toggle mute", command=toggle_mute, bg="#10233D", fg="#EAF4FF",
                  relief="flat", padx=12, pady=8).pack(side="left", padx=(0, 7))
        tk.Button(toggles, text="Toggle speaker", command=toggle_speaker, bg="#10233D", fg="#EAF4FF",
                  relief="flat", padx=12, pady=8).pack(side="left")
        tk.Label(win, textvariable=self.phone_status, bg="#071426", fg="#58A9FF",
                 wraplength=470, justify="left").pack(anchor="w", padx=18, pady=10)

    def _show_call_popup(self):
        if self.call_popup and self.call_popup.winfo_exists():
            win = self.call_popup
            for child in win.winfo_children():
                child.destroy()
        else:
            win = tk.Toplevel(self.root)
            self.call_popup = win
            win.title("Geronimo · Phone call")
            win.configure(bg="#071426")
            win.geometry("420x290")
            win.attributes("-topmost", True)
            win.protocol("WM_DELETE_WINDOW", win.withdraw)
        name = self.current_call.get("name") or self.current_call.get("number") or "Unknown caller"
        state = self.current_call.get("state", "CALL")
        tk.Label(win, text="GERONIMO PHONE", bg="#071426", fg="#58A9FF",
                 font=("Segoe UI", 10, "bold")).pack(pady=(20, 6))
        tk.Label(win, text=name, bg="#071426", fg="#EAF4FF", font=("Segoe UI", 20, "bold"),
                 wraplength=380).pack(padx=16, pady=4)
        if self.current_call.get("name"):
            tk.Label(win, text=self.current_call.get("number", ""), bg="#071426", fg="#9DB5D0").pack()
        tk.Label(win, text=state.replace("CALL_", "").replace("_", " "), bg="#071426", fg="#9DB5D0",
                 font=("Segoe UI", 11)).pack(pady=10)
        bar = tk.Frame(win, bg="#071426")
        bar.pack(pady=8)
        if state == "CALL_RINGING":
            tk.Button(bar, text="Answer", command=lambda: self._send_phone_command({"command": "ANSWER"}),
                      bg="#1B8D58", fg="white", relief="flat", padx=15, pady=9).pack(side="left", padx=5)
            tk.Button(bar, text="Decline", command=lambda: self._send_phone_command({"command": "REJECT"}),
                      bg="#A52A36", fg="white", relief="flat", padx=15, pady=9).pack(side="left", padx=5)
        else:
            tk.Button(bar, text="Hang up", command=lambda: self._send_phone_command({"command": "DISCONNECT"}),
                      bg="#A52A36", fg="white", relief="flat", padx=15, pady=9).pack(side="left", padx=5)
            tk.Button(bar, text="Phone settings", command=self.phone_call_panel, bg="#243A55", fg="#EAF4FF",
                      relief="flat", padx=12, pady=9).pack(side="left", padx=5)
        win.deiconify()
        win.lift()
        win.attributes("-topmost", True)

    def shutdown(self):
        if self.phone_bridge is not None:
            try:
                self.phone_bridge.stop()
            except Exception:
                pass
            self.phone_bridge = None
        super().shutdown()


def main():
    root = tk.Tk()
    GeronimoApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
