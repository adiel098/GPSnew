import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import sys

def create_fig19_los_nlos_misclassification_chart():
    """
    Create Fig 19: The average error as a function of p - the error percentage of LOS/NLOS misclassifications
    Matches the research paper figure exactly
    """
    
    # File paths
    data_file = os.path.join('data', 'los_nlos_misclassification_data.csv')
    output_file = os.path.join('output', 'fig19_los_nlos_misclassification.png')
    
    # Check if data file exists
    if not os.path.exists(data_file):
        print(f"Error: Data file {data_file} not found!")
        print("Please run the Java batch runner with 'los-nlos' argument first to generate the data.")
        return False
    
    # Read the CSV data
    try:
        df = pd.read_csv(data_file)
        print(f"Loaded {len(df)} data points from {data_file}")
    except Exception as e:
        print(f"Error reading CSV file: {e}")
        return False
    
    # Check if we have misclassification_percentage column
    if 'misclassification_percentage' not in df.columns:
        print("Error: The data file doesn't contain misclassification_percentage column.")
        print("Please regenerate the data using the los-nlos runner.")
        return False
    
    # Create the plot with exact styling from the research paper
    plt.figure(figsize=(10, 7))
    
    # Define colors for each misclassification percentage (matching the research paper)
    colors = {
        0.0: '#1f77b4',   # Blue for p=0%
        10.0: '#2ca02c',  # Green for p=10%  
        20.0: '#d62728',  # Red for p=20%
        45.0: '#17becf'   # Cyan for p=45%
    }
    
    # Plot each misclassification percentage
    misclassification_percentages = [0.0, 10.0, 20.0, 45.0]
    
    for p_value in misclassification_percentages:
        # Filter data for this misclassification percentage
        p_data = df[df['misclassification_percentage'] == p_value]
        
        if len(p_data) == 0:
            print(f"Warning: No data found for p={p_value}%")
            continue
            
        # Convert timestamps to seconds relative to start time
        time_seconds = (p_data['timestamp'].values - p_data['timestamp'].iloc[0]) / 1000.0
        error_meters = p_data['average_error'].values
        
        # Create label
        if p_value == 0.0:
            label = f'p=0%'
        else:
            label = f'p={int(p_value)}%'
        
        # Plot the line
        plt.plot(time_seconds, error_meters, 
                color=colors[p_value], 
                linewidth=2, 
                label=label)
        
        print(f"Plotted {len(p_data)} points for p={p_value}%")
    
    # Calculate the maximum time across all data for consistent axis scaling
    max_time = 0
    for p_value in misclassification_percentages:
        p_data = df[df['misclassification_percentage'] == p_value]
        if len(p_data) > 0:
            time_seconds = (p_data['timestamp'].values - p_data['timestamp'].iloc[0]) / 1000.0
            max_time = max(max_time, time_seconds.max())
    
    # Set up the plot to match the research paper with auto-scaled time axis
    plt.xlim(0, max_time + 5)  # Add small buffer
    plt.ylim(0, 50)
    plt.xlabel('Time from initialization [s]', fontsize=12)
    plt.ylabel('Average Error [m]', fontsize=12)
    plt.title('LOS/NLOS mis-classifications – 2500 Particles', fontsize=14, fontweight='bold')
    
    # Add grid (light gray)
    plt.grid(True, alpha=0.3, color='gray', linestyle='-', linewidth=0.5)
    
    # Add legend in upper right
    plt.legend(loc='upper right', fontsize=11)
    
    # Set tick parameters
    plt.tick_params(axis='both', which='major', labelsize=10)
    
    # Tight layout to prevent label cutoff
    plt.tight_layout()
    
    # Create output directory if it doesn't exist
    os.makedirs('output', exist_ok=True)
    
    # Save the plot
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Chart saved as {output_file}")
    
    # Show the plot
    plt.show()
    
    return True

def print_data_summary():
    """Print summary statistics of the data"""
    data_file = os.path.join('data', 'los_nlos_misclassification_data.csv')
    
    if not os.path.exists(data_file):
        print("No data file found for summary")
        return
    
    df = pd.read_csv(data_file)
    
    print("\n=== Data Summary ===")
    print(f"Total data points: {len(df)}")
    print(f"Particle count: {df['particle_count'].iloc[0]}")
    
    if 'misclassification_percentage' in df.columns:
        print(f"Misclassification percentages: {sorted(df['misclassification_percentage'].unique())}")
    
    print(f"Time range: {df['time_step'].min()} to {df['time_step'].max()} steps")
    print(f"Error range: {df['average_error'].min():.2f} to {df['average_error'].max():.2f} meters")
    
    print("\nAverage error by misclassification percentage:")
    if 'misclassification_percentage' in df.columns:
        for p_value in sorted(df['misclassification_percentage'].unique()):
            p_data = df[df['misclassification_percentage'] == p_value]
            avg_error = p_data['average_error'].mean()
            final_error = p_data['average_error'].iloc[-1]
            print(f"  p={p_value:4.1f}%: Avg = {avg_error:5.2f}m, Final = {final_error:5.2f}m")

def print_misclassification_analysis():
    """Print analysis showing the impact of misclassification errors"""
    data_file = os.path.join('data', 'los_nlos_misclassification_data.csv')
    
    if not os.path.exists(data_file):
        return
    
    df = pd.read_csv(data_file)
    
    if 'misclassification_percentage' not in df.columns:
        return
    
    print("\n=== Misclassification Error Impact Analysis ===")
    
    # Get baseline (p=0%) performance
    baseline_data = df[df['misclassification_percentage'] == 0.0]
    if len(baseline_data) == 0:
        print("No baseline (p=0%) data found for comparison")
        return
    
    baseline_avg = baseline_data['average_error'].mean()
    
    for p_value in sorted(df['misclassification_percentage'].unique()):
        if p_value == 0.0:
            continue
            
        p_data = df[df['misclassification_percentage'] == p_value]
        
        if len(p_data) > 0:
            p_avg = p_data['average_error'].mean()
            error_increase = ((p_avg - baseline_avg) / baseline_avg) * 100
            
            print(f"  p={p_value:4.1f}%:")
            print(f"    Average error:    {p_avg:6.2f}m")
            print(f"    Error increase:   {error_increase:6.1f}% vs baseline")
            print(f"    Impact severity:  {get_impact_severity(error_increase)}")

def get_impact_severity(error_increase):
    """Categorize the severity of error increase"""
    if error_increase < 10:
        return "Minimal"
    elif error_increase < 25:
        return "Moderate"
    elif error_increase < 50:
        return "Significant"
    else:
        return "Severe"

if __name__ == "__main__":
    print("=== Fig 19 LOS/NLOS Misclassification Chart Generator ===")
    
    # Print data summary first
    print_data_summary()
    
    # Print misclassification analysis
    print_misclassification_analysis()
    
    # Generate the chart
    success = create_fig19_los_nlos_misclassification_chart()
    
    if success:
        print("\nChart generation completed successfully!")
        print("The chart shows how LOS/NLOS misclassification errors affect particle filter accuracy.")
        print("Higher misclassification percentages (p) lead to increased positioning errors.")
        print("This demonstrates the importance of accurate LOS/NLOS classification in urban environments.")
    else:
        print("\nChart generation failed!")
        print("Please check the error messages above and ensure the data file exists.")
        sys.exit(1)