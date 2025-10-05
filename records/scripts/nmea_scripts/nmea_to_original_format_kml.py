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
                line = line.strip()
                if '$GPGGA' in line:
                    position = self._parse_gpgga(line)
                    if position:
                        positions.append(position)
                elif '$GPRMC' in line:
                    position = self._parse_gprmc(line)
                    if position:
                        positions.append(position)
                elif '$GPGLL' in line:
                    position = self._parse_gpgll(line)
                    if position:
                        positions.append(position)

        return positions

    def _parse_gpgga(self, line):
        """Parse GPGGA sentence for position data"""
        try:
            parts = line.split(',')
            if len(parts) < 15:
                return None

            time_str = parts[2] if len(parts) > 2 else ''  # HHMMSS.sss
            lat_str = parts[3] if len(parts) > 3 else ''   # DDMM.mmmm
            lat_dir = parts[4] if len(parts) > 4 else ''   # N/S
            lon_str = parts[5] if len(parts) > 5 else ''   # DDDMM.mmmm
            lon_dir = parts[6] if len(parts) > 6 else ''   # E/W
            quality = parts[7] if len(parts) > 7 else '0'   # Fix quality
            satellites = parts[8] if len(parts) > 8 else '0'  # Number of satellites
            altitude_str = parts[10] if len(parts) > 10 else '0'  # Altitude
            timestamp_str = parts[-1]  # Unix timestamp

            # Validate GPS fix quality (0 = invalid, 1 = GPS fix, 2 = DGPS fix)
            if not lat_str or not lon_str or not quality or quality == '0':
                return None

            # Additional validation for satellite count
            sat_count = int(satellites) if satellites else 0
            if sat_count < 4:  # Need at least 4 satellites for 3D fix
                return None

            # Convert coordinates with error handling
            latitude = self._nmea_to_decimal(lat_str, lat_dir)
            longitude = self._nmea_to_decimal(lon_str, lon_dir)

            # Validate coordinate ranges
            if latitude == 0.0 or longitude == 0.0:
                return None
            if not (-90 <= latitude <= 90) or not (-180 <= longitude <= 180):
                return None

            # Parse altitude
            altitude = float(altitude_str) if altitude_str else 0.0

            # Parse timestamp - handle millisecond Unix timestamp
            try:
                timestamp = float(timestamp_str) / 1000.0 if timestamp_str else 0.0
            except ValueError:
                timestamp = 0.0

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

        try:
            # Find decimal point position to determine format
            decimal_pos = coord_str.find('.')

            if decimal_pos >= 5:  # longitude format DDDMM.mmmm (e.g., 03500.4211)
                degrees = int(coord_str[:3])
                minutes = float(coord_str[3:])
            elif decimal_pos >= 4:  # latitude format DDMM.mmmm (e.g., 3154.0051)
                degrees = int(coord_str[:2])
                minutes = float(coord_str[2:])
            else:
                # Handle edge cases
                return 0.0

            # Convert to decimal degrees with high precision
            decimal_degrees = degrees + (minutes / 60.0)

            # Apply direction
            if direction in ['S', 'W']:
                decimal_degrees = -decimal_degrees

            return decimal_degrees

        except (ValueError, IndexError) as e:
            print(f"Error converting coordinates: {coord_str}, direction: {direction}, error: {e}")
            return 0.0

    def _parse_gprmc(self, line):
        """Parse GPRMC sentence for position data"""
        try:
            parts = line.split(',')
            if len(parts) < 12:
                return None

            time_str = parts[1] if len(parts) > 1 else ''
            status = parts[2] if len(parts) > 2 else 'V'  # A=valid, V=invalid
            lat_str = parts[3] if len(parts) > 3 else ''
            lat_dir = parts[4] if len(parts) > 4 else ''
            lon_str = parts[5] if len(parts) > 5 else ''
            lon_dir = parts[6] if len(parts) > 6 else ''
            timestamp_str = parts[-1] if parts else ''

            # Validate status
            if status != 'A':
                return None

            if not lat_str or not lon_str:
                return None

            latitude = self._nmea_to_decimal(lat_str, lat_dir)
            longitude = self._nmea_to_decimal(lon_str, lon_dir)

            if latitude == 0.0 or longitude == 0.0:
                return None
            if not (-90 <= latitude <= 90) or not (-180 <= longitude <= 180):
                return None

            try:
                timestamp = float(timestamp_str) / 1000.0 if timestamp_str else 0.0
            except ValueError:
                timestamp = 0.0

            return {
                'timestamp': timestamp,
                'latitude': latitude,
                'longitude': longitude,
                'altitude': 0.0,  # GPRMC doesn't include altitude
                'satellites': 0
            }

        except (ValueError, IndexError):
            return None

    def _parse_gpgll(self, line):
        """Parse GPGLL sentence for position data"""
        try:
            parts = line.split(',')
            if len(parts) < 7:
                return None

            lat_str = parts[1] if len(parts) > 1 else ''
            lat_dir = parts[2] if len(parts) > 2 else ''
            lon_str = parts[3] if len(parts) > 3 else ''
            lon_dir = parts[4] if len(parts) > 4 else ''
            status = parts[6] if len(parts) > 6 else 'V'  # A=valid, V=invalid
            timestamp_str = parts[-1] if parts else ''

            # Validate status
            if status != 'A':
                return None

            if not lat_str or not lon_str:
                return None

            latitude = self._nmea_to_decimal(lat_str, lat_dir)
            longitude = self._nmea_to_decimal(lon_str, lon_dir)

            if latitude == 0.0 or longitude == 0.0:
                return None
            if not (-90 <= latitude <= 90) or not (-180 <= longitude <= 180):
                return None

            try:
                timestamp = float(timestamp_str) / 1000.0 if timestamp_str else 0.0
            except ValueError:
                timestamp = 0.0

            return {
                'timestamp': timestamp,
                'latitude': latitude,
                'longitude': longitude,
                'altitude': 0.0,  # GPGLL doesn't include altitude
                'satellites': 0
            }

        except (ValueError, IndexError):
            return None


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
        """Sample positions to exactly 1 per second based on timestamps"""
        if not positions:
            return []

        # Sort positions by timestamp to ensure proper ordering
        sorted_positions = sorted(positions, key=lambda x: x['timestamp'])

        sampled = []
        last_timestamp_sec = None

        print(f"Processing {len(sorted_positions)} positions for 1-second sampling...")

        for pos in sorted_positions:
            # Convert timestamp to seconds (round down to get second boundary)
            current_timestamp_sec = int(pos['timestamp'])

            # Include first position or if we've moved to the next second
            if last_timestamp_sec is None or current_timestamp_sec > last_timestamp_sec:
                sampled.append(pos)
                last_timestamp_sec = current_timestamp_sec

                # Debug info for first few samples
                if len(sampled) <= 5:
                    print(f"Sampled point {len(sampled)}: timestamp={pos['timestamp']:.3f}, "
                          f"lat={pos['latitude']:.8f}, lon={pos['longitude']:.8f}")

        print(f"Sampled {len(sampled)} positions (1 per second) from {len(positions)} total")
        return sampled

    def save_kml(self, kml_content, output_path):
        """Save KML content to file"""
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(kml_content)


def main():
    """Main function to process NMEA files and generate original format KML"""
    import sys

    parser = NMEAParser()
    kml_gen = OriginalFormatKMLGenerator()

    # Check for command-line arguments
    if len(sys.argv) > 1:
        # Process specific file(s) provided as arguments
        for nmea_file in sys.argv[1:]:
            if not os.path.exists(nmea_file):
                print(f"Error: File not found: {nmea_file}")
                continue

            print(f"Processing {nmea_file}...")

            # Parse NMEA data
            positions = parser.parse_nmea_file(nmea_file)

            if not positions:
                print(f"No valid GPS positions found in {nmea_file}")
                continue

            print(f"Found {len(positions)} GPS positions")

            # Determine output path
            base_name = os.path.splitext(os.path.basename(nmea_file))[0]
            output_dir = os.path.join(os.path.dirname(nmea_file), 'output')
            os.makedirs(output_dir, exist_ok=True)
            output_file = os.path.join(output_dir, f"{base_name}_kml.kml")

            # Generate original format KML
            kml_content = kml_gen.create_original_format_kml(positions, base_name)

            # Save KML
            kml_gen.save_kml(kml_content, output_file)

            print(f"KML saved to: {output_file}")
            print(f"Route duration: {(positions[-1]['timestamp'] - positions[0]['timestamp'])/60:.1f} minutes")
            print()

    else:
        # Default behavior: process hardcoded routes
        script_dir = os.path.dirname(os.path.abspath(__file__))
        scripts_dir = os.path.dirname(script_dir)
        records_dir = os.path.dirname(scripts_dir)
        output_dir = os.path.join(records_dir, 'output', 'nmea')

        os.makedirs(output_dir, exist_ok=True)

        routes = [
            ('direct_route', 'Direct Route'),
            ('rectangle_route', 'Rectangle Route')
        ]

        for route_folder, route_name in routes:
            route_path = os.path.join(records_dir, route_folder)

            if not os.path.exists(route_path):
                print(f"Route folder not found: {route_path}")
                continue

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