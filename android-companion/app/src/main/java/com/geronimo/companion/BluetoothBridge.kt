package com.geronimo.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.util.UUID

/**
 * Minimal BLE GATT peripheral for local Geronimo messages.
 * This is a transport proof-of-concept, not a cellular-call or audio bridge.
 */
class BluetoothBridge(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8f3a0001-7b2c-4d5e-9a10-1234567890ab")
        val COMMAND_UUID: UUID = UUID.fromString("8f3a0002-7b2c-4d5e-9a10-1234567890ab")
        val STATUS_UUID: UUID = UUID.fromString("8f3a0003-7b2c-4d5e-9a10-1234567890ab")
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private var gattServer: BluetoothGattServer? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            onStatus("Bluetooth is off")
            return
        }
        val advertiser = adapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            onStatus("BLE advertising is not supported")
            return
        }

        val service = BluetoothGattService(
            SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        val command = BluetoothGattCharacteristic(
            COMMAND_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        val status = BluetoothGattCharacteristic(
            STATUS_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        status.value = "GERONIMO_READY".toByteArray(Charsets.UTF_8)
        service.addCharacteristic(command)
        service.addCharacteristic(status)

        gattServer = manager.openGattServer(appContext, object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, statusCode: Int, newState: Int) {
                onStatus(if (newState == BluetoothGatt.STATE_CONNECTED) {
                    "Windows connected: ${device.name ?: "Geronimo PC"}"
                } else {
                    "Advertising — waiting for Windows"
                })
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (characteristic.uuid == COMMAND_UUID) {
                    val message = value.toString(Charsets.UTF_8)
                    if (message == "PING") {
                        onStatus("Bluetooth test received from Windows")
                    } else {
                        onStatus("Received message: ${message.take(80)}")
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf()
                        )
                    }
                }
            }
        })

        if (gattServer == null) {
            onStatus("Could not open BLE GATT server")
            return
        }
        gattServer?.addService(service)
        advertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .build(),
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    onStatus("Advertising for Geronimo Windows app")
                }

                override fun onStartFailure(errorCode: Int) {
                    onStatus("BLE advertising failed (code $errorCode)")
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            manager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(object : AdvertiseCallback() {})
        } catch (_: SecurityException) {
            // Permissions may have been revoked while the activity was stopped.
        }
        gattServer?.close()
        gattServer = null
    }
}
