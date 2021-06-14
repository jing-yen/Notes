package com.jingyen.notes

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.media.MediaPlayer.*
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DEFAULT_SOUND
import androidx.core.app.NotificationCompat.DEFAULT_VIBRATE
import com.jingyen.notes.databinding.ActivityAlarmBinding
import android.os.Binder

import android.os.IBinder




class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.extras?.apply {
            binding.alarmtitle.text = getString("title", "")
            binding.alarmtext.text = getString("text", "")
        }
    }
}


class AlarmBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val builder = NotificationCompat.Builder(context, "s")
        builder.setSmallIcon(R.drawable.lock)
        val intentt = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intentt, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pendingIntent)
        builder.setContentTitle("Alarm")
        builder.setContentText("HELLO")
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setDefaults(DEFAULT_VIBRATE)
        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        val notificationManager = context.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())

        context.startService(Intent(context, MediaPlayerService::class.java))
    }
}

class MediaPlayerService : Service(), OnCompletionListener, OnPreparedListener,
    OnErrorListener, OnSeekCompleteListener, OnInfoListener,
    OnBufferingUpdateListener, OnAudioFocusChangeListener {
    // Binder given to clients
    private val iBinder: IBinder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        return iBinder
    }

    override fun onBufferingUpdate(mp: MediaPlayer, percent: Int) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    override fun onCompletion(mp: MediaPlayer) {
        //Invoked when playback of a media source has completed.
    }

    //Handle errors
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked when there has been an error during an asynchronous operation.
        return false
    }

    override fun onInfo(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        //Invoked to communicate some info.
        return false
    }

    override fun onPrepared(mp: MediaPlayer) {
        //Invoked when the media source is ready for playback.
    }

    override fun onSeekComplete(mp: MediaPlayer) {
        //Invoked indicating the completion of a seek operation.
    }

    override fun onAudioFocusChange(focusChange: Int) {
        //Invoked when the audio focus of the system is updated.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        initMediaPlayer()
        return super.onStartCommand(intent, flags, startId)
    }

    inner class LocalBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    private fun initMediaPlayer() {
        val myUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val mediaPlayer = MediaPlayer().apply {
            setOnSeekCompleteListener(this@MediaPlayerService)
            setOnErrorListener(this@MediaPlayerService)
            setOnPreparedListener(this@MediaPlayerService)
            setOnBufferingUpdateListener(this@MediaPlayerService)
            setOnSeekCompleteListener(this@MediaPlayerService)
            setOnInfoListener(this@MediaPlayerService)
            reset()
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
            isLooping = true
            setDataSource(applicationContext, myUri)
            prepareAsync()
            start()

        }
    }
}