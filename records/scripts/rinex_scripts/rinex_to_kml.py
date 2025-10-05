#!/usr/bin/env python3
"""
RINEX to KML Converter
Converts RINEX observation files to KML format for visualization
"""

import os
import sys
import math
from datetime import datetime, timedelta
from collections import defaultdict


class RINEXParser:
    def __init__(self):
        self.constellation_map = {
            'G': 'GPS',
            'R': 'GLONASS',
            'E': 'Galileo',
            'C': 'BeiDou'
        }

    def parse_rinex_file(self, file_path):
        """Parse RINEX observation file and extract position data"""
        print(f"Parsing RINEX file: {file_path}")

        observations = []
        header_info = {}
        obs_types = {}
        approx_position = None

        with open(file_path, 'r') as f:
            lines = f.readlines()

        # Parse header
        header_end = 0
        for i, line in enumerate(lines):
            if 'END OF HEADER' in line:
                header_end = i + 1
                break

            if 'RINEX VERSION' in line:
                header_info['version'] = line[:9].strip()

            elif 'APPROX POSITION XYZ' in line:
                # Extract approximate position in ECEF coordinates
                parts = line[:60].split()
                if len(parts) >= 3:
                    try:
                        x = float(parts[0])
                        y = float(parts[1])
                        z = float(parts[2])
                        approx_position = self._ecef_to_lla(x, y, z)
                        header_info['approx_position'] = approx_position
                    except ValueError:
                        pass

            elif 'SYS / # / OBS TYPES' in line:
                constellation = line[0]
                obs_list = line[7:58].split()
                obs_types[constellation] = obs_list

        # Parse observation epochs
        i = header_end
        while i < len(lines):
            line = lines[i]

            if line.startswith('>'):
                parts = line.split()
                if len(parts) >= 7:
                    year = int(parts[1])
                    month = int(parts[2])
                    day = int(parts[3])
                    hour = int(parts[4])
                    minute = int(parts[5])
                    second = float(parts[6])
                    num_sats = int(parts[8]) if len(parts) > 8 else 0

                    epoch_time = datetime(year, month, day, hour, minute, int(second))

                    observations.append({
                        'timestamp': epoch_time,
                        'num_satellites': num_sats
                    })

                    i += num_sats  # Skip satellite observation lines

            i += 1

        return {
            'header': header_info,
            'observations': observations,
            'approx_position': approx_position
        }

    def _ecef_to_lla(self, x, y, z):
        """Convert ECEF (Earth-Centered Earth-Fixed) to Latitude/Longitude/Altitude"""
        # WGS84 ellipsoid parameters
        a = 6378137.0  # semi-major axis
        e2 = 0.00669437999014  # first eccentricity squared

        # Calculate longitude
        lon = math.atan2(y, x)

        # Calculate latitude iteratively
        p = math.sqrt(x**2 + y**2)
        lat = math.atan2(z, p * (1 - e2))

        for _ in range(5):  # iterate for accuracy
            N = a / math.sqrt(1 - e2 * math.sin(lat)**2)
            lat = math.atan2(z + e2 * N * math.sin(lat), p)

        # Calculate altitude
        N = a / math.sqrt(1 - e2 * math.sin(lat)**2)
        alt = p / math.cos(lat) - N

        # Convert to degrees
        lat_deg = math.degrees(lat)
        lon_deg = math.degrees(lon)

        return {
            'latitude': lat_deg,
            'longitude': lon_deg,
            'altitude': alt
        }


class KMLGenerator:
    def __init__(self):
        pass

    def create_kml(self, rinex_data, route_name):
        """Generate KML from RINEX data"""
        observations = rinex_data['observations']
        approx_pos = rinex_data['approx_position']

        if not observations:
            print("No observations found in RINEX file")
            return None

        # Build KML
        kml_content = []

        # Header
        kml_content.append('<?xml version="1.0" encoding="UTF-8"?>')
        kml_content.append('<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2" xmlns:kml="http://www.opengis.net/kml/2.2" xmlns:atom="http://www.w3.org/2005/Atom">')
        kml_content.append('<Document>')
        kml_content.append(f'<name>{route_name}</name>')

        # Styles
        kml_content.extend([
            '<Style id="rinex_point">',
            '<IconStyle>',
            '<color>ff0000ff</color>',
            '<scale>0.6</scale>',
            '<Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon>',
            '</IconStyle>',
            '</Style>',
            ''
        ])

        # Create placemarks from observations
        if approx_pos:
            lat = approx_pos['latitude']
            lon = approx_pos['longitude']
            alt = approx_pos.get('altitude', 0.0)

            # Add approximate position as main placemark
            kml_content.extend([
                '<Placemark>',
                f'<name>RINEX Station Position</name>',
                '<description>Approximate position from RINEX header</description>',
                '<styleUrl>#rinex_point</styleUrl>',
                '<Point>',
                '<altitudeMode>absolute</altitudeMode>',
                f'<coordinates>{lon:.8f},{lat:.8f},{alt:.2f}</coordinates>',
                '</Point>',
                '</Placemark>',
                ''
            ])

        # Add time-stamped observation points (using same position, different timestamps)
        start_time = observations[0]['timestamp'] if observations else datetime.now()

        for i, obs in enumerate(observations):
            if i % 10 == 0 and approx_pos:  # Sample every 10th observation to reduce size
                relative_time = int((obs['timestamp'] - start_time).total_seconds())

                kml_content.extend([
                    '<Placemark>',
                    ' <styleUrl>#rinex_point</styleUrl>',
                    '<Style>',
                    '<BalloonStyle>',
                    f'<text>Observation at {obs["timestamp"].strftime("%H:%M:%S")}, Satellites: {obs["num_satellites"]}</text>',
                    '</BalloonStyle>',
                    '</Style>',
                    '<TimeStamp>',
                    f'<when>{obs["timestamp"].isoformat()}Z</when>',
                    '</TimeStamp>',
                    '<Point>',
                    '<altitudeMode>absolute</altitudeMode>',
                    f'<coordinates>{approx_pos["longitude"]:.8f},{approx_pos["latitude"]:.8f},{approx_pos.get("altitude", 0):.2f}</coordinates>',
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

    def save_kml(self, kml_content, output_path):
        """Save KML content to file"""
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write(kml_content)


def main():
    """Main function to process RINEX files and generate KML"""

    parser = RINEXParser()
    kml_gen = KMLGenerator()

    # Check for command-line arguments
    if len(sys.argv) > 1:
        # Process specific file(s) provided as arguments
        for rinex_file in sys.argv[1:]:
            if not os.path.exists(rinex_file):
                print(f"Error: File not found: {rinex_file}")
                continue

            print(f"Processing {rinex_file}...")

            # Parse RINEX data
            rinex_data = parser.parse_rinex_file(rinex_file)

            if not rinex_data['observations']:
                print(f"No observations found in {rinex_file}")
                continue

            print(f"Found {len(rinex_data['observations'])} observation epochs")

            # Determine output path
            base_name = os.path.splitext(os.path.basename(rinex_file))[0]
            output_dir = os.path.join(os.path.dirname(rinex_file), 'output')
            os.makedirs(output_dir, exist_ok=True)
            output_file = os.path.join(output_dir, f"{base_name}_kml.kml")

            # Generate KML
            kml_content = kml_gen.create_kml(rinex_data, base_name)

            if kml_content:
                # Save KML
                kml_gen.save_kml(kml_content, output_file)
                print(f"KML saved to: {output_file}")

                if rinex_data['observations']:
                    duration = (rinex_data['observations'][-1]['timestamp'] -
                              rinex_data['observations'][0]['timestamp']).total_seconds() / 60
                    print(f"Observation duration: {duration:.1f} minutes")
            print()

    else:
        # Default behavior: process hardcoded routes
        script_dir = os.path.dirname(os.path.abspath(__file__))
        scripts_dir = os.path.dirname(script_dir)
        records_dir = os.path.dirname(scripts_dir)
        output_dir = os.path.join(records_dir, 'output', 'rinex')

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

            # Find .25o or other RINEX observation files
            rinex_files = [f for f in os.listdir(route_path)
                          if f.endswith('.25o') or f.endswith('o')]

            if not rinex_files:
                print(f"No RINEX files found in {route_folder}")
                continue

            rinex_file = os.path.join(route_path, rinex_files[0])
            print(f"Processing {rinex_file}...")

            # Parse RINEX data
            rinex_data = parser.parse_rinex_file(rinex_file)

            if not rinex_data['observations']:
                print(f"No observations found in {rinex_file}")
                continue

            print(f"Found {len(rinex_data['observations'])} observation epochs")

            # Generate KML
            kml_content = kml_gen.create_kml(rinex_data, route_name)

            if kml_content:
                # Save KML
                output_file = os.path.join(output_dir, f"{route_folder}_rinex.kml")
                kml_gen.save_kml(kml_content, output_file)

                print(f"KML saved to: {output_file}")

                if rinex_data['observations']:
                    duration = (rinex_data['observations'][-1]['timestamp'] -
                              rinex_data['observations'][0]['timestamp']).total_seconds() / 60
                    print(f"Observation duration: {duration:.1f} minutes")
            print()


if __name__ == "__main__":
    main()
