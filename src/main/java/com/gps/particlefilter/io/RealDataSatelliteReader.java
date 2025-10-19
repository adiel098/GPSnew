package com.gps.particlefilter.io;

import com.gps.particlefilter.model.*;
import com.gps.particlefilter.util.CoordinateSystemManager;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads real satellite observations from GNSS log TXT files.
 * Parses Status lines to extract satellite data including C/N0, azimuth, elevation.
 */
public class RealDataSatelliteReader {

    // Constellation type constants
    private static final int CONSTELLATION_GPS = 1;
    private static final int CONSTELLATION_SBAS = 2;
    private static final int CONSTELLATION_GALILEO = 3;
    private static final int CONSTELLATION_BEIDOU = 5;
    private static final int CONSTELLATION_GLONASS = 6;

    // Average satellite orbital distance (meters)
    private static final double SATELLITE_ORBITAL_DISTANCE = 20200000.0; // ~20,200 km for GPS

    /**
     * Read satellites from GNSS log TXT file
     * @param filename Path to the TXT file
     * @param observerPosition Observer's position (used to calculate satellite positions)
     * @return List of satellites with real C/N0 values
     */
    public List<Satellite> readSatellites(String filename, Point3D observerPosition) {
        List<Satellite> satellites = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            Map<String, SatelliteObservation> latestObservations = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Status,")) {
                    SatelliteObservation obs = parseStatusLine(line);
                    if (obs != null && obs.cn0DbHz > 0) {
                        // Keep the latest observation for each satellite
                        String key = getConstellationPrefix(obs.constellationType) + obs.svid;
                        latestObservations.put(key, obs);
                    }
                }
            }

            System.out.println("\nRealDataSatelliteReader: Found " + latestObservations.size() + " unique satellites");

            // Convert observations to Satellite objects
            for (Map.Entry<String, SatelliteObservation> entry : latestObservations.entrySet()) {
                String satelliteName = entry.getKey();
                SatelliteObservation obs = entry.getValue();

                // Calculate satellite position from azimuth/elevation
                Point3D satellitePosition = calculateSatellitePosition(
                    observerPosition,
                    obs.azimuthDegrees,
                    obs.elevationDegrees
                );

                // Create Satellite object with real C/N0 value
                Satellite satellite = new Satellite(
                    satelliteName,
                    satellitePosition,
                    obs.azimuthDegrees,
                    obs.elevationDegrees
                );

                // Set the real C/N0 value from GNSS data
                satellite.setCnRatio(obs.cn0DbHz);

                satellites.add(satellite);

                // Debug output for first few satellites
                if (satellites.size() <= 5) {
                    System.out.printf("  Satellite %s: Az=%.1f°, El=%.1f°, C/N0=%.1f dB-Hz (%s)%n",
                        satelliteName, obs.azimuthDegrees, obs.elevationDegrees, obs.cn0DbHz,
                        obs.cn0DbHz >= 37.0 ? "LOS" : "NLOS");
                }
            }

            System.out.println("RealDataSatelliteReader: Created " + satellites.size() + " satellite objects");

            // Sort satellites by C/N0 (signal strength) in descending order
            // Higher C/N0 = stronger signal = more reliable
            satellites.sort((s1, s2) -> Double.compare(s2.getCnRatio(), s1.getCnRatio()));

            // Keep only the best 20 satellites (matching article's tested range of ~17)
            // The article formula works correctly with N ≤ 20
            if (satellites.size() > 20) {
                System.out.println("Filtering from " + satellites.size() + " to best 20 satellites based on C/N0 signal strength");
                satellites = new ArrayList<>(satellites.subList(0, 20));
            }

            System.out.println("Using " + satellites.size() + " satellites for particle filter\n");

        } catch (IOException e) {
            System.err.println("Error reading satellite data from " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }

        return satellites;
    }

    /**
     * Parse a Status line from the GNSS log
     * Format: Status,,SignalCount,SignalIndex,ConstellationType,Svid,CarrierFrequencyHz,Cn0DbHz,AzimuthDegrees,ElevationDegrees,UsedInFix,HasAlmanacData,HasEphemerisData,BasebandCn0DbHz
     */
    private SatelliteObservation parseStatusLine(String line) {
        try {
            String[] parts = line.split(",");
            if (parts.length < 14) {
                return null;
            }

            SatelliteObservation obs = new SatelliteObservation();

            // parts[0] = "Status"
            // parts[1] = empty
            // parts[2] = SignalCount
            // parts[3] = SignalIndex
            obs.constellationType = Integer.parseInt(parts[4]);
            obs.svid = Integer.parseInt(parts[5]);
            // parts[6] = CarrierFrequencyHz
            obs.cn0DbHz = Double.parseDouble(parts[7]);
            obs.azimuthDegrees = Double.parseDouble(parts[8]);
            obs.elevationDegrees = Double.parseDouble(parts[9]);
            // parts[10] = UsedInFix
            // parts[11] = HasAlmanacData
            // parts[12] = HasEphemerisData
            // parts[13] = BasebandCn0DbHz

            return obs;

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // Skip malformed lines
            return null;
        }
    }

    /**
     * Calculate satellite position from observer position and azimuth/elevation
     */
    private Point3D calculateSatellitePosition(Point3D observerPosition, double azimuthDegrees, double elevationDegrees) {
        // Convert to radians
        double azimuthRad = Math.toRadians(azimuthDegrees);
        double elevationRad = Math.toRadians(elevationDegrees);

        // Calculate offset in local ENU (East-North-Up) coordinates
        double east = SATELLITE_ORBITAL_DISTANCE * Math.cos(elevationRad) * Math.sin(azimuthRad);
        double north = SATELLITE_ORBITAL_DISTANCE * Math.cos(elevationRad) * Math.cos(azimuthRad);
        double up = SATELLITE_ORBITAL_DISTANCE * Math.sin(elevationRad);

        // Add offset to observer position
        // Note: In UTM coordinates, X=Easting, Y=Northing, Z=Altitude
        return new Point3D(
            observerPosition.getX() + east,
            observerPosition.getY() + north,
            observerPosition.getZ() + up
        );
    }

    /**
     * Get constellation prefix for satellite name
     */
    private String getConstellationPrefix(int constellationType) {
        switch (constellationType) {
            case CONSTELLATION_GPS:
                return "G";
            case CONSTELLATION_SBAS:
                return "S";
            case CONSTELLATION_GALILEO:
                return "E";
            case CONSTELLATION_BEIDOU:
                return "C";
            case CONSTELLATION_GLONASS:
                return "R";
            default:
                return "U"; // Unknown
        }
    }

    /**
     * Inner class to hold satellite observation data
     */
    private static class SatelliteObservation {
        int constellationType;
        int svid;
        double cn0DbHz;
        double azimuthDegrees;
        double elevationDegrees;
    }

    /**
     * Generate validation report for satellite data
     */
    public String generateValidationReport(String filename, Point3D observerPosition) {
        StringBuilder report = new StringBuilder();
        report.append("Real Satellite Data Validation Report\n");
        report.append("=====================================\n\n");

        List<Satellite> satellites = readSatellites(filename, observerPosition);
        report.append("Total satellites found: ").append(satellites.size()).append("\n\n");

        // Count by constellation
        Map<Character, Integer> constellationCounts = new HashMap<>();
        int losCount = 0;
        int nlosCount = 0;

        for (Satellite satellite : satellites) {
            char prefix = satellite.getName().charAt(0);
            constellationCounts.put(prefix, constellationCounts.getOrDefault(prefix, 0) + 1);

            if (satellite.isLosFromSignalStrength()) {
                losCount++;
            } else {
                nlosCount++;
            }
        }

        report.append("Constellation breakdown:\n");
        for (Map.Entry<Character, Integer> entry : constellationCounts.entrySet()) {
            String constellationName = getConstellationName(entry.getKey());
            report.append(String.format("  %s (%c): %d satellites%n", constellationName, entry.getKey(), entry.getValue()));
        }

        report.append("\nSignal strength classification (C/N0 >= 37 dB-Hz):\n");
        report.append(String.format("  LOS: %d satellites (%.1f%%)%n", losCount, 100.0 * losCount / satellites.size()));
        report.append(String.format("  NLOS: %d satellites (%.1f%%)%n", nlosCount, 100.0 * nlosCount / satellites.size()));

        report.append("\nSample satellites:\n");
        for (int i = 0; i < Math.min(10, satellites.size()); i++) {
            Satellite sat = satellites.get(i);
            report.append(String.format("  %s: Az=%.1f°, El=%.1f°, C/N0=%.1f dB-Hz (%s)%n",
                sat.getName(), sat.getAzimuth(), sat.getElevation(), sat.getCnRatio(),
                sat.isLosFromSignalStrength() ? "LOS" : "NLOS"));
        }

        return report.toString();
    }

    private String getConstellationName(char prefix) {
        switch (prefix) {
            case 'G': return "GPS";
            case 'E': return "Galileo";
            case 'R': return "GLONASS";
            case 'C': return "BeiDou";
            case 'S': return "SBAS";
            default: return "Unknown";
        }
    }
}
