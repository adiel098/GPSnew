package com.gps.particlefilter.kml;

import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class KMLValidator {
    private static void handleValidationException(String filePath, Exception e, StringBuilder report) {
        try {
            report.append("Error validating file: ").append(filePath).append("\n");
            report.append("Error message: ").append(e.getMessage()).append("\n\n");
            e.printStackTrace(); // Print stack trace for debugging
            
            // Create report file even if there's an error
            File kmlFile = new File(filePath);
            Path reportPath = kmlFile.getParentFile() != null ? 
                    kmlFile.getParentFile().toPath().resolve("kml_validation_report.txt") : 
                    Paths.get("kml_validation_report.txt");
                    
            try (FileWriter writer = new FileWriter(reportPath.toFile())) {
                writer.write(report.toString());
                System.out.println("Validation report written to: " + reportPath);
            }
        } catch (Exception ex) {
            System.err.println("Error writing validation report: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public static void validateAndGenerateReport(String buildingsFile, String satellitesFile, String routeFile) {
        try {
            BuildingKMLReader buildingReader = new BuildingKMLReader();
            SatelliteKMLReader satelliteReader = new SatelliteKMLReader();
            RouteKMLReader routeReader = new RouteKMLReader();

            StringBuilder fullReport = new StringBuilder();
            fullReport.append("KML Validation Report\n");
            fullReport.append("===================\n");
            fullReport.append("Generated at: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");

            try {
                // Validate buildings
                fullReport.append(buildingReader.generateValidationReport(buildingsFile)).append("\n");
            } catch (Exception e) {
                handleValidationException(buildingsFile, e, fullReport);
            }

            try {
                // Validate satellites
                fullReport.append(satelliteReader.generateValidationReport(satellitesFile)).append("\n");
            } catch (Exception e) {
                handleValidationException(satellitesFile, e, fullReport);
            }

            try {
                // Validate route
                fullReport.append(routeReader.generateValidationReport(routeFile)).append("\n");
            } catch (Exception e) {
                handleValidationException(routeFile, e, fullReport);
            }

            // Write report to file
            File buildingFile = new File(buildingsFile);
            Path reportPath = buildingFile.getParentFile() != null ? 
                    buildingFile.getParentFile().toPath().resolve("kml_validation_report.txt") : 
                    Paths.get("kml_validation_report.txt");
                    
            try (FileWriter writer = new FileWriter(reportPath.toFile())) {
                writer.write(fullReport.toString());
                System.out.println("Validation report written to: " + reportPath);
            }
        } catch (Exception e) {
            System.err.println("Error validating KML files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main method to run the KML validator directly.
     * Usage: java com.gps.particlefilter.kml.KMLValidator [buildingsFile satellitesFile routeFile]
     */
    public static void main(String[] args) {
        System.out.println("Starting KML Validator...");
        
        String buildingsFile = "building3d.kml";
        String satellitesFile = "satellites.kml";
        String routeFile = "original_route.kml";
        
        // אם יש ארגומנטים שהועברו בשורת הפקודה, השתמש בהם
        if (args.length >= 3) {
            buildingsFile = args[0];
            satellitesFile = args[1];
            routeFile = args[2];
        } else if (args.length > 0) {
            System.out.println("Usage: KMLValidator [buildingsFile satellitesFile routeFile]");
            System.out.println("Using default file paths instead.");
        }
        
        System.out.println("Validating files:");
        System.out.println("- Buildings: " + buildingsFile);
        System.out.println("- Satellites: " + satellitesFile);
        System.out.println("- Route: " + routeFile);
        
        try {
            // הפעלת מתודת הוולידציה
            validateAndGenerateReport(buildingsFile, satellitesFile, routeFile);
            System.out.println("Validation completed successfully!");
            System.out.println("Check kml_validation_report.txt for the full validation report.");
        } catch (Exception e) {
            System.err.println("Error during validation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
