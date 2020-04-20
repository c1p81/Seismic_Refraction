package com.luca. innocenti.seismicrefraction

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import com.lyft.kronos.SyncListener
import kotlinx.android.synthetic.main.activity_main.*
import org.nield.kotlinstatistics.median
import org.nield.kotlinstatistics.standardDeviation
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt


class MainActivity : AppCompatActivity(), SensorEventListener {

    private var mail: Boolean = false
    private lateinit var lista: ArrayList<Float>

    private lateinit var dati_csv: ArrayList<String>

    private lateinit var mConstraintLayout: ConstraintLayout
    private lateinit var socket: DatagramSocket
    private var formatter: DateTimeFormatter? = null
    private var formatted: String? = null
    private lateinit var fileOutPutStream: FileOutputStream
    private var msg: String = "OFF"
    private var mAccelerometer: Sensor? = null
    private lateinit var mSensorManager: SensorManager
    private var delta: Long = 0
    private var rete:Boolean = false

    private var backButtonCount:Int = 0



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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // inizializza la lista dove sono contenute le misure
        // serve al calcolo della media e della std
        lista = ArrayList<Float>()
        dati_csv = ArrayList<String>()



        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)



        val testo = findViewById<TextView>(R.id.testo)
        val check = findViewById<CheckBox>(R.id.checkBox)
        check?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked)
                rete = true else rete=false
        }
        toggleButton.isEnabled = false

        val check2 = findViewById<CheckBox>(R.id.checkBox2)
        check2?.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked)
                mail = true else rete=false
        }
        
        mConstraintLayout = findViewById<ConstraintLayout>(R.id.layout)


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
                    mConstraintLayout.setBackgroundColor(Color.GREEN)



                conteggio = 0

                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    val current = LocalDateTime.now()
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")
                    formatted = current.format(formatter)

                }
                else
                {
                    val forma = System.currentTimeMillis()
                    formatted = forma.toString()

                }


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
                mConstraintLayout.setBackgroundColor(Color.WHITE)
                toggleButton.isEnabled = false


                if (mail) {
                    // invio dati per email
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/html"
                    //intent.putExtra(Intent.EXTRA_EMAIL, "lucainnoc@gmail.com")
                    intent.putExtra(
                        Intent.EXTRA_SUBJECT,
                        "Data station nr" + nr_stazione.toString()
                    )
                    intent.putExtra(Intent.EXTRA_TEXT, dati_csv.toString())
                    startActivity(Intent.createChooser(intent, "Send Email"))
                }



            }

        }


        val syncListener: SyncListener = object : SyncListener {
            override fun onStartSync(host: String) {
                Log.d("Kronos", "Clock sync started ($host)")
                testo.setText("Clock sync started ($host)")
            }

            override fun onSuccess(ticksDelta: Long, responseTimeMs: Long) {
                Log.d("Kronos", "Clock sync succeed. Time delta: $ticksDelta")

                testo.setText("Clock sync succeed.")

                delta = ticksDelta
                if (testo.text == "Clock sync succeed.") toggleButton.isEnabled = true

            }

            override fun onError(host: String, throwable: Throwable) {
                Log.e("Kronos", "Clock sync failed ($host)", throwable)
                testo.setText("Clock sync failed ($host)")


            }
        }

        val server = mutableListOf("0.us.pool.ntp.org", "1.us.pool.ntp.org","2.us.pool.ntp.org","time.inrim.it")

        kronosClock = AndroidClockFactory.createKronosClock(applicationContext, syncListener,server,15000,5000,5000)
        kronosClock.syncInBackground()
    }

    override fun onResume() {
        super.onResume()
        kronosClock.syncInBackground()
        mSensorManager!!.registerListener(this,mAccelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onDestroy() {
        super.onDestroy()
        kronosClock.shutdown()
        mSensorManager.unregisterListener(this, mAccelerometer)
    }

    override fun onBackPressed() {
        if (backButtonCount >= 1) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                "Press the back button once again to close the application.",
                Toast.LENGTH_SHORT
            ).show()
            backButtonCount++
        }
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
                    var salva: String = nr_stazione.toString()+";"+tempo.toString()+";"+ z.toString()+"\n"

                    dati_csv.add(salva)

                    fileOutPutStream.write(salva.toByteArray())
                    lista.add(z)

                    var media = lista.median()
                    var std_dev = lista.standardDeviation()

                    // il controllo della media avviene solo quando ci sono abbastanza dati
                    if (lista.size > 200) {
                        if (abs(z) > (abs(media) + (5.0 * std_dev))) {
                            mConstraintLayout.setBackgroundColor(Color.RED)
                        }
                    }

                    //Log.d("Media",media.toString()+";"+std_dev.toString()+";"+z.toString())

                    if (rete) {
                        val byte = salva.toByteArray();
                        val packet = DatagramPacket(
                            byte,
                            byte.size,
                            InetAddress.getByName(iptxt.text.toString()),
                            5550
                        );
                        socket.send(packet);
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}
