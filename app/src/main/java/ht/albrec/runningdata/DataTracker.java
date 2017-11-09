package ht.albrec.runningdata;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;

public class DataTracker {
    private FileOutputStream out;
    private PrintWriter pw;
    private boolean closed = false;
    private int appended = 0;

    public DataTracker(FileOutputStream out) throws IOException {
        this.out = out;
        pw = new PrintWriter(new OutputStreamWriter(out, "UTF-8"));

        pw.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.print("<gpx creator=\"RunningData\" version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\">");
        pw.print("<trk><trkseg>");
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        pw.print("</trkseg></trk></gpx>");
        pw.close();
    }

    public void newSegment() {
        if (closed) {
            return;
        }
        pw.print("</trkseg><trkseg>");
    }

    public void addData(DataPoint d) {
        if (closed) {
            return;
        }

        pw.format("<trkpt lat=\"%.9f\" lon=\"%.9f\">"
            + "<extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>%d</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions>"
            + "<ele>%.2f</ele><time>" + d.getTimeText() + "</time></trkpt>", d.getY(), d.getX(), d.getHR(), d.getElevation());

        // Periodic flush
        appended++;
        if (appended > 10) {
            appended = 0;
            pw.flush();
        }
    }
}
