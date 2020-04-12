package com.luca.innocenti.seismicrefraction

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import com.lyft.kronos.SyncListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var fileOutPutStream: FileOutputStream
    private var msg: String = "OFF"
    private var mAccelerometer: Sensor? = null
    private lateinit var mSensorManager: SensorManager


    private var tempo: Long = 0
    private var conteggio: Long = 0
    lateinit var kronosClock: KronosClock

    private val filepath = "MyFileStorage"
    internal var myExternalFile: File?=null
    private val isExternalStorageReadOnly: Boolean get() {
        val extStorageState = Environment.getExternalStorageState()
        return if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(extStorageState)) {
            true
        } else {
            false
        }
    }
    private val isExternalStorageAvailable: Boolean get() {
        val extStorageState = Environment.getExternalStorageState()
        return if (Environment.MEDIA_MOUNTED.equals(extStorageState)) {
            true
        } else{
            false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myExternalFile = File(getExternalFilesDir(filepath), "dati.csv")

        if (!isExternalStorageAvailable || isExternalStorageReadOnly) {
            Log.d("Errore", "non posso scrivere")
        }

        fileOutPutStream = FileOutputStream(myExternalFile)



        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)



        val testo = findViewById<TextView>(R.id.testo)
        val campioni = findViewById<TextView>(R.id.campioni)

        val toggleButton = findViewById<ToggleButton>(R.id.toggleButton)
        toggleButton?.setOnCheckedChangeListener { buttonView, isChecked ->
              if (isChecked) msg="ON" else msg="OFF"
            Toast.makeText(this@MainActivity, "Acquisition is "+msg, Toast.LENGTH_SHORT).show()
            conteggio = 0
        }

        val syncListener: SyncListener = object : SyncListener {
            override fun onStartSync(host: String) {
                Log.d("Kronos", "Clock sync started ($host)")
                testo.setText("Clock sync started ($host)")
            }

            override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
                Log.d("Kronos", "Clock sync succeed. Time delta: $ticksDelta. Response time: $responseTimeMs")
                testo.setText("Clock sync succeed. Time delta: $ticksDelta")
            }

            override fun onError(host: String, throwable: Throwable) {
                Log.e("Kronos", "Clock sync failed ($host)", throwable)
                testo.setText("Clock sync failed ($host)")

            }
        }

        //val server = mutableListOf("npt1.inrim.it", "npt2.inrim.it")

        kronosClock = AndroidClockFactory.createKronosClock(applicationContext, syncListener)
        kronosClock.syncInBackground()
    }

    override fun onResume() {
        super.onResume()
        mSensorManager!!.registerListener(this,mAccelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                var x = event.values[0]
                var y = event.values[1]
                var z = event.values[2]



                if (msg == "ON") {
                    conteggio = conteggio + 1
                    campioni.setText(conteggio.toString())
                    tempo = kronosClock.getCurrentTimeMs()
                    var salva: String = tempo.toString() + ";" + x.toString() + ";" + y.toString() + ";" + z.toString()+"\n"

                    fileOutPutStream.write(salva.toByteArray())

                    Log.d(
                            "Seismo",conteggio.toString()+";"+
                            tempo.toString() + ";" + x.toString() + ";" + y.toString() + ";" + z.toString()
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
