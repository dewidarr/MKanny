package com.dewidar.makanny.map;
import android.os.Handler;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class GeoDistanceAlgorithm {
    public  int onTrack = 01;
    public  int onDirection = 01;
    public  int markPos = -1;
    private boolean isLine;
    private static boolean checkOnCorrectRoadFinished = false;
    private int nos_calls;
    private OSMdroid osMdroid;
    private Handler handler;

    public void setOsMdroid(OSMdroid osMdroid) {
        this.osMdroid = osMdroid;
    }

    public GeoDistanceAlgorithm(boolean isLine) {
        this.isLine = isLine;
    }



    public void geoDistanceCheckWithRadius(final List<LatLng> poly, final LatLng point, final int radius)
    {
        // if it first time to call this method else it will wait after every call to this method to finish
        if (nos_calls++ ==0) {
            Log.i("dewidarCheckRoad","first time to check");
          checkOnCorrectRoad(poly,point,radius);
        }else if (checkOnCorrectRoadFinished){
            Log.i("dewidarCheckRoad","another check");
            this.checkOnCorrectRoadFinished = false;
            checkOnCorrectRoad(poly,point,radius);
        }else {
            Log.i("dewidarCheckRoad","wait to check no of threads =" + Thread.activeCount());
        }
    }

    private void checkOnCorrectRoad(final List<LatLng> poly, final LatLng point, final int radius){
        new Thread(new Runnable() {
            @Override
            public void run() {
                int i;
                bdccGeo p = new bdccGeo(point.latitude, point.longitude);

                for (i = 0; i < (poly.size() - 1); i++) {
                    LatLng p1 = poly.get(i);
                    bdccGeo l1 = new bdccGeo(p1.latitude, p1.longitude);

                    LatLng p2 = poly.get(i + 1);
                    bdccGeo l2 = new bdccGeo(p2.latitude, p2.longitude);

                    double distance = p.function_distanceToLineSegMtrs(l1, l2);
                    if (distance < radius) {
                        onTrack = 1;
                        break;
                    } else if (distance > radius) {
                        onTrack = 0;
                    }
                }

                if (onTrack == 0 && osMdroid != null) {
                    Log.i("miss", "the road");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            osMdroid.reDrawCorrectRoad();
                        }
                    });
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                checkOnCorrectRoadFinished = true;

            }
        }).start();
    }

    public boolean isCurrentLocationFocused(List<LatLng> poly, LatLng point, int radius) {
        boolean isFocused = true;
        int i;
        bdccGeo p = new bdccGeo(point.latitude, point.longitude);

        for (i = 0; i < (poly.size() - 1); i++) {
            LatLng p1 = poly.get(i);
            bdccGeo l1 = new bdccGeo(p1.latitude, p1.longitude);

            LatLng p2 = poly.get(i + 1);
            bdccGeo l2 = new bdccGeo(p2.latitude, p2.longitude);

            double distance = p.function_distanceToLineSegMtrs(l1, l2);
            if (distance < radius) {
                isFocused = true;
            } else if (distance > radius) {
                isFocused = false;
            }
        }
       

        return isFocused;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }

// object

    public static class bdccGeo {
        public double lat;
        public double lng;

        public double x;
        public double y;
        public double z;


        public bdccGeo(double lat, double lon) {
            this.lat = lat;
            this.lng = lng;

            double theta = (lon * Math.PI / 180.0);
            double rlat = function_bdccGeoGeocentricLatitude(lat * Math.PI / 180.0);
            double c = Math.cos(rlat);
            this.x = c * Math.cos(theta);
            this.y = c * Math.sin(theta);
            this.z = Math.sin(rlat);
        }

        //returns in meters the minimum of the perpendicular distance of this point from the line segment geo1-geo2
        //and the distance from this point to the line segment ends in geo1 and geo2
        public double function_distanceToLineSegMtrs(bdccGeo geo1, bdccGeo geo2) {

            //point on unit sphere above origin and normal to plane of geo1,geo2
            //could be either side of the plane
            bdccGeo p2 = geo1.function_crossNormalize(geo2);

            // intersection of GC normal to geo1/geo2 passing through p with GC geo1/geo2
            bdccGeo ip = function_bdccGeoGetIntersection(geo1, geo2, this, p2);

            //need to check that ip or its antipode is between p1 and p2
            double d = geo1.function_distance(geo2);
            double d1p = geo1.function_distance(ip);
            double d2p = geo2.function_distance(ip);
            //window.status = d + ", " + d1p + ", " + d2p;
            if ((d >= d1p) && (d >= d2p))
                return function_bdccGeoRadiansToMeters(this.function_distance(ip));
            else {
                ip = ip.function_antipode();
                d1p = geo1.function_distance(ip);
                d2p = geo2.function_distance(ip);
            }
            if ((d >= d1p) && (d >= d2p))
                return function_bdccGeoRadiansToMeters(this.function_distance(ip));
            else
                return function_bdccGeoRadiansToMeters(Math.min(geo1.function_distance(this), geo2.function_distance(this)));
        }

        // More Maths
        public bdccGeo function_crossNormalize(bdccGeo b) {
            double x = (this.y * b.z) - (this.z * b.y);
            double y = (this.z * b.x) - (this.x * b.z);
            double z = (this.x * b.y) - (this.y * b.x);
            double L = Math.sqrt((x * x) + (y * y) + (z * z));
            bdccGeo r = new bdccGeo(0, 0);
            r.x = x / L;
            r.y = y / L;
            r.z = z / L;

            return r;
        }

        // Returns the two antipodal points of intersection of two great
        // circles defined by the arcs geo1 to geo2 and
        // geo3 to geo4. Returns a point as a Geo, use .antipode to get the other point
        public bdccGeo function_bdccGeoGetIntersection(bdccGeo geo1, bdccGeo geo2, bdccGeo geo3, bdccGeo geo4) {
            bdccGeo geoCross1 = geo1.function_crossNormalize(geo2);
            bdccGeo geoCross2 = geo3.function_crossNormalize(geo4);
            return geoCross1.function_crossNormalize(geoCross2);
        }

        public double function_distance(bdccGeo v2) {
            return Math.atan2(v2.function_crossLength(this), v2.function_dot(this));
        }

        //More Maths
        public double function_crossLength(bdccGeo b) {
            double x = (this.y * b.z) - (this.z * b.y);
            double y = (this.z * b.x) - (this.x * b.z);
            double z = (this.x * b.y) - (this.y * b.x);
            return Math.sqrt((x * x) + (y * y) + (z * z));
        }

        //Maths
        public double function_dot(bdccGeo b) {
            return ((this.x * b.x) + (this.y * b.y) + (this.z * b.z));
        }

        //from Radians to Meters
        public double function_bdccGeoRadiansToMeters(double rad) {
            return rad * 6378137.0; // WGS84 Equatorial Radius in Meters
        }

        // point on opposite side of the world to this point
        public bdccGeo function_antipode() {
            return this.function_scale(-1.0);
        }

        //More Maths
        public bdccGeo function_scale(double s) {
            bdccGeo r = new bdccGeo(0, 0);
            r.x = this.x * s;
            r.y = this.y * s;
            r.z = this.z * s;
            return r;
        }

        // Convert from geographic to geocentric latitude (radians).
        public double function_bdccGeoGeocentricLatitude(double geographicLatitude) {
            double flattening = 1.0 / 298.257223563;//WGS84
            double f = (1.0 - flattening) * (1.0 - flattening);
            return Math.atan((Math.tan(geographicLatitude) * f));
        }
    }
}
