"""Persistent Windows BLE client for the Geronimo Android companion.

BLE carries call-control messages and call-state notifications only. It is not
used for live cellular call audio.
"""
from __future__ import annotations

import asyncio
import json
import threading
from typing import Any, Callable

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "8f3a0001-7b2c-4d5e-9a10-1234567890ab"
COMMAND_UUID = "8f3a0002-7b2c-4d5e-9a10-1234567890ab"
STATUS_UUID = "8f3a0003-7b2c-4d5e-9a10-1234567890ab"
_FRAME_MAGIC = 0x47


class GeronimoPhoneBridge:
    """Reconnect-capable BLE session, with notifications delivered to callbacks."""

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
        self._stop = threading.Event()
        self._lock = threading.RLock()
        self._connected = threading.Event()
        self._rx_sequence: int | None = None
        self._rx_count = 0
        self._rx_parts: dict[int, bytes] = {}

    @property
    def connected(self) -> bool:
        return self._connected.is_set()

    def start(self) -> None:
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._thread_main, name="GeronimoBLE", daemon=True)
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
            raise RuntimeError("Phone is not connected. Open Geronimo on Android and connect again.")
        future = asyncio.run_coroutine_threadsafe(self._write_command(command), loop)
        return future.result(timeout=timeout)

    def _thread_main(self) -> None:
        try:
            asyncio.run(self._run())
        except Exception as exc:
            self.on_status(f"Bluetooth stopped: {exc}")
        finally:
            self._connected.clear()
            self._loop = None
            self._client = None

    async def _run(self) -> None:
        self._loop = asyncio.get_running_loop()
        while not self._stop.is_set():
            self.on_status("Searching for your Geronimo phone…")
            device = await BleakScanner.find_device_by_filter(
                lambda _device, adv: SERVICE_UUID.lower() in {
                    str(value).lower() for value in (adv.service_uuids or [])
                },
                timeout=self.scan_timeout,
            )
            if device is None:
                self.on_status("Phone not found. Keep Geronimo open on Android and Bluetooth on.")
                await self._sleep_or_stop(3.0)
                continue

            client = BleakClient(device, timeout=15.0)
            with self._lock:
                self._client = client
            try:
                await client.connect()
                await client.start_notify(STATUS_UUID, self._notification_received)
                with self._lock:
                    self._connected.set()
                self.on_status(f"Connected to {device.name or device.address}")
                while client.is_connected and not self._stop.is_set():
                    await asyncio.sleep(0.5)
            except Exception as exc:
                self.on_status(f"Phone connection issue: {exc}")
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
                self.on_status("Phone disconnected. Reconnecting…")
                await self._sleep_or_stop(2.0)

    async def _sleep_or_stop(self, seconds: float) -> None:
        end = asyncio.get_running_loop().time() + seconds
        while not self._stop.is_set() and asyncio.get_running_loop().time() < end:
            await asyncio.sleep(0.2)

    async def _write_command(self, command: dict[str, Any]) -> str:
        with self._lock:
            client = self._client
        if not client or not client.is_connected:
            raise RuntimeError("Phone disconnected before the command could be sent.")
        payload = json.dumps(command, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
        if len(payload) > 480:
            raise ValueError("Command is too large.")
        await client.write_gatt_char(COMMAND_UUID, payload, response=True)
        return f"Sent {command.get('command', 'command')} to the phone."

    async def _disconnect(self) -> None:
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
