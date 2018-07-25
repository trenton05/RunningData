package ht.albrec.runningdata;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import java.util.LinkedList;

public class LocationTracker implements LocationListener, com.google.android.gms.location.LocationListener {
    private static final float MIN_ACCURACY = 20.0f;
    public interface LocationUpdated {
        void onDataPoint(DataPoint point);
    }

    private HeartRateTracker heartRateTracker;

    private LinkedList<DataPoint> pendingLocations = new LinkedList<>();

    private boolean running = false;
    private LocationUpdated locationUpdated;
    private Runnable refresh;
    private int smoothing;

    public LocationTracker(HeartRateTracker heartRateTracker, Runnable refresh, LocationUpdated locationUpdated, int smoothing) {
        this.heartRateTracker = heartRateTracker;
        this.refresh = refresh;
        this.locationUpdated = locationUpdated;
        this.smoothing = smoothing;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        if (running != this.running) {
            this.flush();
            this.running = running;
        }
    }

    private DataPoint lastLocation;
    private double lastAccuracy;
    private double pendingDistance = 0.0;
    private long pendingTime = 0;

    @Override
    public void onLocationChanged(Location tmp) {
        Log.d("LocationTracker", tmp.toString());

        DataPoint location = DataPoint.create(tmp, heartRateTracker.getHeartRate());

        if (lastLocation == null) {
            lastAccuracy = location.getAccuracy();
        } else if (lastAccuracy > 1) {
            double dist = lastLocation.distance(location);
            if (dist < lastAccuracy) {
                lastAccuracy = Math.min(lastAccuracy - dist, location.getAccuracy());
            } else {
                DataPoint.interpolate(lastLocation, location, lastAccuracy / dist);
                addLocation(lastLocation);
                lastAccuracy = 0.0;
            }
        }
        lastLocation = location;
        if (lastAccuracy <= 1 && lastLocation.getAccuracy() <= MIN_ACCURACY) {
            addLocation(lastLocation);
        }
        refresh.run();
    }

    private void addLocation(DataPoint last) {
        if (running) {
            pendingLocations.add(last);

            DataPoint first = pendingLocations.getFirst();
            pendingDistance = last.distance(first);
            pendingTime = last.getTime() - first.getTime();
            while (pendingDistance > 0.5 * (first.getAccuracy() + last.getAccuracy()) && pendingLocations.size() > 2) {
                pendingLocations.removeFirst();
                DataPoint next = pendingLocations.getFirst();
                DataPoint.interpolate(first, next, last, smoothing);
                pendingDistance = last.distance(next);
                pendingTime = last.getTime() - next.getTime();
                locationUpdated.onDataPoint(first);
                first = next;
            }
        }
    }

    public double getPendingDistance() {
        return pendingDistance;
    }

    public long getPendingDuration() {
        return pendingTime;
    }

    public void flush() {
        while (pendingLocations.size() > 0) {
            DataPoint first = pendingLocations.removeFirst();
            if (pendingLocations.isEmpty()) {
                locationUpdated.onDataPoint(first);
            } else {
                DataPoint next = pendingLocations.getFirst();
                DataPoint last = pendingLocations.getLast();
                DataPoint.interpolate(first, next, last, smoothing);
                locationUpdated.onDataPoint(first);
            }
        }
        pendingDistance = 0.0;
        pendingTime = 0;
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public DataPoint getLocation() {
        return lastLocation;
    }
}
