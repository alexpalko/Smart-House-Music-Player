package com.example.shmp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import kotlinx.android.synthetic.main.login_layout.*
import org.jetbrains.anko.toast
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_layout)
        loginBtn.setOnClickListener { login() }

    }

    private fun login() {
        var dataOk = true

        if (loginUsernameBox.text.isEmpty()) {
            toast("Please enter a user name");
            dataOk = false
        }
        if (loginPasswordBox.text.isEmpty()){
            toast("Please enter a pasword")
            dataOk = false
        }

        if (dataOk){
            send (java.lang.String.format("{ \"command\" : \"login\", \"username\" : \"%s\", \"password\" : \"%s\" }",
                loginUsernameBox.text.toString(), loginPasswordBox.text.toString()))

            var response = receive()

            if (response != null) {
                var splits = response.split(':')

                if (splits.count() == 2 && splits.component1() == "{ \"response\" ") {
                    if (splits.component2().startsWith(" \"ok\" }")) {
                        toast("Login successful")
                        var intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                    else {
                        toast("Invalid username or password")
                    }
                }
            }
        }
    }

    private fun send(output: String) {
        if (RegistrationActivity.bluetoothSocket != null){
            try{
                RegistrationActivity.bluetoothSocket!!.outputStream.write(output.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun receive(): String? {
        if (RegistrationActivity.bluetoothSocket != null){
            try{
                var buffer = ByteArray(1024)
                RegistrationActivity.bluetoothSocket!!.inputStream.read(buffer)
                return String(buffer)
            } catch (e: IOException) {
                e.printStackTrace()
                toast("An error has occurred, please try again.")
            }
        }
        return null
    }

    private fun disconnect() {
        if (RegistrationActivity.bluetoothSocket != null){
            try{
                RegistrationActivity.bluetoothSocket!!.close()
                RegistrationActivity.bluetoothSocket = null
                RegistrationActivity.isConnected = false
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
}