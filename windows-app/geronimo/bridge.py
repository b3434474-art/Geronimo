"""Geronimo phone transports: USB/ADB first, Bluetooth LE as fallback.

USB carries call-control messages and call-state notifications. Bluetooth LE
remains available as the fallback transport. Neither transport carries
cellular call audio.
"""
from __future__ import annotations

import asyncio
import json
import shutil
import subprocess
import threading
from typing import Any, Callable

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "8f3a0001-7b2c-4d5e-9a10-1234567890ab"
COMMAND_UUID = "8f3a0002-7b2c-4d5e-9a10-1234567890ab"
STATUS_UUID = "8f3a0003-7b2c-4d5e-9a10-1234567890ab"
USB_PORT = 43821
_FRAME_MAGIC = 0x47


class GeronimoPhoneBridge:
    """Reconnect-capable ADB USB + BLE session with event callbacks."""

    def __init__(
        self,
        on_status: Callable[[str], None] | None = None,
        on_event: Callable[[dict[str, Any]], None] | None = None,
        scan_timeout: float = 8.0,
    ) -> None:
        self.on_status = on_status or (lambda _message: None)
        self.on_event = on_event or (lambda _event: None)
        self.scan_timeout = scan_timeout
        self._thread: threading.Thread | None = None
        self._loop: asyncio.AbstractEventLoop | None = None
        self._client: BleakClient | None = None
        self._usb_writer: asyncio.StreamWriter | None = None
        self._usb_serial: str | None = None
        self._stop = threading.Event()
        self._lock = threading.RLock()
        self._connected = threading.Event()
        self._rx_sequence: int | None = None
        self._rx_count = 0
        self._rx_parts: dict[int, bytes] = {}
        self._last_usb_hint = ""

    @property
    def connected(self) -> bool:
        return self._connected.is_set()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._thread_main, name="GeronimoPhoneLink", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        loop = self._loop
        if loop and loop.is_running():
            try:
                asyncio.run_coroutine_threadsafe(self._disconnect(), loop).result(timeout=4)
            except Exception:
                pass
        self._connected.clear()

    def send_command(self, command: dict[str, Any], timeout: float = 12.0) -> str:
        loop = self._loop
        if not loop or not loop.is_running() or not self.connected:
            raise RuntimeError("Phone is not connected. Connect by USB or enable Bluetooth on the phone.")
        future = asyncio.run_coroutine_threadsafe(self._write_command(command), loop)
        return future.result(timeout=timeout)

    def _thread_main(self) -> None:
        try:
            asyncio.run(self._run())
        except Exception as exc:
            self.on_status(f"Phone link stopped: {exc}")
        finally:
            self._connected.clear()
            self._loop = None
            self._client = None
            self._usb_writer = None
            self._usb_serial = None

    async def _run(self) -> None:
        self._loop = asyncio.get_running_loop()
        while not self._stop.is_set():
            # Prefer the physical USB cable whenever an authorized ADB device exists.
            usb = await self._open_usb()
            if usb is not None:
                serial, reader, writer = usb
                self._usb_writer = writer
                self._usb_serial = serial
                self._connected.set()
                self.on_status(f"USB connected · Android device {serial}")
                try:
                    writer.write(b'{"command":"PING"}\n')
                    await writer.drain()
                    await self._read_usb_events(reader)
                except Exception as exc:
                    if not self._stop.is_set():
                        self.on_status(f"USB link interrupted: {exc}")
                finally:
                    self._connected.clear()
                    self._usb_writer = None
                    self._usb_serial = None
                    writer.close()
                    try:
                        await writer.wait_closed()
                    except Exception:
                        pass
                    await self._remove_usb_forward(serial)
                if not self._stop.is_set():
                    self.on_status("USB disconnected · checking USB again, then Bluetooth…")
                    await self._sleep_or_stop(1.5)
                continue

            await self._run_bluetooth_cycle()

    async def _open_usb(
        self,
    ) -> tuple[str, asyncio.StreamReader, asyncio.StreamWriter] | None:
        adb = shutil.which("adb")
        if not adb:
            self._set_usb_hint("ADB not found — install Android SDK Platform-Tools for USB, or use Bluetooth.")
            return None

        try:
            result = await asyncio.to_thread(
                subprocess.run,
                [adb, "devices"],
                capture_output=True,
                text=True,
                timeout=6,
                check=False,
            )
        except Exception as exc:
            self._set_usb_hint(f"Could not check USB devices: {exc}")
            return None

        authorized: list[str] = []
        unauthorized = False
        for line in result.stdout.splitlines()[1:]:
            fields = line.strip().split()
            if len(fields) >= 2:
                if fields[1] == "device":
                    authorized.append(fields[0])
                elif fields[1] == "unauthorized":
                    unauthorized = True

        if not authorized:
            if unauthorized:
                self._set_usb_hint("Unlock your phone and approve the USB debugging prompt on this PC.")
            else:
                self._set_usb_hint("No authorized USB phone yet — connect the cable; Bluetooth fallback is available.")
            return None

        for serial in authorized:
            try:
                await asyncio.to_thread(
                    subprocess.run,
                    [adb, "-s", serial, "forward", "--remove", f"tcp:{USB_PORT}"],
                    capture_output=True,
                    text=True,
                    timeout=6,
                    check=False,
                )
                forward = await asyncio.to_thread(
                    subprocess.run,
                    [adb, "-s", serial, "forward", f"tcp:{USB_PORT}", f"tcp:{USB_PORT}"],
                    capture_output=True,
                    text=True,
                    timeout=6,
                    check=False,
                )
                if forward.returncode != 0:
                    continue
                reader, writer = await asyncio.wait_for(
                    asyncio.open_connection("127.0.0.1", USB_PORT), timeout=2.5
                )
                self._last_usb_hint = ""
                return serial, reader, writer
            except Exception:
                await self._remove_usb_forward(serial)
        return None

    async def _remove_usb_forward(self, serial: str) -> None:
        adb = shutil.which("adb")
        if not adb:
            return
        try:
            await asyncio.to_thread(
                subprocess.run,
                [adb, "-s", serial, "forward", "--remove", f"tcp:{USB_PORT}"],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )
        except Exception:
            pass

    def _set_usb_hint(self, message: str) -> None:
        if message != self._last_usb_hint:
            self._last_usb_hint = message
            self.on_status(message)

    async def _read_usb_events(self, reader: asyncio.StreamReader) -> None:
        while not self._stop.is_set():
            try:
                line = await asyncio.wait_for(reader.readline(), timeout=0.75)
            except asyncio.TimeoutError:
                continue
            if not line:
                return
            if len(line) > 8192:
                self.on_status("Ignored an oversized USB phone-link event.")
                continue
            try:
                event = json.loads(line.decode("utf-8"))
                if isinstance(event, dict):
                    self.on_event(event)
            except (json.JSONDecodeError, UnicodeDecodeError):
                self.on_status("Received an unreadable USB phone event.")

    async def _run_bluetooth_cycle(self) -> None:
        self.on_status("USB not connected · searching for Geronimo over Bluetooth…")
        device = await BleakScanner.find_device_by_filter(
            lambda _device, adv: SERVICE_UUID.lower() in {
                str(value).lower() for value in (adv.service_uuids or [])
            },
            timeout=self.scan_timeout,
        )
        if device is None:
            await self._sleep_or_stop(2.0)
            return

        client = BleakClient(device, timeout=15.0)
        with self._lock:
            self._client = client
        try:
            await client.connect()
            await client.start_notify(STATUS_UUID, self._notification_received)
            with self._lock:
                self._connected.set()
            self.on_status(f"Bluetooth connected to {device.name or device.address}")
            while client.is_connected and not self._stop.is_set():
                # Recheck USB periodically so it takes priority when plugged in.
                await self._sleep_or_stop(1.0)
                if await self._has_authorized_adb_device():
                    break
        except Exception as exc:
            self.on_status(f"Bluetooth connection issue: {exc}")
        finally:
            with self._lock:
                self._connected.clear()
                if self._client is client:
                    self._client = None
            try:
                if client.is_connected:
                    await client.disconnect()
            except Exception:
                pass
        if not self._stop.is_set():
            self.on_status("Bluetooth disconnected · checking USB and reconnecting…")
            await self._sleep_or_stop(1.0)

    async def _has_authorized_adb_device(self) -> bool:
        adb = shutil.which("adb")
        if not adb:
            return False
        try:
            result = await asyncio.to_thread(
                subprocess.run,
                [adb, "devices"],
                capture_output=True,
                text=True,
                timeout=4,
                check=False,
            )
            return any(
                len(fields := line.strip().split()) >= 2 and fields[1] == "device"
                for line in result.stdout.splitlines()[1:]
            )
        except Exception:
            return False

    async def _sleep_or_stop(self, seconds: float) -> None:
        end = asyncio.get_running_loop().time() + seconds
        while not self._stop.is_set() and asyncio.get_running_loop().time() < end:
            await asyncio.sleep(0.2)

    async def _write_command(self, command: dict[str, Any]) -> str:
        payload = json.dumps(command, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        if len(payload) > 480:
            raise ValueError("Command is too large.")

        writer = self._usb_writer
        if writer is not None and not writer.is_closing():
            writer.write(payload + b"\n")
            await writer.drain()
            return f"Sent {command.get('command', 'command')} over USB."

        with self._lock:
            client = self._client
        if not client or not client.is_connected:
            raise RuntimeError("Phone disconnected before the command could be sent.")
        await client.write_gatt_char(COMMAND_UUID, payload, response=True)
        return f"Sent {command.get('command', 'command')} over Bluetooth."

    async def _disconnect(self) -> None:
        writer = self._usb_writer
        if writer is not None:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass
        with self._lock:
            client = self._client
        if client and client.is_connected:
            await client.disconnect()

    def _notification_received(self, _sender: int, data: bytearray) -> None:
        packet = bytes(data)
        if len(packet) >= 4 and packet[0] == _FRAME_MAGIC:
            sequence, index, count = packet[1], packet[2], packet[3]
            if count == 0 or index >= count:
                return
            if sequence != self._rx_sequence:
                self._rx_sequence = sequence
                self._rx_count = count
                self._rx_parts = {}
            if count != self._rx_count:
                self._rx_parts = {}
                self._rx_count = count
            self._rx_parts[index] = packet[4:]
            if len(self._rx_parts) == self._rx_count:
                payload = b"".join(self._rx_parts[i] for i in range(self._rx_count))
                self._rx_parts = {}
                self._dispatch_json(payload.decode("utf-8", errors="replace"))
        else:
            text = packet.decode("utf-8", errors="replace")
            if text.startswith("{"):
                self._dispatch_json(text)

    def _dispatch_json(self, payload: str) -> None:
        try:
            event = json.loads(payload)
            if isinstance(event, dict):
                self.on_event(event)
        except (json.JSONDecodeError, UnicodeDecodeError):
            self.on_status("Received an unreadable phone status message.")
