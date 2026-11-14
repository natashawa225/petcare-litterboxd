package com.litterboxd.app.location;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AmapVetSearchTest {

    // Shaped like the v5 place/around reply in the AMap docs (fields trimmed).
    private static final String AROUND_JSON = "{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\","
            + "\"count\":\"4\",\"pois\":["
            + "{\"name\":\"Far Clinic\",\"address\":\"3 Far Road\",\"distance\":\"2500\","
            + "\"typecode\":\"090701\",\"business\":{\"tel\":\"0512-1111;0512-2222\"}},"
            + "{\"name\":\"Near Clinic\",\"address\":\"1 Near Road\",\"distance\":\"350\","
            + "\"typecode\":\"090701\",\"business\":{\"tel\":[]}},"
            + "{\"name\":\"Middle Hospital\",\"address\":[],\"distance\":\"1200\","
            + "\"typecode\":\"090700\"},"
            + "{\"name\":\"\",\"address\":\"No name\",\"distance\":\"10\"}"
            + "]}";

    @Test
    public void clinicsComeBackClosestFirstWithEmptyFieldsBlank() throws Exception {
        List<VetClinic> clinics = AmapVetSearch.parseAround(AROUND_JSON);
        assertEquals(3, clinics.size());
        assertEquals("Near Clinic", clinics.get(0).getName());
        assertEquals(350, clinics.get(0).getDistanceMeters());
        assertEquals("", clinics.get(0).getPhone());
        assertEquals("Middle Hospital", clinics.get(1).getName());
        assertEquals("", clinics.get(1).getAddress());
        assertEquals("Far Clinic", clinics.get(2).getName());
        assertEquals("0512-1111", clinics.get(2).getPhone());
    }

    @Test
    public void missingDistanceSortsLast() throws Exception {
        List<VetClinic> clinics = AmapVetSearch.parseAround("{\"status\":\"1\",\"pois\":["
                + "{\"name\":\"Unknown\"},{\"name\":\"Known\",\"distance\":\"900\"}]}");
        assertEquals("Known", clinics.get(0).getName());
        assertEquals(VetClinic.UNKNOWN_DISTANCE, clinics.get(1).getDistanceMeters());
    }

    @Test
    public void refusedRequestReportsAmapsReason() {
        try {
            AmapVetSearch.parseAround("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}");
            fail("expected a service error");
        } catch (AmapVetSearch.ServiceException e) {
            assertEquals("INVALID_USER_KEY", e.getMessage());
        }
    }

    @Test
    public void convertedPositionIsLongitudeThenLatitude() throws Exception {
        double[] gcj = AmapVetSearch.parseConvert(
                "{\"status\":\"1\",\"info\":\"ok\",\"locations\":\"120.742957,31.272566\"}");
        assertEquals(120.742957, gcj[0], 1e-9);
        assertEquals(31.272566, gcj[1], 1e-9);
    }

    @Test
    public void requestsUseSixDecimalsAndVetCategories() throws Exception {
        assertEquals("120.738500,31.274500", AmapVetSearch.lngLat(120.7385, 31.2745));
        String around = AmapVetSearch.aroundUrl("k", 120.7385, 31.2745);
        assertTrue(around.startsWith("https://restapi.amap.com/v5/place/around?"));
        assertTrue(around.contains("sortrule=distance"));
        assertTrue(around.contains("types=090700%7C090701"));
        assertTrue(around.contains("location=120.738500,31.274500"));
        assertTrue(AmapVetSearch.convertUrl("k", 120.7385, 31.2745).contains("coordsys=gps"));
    }

    @Test
    public void noKeyMeansNotSetUp() {
        assertEquals(false, new AmapVetSearch("").hasKey());
        assertEquals(false, new AmapVetSearch("  ").hasKey());
        assertEquals(true, new AmapVetSearch("abc").hasKey());
    }
}
