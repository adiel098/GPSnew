package com.gps.particlefilter.util;

import com.gps.particlefilter.model.Point3D;

/**
 * A demo class showing how to use the UTM coordinate system and switch between UTM and geographic coordinates.
 */
public class CoordinateSystemDemo {

    /**
     * Test conversion with reference points from external sources
     */
    private static void testWithReferencePoints() {
        CoordinateConverter converter = new CoordinateConverter();
        
        System.out.println("\n--- Testing with Reference Points ---");
        
        // Test point from Tel Aviv (WGS84)
        double refLat = 32.0853;
        double refLon = 34.7818;
        double refAlt = 0.0;
        
        // Expected UTM values for Tel Aviv (verified with online converter)
        // These values are approximate and can be fine-tuned with a more accurate reference
        double expectedEasting = 668156.0;   // in meters
        double expectedNorthing = 3551279.0; // in meters
        int expectedZone = 36;
        boolean expectedNorthern = true;
        
        Point3D geoPoint = new Point3D(refLon, refLat, refAlt);
        Point3D utmPoint = converter.convertToUtm(geoPoint);
        
        System.out.println("Reference Point (Tel Aviv):");
        System.out.println("  Geographic (Lon, Lat, Alt): " + geoPoint);
        System.out.println("  Converted to UTM: " + utmPoint);
        System.out.println("  Expected UTM (approx): Easting=" + expectedEasting + 
                         ", Northing=" + expectedNorthing + 
                         ", Zone=" + expectedZone + 
                         (expectedNorthern ? " North" : " South"));
        
        double eastingDiff = Math.abs(utmPoint.getX() - expectedEasting);
        double northingDiff = Math.abs(utmPoint.getY() - expectedNorthing);
        System.out.println("  Difference: Easting=" + eastingDiff + 
                         "m, Northing=" + northingDiff + "m");
        
        // Convert back to lat/lon
        Point3D roundTripPoint = converter.convertToLatLon(utmPoint);
        System.out.println("  Round-trip to Geographic: " + roundTripPoint);
        double distanceError = geoPoint.distanceTo(roundTripPoint);
        System.out.println("  Round-trip error (m): " + distanceError);
    }

    public static void main(String[] args) {
        // Get the singleton instance of the coordinate system manager
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        
        // Default is UTM enabled
        System.out.println("Default coordinate system: " + 
                (coordManager.isUsingUtm() ? "UTM" : "Geographic (Lat/Lon)"));
        
        // Some example coordinates in Tel Aviv, Israel (lat/lon)
        double latitude = 32.0853;
        double longitude = 34.7818;
        double altitude = 1.8; // 1.8 meters above ground
        
        // Create a geographic point (using lon, lat, alt order)
        Point3D geoPoint = new Point3D(longitude, latitude, altitude);
        System.out.println("\nGeographic Point (Lon, Lat, Alt): " + geoPoint);
        
        // Convert to UTM
        Point3D utmPoint = coordManager.getConverter().convertToUtm(geoPoint);
        System.out.println("UTM Point (Easting, Northing, Alt): " + utmPoint);
        System.out.println("UTM Zone: " + coordManager.getConverter().getCurrentZone() + 
                (coordManager.getConverter().isNorthern() ? " North" : " South"));
        
        // Set default UTM zone based on first coordinates
        coordManager.setDefaultUtmZone(
                coordManager.getConverter().getCurrentZone(),
                coordManager.getConverter().isNorthern());
        
        // Test round-trip conversion
        Point3D roundTripPoint = coordManager.getConverter().convertToLatLon(utmPoint);
        System.out.println("Round-trip Geographic Point: " + roundTripPoint);
        double distanceMeters = geoPoint.distanceTo(roundTripPoint);
        System.out.println("Original vs Round-trip distance (m): " + distanceMeters);
        System.out.println("Conversion accuracy: " + (distanceMeters < 0.1 ? "EXCELLENT" : 
                          (distanceMeters < 1.0 ? "GOOD" : "POOR")));
        
        // Example of switching to geographic coordinates
        System.out.println("\n--- Switching to Geographic Coordinates ---");
        coordManager.setUseUtm(false);
        System.out.println("Current coordinate system: " + 
                (coordManager.isUsingUtm() ? "UTM" : "Geographic (Lat/Lon)"));
        
        // When we convert from geographic to our coordinate system (which is now geographic)
        // it should return the same point
        Point3D convertedPoint = coordManager.convertFromGeographic(geoPoint);
        System.out.println("Point after conversion: " + convertedPoint);
        System.out.println("Equal to original: " + 
                (Math.abs(geoPoint.getX() - convertedPoint.getX()) < 0.000001 && 
                 Math.abs(geoPoint.getY() - convertedPoint.getY()) < 0.000001));
        
        // Example of switching back to UTM
        System.out.println("\n--- Switching back to UTM Coordinates ---");
        coordManager.setUseUtm(true);
        System.out.println("Current coordinate system: " + 
                (coordManager.isUsingUtm() ? "UTM" : "Geographic (Lat/Lon)"));
        
        // Convert from geographic to our coordinate system (which is now UTM)
        Point3D reConvertedPoint = coordManager.convertFromGeographic(geoPoint);
        System.out.println("Point after conversion: " + reConvertedPoint);
        System.out.println("Equal to UTM point: " + 
                (Math.abs(utmPoint.getX() - reConvertedPoint.getX()) < 0.000001 && 
                 Math.abs(utmPoint.getY() - reConvertedPoint.getY()) < 0.000001));
                 
        // Test with specific known reference points
        testWithReferencePoints();
    }
}
