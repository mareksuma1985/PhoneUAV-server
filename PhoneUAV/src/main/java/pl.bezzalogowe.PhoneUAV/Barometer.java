package pl.bezzalogowe.PhoneUAV;

import android.app.Service;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.util.Log;

public class Barometer {
    /**
     * https://www.daftlogic.com/sandbox-google-maps-find-altitude.htm
     * https://www.youtube.com/watch?v=SyGxDEjXYIU
     */

    MainActivity main;
    SensorManager snsMgr;
    Sensor barometer;
    float pressure_mean_sealevel;
    //pressure_mean_sealevel = SensorManager.PRESSURE_STANDARD_ATMOSPHERE;
    float altitudeBarometric = 0;

    public void startBarometer(MainActivity argActivity) {
        main = argActivity;
        snsMgr = (SensorManager) main.getSystemService(Service.SENSOR_SERVICE);
        barometer = snsMgr.getDefaultSensor(Sensor.TYPE_PRESSURE);
        snsMgr.registerListener(main, barometer, SensorManager.SENSOR_DELAY_UI);
    }

    public void calculateAltitude(SensorEvent event) {
        float[] values = event.values;
        altitudeBarometric = SensorManager.getAltitude(pressure_mean_sealevel, values[0]);

        double altitudeGPS = 0;
        if (main.locObject.recentLocation != null) {
            altitudeGPS = main.locObject.recentLocation.getAltitude();
        }
        main.update.updateConversationHandler.post(new updateTextThread(main.altitude_text, altitudeBarometric + "\n(" + altitudeGPS + ")"));

        Thread feedback = new Thread(new Wrap());
        feedback.start();
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
