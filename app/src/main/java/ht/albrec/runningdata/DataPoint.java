package ht.albrec.runningdata;

import android.location.Location;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DataPoint {
    private static final DateFormat dateFormat;
    static {
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private long time;

    private double px;
    private double py;
    private double pz;

    private double vx;
    private double vy;
    private double vz;

    private double accuracy;
    private double heartRate;

    private long duration;
    private double distance;

    public static DataPoint create(Location next, double heartRate) {
        DataPoint p = new DataPoint();
        p.time = next.getTime();
        p.px = next.getLongitude();
        p.py = next.getLatitude();
        p.pz = next.getAltitude();
        p.accuracy = next.getAccuracy();
        p.heartRate = heartRate;
        p.distance = 0.0;
        p.duration = 0;
        return p;
    }

    public static void interpolate(DataPoint start, DataPoint mid, DataPoint end) {
        //  p0 + v0 t + 1/2 a0 t^2 + 1/6 j t^3 = data
        // Constant jerk between points
        long td = end.time - start.time;
        double ax = 2.0 * (end.px - start.px - start.vx * td) / (td * td);
        double ay = 2.0 * (end.py - start.py - start.vy * td) / (td * td);
        double az = 2.0 * (end.pz - start.pz - start.vz * td) / (td * td);

        td = mid.time - start.time;

        mid.px = start.px + start.vx * td + 0.5 * ax * td * td;
        mid.py = start.py + start.vy * td + 0.5 * ay * td * td;
        mid.pz = start.pz + start.vz * td + 0.5 * az * td * td;

        mid.vx = start.vx + ax * td;
        mid.vy = start.vy + ay * td;
        mid.vz = start.vz + az * td;

        start.duration = mid.time - start.time;
        start.distance = mid.distance(start);
    }

    public static void interpolate(DataPoint start, DataPoint end, double ratio) {
        start.px += (end.px - start.px) * ratio;
        start.py += (end.py - start.py) * ratio;
        start.pz += (end.pz - start.pz) * ratio;
        start.time += (long) ((end.time - start.time) * ratio);
    }

    public long getDuration() {
        return duration;
    }

    public double getDistance() {
        return distance;
    }

    public double getX() {
        return px;
    }

    public double getY() {
        return py;
    }

    public long getTime() {
        return time;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public int getHR() {
        return (int) heartRate;
    }

    public double getElevation() {
        return pz;
    }

    public String getTimeText() {
        return dateFormat.format(new Date(time));
    }

    public double distance(DataPoint o) {
        return Distance.distance(px, py, pz, o.px, o.py, o.pz);
    }
}