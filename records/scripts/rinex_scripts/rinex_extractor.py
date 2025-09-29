#!/usr/bin/env python3
"""
RINEX .25o Data Extractor
Extracts and analyzes data from RINEX observation files (.25o)
"""

import os
import re
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

        # RINEX observation types
        self.obs_types = {}
        self.header_info = {}
        self.observations = []

    def parse_rinex_file(self, file_path):
        """Parse RINEX .25o file and extract all data"""
        print(f"Parsing RINEX file: {file_path}")

        observations = []
        header_info = {}
        obs_types = {}

        with open(file_path, 'r') as f:
            lines = f.readlines()

        # Parse header
        header_end = 0
        for i, line in enumerate(lines):
            if 'END OF HEADER' in line:
                header_end = i + 1
                break

            # Extract header information
            if 'RINEX VERSION' in line:
                header_info['version'] = line[:9].strip()
                header_info['type'] = line[20:21].strip()

            elif 'PGM / RUN BY / DATE' in line:
                header_info['program'] = line[:20].strip()
                header_info['run_by'] = line[20:40].strip()
                header_info['date'] = line[40:60].strip()

            elif 'MARKER NAME' in line:
                header_info['marker_name'] = line[:60].strip()

            elif 'REC # / TYPE / VERS' in line:
                header_info['receiver_number'] = line[:20].strip()
                header_info['receiver_type'] = line[20:40].strip()
                header_info['receiver_version'] = line[40:60].strip()

            elif 'TIME OF FIRST OBS' in line:
                header_info['first_obs_time'] = line[:43].strip()

            elif 'SYS / # / OBS TYPES' in line:
                constellation = line[0]
                num_obs = int(line[3:6])
                obs_list = line[7:58].split()
                obs_types[constellation] = obs_list

            elif 'GLONASS SLOT / FRQ' in line:
                header_info['glonass_freq'] = line[:60].strip()

        # Parse observation data
        i = header_end
        while i < len(lines):
            line = lines[i]

            # Check for epoch header (starts with >)
            if line.startswith('>'):
                # Parse epoch header
                parts = line.split()
                if len(parts) >= 7:
                    year = int(parts[1])
                    month = int(parts[2])
                    day = int(parts[3])
                    hour = int(parts[4])
                    minute = int(parts[5])
                    second = float(parts[6])
                    epoch_flag = int(parts[7])
                    num_sats = int(parts[8]) if len(parts) > 8 else 0

                    epoch_time = datetime(year, month, day, hour, minute, int(second))
                    epoch_microseconds = int((second - int(second)) * 1000000)
                    epoch_time = epoch_time.replace(microsecond=epoch_microseconds)

                    # Read satellite observations for this epoch
                    epoch_obs = {
                        'timestamp': epoch_time,
                        'epoch_flag': epoch_flag,
                        'num_satellites': num_sats,
                        'satellites': {}
                    }

                    # Parse satellite data
                    i += 1
                    for sat_idx in range(num_sats):
                        if i < len(lines):
                            sat_line = lines[i]
                            if len(sat_line) >= 3:
                                constellation = sat_line[0]
                                sat_id = sat_line[1:3]

                                # Parse observations for this satellite
                                sat_obs = self._parse_satellite_observations(
                                    sat_line, constellation, obs_types
                                )

                                if sat_obs:
                                    epoch_obs['satellites'][f"{constellation}{sat_id}"] = sat_obs
                            i += 1
                        else:
                            break

                    observations.append(epoch_obs)
            else:
                i += 1

        return {
            'header': header_info,
            'observation_types': obs_types,
            'observations': observations
        }

    def _parse_satellite_observations(self, line, constellation, obs_types):
        """Parse individual satellite observation line"""
        if constellation not in obs_types:
            return None

        sat_id = line[1:3]
        obs_list = obs_types[constellation]

        # Parse observation values (each observation is 14 characters + 2 spaces)
        observations = {}
        pos = 3  # Start after satellite ID

        for obs_type in obs_list:
            if pos + 14 <= len(line):
                obs_str = line[pos:pos+14].strip()
                if obs_str and obs_str != '':
                    try:
                        value = float(obs_str)
                        observations[obs_type] = value
                    except ValueError:
                        observations[obs_type] = None
                else:
                    observations[obs_type] = None

                # Move to next observation (14 chars + 2 spaces, but sometimes packed)
                pos += 16
            else:
                observations[obs_type] = None

        return {
            'satellite_id': sat_id,
            'constellation': constellation,
            'constellation_name': self.constellation_map.get(constellation, 'Unknown'),
            'observations': observations
        }

    def analyze_rinex_data(self, rinex_data):
        """Analyze parsed RINEX data and generate statistics"""
        observations = rinex_data['observations']
        header = rinex_data['header']
        obs_types = rinex_data['observation_types']

        analysis = {
            'header_info': header,
            'observation_types': obs_types,
            'total_epochs': len(observations),
            'time_span': None,
            'constellations': {},
            'satellites': {},
            'signal_quality': {}
        }

        if not observations:
            return analysis

        # Time span analysis
        start_time = observations[0]['timestamp']
        end_time = observations[-1]['timestamp']
        duration = (end_time - start_time).total_seconds()

        analysis['time_span'] = {
            'start_time': start_time.strftime('%Y-%m-%d %H:%M:%S.%f'),
            'end_time': end_time.strftime('%Y-%m-%d %H:%M:%S.%f'),
            'duration_seconds': duration,
            'duration_minutes': duration / 60.0
        }

        # Constellation and satellite analysis
        constellation_counts = defaultdict(int)
        satellite_data = defaultdict(lambda: {
            'epochs': 0,
            'observations': defaultdict(list),
            'constellation': None,
            'first_seen': None,
            'last_seen': None
        })

        for epoch in observations:
            for sat_id, sat_data in epoch['satellites'].items():
                constellation = sat_data['constellation']
                constellation_counts[constellation] += 1

                satellite_data[sat_id]['epochs'] += 1
                satellite_data[sat_id]['constellation'] = constellation

                if satellite_data[sat_id]['first_seen'] is None:
                    satellite_data[sat_id]['first_seen'] = epoch['timestamp']
                satellite_data[sat_id]['last_seen'] = epoch['timestamp']

                # Collect observation values
                for obs_type, value in sat_data['observations'].items():
                    if value is not None:
                        satellite_data[sat_id]['observations'][obs_type].append(value)

        # Generate constellation statistics
        for constellation, count in constellation_counts.items():
            const_name = self.constellation_map.get(constellation, 'Unknown')
            sats_in_const = [sat for sat in satellite_data.keys() if sat.startswith(constellation)]

            analysis['constellations'][constellation] = {
                'name': const_name,
                'total_observations': count,
                'unique_satellites': len(sats_in_const),
                'satellite_list': sorted(sats_in_const)
            }

        # Generate satellite statistics
        for sat_id, sat_info in satellite_data.items():
            sat_stats = {
                'constellation': sat_info['constellation'],
                'constellation_name': self.constellation_map.get(sat_info['constellation'], 'Unknown'),
                'total_epochs': sat_info['epochs'],
                'tracking_duration': (sat_info['last_seen'] - sat_info['first_seen']).total_seconds(),
                'observation_stats': {}
            }

            # Calculate statistics for each observation type
            for obs_type, values in sat_info['observations'].items():
                if values:
                    sat_stats['observation_stats'][obs_type] = {
                        'count': len(values),
                        'mean': sum(values) / len(values),
                        'min': min(values),
                        'max': max(values),
                        'std': self._calculate_std(values)
                    }

            analysis['satellites'][sat_id] = sat_stats

        return analysis

    def _calculate_std(self, values):
        """Calculate standard deviation"""
        if len(values) < 2:
            return 0.0

        mean = sum(values) / len(values)
        variance = sum((x - mean) ** 2 for x in values) / (len(values) - 1)
        return math.sqrt(variance)

    def export_to_csv(self, analysis, output_file):
        """Export RINEX analysis to CSV"""
        with open(output_file, 'w', encoding='utf-8') as f:
            # Header information
            f.write("RINEX Analysis Report\n")
            f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("\n")

            # Header info
            f.write("HEADER INFORMATION\n")
            header = analysis['header_info']
            for key, value in header.items():
                f.write(f"{key},{value}\n")
            f.write("\n")

            # Time span
            f.write("TIME SPAN\n")
            time_info = analysis['time_span']
            if time_info:
                f.write(f"start_time,{time_info['start_time']}\n")
                f.write(f"end_time,{time_info['end_time']}\n")
                f.write(f"duration_seconds,{time_info['duration_seconds']:.1f}\n")
                f.write(f"duration_minutes,{time_info['duration_minutes']:.2f}\n")
            f.write("\n")

            # Constellation summary
            f.write("CONSTELLATION SUMMARY\n")
            f.write("constellation,name,total_observations,unique_satellites\n")
            for const_id, const_info in analysis['constellations'].items():
                f.write(f"{const_id},{const_info['name']},{const_info['total_observations']},{const_info['unique_satellites']}\n")
            f.write("\n")

            # Satellite details
            f.write("SATELLITE DETAILS\n")
            f.write("satellite_id,constellation,constellation_name,total_epochs,tracking_duration_sec\n")
            for sat_id, sat_info in analysis['satellites'].items():
                f.write(f"{sat_id},{sat_info['constellation']},{sat_info['constellation_name']},{sat_info['total_epochs']},{sat_info['tracking_duration']:.1f}\n")
            f.write("\n")

            # Signal quality statistics
            f.write("SIGNAL QUALITY STATISTICS\n")
            f.write("satellite_id,observation_type,count,mean,min,max,std\n")
            for sat_id, sat_info in analysis['satellites'].items():
                for obs_type, stats in sat_info['observation_stats'].items():
                    f.write(f"{sat_id},{obs_type},{stats['count']},{stats['mean']:.3f},{stats['min']:.3f},{stats['max']:.3f},{stats['std']:.3f}\n")

    def print_summary(self, analysis):
        """Print analysis summary to console"""
        print("\n" + "="*80)
        print("RINEX DATA ANALYSIS SUMMARY")
        print("="*80)

        # Header info
        header = analysis['header_info']
        print(f"Program: {header.get('program', 'Unknown')}")
        print(f"Receiver: {header.get('receiver_type', 'Unknown')}")
        print(f"Version: {header.get('receiver_version', 'Unknown')}")

        # Time information
        time_info = analysis.get('time_span')
        if time_info:
            print(f"\nTime span: {time_info['start_time']} to {time_info['end_time']}")
            print(f"Duration: {time_info['duration_minutes']:.2f} minutes ({time_info['duration_seconds']:.1f} seconds)")

        print(f"Total epochs: {analysis['total_epochs']}")

        # Constellation summary
        print(f"\nCONSTELLATIONS TRACKED:")
        for const_id, const_info in analysis['constellations'].items():
            print(f"  {const_info['name']} ({const_id}): {const_info['unique_satellites']} satellites, {const_info['total_observations']} observations")

        # Satellite summary
        print(f"\nSATELLITES TRACKED:")
        total_satellites = len(analysis['satellites'])
        print(f"Total unique satellites: {total_satellites}")

        # Show top satellites by observation count
        sorted_sats = sorted(analysis['satellites'].items(),
                           key=lambda x: x[1]['total_epochs'], reverse=True)

        print(f"\nTop 10 satellites by observation count:")
        for sat_id, sat_info in sorted_sats[:10]:
            print(f"  {sat_id} ({sat_info['constellation_name']}): {sat_info['total_epochs']} epochs, {sat_info['tracking_duration']:.1f}s tracking")

        print(f"\nObservation types per constellation:")
        obs_types = analysis['observation_types']
        for const_id, obs_list in obs_types.items():
            const_name = self.constellation_map.get(const_id, 'Unknown')
            print(f"  {const_name} ({const_id}): {', '.join(obs_list)}")


def main():
    """Extract data from both RINEX files"""

    parser = RINEXParser()

    # Define paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    scripts_dir = os.path.dirname(script_dir)  # Parent of rinex_scripts is scripts
    records_dir = os.path.dirname(scripts_dir)  # Parent of scripts is records
    output_dir = os.path.join(records_dir, 'output', 'rinex')

    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    routes = [
        ('direct_route', 'Direct Route'),
        ('rectangle_route', 'Rectangle Route')
    ]

    for route_folder, route_name in routes:
        route_path = os.path.join(records_dir, route_folder)

        # Find .25o file
        rinex_files = [f for f in os.listdir(route_path) if f.endswith('.25o')]

        if not rinex_files:
            print(f"No .25o files found in {route_folder}")
            continue

        rinex_file = os.path.join(route_path, rinex_files[0])
        print(f"\n{'='*60}")
        print(f"Processing {route_name}")
        print(f"RINEX file: {rinex_file}")

        # Parse RINEX file
        rinex_data = parser.parse_rinex_file(rinex_file)

        # Analyze data
        analysis = parser.analyze_rinex_data(rinex_data)

        # Print summary
        parser.print_summary(analysis)

        # Export to CSV
        csv_output = os.path.join(output_dir, f"{route_folder}_rinex_analysis.csv")
        parser.export_to_csv(analysis, csv_output)
        print(f"\nRINEX analysis exported to: {csv_output}")

        print(f"\n{'='*60}")


if __name__ == "__main__":
    main()