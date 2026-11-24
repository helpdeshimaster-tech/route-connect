package com.routeconnect.captain

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/** Foreground service: keeps GPS running (and the app alive) even when the screen is off. */
class LocationService : Service(), LocationListener {
    companion object {
        /** MainActivity sets these to receive fixes / errors and forward them to the web page. */
        @Volatile var onFix: ((Location) -> Unit)? = null
        @Volatile var onError: ((Int) -> Unit)? = null
        private const val CH = "rc_tracking"
    }
    private var lm: LocationManager? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Live trip tracking", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("RouteConnect: लाइव ट्रैकिंग चालू")
            .setContentText("पैसेंजर को आपकी गाड़ी की लोकेशन दिख रही है")
            .setOngoing(true).setContentIntent(open).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(1, n)

        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "routeconnect:gps").apply { acquire(12 * 60 * 60 * 1000L) }
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 0f, this)
        } catch (e: SecurityException) { onError?.invoke(1) }
        return START_STICKY
    }

    override fun onLocationChanged(l: Location) { onFix?.invoke(l) }
    override fun onProviderDisabled(p: String) { onError?.invoke(2) }
    @Deprecated("old API") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}

    override fun onDestroy() {
        try { lm?.removeUpdates(this) } catch (_: Exception) {}
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
