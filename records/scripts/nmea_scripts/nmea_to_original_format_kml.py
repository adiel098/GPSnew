#!/usr/bin/env python3
"""
NMEA to Original Format KML Converter
Generates KML exactly matching the original_route.kml format.
"""

import os
import re
from datetime import datetime


class NMEAParser:
    def __init__(self):
        pass

    def parse_nmea_file(self, file_path):
        """Parse NMEA file and extract GPS positions"""
        positions = []

        with open(file_path, 'r') as f:
            for line in f:
                if line.startswith('NMEA,$GPGGA'):
                    position = self._parse_gpgga(line.strip())
                    if position:
                        positions.append(position)

        return positions

    def _parse_gpgga(self, line):
        """Parse GPGGA sentence for position data"""
        try:
            parts = line.split(',')
            if len(parts) < 15:
                return None

            time_str = parts[2]  # HHMMSS.sss
            lat_str = parts[3]   # DDMM.mmmm
            lat_dir = parts[4]   # N/S
            lon_str = parts[5]   # DDDMM.mmmm
            lon_dir = parts[6]   # E/W
            quality = parts[7]   # Fix quality
            satellites = parts[8]  # Number of satellites
            altitude_str = parts[10]  # Altitude
            timestamp_str = parts[-1]  # Unix timestamp

            if not lat_str or not lon_str or quality == '0':
                return None

            # Convert coordinates
            latitude = self._nmea_to_decimal(lat_str, lat_dir)
            longitude = self._nmea_to_decimal(lon_str, lon_dir)

            # Parse altitude
            altitude = float(altitude_str) if altitude_str else 0.0

            # Parse timestamp
            timestamp = float(timestamp_str) / 1000.0 if timestamp_str else 0.0

            return {
                'timestamp': timestamp,
                'latitude': latitude,
                'longitude': longitude,
                'altitude': altitude,
                'satellites': int(satellites) if satellites else 0
            }

        except (ValueError, IndexError):
            return None

    def _nmea_to_decimal(self, coord_str, direction):
        """Convert NMEA coordinate format to decimal degrees"""
        if not coord_str:
            return 0.0

        # Find decimal point position
        decimal_pos = coord_str.find('.')

        if decimal_pos >= 5:  # longitude format DDDMM.mmmm
            degrees = int(coord_str[:3])
            minutes = float(coord_str[3:])
        else:  # latitude format DDMM.mmmm
            degrees = int(coord_str[:2])
            minutes = float(coord_str[2:])

        decimal_degrees = degrees + (minutes / 60.0)

        if direction in ['S', 'W']:
            decimal_degrees = -decimal_degrees

        return decimal_degrees


class OriginalFormatKMLGenerator:
    def __init__(self):
        pass

    def create_original_format_kml(self, positions, route_name):
        """Generate KML exactly matching original_route.kml format"""

        # Sample positions to approximately 1 per second
        sampled_positions = self._sample_positions_per_second(positions)

        print(f"Sampled {len(sampled_positions)} positions from {len(positions)} total positions")

        # Build KML manually to match original format exactly
        kml_content = []

        # Header
        kml_content.append('<?xml version="1.0" encoding="UTF-8"?>')
        kml_content.append('<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2" xmlns:kml="http://www.opengis.net/kml/2.2" xmlns:atom="http://www.w3.org/2005/Atom">')
        kml_content.append('<Document>')

        # Styles
        kml_content.extend([
            '<Style id="redpin">',
            '<IconStyle>',
            '<color>ff0000ff</color>',
            '<scale>0.5</scale>',
            '<Icon><href>http://maps.google.com/mapfiles/kml/pushpin/red-pushpin.png</href></Icon>',
            '</IconStyle>',
            '</Style>',
            '<Style id="yellowpin">',
            '<IconStyle>',
            '<color>ff00ffff</color>',
            '<scale>0.5</scale>',
            '<Icon><href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href></Icon>',
            '</IconStyle>',
            '</Style>',
            '',
            ''
        ])

        # Create placemarks
        start_time = sampled_positions[0]['timestamp'] if sampled_positions else 0

        for i, pos in enumerate(sampled_positions):
            relative_time = int(pos['timestamp'] - start_time)

            # Use ground-relative altitude (like original route)
            ground_altitude = 1.8  # Match original route altitude

            # Placemark
            kml_content.extend([
                '<Placemark>',
                ' <styleUrl>#redpin</styleUrl>',
                '<Style>',
                '<BalloonStyle>',
                f'<text>This point was taken at time {relative_time} with coordinates (x: {pos["longitude"]:.14f}, y: {pos["latitude"]:.14f}, z: {ground_altitude:.1f})</text>',
                '</BalloonStyle>',
                '</Style>',
                '<TimeStamp>',
                f'<when>{relative_time}</when>',
                ' </TimeStamp>',
                '<Point>',
                '<altitudeMode>relativeToGround</altitudeMode>',
                f'<coordinates>{pos["longitude"]:.6f},{pos["latitude"]:.6f},{ground_altitude:.6f}</coordinates>',
                '</Point>',
                '</Placemark>',
                ''
            ])

        # Footer
        kml_content.extend([
            '</Document>',
            '</kml>'
        ])

        return '\n'.join(kml_content)

    def _sample_positions_per_second(self, positions):
        """Sample positions to approximately 1 per second"""
        if not positions:
            return []

        sampled = []
        last_timestamp = None

        for pos in positions:
            current_timestamp = int(pos['timestamp'])  # Round to nearest second

            # Include first position or if at least 1 second has passed
            if last_timestamp is None or current_timestamp >= last_timestamp + 1:
                sampled.append(pos)
                last_timestamp = current_timestamp

        return sampled

    def save_kml(self, kml_content, output_path):
        """Save KML content to file"""
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(kml_content)


def main():
    """Main function to process NMEA files and generate original format KML"""

    parser = NMEAParser()
    kml_gen = OriginalFormatKMLGenerator()

    # Define input and output paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    scripts_dir = os.path.dirname(script_dir)  # Parent of nmea_scripts is scripts
    records_dir = os.path.dirname(scripts_dir)  # Parent of scripts is records
    output_dir = os.path.join(records_dir, 'output', 'nmea')

    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    # Process both routes
    routes = [
        ('direct_route', 'Direct Route'),
        ('rectangle_route', 'Rectangle Route')
    ]

    for route_folder, route_name in routes:
        route_path = os.path.join(records_dir, route_folder)

        # Find NMEA file in the route folder
        nmea_files = [f for f in os.listdir(route_path) if f.endswith('.nmea')]

        if not nmea_files:
            print(f"No NMEA files found in {route_folder}")
            continue

        nmea_file = os.path.join(route_path, nmea_files[0])
        print(f"Processing {nmea_file}...")

        # Parse NMEA data
        positions = parser.parse_nmea_file(nmea_file)

        if not positions:
            print(f"No valid GPS positions found in {nmea_file}")
            continue

        print(f"Found {len(positions)} GPS positions")

        # Generate original format KML
        kml_content = kml_gen.create_original_format_kml(positions, route_name)

        # Save KML
        output_file = os.path.join(output_dir, f"{route_folder}_original_format.kml")
        kml_gen.save_kml(kml_content, output_file)

        print(f"Original format KML saved to: {output_file}")
        print(f"Route duration: {(positions[-1]['timestamp'] - positions[0]['timestamp'])/60:.1f} minutes")
        print()


if __name__ == "__main__":
    main()