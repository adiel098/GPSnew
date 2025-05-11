package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Point3D;

/**
 * Utility class for converting between geographic coordinates (latitude/longitude) and UTM coordinates
 */
public class CoordinateConverter {
    
    // Constants for UTM calculations
    private static final double K0 = 0.9996;                  // Scale factor for UTM
    private static final double E = 0.00669438;               // Square of eccentricity
    private static final double E2 = E * E;                   // E squared
    private static final double E3 = E2 * E;                  // E cubed
    private static final double E_P2 = E / (1 - E);           // E prime squared
    private static final double SQRT_E = Math.sqrt(1 - E);    // Sqrt(1 - e^2)
    private static final double _E = (1 - SQRT_E) / (1 + SQRT_E); // e
    private static final double _E2 = _E * _E;                // e squared
    private static final double _E3 = _E2 * _E;               // e cubed
    private static final double _E4 = _E3 * _E;               // e^4
    private static final double _E5 = _E4 * _E;               // e^5
    private static final double M1 = 1 - E / 4 - 3 * E2 / 64 - 5 * E3 / 256; // M1
    private static final double M2 = 3 * E / 8 + 3 * E2 / 32 + 45 * E3 / 1024; // M2
    private static final double M3 = 15 * E2 / 256 + 45 * E3 / 1024; // M3
    private static final double M4 = 35 * E3 / 3072; // M4
    private static final double P2 = 3 / 2.0 * _E - 27 / 32.0 * _E3 + 269 / 512.0 * _E5; // P2
    private static final double P3 = 21 / 16.0 * _E2 - 55 / 32.0 * _E4; // P3
    private static final double P4 = 151 / 96.0 * _E3 - 417 / 128.0 * _E5; // P4
    private static final double P5 = 1097 / 512.0 * _E4; // P5
    private static final double R = 6378137;              // Earth's radius in meters
    private static final double ZONE_WIDTH = 6;           // Width of UTM zone in degrees

    // Current UTM zone (for converting back to lat/lon)
    private int currentZone = 0;
    private boolean isNorthern = true;

    /**
     * Convert latitude/longitude to UTM coordinates
     * 
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @param alt Altitude in meters
     * @return Point3D with UTM easting (x), northing (y), and altitude (z) in meters
     */
    public Point3D latLonToUtm(double lat, double lon, double alt) {
        // Determine the UTM zone and central meridian
        int zone = (int) Math.floor((lon + 180) / ZONE_WIDTH) + 1;
        double centralMeridian = (zone - 1) * ZONE_WIDTH - 177;
        
        // Store the zone for potential conversion back
        currentZone = zone;
        isNorthern = lat >= 0;
        
        // Convert lat/lon to radians
        double latRad = Math.toRadians(lat);
        double lonRad = Math.toRadians(lon);
        
        // Calculate UTM values
        double N = R / Math.sqrt(1 - E * Math.sin(latRad) * Math.sin(latRad));
        double T = Math.tan(latRad) * Math.tan(latRad);
        double C = E_P2 * Math.cos(latRad) * Math.cos(latRad);
        double A = Math.cos(latRad) * (lonRad - Math.toRadians(centralMeridian));
        
        // Calculate M (true distance along central meridian from equator)
        double M = R * (
                M1 * latRad - 
                M2 * Math.sin(2 * latRad) + 
                M3 * Math.sin(4 * latRad) - 
                M4 * Math.sin(6 * latRad)
        );
        
        // Calculate UTM coordinates
        double x = K0 * N * (A + (1 - T + C) * Math.pow(A, 3) / 6 + 
                (5 - 18 * T + T * T + 72 * C - 58 * E_P2) * Math.pow(A, 5) / 120) + 500000; // Easting with 500km false easting
        
        double y = K0 * (M + N * Math.tan(latRad) * (
                A * A / 2 + 
                (5 - T + 9 * C + 4 * C * C) * Math.pow(A, 4) / 24 + 
                (61 - 58 * T + T * T + 600 * C - 330 * E_P2) * Math.pow(A, 6) / 720
        ));
        
        // Add 10000km false northing for southern hemisphere
        if (lat < 0) {
            y += 10000000;
        }
        
        return new Point3D(x, y, alt);
    }
    
    /**
     * Convert UTM coordinates to latitude/longitude
     * 
     * @param easting Easting in meters (x)
     * @param northing Northing in meters (y)
     * @param zone UTM zone (1-60)
     * @param isNorthern true if in northern hemisphere, false for southern
     * @param alt Altitude in meters
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D utmToLatLon(double easting, double northing, int zone, boolean isNorthern, double alt) {
        // Get central meridian for the zone
        double centralMeridian = (zone - 1) * ZONE_WIDTH - 177;
        
        // Remove false easting and northing
        double x = easting - 500000;
        double y = northing;
        
        if (!isNorthern) {
            y -= 10000000;
        }
        
        // Calculate the meridional arc
        double M = y / K0;
        double mu = M / (R * M1);
        
        // Calculate footprint latitude
        double latRad = mu + 
                P2 * Math.sin(2 * mu) + 
                P3 * Math.sin(4 * mu) + 
                P4 * Math.sin(6 * mu) + 
                P5 * Math.sin(8 * mu);
        
        double C1 = E_P2 * Math.cos(latRad) * Math.cos(latRad);
        double T1 = Math.tan(latRad) * Math.tan(latRad);
        double N1 = R / Math.sqrt(1 - E * Math.sin(latRad) * Math.sin(latRad));
        double R1 = R * (1 - E) / Math.pow(1 - E * Math.sin(latRad) * Math.sin(latRad), 1.5);
        double D = x / (N1 * K0);
        
        // Calculate latitude
        double lat = latRad - (N1 * Math.tan(latRad) / R1) * (
                D * D / 2 - 
                (5 + 3 * T1 + 10 * C1 - 4 * C1 * C1 - 9 * E_P2) * Math.pow(D, 4) / 24 + 
                (61 + 90 * T1 + 298 * C1 + 45 * T1 * T1 - 252 * E_P2 - 3 * C1 * C1) * Math.pow(D, 6) / 720
        );
        
        // Calculate longitude
        double lon = centralMeridian + Math.toDegrees(
                (D - 
                (1 + 2 * T1 + C1) * Math.pow(D, 3) / 6 + 
                (5 - 2 * C1 + 28 * T1 - 3 * C1 * C1 + 8 * E_P2 + 24 * T1 * T1) * Math.pow(D, 5) / 120
        ) / Math.cos(latRad));
        
        // Convert to degrees
        lat = Math.toDegrees(lat);
        
        return new Point3D(lon, lat, alt);
    }
    
    /**
     * Convert UTM coordinates to latitude/longitude using the stored zone and hemisphere
     * 
     * @param easting Easting in meters (x)
     * @param northing Northing in meters (y)
     * @param alt Altitude in meters
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D utmToLatLon(double easting, double northing, double alt) {
        return utmToLatLon(easting, northing, currentZone, isNorthern, alt);
    }
    
    /**
     * Convert a Point3D from lat/lon to UTM
     * 
     * @param geoPoint Point3D with longitude (x), latitude (y), and altitude (z)
     * @return Point3D with UTM easting (x), northing (y), and altitude (z)
     */
    public Point3D convertToUtm(Point3D geoPoint) {
        return latLonToUtm(geoPoint.getY(), geoPoint.getX(), geoPoint.getZ());
    }
    
    /**
     * Convert a Point3D from UTM to lat/lon
     * 
     * @param utmPoint Point3D with UTM easting (x), northing (y), and altitude (z)
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D convertToLatLon(Point3D utmPoint) {
        return utmToLatLon(utmPoint.getX(), utmPoint.getY(), utmPoint.getZ());
    }
    
    /**
     * Convert a Point3D from UTM to lat/lon with specified zone and hemisphere
     * 
     * @param utmPoint Point3D with UTM easting (x), northing (y), and altitude (z)
     * @param zone UTM zone (1-60)
     * @param isNorthern true if in northern hemisphere, false for southern
     * @return Point3D with longitude (x), latitude (y), and altitude (z)
     */
    public Point3D convertToLatLon(Point3D utmPoint, int zone, boolean isNorthern) {
        return utmToLatLon(utmPoint.getX(), utmPoint.getY(), zone, isNorthern, utmPoint.getZ());
    }
    
    /**
     * Get the current UTM zone
     * 
     * @return Current UTM zone (1-60)
     */
    public int getCurrentZone() {
        return currentZone;
    }
    
    /**
     * Check if current coordinates are in northern hemisphere
     * 
     * @return true if in northern hemisphere, false for southern
     */
    public boolean isNorthern() {
        return isNorthern;
    }
}
