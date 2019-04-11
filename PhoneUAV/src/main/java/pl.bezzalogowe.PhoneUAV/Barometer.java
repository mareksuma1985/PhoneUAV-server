package pl.bezzalogowe.PhoneUAV;

import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;
import java.util.Locale;

public class Barometer {
    public float pressure_mean_sealevel;
    // needed only for calibration at startup
    public float pressureBarometricRecent;

/**
     https://www.daftlogic.com/sandbox-google-maps-find-altitude.htm
     https://www.youtube.com/watch?v=SyGxDEjXYIU
*/

    MainActivity main;
    SensorManager snsMgr;
    Sensor barometer;
    float altitudeBarometric = 0;
    float altitudeBarometricRecent = 0;

    /** Takes GPS altitude and measured barometric pressure, returns pressure at sea level */
    public static float getSealevelPressure(float alt, float p) {
        float sealevel = (float) (p / Math.pow(1 - (alt / 44330.0f), 5.255f));
        return sealevel;
    }

    public void startBarometer(MainActivity argActivity) {
        main = argActivity;
        snsMgr = (SensorManager) main.getSystemService(Service.SENSOR_SERVICE);
        barometer = snsMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);
        snsMgr.registerListener(main, barometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void calculateAltitude(SensorEvent event) {
        float[] values = event.values;
        pressureBarometricRecent = values[0];
        altitudeBarometric = SensorManager.getAltitude(pressure_mean_sealevel, values[0]);

        if (main.autopilot.fakeAltitude && main.seekbarTEST != null) {
            /** for testing, takes value from seek bar */
            //main.locObject.recentLocation.setAltitude(100 + main.seekbarTEST.getProgress());
            altitudeBarometricRecent = 100 + main.seekbarTEST.getProgress();
        } else {
            /** sends altitude feedback and updates recent value only if difference is greater than 0.5m */
            if (altitudeBarometric < altitudeBarometricRecent - 0.5 || altitudeBarometric > altitudeBarometricRecent + 0.5) {
                Thread feedbackBarometer = new Thread(new Wrap());
                feedbackBarometer.start();
                altitudeBarometricRecent = altitudeBarometric;
            }
        }

        if (main.locObject.recentLocation != null) {
            main.update.updateConversationHandler.post(new updateTextThread(main.altitude_text, String.format(Locale.getDefault(), "%.2f", altitudeBarometricRecent) + " m\n(" + main.locObject.recentLocation.getAltitude() + " m)"));
        } else {
            main.update.updateConversationHandler.post(new updateTextThread(main.altitude_text, String.format(Locale.getDefault(), "%.2f", altitudeBarometricRecent) + " m\n"));
        }
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            try {
                main.sendTelemetry(3, altitudeBarometric);
            } catch (Exception e) {
                Log.d("Barometer", "error: " + e);
                main.logObject.saveComment("error: " + e.toString());
            }
        }
    }
}
