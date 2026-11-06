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
import android.os.ParcelUuid
import java.util.UUID
import org.json.JSONObject

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
    private var advertiseCallback: AdvertiseCallback? = null

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

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val command = BluetoothGattCharacteristic(
            COMMAND_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
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
            override fun onServiceAdded(statusCode: Int, addedService: BluetoothGattService) {
                if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                    onStatus("Could not register BLE service (code $statusCode)")
                    return
                }
                val callback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                        onStatus("Advertising for Geronimo Windows app")
                    }

                    override fun onStartFailure(errorCode: Int) {
                        onStatus("BLE advertising failed (code $errorCode)")
                    }
                }
                advertiseCallback = callback
                advertiser.startAdvertising(
                    AdvertiseSettings.Builder()
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                        .setConnectable(true)
                        .setTimeout(0)
                        .build(),
                    AdvertiseData.Builder()
                        .addServiceUuid(ParcelUuid(SERVICE_UUID))
                        .build(),
                    callback
                )
            }

            override fun onConnectionStateChange(device: BluetoothDevice, statusCode: Int, newState: Int) {
                onStatus(if (newState == BluetoothGatt.STATE_CONNECTED) {
                    "Windows connected: ${device.name ?: "Geronimo PC"}"
                } else {
                    "Advertising — waiting for Windows"
                })
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == STATUS_UUID) {
                    val value = "GERONIMO_READY".toByteArray(Charsets.UTF_8)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                }
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
                        try {
                            CallBridge.handleCommand(JSONObject(message))
                            onStatus("Received a call-control command from Windows")
                        } catch (_: Exception) {
                            onStatus("Ignored an invalid command from Windows")
                        }
                    }
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
                    }
                } else if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
                }
            }
        })

        if (gattServer == null) {
            onStatus("Could not open BLE GATT server")
            return
        }
        gattServer?.addService(service)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            val advertiser = manager.adapter?.bluetoothLeAdvertiser
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: SecurityException) {
            // Permissions may have been revoked while the activity was stopped.
        }
        advertiseCallback = null
        gattServer?.close()
        gattServer = null
    }
}
