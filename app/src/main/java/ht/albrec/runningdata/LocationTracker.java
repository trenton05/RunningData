package ht.albrec.runningdata;

import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import java.util.LinkedList;

public class LocationTracker implements LocationListener, com.google.android.gms.location.LocationListener {
    public interface LocationUpdated {
        void onDataPoint(DataPoint point);
    }

    private HeartRateTracker heartRateTracker;

    private LinkedList<DataPoint> pendingLocations = new LinkedList<>();

    private boolean running = false;
    private LocationUpdated locationUpdated;
    private Runnable refresh;

    public LocationTracker(HeartRateTracker heartRateTracker, Runnable refresh, LocationUpdated locationUpdated) {
        this.heartRateTracker = heartRateTracker;
        this.refresh = refresh;
        this.locationUpdated = locationUpdated;
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

    private Location lastLocation;

    @Override
    public void onLocationChanged(Location location) {
        Log.d("LocationTracker", location.toString());
        lastLocation = location;

        if (running) {
            DataPoint last = DataPoint.create(location, heartRateTracker.getHeartRate());
            pendingLocations.add(last);

            DataPoint first = pendingLocations.getFirst();

            while (last.distance(first) > 0.5 * (first.getAccuracy() + last.getAccuracy()) && pendingLocations.size() > 2) {
                pendingLocations.removeFirst();
                DataPoint next = pendingLocations.getFirst();
                DataPoint.interpolate(first, next, last);
                locationUpdated.onDataPoint(first);
                first = next;
            }
        }
        refresh.run();
    }

    public double getPendingDistance() {
        if (pendingLocations.size() > 1) {
            return pendingLocations.getLast().distance(pendingLocations.getFirst());
        }
        return 0.0;
    }

    public long getPendingDuration() {
        if (pendingLocations.size() > 1) {
            return pendingLocations.getLast().getTime() - pendingLocations.getFirst().getTime();
        }
        return 0;
    }

    public void flush() {
        while (pendingLocations.size() > 0) {
            DataPoint first = pendingLocations.removeFirst();
            if (pendingLocations.isEmpty()) {
                locationUpdated.onDataPoint(first);
            } else {
                DataPoint next = pendingLocations.getFirst();
                DataPoint last = pendingLocations.getLast();
                DataPoint.interpolate(first, next, last);
                locationUpdated.onDataPoint(first);
            }
        }
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

    public Location getLocation() {
        return lastLocation;
    }
}
