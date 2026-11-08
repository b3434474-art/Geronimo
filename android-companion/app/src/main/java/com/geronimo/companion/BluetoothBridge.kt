package com.geronimo.companion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import java.util.concurrent.Executors
import org.json.JSONObject

/** BLE control + call-state notification channel. It never transports call audio. */
class BluetoothBridge(
    context: Context,
    private val onStatus: (String) -> Unit
) {
    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("8f3a0001-7b2c-4d5e-9a10-1234567890ab")
        val COMMAND_UUID: UUID = UUID.fromString("8f3a0002-7b2c-4d5e-9a10-1234567890ab")
        val STATUS_UUID: UUID = UUID.fromString("8f3a0003-7b2c-4d5e-9a10-1234567890ab")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        @Volatile private var activeBridge: BluetoothBridge? = null

        fun publish(message: JSONObject) { activeBridge?.publishInternal(message) }
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val sendExecutor = Executors.newSingleThreadExecutor()
    private var gattServer: BluetoothGattServer? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var connectedDevice: BluetoothDevice? = null
    @Volatile private var notificationsEnabled = false
    @Volatile private var negotiatedMtu = 23
    private var sequence = 0

    @SuppressLint("MissingPermission")
    fun start() {
        activeBridge = this
        AppContext.application = appContext
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
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        status.value = "GERONIMO_READY".toByteArray(Charsets.UTF_8)
        status.addDescriptor(BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        ))
        statusCharacteristic = status
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
                    AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
                    callback
                )
            }

            override fun onConnectionStateChange(device: BluetoothDevice, statusCode: Int, newState: Int) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        gattServer?.cancelConnection(device)
                        onStatus("Bluetooth pairing required: pair this PC and phone in system settings first")
                        return
                    }
                    connectedDevice = device
                    notificationsEnabled = false
                    negotiatedMtu = 23
                    onStatus("Paired Windows PC connected: ${device.name ?: "Geronimo PC"}")
                } else {
                    if (connectedDevice?.address == device.address) {
                        connectedDevice = null
                        notificationsEnabled = false
                    }
                    onStatus("Advertising — waiting for Windows")
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                if (connectedDevice?.address == device.address) negotiatedMtu = mtu.coerceAtLeast(23)
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (device.bondState != BluetoothDevice.BOND_BONDED || connectedDevice?.address != device.address) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    return
                }
                if (characteristic.uuid == STATUS_UUID) {
                    val value = statusCharacteristic?.value ?: "GERONIMO_READY".toByteArray(Charsets.UTF_8)
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                } else {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (device.bondState != BluetoothDevice.BOND_BONDED || connectedDevice?.address != device.address) {
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    return
                }
                if (descriptor.uuid == CCCD_UUID) {
                    val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    notificationsEnabled = enabled
                    descriptor.value = value
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    if (enabled) publishInternal(JSONObject().put("event", "PHONE_READY").put("transport", "BLUETOOTH"))
                } else if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, null)
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
                if (device.bondState != BluetoothDevice.BOND_BONDED || connectedDevice?.address != device.address) {
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null)
                    return
                }
                if (characteristic.uuid == COMMAND_UUID) {
                    val message = value.toString(Charsets.UTF_8)
                    if (message == "PING") {
                        onStatus("Bluetooth test received from Windows")
                        publishInternal(JSONObject().put("event", "PONG"))
                    } else {
                        try {
                            CallBridge.handleCommand(JSONObject(message))
                            onStatus("Received a call-control command from Windows")
                        } catch (_: Exception) {
                            onStatus("Ignored an invalid command from Windows")
                        }
                    }
                    if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, byteArrayOf())
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

    private fun publishInternal(message: JSONObject) {
        val device = connectedDevice ?: return
        val characteristic = statusCharacteristic ?: return
        val payload = message.toString().toByteArray(Charsets.UTF_8)
        characteristic.value = payload
        if (!notificationsEnabled) return
        val maxValue = (negotiatedMtu - 3).coerceAtLeast(7)
        val chunkSize = (maxValue - 4).coerceAtLeast(1)
        val total = ((payload.size + chunkSize - 1) / chunkSize).coerceAtLeast(1)
        if (total > 255) return
        val messageId = synchronized(this) { sequence = (sequence + 1) and 0xFF; sequence }
        val frames = (0 until total).map { index ->
            val from = index * chunkSize
            val to = minOf(from + chunkSize, payload.size)
            byteArrayOf(0x47, messageId.toByte(), index.toByte(), total.toByte()) + payload.copyOfRange(from, to)
        }
        sendExecutor.execute {
            frames.forEach { frame ->
                try {
                    val server = gattServer ?: return@execute
                    if (Build.VERSION.SDK_INT >= 33) {
                        server.notifyCharacteristicChanged(device, characteristic, false, frame)
                    } else {
                        characteristic.value = frame
                        @Suppress("DEPRECATION")
                        server.notifyCharacteristicChanged(device, characteristic, false)
                    }
                    Thread.sleep(18)
                } catch (_: Exception) {
                    onStatus("Could not send a phone event to Windows")
                    return@execute
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        try {
            val advertiser = manager.adapter?.bluetoothLeAdvertiser
            advertiseCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (_: SecurityException) { }
        advertiseCallback = null
        connectedDevice = null
        notificationsEnabled = false
        gattServer?.close()
        gattServer = null
        if (activeBridge === this) activeBridge = null
    }
}