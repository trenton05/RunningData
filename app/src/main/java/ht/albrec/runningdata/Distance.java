package ht.albrec.runningdata;

public class Distance {
    private static final double R_LONG = 6378137.0;
    private static final double R_LAT = 6378137.0;
    private static final double F = (R_LONG - R_LAT) / R_LONG;
    private static final double E2 = (R_LONG * R_LONG - R_LAT * R_LAT) / (R_LAT * R_LAT);

    public static double distance(double px, double py, double pz, double opx, double opy, double opz) {
        double dist;

        double lat1 = Math.toRadians(py);
        double lat2 = Math.toRadians(opy);
        double lon1 = Math.toRadians(px);
        double lon2 = Math.toRadians(opx);

        if (Math.abs(py - opy) >= 0.25 || Math.abs(px - opx) >= 0.25) {
            // Long distance
            dist = lambertDistance(lat1, lon1, lat2, lon2);
        } else {
            dist = bowringDistance(lat1, lon1, lat2, lon2);
        }

        double dz = pz - opz;
        return Math.sqrt(dist * dist + dz * dz);
    }

    private static double lambertDistance(double lat1, double lon1, double lat2, double lon2) {
        double b1 = Math.atan2((1.0 - F) * Math.sin(lat1), Math.cos(lat1));
        double b2 = Math.atan2((1.0 - F) * Math.cos(lat2), Math.cos(lat2));

        double p = 0.5 * (b1 + b2);
        double q = 0.5 * (b2 - b1);

        double slat = Math.sin((b2 - b1) * 0.5);
        double slon = Math.sin((lon2 - lon1) * 0.5);
        double a = slat * slat + Math.cos(b1) * Math.cos(b2) * slon * slon;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        double sc = Math.sin(c);
        double sp = Math.sin(p);
        double sq = Math.sin(q);
        double sc2 = Math.sin(c * 0.5);
        double x = (c - sc) * sp * sp * (1.0 - sq * sq) / (1.0 - sc2 * sc2);
        double y = (c + sc) * (1.0 - sp * sp) * sq * sq / (sc2 * sc2);

        return R_LONG * (c - 0.5 * F * (x + y));
    }

    private static double bowringDistance(double lat1, double lon1, double lat2, double lon2) {
        double cp = Math.cos(lat1);
        double a = Math.sqrt(1.0 + E2 * cp * cp * cp * cp);
        double b = Math.sqrt(1.0 + E2 * cp * cp);

        double dlonp = a * (lon2 - lon1);
        double tl1p = Math.tan(lat1) / b;
        double tl2p = Math.tan(lat2) / b;
        double cl1p = 1.0 / Math.sqrt(1.0 + tl1p * tl1p);
        double cl2p = 1.0 / Math.sqrt(1.0 + tl2p * tl2p);
        double rp = R_LONG * Math.sqrt(1.0 + E2) / (b * b);

        double dlatp = (lat2 - lat1) / b * (1 + 3 * E2 / (4 * b * b) * (lat2 - lat1) * Math.sin(2.0 * lat1 + 2.0 * (lat2 - lat1) / 3.0));

        double slon = Math.sin(dlonp * 0.5);
        double slat = Math.sin(dlatp * 0.5);

        double h = slat * slat + cl1p * cl2p * slon * slon;
        double c = 2.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));

        return rp * c;
    }

    private static double vincentyDistance(double lat1, double lon1, double lat2, double lon2) {
        double l = lon2 - lon1;
        double lambda = l;
        double lambdap;

        double tu1 = (1 - F) * Math.tan(lat1);
        double tu2 = (1 - F) * Math.tan(lat2);
        double cu1 = 1.0 / Math.sqrt(1.0 + tu1 * tu1);
        double cu2 = 1.0 / Math.sqrt(1.0 + tu2 * tu2);
        double su1 = tu1 * cu1;
        double su2 = tu2 * cu2;

        do {
            double sl = Math.sin(lambda);
            double cl = Math.cos(lambda);
            double ss = Math.sqrt(cu2 * cu2 * sl * sl + (cu1 * su2 - su1 * cu2 * cl) * (cu1 * su2 - su1 * cu2 * cl));
            if (ss == 0.0) {
                return 0.0;
            }
            double cs = su1 * su2 + cu1 * cu2 * cl;
            double s = Math.atan2(ss, cs);
            double sa = cu1 * cu2 * sl / ss;
            double c2a = 1.0 - sa * sa;
            double c2sm = cs - 2.0 * su1 * su2 / c2a;
            double c = F/16 * c2a * (4.0 + F * (4.0 - 3.0 * c2a));

            lambdap = lambda;
            lambda = l + (1 - c) * F * sa * (s + c * ss * (c2sm + c * cs * (-1.0 + 2.0 * c2sm * c2sm)));

            if (Math.abs(lambda - lambdap) <= 1e-12) {
                double u2 = c2a * E2;
                double a = 1.0 + u2 / 16384 * (4096 + u2 * (-768 + u2 * (320 - 175 * u2)));
                double b = u2 / 1024 * (256 + u2 * (-128 + u2 * (74 - 47 * u2)));
                double ds = b * ss * (c2sm + b/4 * (cs * (-1 + 2 * c2sm * c2sm)
                        - b / 6 * c2sm * (-3 + 4 * ss * ss) * (-3 + 4 * c2sm * c2sm)));
                return R_LAT * a * (s - ds);
            }
        } while (true) ;

    }
}
