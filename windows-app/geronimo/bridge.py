"""Windows BLE client for the Geronimo Android companion.

This proof-of-concept discovers the Geronimo GATT service and sends PING.
It does not access cellular calls, contacts, microphone, or speaker audio.
"""

from __future__ import annotations

import asyncio
import json
from bleak import BleakClient, BleakScanner

SERVICE_UUID = "8f3a0001-7b2c-4d5e-9a10-1234567890ab"
COMMAND_UUID = "8f3a0002-7b2c-4d5e-9a10-1234567890ab"
STATUS_UUID = "8f3a0003-7b2c-4d5e-9a10-1234567890ab"


async def find_phone(timeout: float = 12.0):
    """Find a nearby phone advertising the Geronimo BLE service."""
    devices = await BleakScanner.discover(
        timeout=timeout,
        service_uuids=[SERVICE_UUID],
    )
    return devices[0] if devices else None


async def test_connection() -> str:
    """Connect to the phone, read its status, and send a harmless PING."""
    device = await find_phone()
    if device is None:
        return "Phone not found. Open Geronimo on Android and keep its screen on."

    async with BleakClient(device, timeout=15.0) as client:
        status = await client.read_gatt_char(STATUS_UUID)
        await client.write_gatt_char(COMMAND_UUID, b"PING", response=True)
        phone_status = status.decode("utf-8", errors="replace")
        return f"Connected to {device.name or device.address}. Phone status: {phone_status}. PING sent."


def test_connection_sync() -> str:
    return asyncio.run(test_connection())


async def send_command(command: dict) -> str:
    """Send a JSON call-control command to the paired Geronimo phone over BLE."""
    device = await find_phone()
    if device is None:
        return "Phone not found. Open Geronimo on Android and keep it advertising."
    async with BleakClient(device, timeout=15.0) as client:
        payload = json.dumps(command, separators=(",", ":")).encode("utf-8")
        await client.write_gatt_char(COMMAND_UUID, payload, response=True)
        return f"Sent {command.get('command', 'command')} to {device.name or device.address}."


def send_command_sync(command: dict) -> str:
    return asyncio.run(send_command(command))
