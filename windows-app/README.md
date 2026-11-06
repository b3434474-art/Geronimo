# Geronimo Windows app

Requires Python 3.10+ on Windows.

Install the BLE dependency and start the app:

```powershell
python -m pip install -r requirements.txt
python -m geronimo.main
```

For the first BLE test:
1. Install and open the Android companion on the phone.
2. Turn on Bluetooth and grant the requested Bluetooth permissions.
3. Keep Geronimo open on the phone so it advertises the BLE service.
4. On Windows, run the desktop app and select **Find phone & test Bluetooth**.

The Windows app scans for the Geronimo BLE service, connects, reads a status value, and sends a harmless PING. The Android app should update its status when it receives the PING.

This is a transport proof-of-concept only. It does not read contacts, cellular call state, or call audio, and it cannot answer or place calls.
