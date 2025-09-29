#!/usr/bin/env python3
"""
GNSS Comprehensive Analyzer
Combined raw GNSS data analyzer and report generator.
Parses raw GNSS measurements from phone logs and generates detailed analysis and CSV exports.
"""

import os
import math
import csv
from datetime import datetime, timezone
from collections import defaultdict
from xml.etree.ElementTree import Element, SubElement, tostring, indent


class GNSSConstants:
    """GNSS constellation and frequency constants"""
    GPS_L1_FREQ = 1575420000  # Hz
    GLONASS_L1_FREQ = 1602000000  # Hz
    GALILEO_E1_FREQ = 1575420000  # Hz
    BEIDOU_B1_FREQ = 1561098000  # Hz

    CONSTELLATION_NAMES = {
        1: 'GPS',
        3: 'GLONASS',
        6: 'GALILEO',
        5: 'BEIDOU'
    }

    LIGHT_SPEED = 299792458  # m/s


class RawGNSSParser:
    def __init__(self):
        self.raw_measurements = []
        self.sensor_data = defaultdict(list)
        self.timestamps = set()

    def parse_raw_file(self, file_path):
        """Parse raw GNSS log file"""
        raw_data = []
        sensor_data = defaultdict(list)

        with open(file_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue

                if line.startswith('Raw,'):
                    raw_entry = self._parse_raw_line(line)
                    if raw_entry:
                        raw_data.append(raw_entry)

                elif line.startswith('UncalAccel,') or line.startswith('Accel,') or \
                     line.startswith('UncalGyro,') or line.startswith('Gyro,') or \
                     line.startswith('UncalMag,') or line.startswith('Mag,'):
                    sensor_entry = self._parse_sensor_line(line)
                    if sensor_entry:
                        sensor_type = sensor_entry['type']
                        sensor_data[sensor_type].append(sensor_entry)

        return raw_data, sensor_data

    def _parse_raw_line(self, line):
        """Parse a raw GNSS measurement line"""
        try:
            parts = line.split(',')
            if len(parts) < 20:
                return None

            # Extract key measurements
            measurement = {
                'utc_time_millis': int(parts[1]) if parts[1] else None,
                'time_nanos': int(parts[2]) if parts[2] else None,
                'leap_second': int(parts[3]) if parts[3] else None,
                'full_bias_nanos': int(parts[5]) if parts[5] else None,
                'bias_nanos': float(parts[6]) if parts[6] else None,
                'svid': int(parts[11]) if parts[11] else None,
                'time_offset_nanos': float(parts[12]) if parts[12] else None,
                'state': int(parts[13]) if parts[13] else None,
                'received_sv_time_nanos': int(parts[14]) if parts[14] else None,
                'cn0_dbhz': float(parts[16]) if parts[16] else None,
                'pseudorange_rate_mps': float(parts[17]) if parts[17] else None,
                'accumulated_delta_range_meters': float(parts[20]) if parts[20] else None,
                'carrier_frequency_hz': float(parts[22]) if parts[22] else None,
                'constellation_type': int(parts[28]) if parts[28] else None,
                'multipath_indicator': int(parts[26]) if parts[26] else None,
            }

            return measurement
        except (ValueError, IndexError):
            return None

    def _parse_sensor_line(self, line):
        """Parse sensor measurement line"""
        try:
            parts = line.split(',')
            sensor_type = parts[0]

            base_entry = {
                'type': sensor_type,
                'utc_time_millis': int(parts[1]) if parts[1] else None,
                'elapsed_realtime_nanos': int(parts[2]) if parts[2] else None,
            }

            if sensor_type in ['UncalAccel', 'Accel']:
                if len(parts) >= 6:
                    base_entry.update({
                        'x': float(parts[3]) if parts[3] else None,
                        'y': float(parts[4]) if parts[4] else None,
                        'z': float(parts[5]) if parts[5] else None,
                    })
                    if sensor_type == 'UncalAccel' and len(parts) >= 9:
                        base_entry.update({
                            'bias_x': float(parts[6]) if parts[6] else None,
                            'bias_y': float(parts[7]) if parts[7] else None,
                            'bias_z': float(parts[8]) if parts[8] else None,
                        })

            elif sensor_type in ['UncalGyro', 'Gyro']:
                if len(parts) >= 6:
                    base_entry.update({
                        'x': float(parts[3]) if parts[3] else None,
                        'y': float(parts[4]) if parts[4] else None,
                        'z': float(parts[5]) if parts[5] else None,
                    })
                    if sensor_type == 'UncalGyro' and len(parts) >= 9:
                        base_entry.update({
                            'drift_x': float(parts[6]) if parts[6] else None,
                            'drift_y': float(parts[7]) if parts[7] else None,
                            'drift_z': float(parts[8]) if parts[8] else None,
                        })

            elif sensor_type in ['UncalMag', 'Mag']:
                if len(parts) >= 6:
                    base_entry.update({
                        'x': float(parts[3]) if parts[3] else None,
                        'y': float(parts[4]) if parts[4] else None,
                        'z': float(parts[5]) if parts[5] else None,
                    })
                    if sensor_type == 'UncalMag' and len(parts) >= 9:
                        base_entry.update({
                            'bias_x': float(parts[6]) if parts[6] else None,
                            'bias_y': float(parts[7]) if parts[7] else None,
                            'bias_z': float(parts[8]) if parts[8] else None,
                        })

            return base_entry

        except (ValueError, IndexError):
            return None


class GNSSAnalyzer:
    def __init__(self):
        self.constants = GNSSConstants()

    def analyze_satellites(self, raw_data):
        """Analyze satellite tracking and signal quality"""
        satellite_stats = defaultdict(lambda: {
            'measurements': [],
            'constellation': None,
            'first_seen': None,
            'last_seen': None,
            'cn0_values': [],
            'pseudorange_rates': []
        })

        for measurement in raw_data:
            if not measurement or not measurement.get('svid'):
                continue

            svid = measurement['svid']
            constellation = measurement.get('constellation_type')
            cn0 = measurement.get('cn0_dbhz')
            pr_rate = measurement.get('pseudorange_rate_mps')
            timestamp = measurement.get('utc_time_millis')

            # Store measurement
            satellite_stats[svid]['measurements'].append(measurement)
            satellite_stats[svid]['constellation'] = constellation

            # Track timing
            if timestamp:
                if satellite_stats[svid]['first_seen'] is None:
                    satellite_stats[svid]['first_seen'] = timestamp
                satellite_stats[svid]['last_seen'] = timestamp

            # Collect signal quality data
            if cn0:
                satellite_stats[svid]['cn0_values'].append(cn0)
            if pr_rate:
                satellite_stats[svid]['pseudorange_rates'].append(pr_rate)

        # Calculate statistics
        for svid, stats in satellite_stats.items():
            if stats['cn0_values']:
                stats['avg_cn0'] = sum(stats['cn0_values']) / len(stats['cn0_values'])
                stats['max_cn0'] = max(stats['cn0_values'])
                stats['min_cn0'] = min(stats['cn0_values'])
            else:
                stats['avg_cn0'] = 0
                stats['max_cn0'] = 0
                stats['min_cn0'] = 0

            stats['tracking_duration'] = (
                (stats['last_seen'] - stats['first_seen']) / 1000.0
                if stats['last_seen'] and stats['first_seen']
                else 0
            )

        return satellite_stats

    def calculate_pseudorange(self, measurement):
        """Calculate pseudorange from raw measurement"""
        if not measurement.get('received_sv_time_nanos') or \
           not measurement.get('time_nanos') or \
           not measurement.get('full_bias_nanos'):
            return None

        tx_time_nanos = measurement['received_sv_time_nanos']
        rx_time_nanos = measurement['time_nanos'] - measurement['full_bias_nanos']
        travel_time_seconds = (rx_time_nanos - tx_time_nanos) * 1e-9
        pseudorange = travel_time_seconds * self.constants.LIGHT_SPEED

        return pseudorange

    def estimate_position_weighted_least_squares(self, satellite_data, known_positions=None):
        """Estimate receiver position using weighted least squares"""
        # This is a placeholder for full positioning algorithm
        # Real implementation would require:
        # 1. Satellite ephemeris data for satellite positions
        # 2. Correction for atmospheric delays
        # 3. Iterative least squares solving

        return {
            'method': 'weighted_least_squares',
            'estimated_lat': None,  # Would calculate from satellite positions
            'estimated_lon': None,  # Would calculate from satellite positions
            'estimated_alt': None,  # Would calculate from satellite positions
            'pdop': None,  # Position dilution of precision
            'used_satellites': len(satellite_data) if satellite_data else 0,
            'note': 'Requires satellite ephemeris data for full implementation'
        }


class GNSSReportGenerator:
    def __init__(self):
        self.parser = RawGNSSParser()
        self.analyzer = GNSSAnalyzer()
        self.constants = GNSSConstants()

    def generate_comprehensive_report(self, route_folder):
        """Generate comprehensive GNSS analysis report"""
        print(f"\\n{'='*60}")
        print(f"GNSS ANALYSIS REPORT - {route_folder.upper()}")
        print(f"{'='*60}")

        # Find raw GNSS file
        txt_files = [f for f in os.listdir(route_folder) if f.endswith('.txt')]

        if not txt_files:
            print("No .txt files found for analysis")
            return None

        txt_file = os.path.join(route_folder, txt_files[0])
        print(f"Analyzing: {txt_file}")

        # Parse raw data
        raw_data, sensor_data = self.parser.parse_raw_file(txt_file)

        if not raw_data:
            print("No raw GNSS measurements found")
            return None

        # Analyze satellites
        satellite_stats = self.analyzer.analyze_satellites(raw_data)

        # Generate report
        report = {
            'route_folder': route_folder,
            'data_file': txt_file,
            'total_measurements': len(raw_data),
            'satellite_count': len(satellite_stats),
            'satellite_stats': satellite_stats,
            'sensor_data': sensor_data,
            'analysis_timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        }

        return report

    def print_report(self, report):
        """Print report to console"""
        if not report:
            return

        print(f"\\nDATA SUMMARY:")
        print(f"  Total raw measurements: {report['total_measurements']:,}")
        print(f"  Unique satellites tracked: {report['satellite_count']}")

        # Constellation breakdown
        constellations = defaultdict(int)
        for svid, stats in report['satellite_stats'].items():
            const_type = stats['constellation']
            const_name = self.constants.CONSTELLATION_NAMES.get(const_type, f'Unknown_{const_type}')
            constellations[const_name] += 1

        print(f"\\nCONSTELLATION BREAKDOWN:")
        for const_name, count in constellations.items():
            print(f"  {const_name}: {count} satellites")

        # Signal quality analysis
        all_cn0_values = []
        for stats in report['satellite_stats'].values():
            if stats['avg_cn0'] > 0:
                all_cn0_values.append(stats['avg_cn0'])

        if all_cn0_values:
            avg_signal_quality = sum(all_cn0_values) / len(all_cn0_values)
            print(f"\\nSIGNAL QUALITY:")
            print(f"  Average C/N0: {avg_signal_quality:.1f} dB-Hz")
            print(f"  Max C/N0: {max(all_cn0_values):.1f} dB-Hz")
            print(f"  Min C/N0: {min(all_cn0_values):.1f} dB-Hz")

        # Top satellites by signal quality
        top_satellites = sorted(
            [(svid, stats) for svid, stats in report['satellite_stats'].items()],
            key=lambda x: x[1]['avg_cn0'], reverse=True
        )[:5]

        print(f"\\nTOP 5 SATELLITES BY SIGNAL QUALITY:")
        for svid, stats in top_satellites:
            const_name = self.constants.CONSTELLATION_NAMES.get(stats['constellation'], 'Unknown')
            print(f"  {const_name} {svid}: {stats['avg_cn0']:.1f} dB-Hz "
                  f"({len(stats['measurements'])} measurements)")

        # Sensor data summary
        if report['sensor_data']:
            print(f"\\nSENSOR DATA AVAILABLE:")
            for sensor_type, data_list in report['sensor_data'].items():
                print(f"  {sensor_type}: {len(data_list):,} readings")

    def export_to_csv(self, raw_data, satellite_stats, output_file):
        """Export detailed analysis to CSV"""
        with open(output_file, 'w', newline='', encoding='utf-8') as csvfile:
            writer = csv.writer(csvfile)

            # Header
            writer.writerow([
                'timestamp_millis', 'svid', 'constellation_type', 'constellation_name',
                'cn0_dbhz', 'pseudorange_rate_mps', 'accumulated_delta_range_m',
                'carrier_frequency_hz', 'state', 'multipath_indicator'
            ])

            # Data rows
            for measurement in raw_data:
                if not measurement.get('svid'):
                    continue

                constellation_name = self.constants.CONSTELLATION_NAMES.get(
                    measurement.get('constellation_type'), 'Unknown'
                )

                writer.writerow([
                    measurement.get('utc_time_millis'),
                    measurement.get('svid'),
                    measurement.get('constellation_type'),
                    constellation_name,
                    measurement.get('cn0_dbhz'),
                    measurement.get('pseudorange_rate_mps'),
                    measurement.get('accumulated_delta_range_meters'),
                    measurement.get('carrier_frequency_hz'),
                    measurement.get('state'),
                    measurement.get('multipath_indicator')
                ])

    def export_satellite_summary_csv(self, satellite_stats, output_file):
        """Export satellite summary to CSV"""
        with open(output_file, 'w', newline='', encoding='utf-8') as csvfile:
            writer = csv.writer(csvfile)

            # Header
            writer.writerow([
                'svid', 'constellation_type', 'constellation_name',
                'total_measurements', 'tracking_duration_sec',
                'avg_cn0_dbhz', 'max_cn0_dbhz', 'min_cn0_dbhz'
            ])

            # Data rows
            for svid, stats in satellite_stats.items():
                constellation_name = self.constants.CONSTELLATION_NAMES.get(
                    stats['constellation'], 'Unknown'
                )

                writer.writerow([
                    svid,
                    stats['constellation'],
                    constellation_name,
                    len(stats['measurements']),
                    stats['tracking_duration'],
                    stats['avg_cn0'],
                    stats['max_cn0'],
                    stats['min_cn0']
                ])


def main():
    """Generate comprehensive GNSS analysis for both routes"""

    generator = GNSSReportGenerator()

    # Define paths
    script_dir = os.path.dirname(os.path.abspath(__file__))
    scripts_dir = os.path.dirname(script_dir)  # Parent of nmea_scripts is scripts
    records_dir = os.path.dirname(scripts_dir)  # Parent of scripts is records
    output_dir = os.path.join(records_dir, 'output', 'nmea')

    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    routes = ['direct_route', 'rectangle_route']

    for route in routes:
        route_path = os.path.join(records_dir, route)

        if not os.path.exists(route_path):
            print(f"Route folder not found: {route_path}")
            continue

        # Generate report
        report = generator.generate_comprehensive_report(route_path)

        if not report:
            continue

        # Print report to console
        generator.print_report(report)

        # Export CSVs
        txt_files = [f for f in os.listdir(route_path) if f.endswith('.txt')]
        txt_file = os.path.join(route_path, txt_files[0])

        raw_data, sensor_data = generator.parser.parse_raw_file(txt_file)
        satellite_stats = generator.analyzer.analyze_satellites(raw_data)

        # Export detailed CSV
        csv_output = os.path.join(output_dir, f"{route}_detailed.csv")
        generator.export_to_csv(raw_data, satellite_stats, csv_output)
        print(f"CSV EXPORTED: {csv_output}")

        # Export satellite summary CSV
        sat_csv_output = os.path.join(output_dir, f"{route}_satellites.csv")
        generator.export_satellite_summary_csv(satellite_stats, sat_csv_output)
        print(f"SATELLITE CSV EXPORTED: {sat_csv_output}")

        print()


if __name__ == "__main__":
    main()