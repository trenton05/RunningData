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
        return distance(px, py, pz, o.px, o.py, o.pz);
    }

    public static double distance(double px, double py, double pz, double opx, double opy, double opz) {
        double dist;
        double rLong = 6378137.0;
        double rLat = 6356752.0;

        double lat1 = Math.toRadians(py);
        double lat2 = Math.toRadians(opy);
        double lon1 = Math.toRadians(px);
        double lon2 = Math.toRadians(opx);

        if (Math.abs(py - opy) >= 1.0) {
            double f = (rLong - rLat) / rLong;

            double b1 = Math.atan2((1.0 - f) * Math.sin(lat1), Math.cos(lat1));
            double b2 = Math.atan2((1.0 - f) * Math.cos(lat2), Math.cos(lat2));

            double p = 0.5 * (b1 + b2);
            double q = 0.5 * (b2 - b1);

            double slat = Math.sin((lat2 - lat1) * 0.5);
            double slon = Math.sin((lon2 - lon1) * 0.5);
            double a = slat * slat + Math.cos(lat1) * Math.cos(lat2) * slon * slon;
            double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

            double sc = Math.sin(c);
            double sp = Math.sin(p);
            double sq = Math.sin(q);
            double sc2 = Math.sin(c * 0.5);
            double x = (c - sc) * sp * sp * (1.0 - sq * sq) / (1.0 - sc2 * sc2);
            double y = (c + sc) * (1.0 - sp * sp) * sq * sq / (sc2 * sc2);

            dist = rLong * (c - 0.5 * f * (x + y));
        } else {
            double e = (rLong * rLong - rLat * rLat) / (rLat * rLat);

            double cp = Math.cos(lat1);
            double a = Math.sqrt(1.0 + e * cp * cp * cp * cp);
            double b = Math.sqrt(1.0 + e * cp * cp);

            double dlonp = a * (lon2 - lon1);
            double l1p = Math.atan2(Math.sin(lat1), Math.cos(lat1) * b);
            double l2p = Math.atan2(Math.sin(lat2), Math.cos(lat2) * b);
            double rp = rLong * Math.sqrt(1.0 + e) / (b * b);

            double dlatp = (lat2 - lat1) / b * (1 + 3 * e / (4 * b * b) * (lat2 - lat1) * Math.sin(2.0 * lat1 + 2.0 * (lat2 - lat1) / 3.0));

            double slon = Math.sin(dlonp * 0.5);
            double slat = Math.sin(dlatp * 0.5);

            double h = slat * slat + Math.cos(l1p) * Math.cos(l2p) * slon * slon;
            double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));

            dist = rp * c;
        }

        double dz = pz - opz;
        return Math.sqrt(dist * dist + dz * dz);
    }
}