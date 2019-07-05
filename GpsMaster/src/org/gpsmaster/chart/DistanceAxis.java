package org.gpsmaster.chart;

import org.gpsmaster.UnitConverter;
import org.gpsmaster.UnitConverter.UNIT;
import org.gpsmaster.gpxpanel.Waypoint;
import org.jfree.chart.axis.NumberAxis;


/**
 * Class representing
 * @author rfu
 *
 */
public class DistanceAxis extends ChartXAxis {

	private double distance = 0.0f;
	private Waypoint prev = null;

	/**
	 * Default constructor
	 */
	public DistanceAxis(UnitConverter uc) {
		super(uc);
		title = "Distance";
		iconFile = "axis_distance.png";
		valueAxis = new NumberAxis();
		valueAxis.setLabel(uc.getUnit(UNIT.KM));
		super.setDefaults();
		// distance axis needs caching
		enableCaching();
	}


	/**
	 * needs to be called with consecutive waypoints
	 * @param wpt
	 * @return distance to specified waypoint
	 */
	public double getValue(Waypoint wpt) {
		if (prev != null) {
			distance += wpt.getDistance(prev);
		}
		prev = wpt;
		double value = uc.dist(distance, UNIT.KM);
		cache(wpt, value);
		return value;
	}

	/**
	 *
	 */
	public void reset() {
		distance = 0.0f;
		prev = null;
	}


}
