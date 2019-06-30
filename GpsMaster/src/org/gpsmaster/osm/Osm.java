package org.gpsmaster.osm;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.gpsmaster.gpxpanel.GPXFile;
import org.gpsmaster.gpxpanel.Track;
import org.gpsmaster.gpxpanel.Waypoint;
import org.gpsmaster.gpxpanel.WaypointGroup;
import org.gpsmaster.gpxpanel.WaypointGroup.WptGrpType;

import eu.fuegenstein.messagecenter.MessageCenter;
import eu.fuegenstein.messagecenter.MessagePanel;

import se.kodapan.osm.domain.Node;
import se.kodapan.osm.domain.OsmObject;
import se.kodapan.osm.domain.Relation;
import se.kodapan.osm.domain.RelationMembership;
import se.kodapan.osm.domain.Way;
import se.kodapan.osm.parser.xml.OsmXmlParserException;
import se.kodapan.osm.services.overpass.Overpass;
import se.kodapan.osm.services.overpass.OverpassException;
import se.kodapan.osm.services.overpass.OverpassUtils;


public class Osm {

	private Overpass overpass = null;
	private OverpassUtils overpassUtils = null;

	private MessagePanel osmPanel = null;
	private MessageCenter msg = null;
	// possible tag keys containing a meaningful name 
	private final String[] nameKeys = { "name", "alt_name", "ref", "operator" };
	
	
	
	/**
	 * 
	 */
	public Osm(MessageCenter msg) {
		this.msg = msg;		
	}
	

	public MessageCenter getMessageCenter() {
		return msg;
	}


	public void setMessageCenter(MessageCenter msg) {
		this.msg = msg;
	}


	/**
	 * Get a relation from OSM and it as 1..n tracks to {@link gpx} 
	 * @param gpx {@link GPXFile} to add relation data to
	 * @param id ID of the relation to download
	 * @throws OsmXmlParserException 
	 * @throws OverpassException 
	 */
	public void downloadRelation(long id, GPXFile gpx) {
		
		// TODO handle relations recursively 
		
		osmPanel = msg.infoOn("Retrieving data ...", new Cursor(Cursor.WAIT_CURSOR));
		if (gpx.getMetadata().getName().isEmpty()) {
			gpx.getMetadata().setName("OSM Download");
		}
		try {
			overpass = new Overpass();
			overpass.open();
			overpass.setUserAgent(this.getClass().getCanonicalName());
			overpassUtils = new OverpassUtils(overpass);
			Relation relation = overpassUtils.loadRelation(id);
			overpass.close();
			relationToGpx(relation, gpx);
			
		} catch (OverpassException e) {
			msg.error(e);
		} catch (OsmXmlParserException e) {
			msg.error(e);
		} catch (Exception e) {
			// msg.error(e);
			e.printStackTrace();
		}
				
		msg.infoOff(osmPanel);
	}
	
	/**
	 * Convert {@link Way} to GPX {@link WaypointGroup}
	 * @param way
	 * @param segment
	 */
	private void wayToSegment(Way way, WaypointGroup segment) {	
		for (Node node : way.getNodes()) {
			Waypoint wpt = new Waypoint(node.getLatitude(), node.getLongitude());
			segment.addWaypoint(wpt);
		}
	}
	
	/**
	 * Get the way that connects to {@link node}
	 * @param way
	 * @param node
	 * @param allWays
	 * @return
	 */
	private Way getNeighbour(Way way, Node node, List<Way> allWays) {
		
		for (Way currentWay : allWays) {
			if (currentWay.equals(way) == false) {
				if (currentWay.getFirst().equals(node) || currentWay.getLast().equals(node)) {
					return currentWay;
				}
			}			
		}
		return null;
	}
	/**
	 * continue a way by adding neighbouring ways
	 * @param way {@link Way} to extend
	 * @param linkNode {@link Node} to attach neighbouring {@link Way}s to. 
	 * Must be either first or last node of {@link current}  
	 * @param consumable List containing remaining ways. Ways attached to {@link current}
	 * will be removed 
	 */
	private void continueWay(Way way, Node linkNode, List<Way> consumable) {
		Way neighbour = getNeighbour(way, linkNode, consumable);
		while (neighbour != null) {
			if (way.getFirst().equals(linkNode)) {
				if (neighbour.getFirst().equals(linkNode)) {			
					neighbour.reverse();
				}
				neighbour.getNodes().remove(linkNode);
				way.addBefore(neighbour);
				linkNode = way.getFirst();
			} else if (way.getLast().equals(linkNode)) {
				if (neighbour.getLast().equals(linkNode)) {			
					neighbour.reverse();
				}
				neighbour.getNodes().remove(linkNode);
				way.addAfter(neighbour);				
				linkNode = way.getLast();
			}
			consumable.remove(neighbour);
			neighbour = getNeighbour(way, linkNode, consumable);
		}		
	}
	
	
	/**
	 * Convert all members of type {@link Way} of an OSM {@link Relation} 
	 * to a {@link Track} by properly sorting relation members.
	 * 
	 * @param relation
	 * @param gpx
	 */
	public void relationToGpx(Relation relation, GPXFile gpx) {
		List<Way> consumable = new ArrayList<Way>();
		// TODO DEBUG loadRelation() returns each way twice!!
		// stupid workaround - don't know why OverpassUtils return every object twice :-(
		List<Long> dupeCheck = new ArrayList<Long>(); 
		
		// First: check for sub-relations and handle them recursively
		for (RelationMembership member : relation.getMembers()) {			
			if (member.getObject().getClass().equals(Relation.class)) {
				Relation subRelation = (Relation) member.getObject();
				if (dupeCheck.contains(subRelation.getId()) == false) {
					try {
						osmPanel.setText(String.format("Downloading Subrelation %d", subRelation.getId()));
						subRelation = overpassUtils.loadRelation(subRelation.getId());
						subRelation.setLoaded(true);
						relationToGpx(subRelation, gpx);
						dupeCheck.add(subRelation.getId());
					} catch (OverpassException e) {
						msg.error(String.format("failed to download subrelation %d", subRelation.getId()), e);
					} catch (OsmXmlParserException e) {
						msg.error(String.format("failed to parse subrelation %d", subRelation.getId()), e);
					}
				}								
			}						
		}
		
		// Second: collect all ways of this relation			
		for (RelationMembership member : relation.getMembers()) {			 
			if (member.getObject().getClass().equals(Way.class)) {
				Way way = (Way) member.getObject();
				// Workaround: do not add duplicate ways
				if (consumable.contains(way) == false) {					
					consumable.add(way);
				}
			}			
		}

		if (consumable.size() > 0) {
			Track track = new Track(gpx.getColor());
			track.setName(getRelationName(relation));
			// TODO: check fields: getVersion(), getTimestamp (null)
			track.setDesc(String.format("OSM Relation (%d)", relation.getId()));
					
			// main loop
			while (consumable.size() > 0) {
				// Way result = new Way();
				Way current = consumable.get(0);
				consumable.remove(current);
				continueWay(current, current.getFirst(), consumable);
				int size = current.getNodes().size();					
				Node node = current.getNodes().get(size - 1);
				continueWay(current, node, consumable);
				
				WaypointGroup segment = new WaypointGroup(track.getColor(), WptGrpType.TRACKSEG);
				wayToSegment(current, segment);
				track.getTracksegs().add(segment);
			}
			if (track.getTracksegs().size() > 0) {
				gpx.getTracks().add(track);
			}
		}
	}
	
	/**
	 * 
	 * @param relation
	 * @return
	 */
	private String getRelationName(Relation relation) {
		String name = getName(relation);
		if (name.isEmpty()) {
			name = String.format("Relation %d", relation.getId());
		}
		return name;
	}
	
	/**
	 * 
	 * @param tagset
	 * @return
	 */
	private String getName(OsmObject osmObject) {
		
		String lang = "name:".concat(Locale.getDefault().getLanguage());
		Map<String, String> tagset = osmObject.getTags();
		if (tagset.containsKey(lang)) {
			return tagset.get(lang);
		}
		for (String key : nameKeys) {
			if (tagset.containsKey(key)) {
				return tagset.get(key);
			}
		}
		return "";
	}
	
}
