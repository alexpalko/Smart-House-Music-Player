package com.example.shmp

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.AdapterView
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.store_layout.*
import org.jetbrains.anko.toast
import java.io.IOException

class StoreActivity : AppCompatActivity() {

    private lateinit var songList: List<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.store_layout)
        listSongs()
        storeFinishBtn.setOnClickListener { toMainPage() }
    }

    private fun listSongs(){
        send("{ \"command\" : \"get_songs_store\" }")

        var response = receive()

        if (response == null) {
            toast("Store unavailable at the moment")
            onBackPressed()
        }

        var split = response!!.split(":")

        if (split.component1() == "{ \"response\" " && split.component2() == " \"ok\", \"titles\" ") {
            var titles = split.component3()
                .removePrefix(" ")
                .replace("[","")
                .replaceAfter("]","")
                .removeSuffix("]")
                .replace(", ", ",")
                .split(",")

            var songList = titles.toMutableList()
            adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songList)
            storeSongsList.adapter = adapter
            storeSongsList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val title = songList[position]
                songList.removeAt(position)
                send(java.lang.String.format("{ \"title\" : \"%s\" }", title))
                toast("$title added to your collection")
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun toMainPage() {
        send("{ \"command\" : \"exit\" }")
        intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
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