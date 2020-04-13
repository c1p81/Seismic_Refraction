package com.luca.innocenti.seismicrefraction

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import com.lyft.kronos.SyncListener
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var socket: DatagramSocket
    private var formatter: DateTimeFormatter? = null
    private var formatted: String? = null
    private lateinit var fileOutPutStream: FileOutputStream
    private var msg: String = "OFF"
    private var mAccelerometer: Sensor? = null
    private lateinit var mSensorManager: SensorManager


    private var tempo: Long = 0
    private var conteggio: Long = 0
    private var nr_stazione: Int = 1
    lateinit var kronosClock: KronosClock


    private val filepath = "Seismic_Refraction"
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

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)



        val testo = findViewById<TextView>(R.id.testo)
        val iptxt = findViewById<EditText>(R.id.iptxt)
        val stazione = findViewById<TextView>(R.id.stazione)
        stazione.setText("Station nr: 1")

        val campioni = findViewById<TextView>(R.id.campioni)
        val barra = findViewById<SeekBar>(R.id.seekBar)

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Write code to perform some action when progress is changed.
                //Toast.makeText(this@MainActivity, "Progress is " + seekBar.progress + "%", Toast.LENGTH_SHORT).show()

            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // Write code to perform some action when touch is started.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // Write code to perform some action when touch is stopped.
                nr_stazione = seekBar.progress + 1
                stazione.setText("Station Nr: "+ nr_stazione.toString())
                Log.d("file", myExternalFile.toString())
            }
        })


        val toggleButton = findViewById<ToggleButton>(R.id.toggleButton)
        toggleButton?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                    msg = "ON"
                    socket = DatagramSocket()
                    socket.broadcast = false


                    conteggio = 0
                    val current = LocalDateTime.now()
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")
                    formatted = current.format(formatter)

                    myExternalFile = File(
                        getExternalFilesDir(filepath),
                        formatted + "_" + nr_stazione.toString() + ".csv"
                    )
                    fileOutPutStream = FileOutputStream(myExternalFile)
                    Toast.makeText(this@MainActivity, "Acquisition is " + msg, Toast.LENGTH_SHORT).show()
        }
            else{
                msg="OFF"
                socket.close()
                fileOutPutStream.close()
                Toast.makeText(this@MainActivity, "File saved : " + formatted+".csv" , Toast.LENGTH_LONG).show()
            }

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
            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                var x = event.values[0]
                var y = event.values[1]
                var z = event.values[2]
                var m = sqrt((x*x)+(y*y)+(z*z))




                if (msg == "ON") {
                    conteggio = conteggio + 1
                    campioni.setText("Samples Nr : " + conteggio.toString())
                    tempo = kronosClock.getCurrentTimeMs()
                    //var salva: String = nr_stazione.toString()+";"+tempo.toString() + ";" + x.toString() + ";" + y.toString() + ";" + z.toString()+";"+m.toString()+"\n"
                    var salva: String = nr_stazione.toString()+";"+tempo.toString() + z.toString()+";"+m.toString()+"\n"

                    fileOutPutStream.write(salva.toByteArray())

                    val byte = salva.toByteArray();
                    val packet = DatagramPacket(byte, byte.size, InetAddress.getByName(iptxt.text.toString()),5550);
                    socket.send(packet);

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
