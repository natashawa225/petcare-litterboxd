package com.litterboxd.app.location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds vet clinics near a phone position with the AMap (Gaode) Web Service API. AMap is used
 * because Google services are blocked in mainland China; it is a plain HTTPS API, so no SDK.
 *
 * The phone's GPS gives WGS-84 coordinates but AMap works in GCJ-02, so the position is
 * converted first. The search uses AMap's POI category 090700 "动物医疗场所" (vet hospitals)
 * and 090701 "宠物诊所" (pet clinics); 090702 "兽医站" is left out because those are livestock
 * stations. A category is needed for AMap to sort by distance.
 * Plain Java (apart from org.json) so the request and parsing can be unit-tested.
 */
public final class AmapVetSearch {

    static final String ORIGIN = "https://restapi.amap.com";
    static final String VET_TYPES = "090700|090701";
    static final int RADIUS_METERS = 10000;
    static final int PAGE_SIZE = 10;
    public static final int MAX_RESULTS = 3;
    private static final int TIMEOUT_MS = 15000;
    private static final long RETRY_DELAY_MS = 1500;
    private static final int MAX_BYTES = 200_000;

    /** What kind of refusal AMap gave, so the screen can say what to do about it. */
    public static final int PROBLEM_QUOTA = 1;  // daily limit used up
    public static final int PROBLEM_BUSY = 2;   // too many requests per second or minute
    public static final int PROBLEM_KEY = 3;    // key wrong, deleted, wrong type or not allowed
    public static final int PROBLEM_OTHER = 4;

    /** AMap answered, but with status 0 (for example a wrong key or a used-up daily quota). */
    public static final class ServiceException extends Exception {
        private final String infocode;

        public ServiceException(String info) {
            this(info, "");
        }

        public ServiceException(String info, String infocode) {
            super(info);
            this.infocode = infocode == null ? "" : infocode;
        }

        /** AMap's numeric code, such as 10044; "" if the reply had none. */
        public String getInfocode() {
            return infocode;
        }

        public int getProblem() {
            return problemFor(infocode);
        }
    }

    /** Groups AMap's info codes (Web Service "info" table). */
    static int problemFor(String infocode) {
        switch (infocode == null ? "" : infocode) {
            case "10003": case "10010": case "10044": case "40000":
                return PROBLEM_QUOTA;
            case "10004": case "10014": case "10015": case "10016":
            case "10019": case "10020": case "10021": case "10029":
                return PROBLEM_BUSY;
            case "10001": case "10002": case "10005": case "10006": case "10007":
            case "10008": case "10009": case "10012": case "10013": case "10026": case "10041":
                return PROBLEM_KEY;
            default:
                return PROBLEM_OTHER;
        }
    }

    private interface Parser<T> {
        T parse(String json) throws ServiceException;
    }

    private final String key;

    public AmapVetSearch(String key) {
        this.key = key == null ? "" : key.trim();
    }

    public boolean hasKey() {
        return !key.isEmpty();
    }

    /** Background thread. Up to {@link #MAX_RESULTS} clinics, closest first. */
    public List<VetClinic> findNearby(double wgsLatitude, double wgsLongitude)
            throws IOException, ServiceException {
        double[] gcj = request(convertUrl(key, wgsLongitude, wgsLatitude), AmapVetSearch::parseConvert);
        List<VetClinic> clinics = request(aroundUrl(key, gcj[0], gcj[1]), AmapVetSearch::parseAround);
        return clinics.size() > MAX_RESULTS ? new ArrayList<>(clinics.subList(0, MAX_RESULTS)) : clinics;
    }

    /** One retry after a short pause when the network drops or AMap says it is busy. */
    private <T> T request(String url, Parser<T> parser) throws IOException, ServiceException {
        for (int attempt = 1; ; attempt++) {
            try {
                return parser.parse(get(url));
            } catch (IOException e) {
                if (attempt >= 2) throw e;
            } catch (ServiceException e) {
                if (attempt >= 2 || e.getProblem() != PROBLEM_BUSY) throw e;
            }
            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted");
            }
        }
    }

    static String convertUrl(String key, double longitude, double latitude) throws IOException {
        return ORIGIN + "/v3/assistant/coordinate/convert?coordsys=gps&output=json"
                + "&locations=" + lngLat(longitude, latitude)
                + "&key=" + encode(key);
    }

    static String aroundUrl(String key, double longitude, double latitude) throws IOException {
        return ORIGIN + "/v5/place/around?sortrule=distance&show_fields=business"
                + "&types=" + encode(VET_TYPES)
                + "&radius=" + RADIUS_METERS
                + "&page_size=" + PAGE_SIZE
                + "&location=" + lngLat(longitude, latitude)
                + "&key=" + encode(key);
    }

    /**
     * Rough box around China (latitude 18 to 54, longitude 73 to 135). AMap has no places
     * outside China, so an empty result outside this box is explained differently. The box also
     * covers some neighbouring countries; it is only used to pick a message.
     */
    public static boolean mayBeInChina(double latitude, double longitude) {
        return latitude >= 18 && latitude <= 54 && longitude >= 73 && longitude <= 135;
    }

    /** AMap wants "longitude,latitude" with at most 6 decimals. */
    static String lngLat(double longitude, double latitude) {
        return String.format(Locale.US, "%.6f,%.6f", longitude, latitude);
    }

    /** {longitude, latitude} in GCJ-02. */
    static double[] parseConvert(String json) throws ServiceException {
        try {
            JSONObject root = checkStatus(json);
            String first = root.getString("locations").split(";")[0];
            String[] parts = first.split(",");
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (JSONException | RuntimeException e) {
            throw new ServiceException("Unreadable coordinate reply");
        }
    }

    /** Every clinic in the reply, closest first. */
    static List<VetClinic> parseAround(String json) throws ServiceException {
        List<VetClinic> clinics = new ArrayList<>();
        try {
            JSONArray pois = checkStatus(json).optJSONArray("pois");
            if (pois == null) return clinics;
            for (int i = 0; i < pois.length(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String name = text(poi, "name");
                if (name.isEmpty()) continue;
                JSONObject business = poi.optJSONObject("business");
                double[] position = position(text(poi, "location"));
                clinics.add(new VetClinic(name, text(poi, "address"),
                        distance(text(poi, "distance")),
                        business == null ? "" : firstPhone(text(business, "tel")),
                        text(poi, "id"), position[0], position[1]));
            }
        } catch (JSONException e) {
            throw new ServiceException("Unreadable search reply");
        }
        clinics.sort(Comparator.comparingInt(VetClinic::getDistanceMeters));
        return clinics;
    }

    private static JSONObject checkStatus(String json) throws JSONException, ServiceException {
        JSONObject root = new JSONObject(json);
        if (!"1".equals(root.optString("status"))) {
            throw new ServiceException(root.optString("info", "Unknown error"),
                    root.optString("infocode", ""));
        }
        return root;
    }

    /** AMap sends an empty value as [] instead of "", so arrays count as empty. */
    private static String text(JSONObject o, String name) {
        Object value = o.opt(name);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static int distance(String meters) {
        try {
            return (int) Math.round(Double.parseDouble(meters));
        } catch (NumberFormatException e) {
            return VetClinic.UNKNOWN_DISTANCE;
        }
    }

    /** "longitude,latitude" to {longitude, latitude}; NaN when missing or unreadable. */
    static double[] position(String lngLat) {
        String[] parts = lngLat == null ? new String[0] : lngLat.split(",");
        try {
            if (parts.length == 2) {
                return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
            }
        } catch (NumberFormatException ignored) {
            // falls through to "no position"
        }
        return new double[]{Double.NaN, Double.NaN};
    }

    /** Several numbers come back separated by ";", so only the first is kept. */
    static String firstPhone(String tel) {
        return tel == null ? "" : tel.split(";")[0].trim();
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1 && total < MAX_BYTES) {
                    out.write(buffer, 0, read);
                    total += read;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }
}
