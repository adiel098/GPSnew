#!/usr/bin/env python3
"""
TXT (GPS Fix) to KML Converter
Converts Android GNSS Logger .txt files to KML format using GPS Fix data
Provides higher precision than NMEA format
"""

import os
import sys
from datetime import datetime


class TXTParser:
    def __init__(self):
        pass

    def parse_txt_file(self, file_path):
        """Parse TXT file and extract GPS Fix positions"""
        positions = []

        with open(file_path, 'r') as f:
            for line in f:
                line = line.strip()
                if line.startswith('Fix,GPS,'):
                    position = self._parse_gps_fix(line)
                    if position:
                        positions.append(position)

        return positions

    def _parse_gps_fix(self, line):
        """Parse GPS Fix line for position data"""
        try:
            # Fix,GPS,Lat,Lon,Alt,Speed,Accuracy,Bearing,UnixTimeMillis,...
            parts = line.split(',')
            if len(parts) < 9:
                return None

            provider = parts[1]  # Should be 'GPS'
            latitude = float(parts[2])
            longitude = float(parts[3])
            altitude = float(parts[4]) if parts[4] else 0.0
            speed = float(parts[5]) if parts[5] else 0.0
            accuracy = float(parts[6]) if parts[6] else 0.0
            bearing = float(parts[7]) if parts[7] else 0.0
            timestamp_ms = int(parts[8]) if parts[8] else 0

            # Validate coordinates
            if not (-90 <= latitude <= 90) or not (-180 <= longitude <= 180):
                return None

            # Convert timestamp to seconds
            timestamp = timestamp_ms / 1000.0

            return {
                'timestamp': timestamp,
                'latitude': latitude,
                'longitude': longitude,
                'altitude': altitude,
                'speed': speed,
                'accuracy': accuracy,
                'bearing': bearing
            }

        except (ValueError, IndexError):
            return None


class KMLGenerator:
    def __init__(self):
        pass

    def create_kml(self, positions, route_name):
        """Generate KML from GPS Fix positions"""

        # Sample positions to approximately 1 per second
        sampled_positions = self._sample_positions_per_second(positions)

        print(f"Sampled {len(sampled_positions)} positions from {len(positions)} total positions")

        # Build KML
        kml_content = []

        # Header
        kml_content.append('<?xml version="1.0" encoding="UTF-8"?>')
        kml_content.append('<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2" xmlns:kml="http://www.opengis.net/kml/2.2" xmlns:atom="http://www.w3.org/2005/Atom">')
        kml_content.append('<Document>')
        kml_content.append(f'<name>{route_name}</name>')

        # Styles
        kml_content.extend([
            '<Style id="gps_fix_point">',
            '<IconStyle>',
            '<color>ff00ff00</color>',
            '<scale>0.5</scale>',
            '<Icon><href>http://maps.google.com/mapfiles/kml/pushpin/grn-pushpin.png</href></Icon>',
            '</IconStyle>',
            '</Style>',
            ''
        ])

        # Create placemarks
        start_time = sampled_positions[0]['timestamp'] if sampled_positions else 0

        for i, pos in enumerate(sampled_positions):
            relative_time = int(pos['timestamp'] - start_time)

            # Use absolute altitude from GPS
            altitude = pos['altitude']

            # Placemark
            kml_content.extend([
                '<Placemark>',
                ' <styleUrl>#gps_fix_point</styleUrl>',
                '<Style>',
                '<BalloonStyle>',
                f'<text>GPS Fix at time {relative_time}s - Coordinates: ({pos["longitude"]:.10f}, {pos["latitude"]:.10f}, {altitude:.2f}m) - Speed: {pos["speed"]:.2f} m/s - Accuracy: {pos["accuracy"]:.2f}m</text>',
                '</BalloonStyle>',
                '</Style>',
                '<TimeStamp>',
                f'<when>{relative_time}</when>',
                ' </TimeStamp>',
                '<Point>',
                '<altitudeMode>absolute</altitudeMode>',
                f'<coordinates>{pos["longitude"]:.10f},{pos["latitude"]:.10f},{altitude:.6f}</coordinates>',
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

        # Sort positions by timestamp
        sorted_positions = sorted(positions, key=lambda x: x['timestamp'])

        sampled = []
        last_timestamp_sec = None

        print(f"Processing {len(sorted_positions)} GPS Fix positions for 1-second sampling...")

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
                          f"lat={pos['latitude']:.10f}, lon={pos['longitude']:.10f}")

        print(f"Sampled {len(sampled)} positions (1 per second) from {len(positions)} total")
        return sampled

    def save_kml(self, kml_content, output_path):
        """Save KML content to file"""
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(kml_content)


def main():
    """Main function to process TXT files and generate KML"""

    parser = TXTParser()
    kml_gen = KMLGenerator()

    # Check for command-line arguments
    if len(sys.argv) > 1:
        # Process specific file(s) provided as arguments
        for txt_file in sys.argv[1:]:
            if not os.path.exists(txt_file):
                print(f"Error: File not found: {txt_file}")
                continue

            print(f"Processing {txt_file}...")

            # Parse GPS Fix data
            positions = parser.parse_txt_file(txt_file)

            if not positions:
                print(f"No valid GPS Fix positions found in {txt_file}")
                continue

            print(f"Found {len(positions)} GPS Fix positions")

            # Determine output path
            base_name = os.path.splitext(os.path.basename(txt_file))[0]
            output_dir = os.path.join(os.path.dirname(txt_file), 'output')
            os.makedirs(output_dir, exist_ok=True)
            output_file = os.path.join(output_dir, f"{base_name}_gps_fix_kml.kml")

            # Generate KML
            kml_content = kml_gen.create_kml(positions, base_name)

            # Save KML
            kml_gen.save_kml(kml_content, output_file)

            print(f"KML saved to: {output_file}")
            if len(positions) > 1:
                print(f"Route duration: {(positions[-1]['timestamp'] - positions[0]['timestamp'])/60:.1f} minutes")
            print()

    else:
        # Default behavior: process hardcoded routes
        script_dir = os.path.dirname(os.path.abspath(__file__))
        scripts_dir = os.path.dirname(script_dir)
        records_dir = os.path.dirname(scripts_dir)
        output_dir = os.path.join(records_dir, 'output', 'txt')

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

            # Find TXT file in the route folder
            txt_files = [f for f in os.listdir(route_path) if f.endswith('.txt')]

            if not txt_files:
                print(f"No TXT files found in {route_folder}")
                continue

            txt_file = os.path.join(route_path, txt_files[0])
            print(f"Processing {txt_file}...")

            # Parse GPS Fix data
            positions = parser.parse_txt_file(txt_file)

            if not positions:
                print(f"No valid GPS Fix positions found in {txt_file}")
                continue

            print(f"Found {len(positions)} GPS Fix positions")

            # Generate KML
            kml_content = kml_gen.create_kml(positions, route_name)

            # Save KML
            output_file = os.path.join(output_dir, f"{route_folder}_gps_fix.kml")
            kml_gen.save_kml(kml_content, output_file)

            print(f"KML saved to: {output_file}")
            if len(positions) > 1:
                print(f"Route duration: {(positions[-1]['timestamp'] - positions[0]['timestamp'])/60:.1f} minutes")
            print()


if __name__ == "__main__":
    main()
