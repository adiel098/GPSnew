#!/usr/bin/env python3
"""
Verification script to compare NMEA source data with KML output
"""

def nmea_to_decimal(coord_str, direction):
    """Convert NMEA coordinate to decimal degrees"""
    if '.' in coord_str:
        decimal_pos = coord_str.find('.')
        if decimal_pos >= 5:  # longitude
            degrees = int(coord_str[:3])
            minutes = float(coord_str[3:])
        else:  # latitude
            degrees = int(coord_str[:2])
            minutes = float(coord_str[2:])

        decimal = degrees + (minutes / 60.0)

        if direction in ['S', 'W']:
            decimal = -decimal

        return decimal
    return 0.0

# Test data from NMEA file
test_cases = [
    # (point_number, NMEA_line, expected_time, expected_lat, expected_lon)
    (1, "NMEA,$GPGGA,131911.000,3154.0051,N,03500.4211,E,1,23,0.6,268.6,M,16.6,M,,*5D,1759151951000", 0, None, None),
    (3, "NMEA,$GPGGA,131913.000,3154.0049,N,03500.4209,E,1,25,0.6,265.9,M,16.6,M,,*5B,1759151953000", 2, None, None),
    (50, "NMEA,$GPGGA,132000.000,3154.0114,N,03500.4121,E,1,28,0.4,238.6,M,16.6,M,,*5B,1759152000000", 49, None, None),
    (109, "NMEA,$GPGGA,132059.000,3153.9984,N,03500.4132,E,1,21,0.6,222.0,M,16.6,M,,*5C,1759152059000", 108, None, None),
]

# Expected KML coordinates
kml_coords = [
    (0, 35.00701833333333, 31.90008500000000),
    (2, 35.00701500000000, 31.90008166666667),
    (49, 35.00686833333334, 31.90019000000000),
    (108, 35.00688666666667, 31.89997333333333),
]

print("NMEA to KML Verification Test")
print("=" * 80)
print()

for i, (point_num, nmea_line, expected_time, _, _) in enumerate(test_cases):
    parts = nmea_line.split(',')

    lat_str = parts[3]
    lat_dir = parts[4]
    lon_str = parts[5]
    lon_dir = parts[6]
    timestamp_ms = int(parts[-1])

    # Convert coordinates
    lat = nmea_to_decimal(lat_str, lat_dir)
    lon = nmea_to_decimal(lon_str, lon_dir)

    # Get corresponding KML coords
    kml_time, kml_lon, kml_lat = kml_coords[i]

    # Calculate timestamp relative to first point
    first_timestamp = 1759151951000
    relative_time = int((timestamp_ms - first_timestamp) / 1000)

    print(f"Point {point_num}:")
    print(f"  NMEA: lat={lat:.14f}, lon={lon:.14f}")
    print(f"  KML:  lat={kml_lat:.14f}, lon={kml_lon:.14f}")
    match = 'YES' if abs(lat - kml_lat) < 0.000001 and abs(lon - kml_lon) < 0.000001 else 'NO'
    time_match = 'YES' if relative_time == kml_time else 'NO'
    print(f"  Match: {match}")
    print(f"  Time: NMEA={relative_time}s, KML={kml_time}s, Match={time_match}")
    print()

print("=" * 80)
print("CONCLUSION:")
print("If all points show 'YES', the KML accurately represents the NMEA route.")
