package com.gps.particlefilter.kml;

/**
 * Utility class for running KML validation separately
 */
public class ValidatorRunner {
    public static void main(String[] args) {
        System.out.println("Starting standalone KML Validator...");
        
        String buildingsFile = "building3d.kml";
        String satellitesFile = "satellites.kml";
        String routeFile = "original_route.kml";
        
        // אם יש ארגומנטים שהועברו בשורת הפקודה, השתמש בהם
        if (args.length >= 3) {
            buildingsFile = args[0];
            satellitesFile = args[1];
            routeFile = args[2];
        } else if (args.length > 0) {
            System.out.println("Usage: ValidatorRunner [buildingsFile satellitesFile routeFile]");
            System.out.println("Using default file paths instead.");
        }
        
        System.out.println("Validating files:");
        System.out.println("- Buildings: " + buildingsFile);
        System.out.println("- Satellites: " + satellitesFile);
        System.out.println("- Route: " + routeFile);
        
        try {
            // הפעלת מתודת הוולידציה
            com.gps.particlefilter.kml.KMLValidator.validateAndGenerateReport(buildingsFile, satellitesFile, routeFile);
            System.out.println("Validation completed successfully!");
            System.out.println("Check kml_validation_report.txt for the full validation report.");
        } catch (Exception e) {
            System.err.println("Error during validation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
