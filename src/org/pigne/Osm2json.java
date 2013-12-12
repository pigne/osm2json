/**
 * 
 * Copyright (c) 2012 University of Le Havre
 * 
 * @file Osm2json.java
 * @date Sep 10, 2012
 * 
 * @author Yoann Pigné
 * 
 */
package org.pigne;

import java.awt.geom.Point2D;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONValue;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.ProjectionFactory;

/**
 * 
 */
public class Osm2json {

	String osm;
	String json;
	
	Double pixelWidth=800.0;
	Double pixelHeight=500.0;
	
	HashMap<String, Point2D.Double> nodes;
	boolean firstRun = true;
	static String projParameter = "+proj=utm +zone=31 +ellps=WGS84 +datum=WGS84 +units=m +no_defs";
	Projection proj;
	Map<String, Object> currentWay = null;
	private List<Object> ways;
	double min_x = Double.MAX_VALUE, max_x = Double.MIN_VALUE,
			min_y = Double.MAX_VALUE, max_y = Double.MIN_VALUE;

	class Way {
		String id;
		boolean building = true;
		boolean wall = true;
		String amenity;
		String source;
		String name;
		ArrayList<String> nodeIds;
		public Way() {
			nodeIds = new ArrayList<String>();
		}
	}
	/**
	 * 
	 */
	public class Osm2jsonHandler extends DefaultHandler {

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {

			if (firstRun) {
				if (qName.equals("node")) {
					String id = attributes.getValue("id");
					double lon = Double.parseDouble(attributes.getValue("lon"));
					double lat = Double.parseDouble(attributes.getValue("lat"));
					Point2D.Double source = new Point2D.Double(
							Double.parseDouble(attributes.getValue("lon")),
							Double.parseDouble(attributes.getValue("lat")));
					Point2D.Double dest = new Point2D.Double();
					//proj.transform(lon, lat, dest);
					proj.transform(source, dest);
					

					nodes.put(id, dest);
				}
			} else {
				if (qName.equals("way")) {
					
					currentWay = new HashMap<String, Object>();
					currentWay.put("_id", attributes.getValue("id"));
				} else if (currentWay!= null && qName.equals("tag")) {
					String k = attributes.getValue("k");
					if (k.equals("building")) {
						currentWay.put("building", new Boolean(true));
					} else if (k.equals("wall")) {
						currentWay= null;
					} else if (k.equals("amenity")) {
						currentWay.put("amenity", attributes.getValue("v"));
					} else if (k.equals("name")) {
						currentWay.put("name", attributes.getValue("v"));
					} else if (k.equals("highway")) {
						currentWay.put("highway", attributes.getValue("v"));
						currentWay.put("building", new Boolean(false));
					} else if (k.equals("shop")) {
						currentWay.put("shop", attributes.getValue("v"));
						currentWay.put("building", new Boolean(false));
					} else if (k.equals("natural")) {
						assert (currentWay != null);
						currentWay.put("natural", attributes.getValue("v"));
						currentWay.put("building", new Boolean(false));
					}
				} else if (qName.equals("nd")) {
					if (currentWay != null) {
						String nodeId = attributes.getValue("ref");
						List<Map<String, Double>> nodesList = (List<Map<String, Double>>) currentWay.get("nodes");
						if (nodesList == null) {
							nodesList = new ArrayList<Map<String, Double>>();
							currentWay.put("nodes", nodesList);
						}
						Map<String, Double> xy = new HashMap<String, Double>(4);
						xy.put("x", nodes.get(nodeId).x);
						xy.put("y", nodes.get(nodeId).y);
						nodesList.add(xy);
						//System.out.println(xy);
					}
				}
			}
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String,
		 * java.lang.String, java.lang.String)
		 */
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (qName.equals("way")) {
				if (currentWay != null ) {
					ways.add(currentWay);
					System.out.println(currentWay.get("natural") + " - "+ currentWay.get("highway")+ " - "+ currentWay.get("building"));
					currentWay = null;
				}
			}
		}
		/*
		 * (non-Javadoc)
		 * 
		 * @see org.xml.sax.helpers.DefaultHandler#endDocument()
		 */
		@Override
		public void endDocument() throws SAXException {

		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws SAXException
	 * @throws FileNotFoundException
	 */
	public static void main(String[] args) throws FileNotFoundException,
			SAXException, IOException {
		new Osm2json(args[0], args[1]);
	}

	public Osm2json(String osm, String json) throws SAXException,
			FileNotFoundException, IOException {

		nodes = new HashMap<String, Point2D.Double>();
		ways = new ArrayList<Object>();
		proj = ProjectionFactory.fromPROJ4Specification(projParameter.split(" "));

		XMLReader parser = XMLReaderFactory.createXMLReader();
		parser.setContentHandler(new Osm2jsonHandler());

		firstRun = true;
		parser.parse(new InputSource(new FileInputStream(osm)));

		// ...

		for (Point2D.Double point : nodes.values()) {
			if (min_x > point.x) {
				min_x = point.x;
			}
			if (min_y > point.y) {
				min_y = point.y;
			}
			if (max_x < point.x) {
				max_x = point.x;
			}
			if (max_y < point.y) {
				max_y = point.y;
			}
		}
		double x_range = max_x - min_x;
		double y_range = max_y-min_y;
		double ratio = (x_range) / (y_range);
		double factor;
		double x_offset;
		double y_offset;
		
		if(ratio > 1.6){
			factor = pixelWidth / x_range;
			x_offset=0.0;
			y_offset = (pixelHeight - (factor*y_range))/2;
		}
		else{
			factor = pixelHeight / y_range;
			x_offset=(pixelWidth - (factor*x_range))/2;
			y_offset = 0.0;
		
		}
		System.out.printf("ratio=%f, factor=%f, x_offset=%f, y_offset=%f%n",ratio,factor,x_offset,y_offset);
		for (Point2D.Double point : nodes.values()) {
			point.x = Math.floor((point.x - min_x)*factor + x_offset);
			point.y = pixelHeight - Math.floor((point.y - min_y)*factor + y_offset);
			//System.out.println(point);
		}
		
		firstRun = false;
		parser.parse(new InputSource(new FileInputStream(osm)));

		 
			
		FileWriter fstream = new FileWriter(json);
		BufferedWriter out = new BufferedWriter(fstream);
		out.write(JSONValue.toJSONString(ways).replace(",", ",\n"));
		out.flush();
		System.out.print("Done.");

	}
}