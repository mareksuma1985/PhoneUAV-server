package pl.bezzalogowe.PhoneUAV;

import android.content.Context;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class Location extends Thread {
    private static final String TAG = "location";
    LocationManager locationManager;
    android.location.Location recentLocation;
    android.location.Location waypointNext;
    LinkedList<Waypoint> route = new LinkedList<Waypoint>();
    String deviceIdentifier = null;
    MainActivity main;

    class Waypoint {
        // constructor with latitude and longitude
        public Waypoint(double argLat, double argLon) {
            super();
            this.lat = argLat;
            this.lon = argLon;
        }

        // constructor with latitude, longitude and elevation
        public Waypoint(double argLat, double argLon, double argElevation) {
            super();
            this.lat = argLat;
            this.lon = argLon;
            this.ele = argElevation;
        }

        public double lat;
        public double lon;
        public double ele;
    }

    class ReadXMLfromURL extends Thread {
        @Override
        public void run() {
            synchronized (this) {
                try {

                    URL url = new URL(main.serverpath + "route.gpx");
                    URLConnection conn = url.openConnection();

                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(conn.getInputStream());

                    // try reading route points
                    NodeList nodes = doc.getElementsByTagName("rtept");
                    // if the file doesn't contain route points, try reading track points
                    if (nodes.getLength() == 0)
                        nodes = doc.getElementsByTagName("trkpt");

                    for (int i = 0; i < nodes.getLength(); i++) {
                        Element element = (Element) nodes.item(i);

                        String parameter1value = element.getAttribute("lat");
                        String parameter2value = element.getAttribute("lon");

                        NodeList tag1 = element.getElementsByTagName("ele");
                        Element tag1content = (Element) tag1.item(0);
                        String tag1string = tag1content.getTextContent().toString();

                        route.add(new Waypoint(Double.parseDouble(parameter1value), Double.parseDouble(parameter2value), Double.parseDouble(tag1string)));
                    }
                } catch (NumberFormatException e) {
                    Log.d(TAG, "NumberFormatException");
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    Log.d(TAG, "MalformedURLException");
                    e.printStackTrace();
                } catch (DOMException e) {
                    Log.d(TAG, "DOMException");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.d(TAG, "IOException");
                    e.printStackTrace();
                } catch (ParserConfigurationException e) {
                    Log.d(TAG, "ParserConfigurationException");
                    e.printStackTrace();
                } catch (SAXException e) {
                    Log.d(TAG, "SAXException");
                    e.printStackTrace();
                }
                notify();
            }
        }
    }

    public void processLocation(MainActivity argActivity, android.location.Location location) {
        recentLocation = location;
        float distance = recentLocation.distanceTo(waypointNext);
        Log.d("location", "Lat: " + recentLocation.getLatitude() + " Lon: " + recentLocation.getLongitude() + " alt: " + recentLocation.getAltitude() + " hdg: " + argActivity.magObject.heading + " acc: " + recentLocation.getAccuracy());

/*
        double is 8 bytes
        two doubles give 16 bytes
        16 doesn't divide by 3
        18 / 3 = 6
        6 x 4 = 24 (characters)

        double[] data = {recentLocation.getLatitude(), recentLocation.getLongitude(), recentLocation.getAltitude()};
        String base64string = android.util.Base64.encodeToString(double2Byte(data), Base64.URL_SAFE | Base64.NO_WRAP);
        Log.d("location", "base64: " + base64string);
*/
        main.update.updateConversationHandler.post(new updateTextThread(main.text_server, String.format("%.02f", distance) + "m No:" + main.logObject.trackpointNumber));

        Time timeLocal = new Time();
        timeLocal.setToNow();

        try {
            String[] parameters = {main.serverpath,
                    String.valueOf(recentLocation.getLatitude()),
                    String.valueOf(recentLocation.getLongitude()),
                    String.valueOf(recentLocation.getAltitude()),
                    String.valueOf(main.magObject.heading),
                    String.format("%.00f", recentLocation.getAccuracy()),
                    deviceIdentifier,
                    /* base64string, */
                    timeLocal.format("%Y-%m-%dT%H:%M:%S")
            };

            new LogOnline().execute(parameters);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("location", e.toString());
        }

        try {
            main.logObject.saveTrackpoint(
                    recentLocation.getLatitude(),
                    recentLocation.getLongitude(),
                    recentLocation.getAltitude(),
                    main.magObject.heading,
                    recentLocation.getAccuracy(), recentLocation.getTime());
        } catch (IOException e) {
            Log.d("location", e.toString());
            e.printStackTrace();
        }
	    
	/** sends location over UDP (must be done in a separate thread) */
        Thread feedbackLocation = new Thread(new Wrap());
        feedbackLocation.start();

        if (distance < 64) {
            Waypoint read;
            try {
                read = route.remove();
                waypointNext.setLatitude(read.lat);
                waypointNext.setLongitude(read.lon);
                Toast.makeText(main, route.size() + " waypoints left in queue", Toast.LENGTH_SHORT).show();
                main.logObject.saveComment(route.size() + " waypoints left in queue");

            } catch (Exception e) {
                Toast.makeText(main, "no more waypoints in queue", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void startLocation(final MainActivity argActivity) {
        main = argActivity;
        locationManager = (LocationManager) main.getSystemService(Context.LOCATION_SERVICE);
        TelephonyManager telemamanger = (TelephonyManager) main.getSystemService(main.TELEPHONY_SERVICE);
/*
        try {
            deviceIdentifier = telemamanger.getLine1Number();
        } catch (Exception e) {
            e.printStackTrace();
        }
*/
        try {
             deviceIdentifier = telemamanger.getDeviceId();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (deviceIdentifier == null)
            deviceIdentifier = "N/A";

        Log.d("location", "started");

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(android.location.Location location) {
                processLocation(main, location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
            }

            public void onProviderDisabled(String provider) {
            }
        };

        Thread readXML = new ReadXMLfromURL();
        readXML.start();
        synchronized (readXML) {
            try {
                readXML.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            startFlightpath();
        }

// dummy startup coordinates
/*
        recentLocation= new
		android.location.Location(LocationManager.GPS_PROVIDER);
		recentLocation.setLatitude(52.2243);
		recentLocation.setLongitude(20.9178);
		recentLocation.setAltitude(100); recentLocation.setAccuracy(32);
		recentLocation.setBearing(0);
		recentLocation.setProvider(LocationManager.GPS_PROVIDER);
		recentLocation.setSpeed(10); recentLocation.setTime(0);
*/
        Waypoint first;
        try {
            //when the online XML file is read properly
            first = route.remove();
        } catch (Exception e1) {
            //when the file cannot be read for some reason
            first = new Waypoint(0, 0, 0);
        }
        waypointNext = new android.location.Location(LocationManager.GPS_PROVIDER);
        waypointNext.setLatitude(first.lat);
        waypointNext.setLongitude(first.lon);
        waypointNext.setAltitude(first.ele);
        waypointNext.setAccuracy(32);
/*
        waypoint.setBearing(0);
		waypoint.setProvider(LocationManager.GPS_PROVIDER);
		waypoint.setSpeed(10); waypoint.setTime(0);
*/

        Log.d("waypoint",
                "first:" + waypointNext.getLatitude() + "\u00B0 "
                        + waypointNext.getLongitude() + "\u00B0 "
                        + waypointNext.getAltitude() + "m "
                        + waypointNext.getAccuracy() + "m");

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("location", e.toString());
        }

    }

    public void startFlightpath() {
        try {
            Log.d("waypoint", "waypoints read: " + route.size());
            for (Waypoint item : route) {
                Log.d("waypoint", item.lat + "\u00B0 " + item.lon + "\u00B0 " + item.ele + "m");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            Log.d("waypoint", e.toString());
        }
    }

/*
    public void skipWaypoint()
	{
		try {
			Waypoint read = route.remove();
			Log.d("loc", "rtept. " + read.lat + "/" + read.lon + " polled/removed");
			argActivity.update.updateConversationHandler.post(new updateTextThread(argActivity.text_server, "wpt. " + read.lat + "/" + read.lon + " polled " + argActivity.locObject.route.size()));
			argActivity.locObject.waypointNext.setLatitude(read.lat);
			waypointNext.setLongitude(read.lon);
			read = null;
		} catch (Exception e) {
			argActivity.update.updateConversationHandler.post(new updateTextThread(argActivity.text_server, "no more waypoints"));
		}
	}
*/

    /* http://www.java2s.com/Code/Java/File-Input-Output/writesdoublestobytearray.htm
       Copyright 2007 Creare Inc. */
	
    /** converts one double to an array of 8 bytes */
    public static final byte[] double2Bytes(double inData) {
        long bits = Double.doubleToLongBits(inData);
        byte[] buffer = {(byte) (bits & 0xff),
                (byte) ((bits >> 8) & 0xff),
                (byte) ((bits >> 16) & 0xff),
                (byte) ((bits >> 24) & 0xff),
                (byte) ((bits >> 32) & 0xff),
                (byte) ((bits >> 40) & 0xff),
                (byte) ((bits >> 48) & 0xff),
                (byte) ((bits >> 56) & 0xff)};
        return buffer;
    }

    /** converts an array of doubles to an array of bytes */
    public static final byte[] doubleArray2Bytes(double[] inArray) {
        int j = 0;
        int length = inArray.length;
        byte[] outData = new byte[length * 8];
        for (int i = 0; i < length; i++) {
            long bits = Double.doubleToLongBits(inArray[i]);
            outData[j++] = (byte) (bits & 0xff);
            outData[j++] = (byte) ((bits >> 8) & 0xff);
            outData[j++] = (byte) ((bits >> 16) & 0xff);
            outData[j++] = (byte) ((bits >> 24) & 0xff);
            outData[j++] = (byte) ((bits >> 32) & 0xff);
            outData[j++] = (byte) ((bits >> 40) & 0xff);
            outData[j++] = (byte) ((bits >> 48) & 0xff);
            outData[j++] = (byte) ((bits >> 56) & 0xff);
        }
        return outData;
    }

    class Wrap implements Runnable {
        @Override
        public void run() {
            double[] coordinates = {recentLocation.getLatitude(), recentLocation.getLongitude()};
            try {
                main.sendTelemetry(3, coordinates);
            } catch (Exception e) {
                Log.d("Barometer", "error: " + e);
                main.logObject.saveComment("error: " + e.toString());
            }
        }
    }
}
