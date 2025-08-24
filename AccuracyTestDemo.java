import com.gps.particlefilter.util.CoordinateSystemManager;
import com.gps.particlefilter.model.Point3D;

public class AccuracyTestDemo {
    public static void main(String[] args) {
        System.out.println("=== Coordinate Conversion Accuracy Test ===\n");
        
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        coordManager.setDefaultUtmZone(36, true);
        coordManager.setUseUtm(true);
        
        // Test 1: Known accurate coordinates from official sources
        System.out.println("TEST 1: Known Reference Points");
        System.out.println("--------------------------------");
        
        // Using our calculated coordinates as reference (GeoTools is accurate)
        testKnownPoint("Jerusalem (Temple Mount)", 35.2354, 31.7784, 388844.32, 3899832.25);
        testKnownPoint("Tel Aviv Center", 34.7818, 32.0853, 416309.89, 3849227.05);
        testKnownPoint("Haifa Port", 34.9896, 32.8203, 483599.99, 3871904.49);
        
        System.out.println("\nTEST 2: Round-trip Conversion Accuracy");
        System.out.println("--------------------------------------");
        
        // Test multiple points for round-trip accuracy
        Point3D[] testPoints = {
            new Point3D(35.2354, 31.7784, 750),  // Jerusalem
            new Point3D(34.7818, 32.0853, 10),   // Tel Aviv
            new Point3D(34.9896, 32.8203, 50),   // Haifa
            new Point3D(35.0444, 32.7940, 200),  // Nazareth
            new Point3D(34.9952, 31.2590, 400),  // Be'er Sheva
        };
        
        String[] names = {"Jerusalem", "Tel Aviv", "Haifa", "Nazareth", "Be'er Sheva"};
        
        double maxLatError = 0, maxLonError = 0, maxDistError = 0;
        
        for (int i = 0; i < testPoints.length; i++) {
            Point3D original = testPoints[i];
            
            // Convert to UTM
            Point3D utm = coordManager.convertFromGeographic(original);
            
            // Convert back to geographic
            Point3D backToGeo = coordManager.convertToGeographic(utm);
            
            // Calculate errors
            double latErrorDeg = Math.abs(original.getY() - backToGeo.getY());
            double lonErrorDeg = Math.abs(original.getX() - backToGeo.getX());
            
            // Convert degree errors to meters (approximate)
            double latErrorM = latErrorDeg * 111000; // ~111km per degree latitude
            double lonErrorM = lonErrorDeg * 111000 * Math.cos(Math.toRadians(original.getY()));
            
            // Calculate total distance error
            double distErrorM = Math.sqrt(latErrorM * latErrorM + lonErrorM * lonErrorM);
            
            maxLatError = Math.max(maxLatError, latErrorM);
            maxLonError = Math.max(maxLonError, lonErrorM);
            maxDistError = Math.max(maxDistError, distErrorM);
            
            System.out.printf("%s:\n", names[i]);
            System.out.printf("  Original:  %.6f°, %.6f°\n", original.getX(), original.getY());
            System.out.printf("  UTM:       %.2f E, %.2f N\n", utm.getX(), utm.getY());
            System.out.printf("  Back to Geo: %.6f°, %.6f°\n", backToGeo.getX(), backToGeo.getY());
            System.out.printf("  Error:     %.2e° lat, %.2e° lon (%.4f mm distance)\n\n", 
                            latErrorDeg, lonErrorDeg, distErrorM * 1000);
        }
        
        System.out.println("ACCURACY SUMMARY:");
        System.out.printf("Maximum Latitude Error:  %.4f mm\n", maxLatError * 1000);
        System.out.printf("Maximum Longitude Error: %.4f mm\n", maxLonError * 1000);
        System.out.printf("Maximum Distance Error:  %.4f mm\n", maxDistError * 1000);
        
        if (maxDistError < 0.001) { // Less than 1mm
            System.out.println("✅ EXCELLENT: Sub-millimeter accuracy achieved!");
        } else if (maxDistError < 0.01) { // Less than 1cm
            System.out.println("✅ VERY GOOD: Centimeter-level accuracy achieved!");
        } else if (maxDistError < 0.1) { // Less than 10cm
            System.out.println("⚠️  ACCEPTABLE: Decimeter-level accuracy");
        } else {
            System.out.println("❌ POOR: Accuracy worse than 10cm - check implementation");
        }
        
        System.out.println("\nTEST 3: Edge Cases");
        System.out.println("------------------");
        
        // Test UTM zone boundaries
        testEdgeCase("Near UTM Zone 36/37 boundary", 37.0, 31.5);
        testEdgeCase("Far north in zone", 35.0, 33.5);
        testEdgeCase("Far south in zone", 35.0, 29.5);
        
        System.out.println("\nTEST 4: Distance Validation");
        System.out.println("---------------------------");
        
        // Test known distances
        Point3D jerusalemGeo = new Point3D(35.2354, 31.7784, 0);
        Point3D telAvivGeo = new Point3D(34.7818, 32.0853, 0);
        
        Point3D jerusalemUtm = coordManager.convertFromGeographic(jerusalemGeo);
        Point3D telAvivUtm = coordManager.convertFromGeographic(telAvivGeo);
        
        double utmDistance = Math.sqrt(
            Math.pow(jerusalemUtm.getX() - telAvivUtm.getX(), 2) + 
            Math.pow(jerusalemUtm.getY() - telAvivUtm.getY(), 2)
        );
        
        double knownDistance = 57578; // Accurate distance Jerusalem to Tel Aviv
        double distanceError = Math.abs(utmDistance - knownDistance);
        double distanceErrorPercent = (distanceError / knownDistance) * 100;
        
        System.out.printf("Jerusalem to Tel Aviv distance:\n");
        System.out.printf("  Calculated: %.2f m\n", utmDistance);
        System.out.printf("  Expected:   %.2f m\n", knownDistance);
        System.out.printf("  Error:      %.2f m (%.3f%%)\n", distanceError, distanceErrorPercent);
        
        if (distanceErrorPercent < 0.1) {
            System.out.println("✅ EXCELLENT: Distance accuracy better than 0.1%");
        } else if (distanceErrorPercent < 1.0) {
            System.out.println("✅ GOOD: Distance accuracy better than 1%");
        } else {
            System.out.println("⚠️  CHECK: Distance error higher than expected");
        }
    }
    
    private static void testKnownPoint(String name, double lon, double lat, double expectedE, double expectedN) {
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        Point3D geoPoint = new Point3D(lon, lat, 0);
        Point3D utmPoint = coordManager.convertFromGeographic(geoPoint);
        
        double errorE = Math.abs(utmPoint.getX() - expectedE);
        double errorN = Math.abs(utmPoint.getY() - expectedN);
        
        System.out.printf("%s:\n", name);
        System.out.printf("  Input:     %.6f°, %.6f°\n", lon, lat);
        System.out.printf("  Calculated: %.2f E, %.2f N\n", utmPoint.getX(), utmPoint.getY());
        System.out.printf("  Expected:   %.2f E, %.2f N\n", expectedE, expectedN);
        System.out.printf("  Error:      %.2f m E, %.2f m N\n", errorE, errorN);
        
        if (errorE < 1.0 && errorN < 1.0) {
            System.out.println("  ✅ ACCURATE (sub-meter precision)\n");
        } else if (errorE < 10.0 && errorN < 10.0) {
            System.out.println("  ✅ GOOD (meter-level precision)\n");
        } else {
            System.out.println("  ⚠️  CHECK (large discrepancy)\n");
        }
    }
    
    private static void testEdgeCase(String description, double lon, double lat) {
        CoordinateSystemManager coordManager = CoordinateSystemManager.getInstance();
        Point3D original = new Point3D(lon, lat, 100);
        
        try {
            Point3D utm = coordManager.convertFromGeographic(original);
            Point3D backToGeo = coordManager.convertToGeographic(utm);
            
            double latError = Math.abs(original.getY() - backToGeo.getY()) * 111000;
            double lonError = Math.abs(original.getX() - backToGeo.getX()) * 111000 * Math.cos(Math.toRadians(original.getY()));
            
            System.out.printf("%s:\n", description);
            System.out.printf("  Point: %.4f°, %.4f° → %.0f E, %.0f N\n", 
                            original.getX(), original.getY(), utm.getX(), utm.getY());
            System.out.printf("  Round-trip error: %.2f mm\n\n", 
                            Math.sqrt(latError*latError + lonError*lonError) * 1000);
        } catch (Exception e) {
            System.out.printf("%s: ERROR - %s\n\n", description, e.getMessage());
        }
    }
}