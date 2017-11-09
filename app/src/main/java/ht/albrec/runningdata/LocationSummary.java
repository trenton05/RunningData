package ht.albrec.runningdata;

import java.util.LinkedList;

public class LocationSummary {
    private LinkedList<DataPoint> ps = new LinkedList<>();

    private double distance = 0.0;
    private long duration = 0;

    private double maxDistance;
    private long maxDuration;

    public LocationSummary(double maxDistance, long maxDuration) {
        this.maxDistance = maxDistance;
        this.maxDuration = maxDuration;
    }

    public void addPoint(DataPoint p) {
        duration += p.getDuration();
        distance += p.getDistance();

        if (maxDistance > 0 || maxDuration > 0) {
            this.ps.addLast(p);

            while (maxDistance > 0.0 && distance > maxDistance
                    || maxDuration > 0 && duration > maxDuration) {
                DataPoint removed = this.ps.removeFirst();
                duration -= removed.getDuration();
                distance -= removed.getDistance();
            }
        }
    }

    public LinkedList<DataPoint> getPoints() {
        return ps;
    }

    public long getDuration() {
        return duration;
    }

    public double getDistance() {
        return distance;
    }

    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }
}
