/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package demo.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.transform.stream.StreamResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.XmlMappingException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import de.micromata.opengis.kml.v_2_2_0.AltitudeMode;
import de.micromata.opengis.kml.v_2_2_0.Coordinate;
import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.IconStyle;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.KmlFactory;
import de.micromata.opengis.kml.v_2_2_0.Link;
import de.micromata.opengis.kml.v_2_2_0.LookAt;
import de.micromata.opengis.kml.v_2_2_0.NetworkLink;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.RefreshMode;
import demo.model.Point;
import demo.model.PositionInfo;
import demo.model.PositionInfo.VehicleStatus;
import demo.service.KmlService;

/**
 * KML utility functions.
 * Each thread that utilizes this class should instantiate a new instance of it because jaxb marshal/unmarshalling is
 * not thread safe.
 * @author faram
 * @author Gunnar Hillert
 */
@Service
public class DefaultKmlService implements KmlService {

	public static final String KML_POSITION_FILE_NAME = "pos";
	public static final String KML_POSITION_FILE_SUFFIX = ".xml";

	@Autowired
	private Marshaller marshaller;

	private Map<Long, byte[]> kmlInstances = new ConcurrentHashMap<>();

	@Override
	public final void setupKmlIntegration(Set<Long> intanceIds, Point lookAtPoint) {
		Assert.notEmpty(intanceIds);
		Assert.isTrue(intanceIds.size() >= 1);

		File f = new File("gps.kml");

		Kml kml = KmlFactory.createKml();
		Document folder = KmlFactory.createDocument();
		folder.setOpen(true);
		folder.setName("Contains GPS Coordinates");
		kml.setFeature(folder);

		final LookAt lookAt = KmlFactory.createLookAt();
		lookAt.setLatitude(lookAtPoint.getLatitude());
		lookAt.setLongitude(lookAtPoint.getLongitude());
		lookAt.setAltitude(13000);
		lookAt.setAltitudeMode(AltitudeMode.ABSOLUTE);;
		folder.setAbstractView(lookAt);

		for (long instanceId : intanceIds) {
			Link link = KmlFactory.createLink();
			link.setHref("http://localhost:9005/api/kml/" + instanceId);
			link.setRefreshMode(RefreshMode.ON_INTERVAL);
			link.setRefreshInterval(1d);

			NetworkLink networkLink = KmlFactory.createNetworkLink();
			networkLink.setName("GPS link " + instanceId);
			networkLink.setOpen(true);
			networkLink.setLink(link);
			folder.addToFeature(networkLink);
		}

		final OutputStream out;

		try {
			out = new FileOutputStream(f);
		}
		catch (FileNotFoundException e) {
			throw new IllegalStateException(e);
		}

		try {
			marshaller.marshal(kml, new StreamResult(out));
		} catch (XmlMappingException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/* (non-Javadoc)
	 * @see frk.gpssimulator.service.KmlService#createPosKml(frk.gpssimulator.model.PositionInfo)
	 */
	@Override
	public void updatePosition(Long instanceId, PositionInfo position) {
		de.micromata.opengis.kml.v_2_2_0.Point point = KmlFactory.createPoint();
		Integer speedKph = 0;

		if(position != null) {
			Coordinate coordinate = KmlFactory.createCoordinate(position.getPosition().getLongitude(), position.getPosition().getLatitude());
			point.getCoordinates().add(coordinate);
		}
		else {
			Coordinate coordinate = KmlFactory.createCoordinate(0,0);
			point.getCoordinates().add(coordinate);
		}

		final Kml kml = KmlFactory.createKml();
		final Document document = kml.createAndSetDocument().withOpen(true);

		final IconStyle iconStyleError = document.createAndAddStyle()
			.withId("errorStyle")
			.createAndSetIconStyle();
		iconStyleError.setColor("ff0000ff");
		iconStyleError.setScale(1.2);

		final IconStyle iconStyleWarning = document.createAndAddStyle()
				.withId("warningStyle")
				.createAndSetIconStyle();
		iconStyleWarning.setColor("ff0080ff");
		iconStyleWarning.setScale(1.2);

		final IconStyle iconStyleNormal = document.createAndAddStyle()
				.withId("normalStyle")
				.createAndSetIconStyle();
		iconStyleNormal.setColor("ff00ff00");
		iconStyleNormal.setScale(1.2);

		final Placemark placemark = KmlFactory.createPlacemark();

		if(position != null) {
			Double speed = position.getSpeed();
			speedKph = (int)(speed * 3600 / 1000);
		}
		else {
			speedKph = 0;
		}

		if (VehicleStatus.NORMAL.equals(position.getVehicleStatus())) {
			placemark.setName(speedKph.toString() + " kph");
			placemark.setStyleUrl("#normalStyle");
		}
		else if (VehicleStatus.WARNING.equals(position.getVehicleStatus())) {
			placemark.setName(String.format(speedKph.toString() + " kph (Status: %s)", position.getVehicleStatus()));
			placemark.setStyleUrl("#warningStyle");
		}
		else if (VehicleStatus.ERROR.equals(position.getVehicleStatus())) {
			placemark.setName(String.format(speedKph.toString() + " kph (Status: %s)", position.getVehicleStatus()));
			placemark.setStyleUrl("#errorStyle");
		}
		placemark.setGeometry(point);
		document.addToFeature(placemark);

		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		try {
			marshaller.marshal(kml, new StreamResult(out));
			this.kmlInstances.put(instanceId, out.toByteArray());
		} catch (XmlMappingException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public byte[] getKmlInstance(Long instanceId) {
		return this.kmlInstances.get(instanceId);
	}
}