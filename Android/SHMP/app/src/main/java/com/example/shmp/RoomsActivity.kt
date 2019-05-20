package com.example.shmp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.rooms_layout.*
import kotlinx.android.synthetic.main.store_layout.*
import org.jetbrains.anko.toast
import java.io.IOException

class RoomsActivity : AppCompatActivity() {
    private var settingPlaylists = false
    private lateinit var adapter1: ArrayAdapter<String>
    private lateinit var adapter2: ArrayAdapter<String>
    private lateinit var roomList: List<String>
    private lateinit var playlistList: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.rooms_layout)

        pairRoomList()
        pairPlaylistList()

        confirmSettingBtn.setOnClickListener { nextRoom() }
        setPlaylistBtn.setOnClickListener { startPlaylistForm() }
    }

    private fun pairRoomList(){
        send("{ \"command\" : \"get_room_names\" }")

        var response = receive()

        if (response == null) {
            toast("Error occurred")
            onBackPressed()
        }

        var split = response!!.split(":")

        if (split.component1() == "{ \"response\" " && split.component2() == " \"ok\", \"names\" ") {
            var names = split.component3()
                .removePrefix(" ")
                .replace("[","")
                .replaceAfter("]","")
                .removeSuffix("]")
                .replace(", ", ",")
                .split(",")

            var roomList = names.toMutableList()
            adapter1 = ArrayAdapter(this, android.R.layout.simple_list_item_1, roomList)
            roomsTitlesList.adapter = adapter1
        }
    }

    private fun pairPlaylistList() {
        send("{ \"command\" : \"get_room_playlist_names\" }")

        var response = receive()

        if (response == null) {
            toast("Error occurred")
            onBackPressed()
        }

        var split = response!!.split(":")

        if (split.component1() == "{ \"response\" " && split.component2() == " \"ok\", \"names\" ") {
            var names = split.component3()
                .removePrefix(" ")
                .replace("[","")
                .replaceAfter("]","")
                .removeSuffix("]")
                .replace(", ", ",")
                .split(",")

            var playlistList = names.toMutableList()
            adapter2 = ArrayAdapter(this, android.R.layout.simple_list_item_1, playlistList)
            playlistTitlesList.adapter = adapter2
        }
    }

    private fun nextRoom() {

    }

    private fun startPlaylistForm() {

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
        send("{ \"command\" : \"exit\" }")
        finish()
    }
}