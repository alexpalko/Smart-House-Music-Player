package com.example.shmp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.google.firebase.FirebaseApp
import kotlinx.android.synthetic.main.main_page_layout.*
import org.jetbrains.anko.toast
import java.io.IOException

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_page_layout)

        playBtn.setOnClickListener { play() }
        pauseBtn.setOnClickListener { pause() }
        manageRoomsBtn.setOnClickListener { toRooms() }
        managePlaylistsBtn.setOnClickListener { toPlaylists() }
        storeBtn.setOnClickListener { toStore() }
    }

    private fun play() {
        playBtn.isEnabled = false
        playBtn.visibility = View.INVISIBLE
        pauseBtn.isEnabled = true
        pauseBtn.visibility = View.VISIBLE
        send("{ \"command\" : \"play\" }")
        infoSection.visibility = View.VISIBLE
    }

    private fun pause() {
        pauseBtn.isEnabled = false
        pauseBtn.visibility = View.INVISIBLE
        playBtn.isEnabled = true
        playBtn.visibility = View.VISIBLE
        send("{ \"command\" : \"pause\" }")
    }

    private fun toRooms() {
        intent = Intent(this, RoomsActivity::class.java)
        startActivity(intent)
    }

    private fun toPlaylists() {
        intent = Intent(this, PlaylistsActivity::class.java)
        startActivity(intent)
    }

    private fun toStore(){
        intent = Intent(this, StoreActivity::class.java)
        startActivity(intent)
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
        finish()
    }
}