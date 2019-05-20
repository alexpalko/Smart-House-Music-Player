package com.example.shmp

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.find_devices_layout.*
import org.jetbrains.anko.toast

class FindDevicesActivity : AppCompatActivity() {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val REQUEST_ENABLE_BLUETOOTH = 1
    private val deviceList : ArrayList<BluetoothDevice> = ArrayList()
    lateinit var adapter: ArrayAdapter<BluetoothDevice>
    private val receiver = object : BroadcastReceiver () {
        override fun onReceive(context: Context?, intent: Intent) {
            val device: BluetoothDevice =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

            if (!deviceList.contains(device)) {
                deviceList.add(device)
                adapter.notifyDataSetChanged()
            }
        }
    }

    companion object {
        const val EXTRA_ADDRESS: String = "Device_address"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.find_devices_layout)
        if (bluetoothAdapter == null){
            toast("This device doesn't support bluetooth.")
            return
        }

        if (!bluetoothAdapter?.isEnabled){
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
        }

        while (!bluetoothAdapter?.isEnabled){ }

        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter)

        pairDeviceList()
    }

    private fun pairDeviceList(){
        deviceList.clear()
        if (bluetoothAdapter?.bondedDevices != null){
            for (d in bluetoothAdapter?.bondedDevices){
                deviceList.add(d)
            }
        }
        bluetoothAdapter?.startDiscovery()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)

        activity_main_list.adapter = adapter
        activity_main_list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val device: BluetoothDevice = deviceList[position]
            val address: String = device.address
            val intent = Intent(this, RegistrationActivity::class.java)
            intent.putExtra(EXTRA_ADDRESS, address)
            startActivity(intent)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH){
            if (resultCode == Activity.RESULT_OK){
                if (bluetoothAdapter!!.isEnabled){
                    toast("Bluetooth has been enabled.")
                } else {
                    toast("Bluetooth has been disabled.")
                }
            } else if (resultCode == Activity.RESULT_CANCELED){
                toast("Bluetooth enabling has been canceled.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothAdapter!!.isDiscovering) {
            bluetoothAdapter?.cancelDiscovery()
        }
            unregisterReceiver(receiver)
    }
}
