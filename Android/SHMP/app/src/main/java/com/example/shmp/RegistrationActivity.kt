package com.example.shmp

import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.android.synthetic.main.registration_layout.*
import org.jetbrains.anko.toast
import java.io.IOException
import java.util.*

class RegistrationActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.registration_layout)
        address = intent.getStringExtra(FindDevicesActivity.EXTRA_ADDRESS)
        registrationActivity = this

        ConnectToDevice(this).execute()

        registerBtn.setOnClickListener { register() }
    }



    private fun register() {
        var dataOk = true
        if (usernameBox.text.isEmpty()) {
            toast("Please choose a user name");
            dataOk = false
        }
        if (emailBox.text.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(emailBox.text.toString()).matches()){
            toast("Please enter a valid email address")
            dataOk = false
        }
        if (passwordBox.text.isEmpty()){
            toast("Please enter a password")
            dataOk = false
        }
        if (rePasswordBox.text.isEmpty()){
            toast("Please enter the password in both boxes")
            dataOk = false
        }

        if (passwordBox.text.toString() != (rePasswordBox.text.toString())){
            toast("Passwords do not match")
            dataOk = false
        }

        if (dataOk) {
            var output = java.lang.String.format(
                "{\"username\" : \"%s\", \"password\" : \"%s\", \"email\" : \"%s\"}",
                usernameBox.text.toString(), passwordBox.text.toString(), emailBox.text.toString()
            )

            send(output)

            var response = receive()

            if (response != null) {
                var splits = response.split(':')

                if (splits.count() == 2 && splits.component1() == "{ \"response\" ") {
                    if (splits.component2().startsWith(" \"ok\" }")) {
                        toast("Registration successful")
                        var intent = Intent(this, StoreActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    else if (splits.component2().startsWith(" \"user_taken\" }")){
                        toast("The username is already taken")
                    }
                    else {
                        toast("Something went wrong, please try again")
                    }
                }
            }
        }
    }

    private fun send(output: String) {
        if (bluetoothSocket != null){
            try{
                bluetoothSocket!!.outputStream.write(output.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun receive(): String? {
        if (bluetoothSocket != null){
            try{
                var buffer = ByteArray(1024)
                bluetoothSocket!!.inputStream.read(buffer)
                return String(buffer)
            } catch (e: IOException) {
                e.printStackTrace()
                toast("An error has occurred, please try again.")
            }
        }
        return null
    }

    private fun disconnect() {
        if (bluetoothSocket != null){
            try{
                bluetoothSocket!!.close()
                bluetoothSocket = null
                isConnected = false
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val intent = Intent(this, FindDevicesActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private class ConnectToDevice(c: Context) : AsyncTask<Void, Void, String>(){
        private var connectSuccess: Boolean = true
        private val context: Context

        init {
            this.context = c
        }

        override fun onPreExecute() {
            super.onPreExecute()
            progress = ProgressDialog.show(context, "Connecting...", "please wait")
        }

        override fun doInBackground(vararg params: Void?): String? {
            try {
                if (bluetoothSocket == null || !isConnected) {
                    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(address)
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(myUUID)
                    bluetoothSocket!!.connect()
                }
            } catch (e: IOException) {
                connectSuccess = false
                e.printStackTrace()
            }

            if (connectSuccess) {
                checkRegistrationNeeded()
            }

            return null
        }

        private fun checkRegistrationNeeded() {
            val output = "{ \"command\" : \"register\" }"

            registrationActivity.send(output)

            var input = registrationActivity.receive()

            if (input == null) {
                registrationActivity.toast("Something went wrong, please try again")
                return
            }

            var splits = input?.split(':')

            if (splits.count() != 2 || splits.component1() != ("{ \"response\" ")) {
                registrationActivity.toast("Something went wrong, please try again")
                return
            }

            if (splits.component2().startsWith(" \"ok\" }")){

            }
            else if (splits.component2().startsWith(" \"nok\" ")){
                var intent = Intent(registrationActivity, LoginActivity::class.java)
                registrationActivity.startActivity(intent)
                registrationActivity.finish()
            }
            else {
                registrationActivity.toast("Something went wrong, please try again")
                registrationActivity.finish()
                registrationActivity.startActivity(registrationActivity.intent)
            }
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            if (!connectSuccess){
                Log.i("data", "couldn't connect")
                val intent = Intent(context, FindDevicesActivity::class.java)
                context.startActivity(intent)
                registrationActivity.finish()
            } else {
                isConnected = true
            }
            progress.dismiss()
        }
    }

    companion object {
        var myUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        var bluetoothSocket: BluetoothSocket? = null
        lateinit var progress: ProgressDialog
        lateinit var bluetoothAdapter: BluetoothAdapter
        var isConnected: Boolean = false
        lateinit var address: String
        lateinit var registrationActivity: RegistrationActivity
    }
}