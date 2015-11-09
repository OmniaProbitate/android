package ng.prk.prkngandroid.ui.fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PointF;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.lsjwzh.widget.materialloadingprogressbar.CircleProgressBar;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.annotations.Sprite;
import com.mapbox.mapboxsdk.annotations.SpriteFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngZoom;
import com.mapbox.mapboxsdk.views.MapView;

import java.util.ArrayList;
import java.util.List;

import ng.prk.prkngandroid.Const;
import ng.prk.prkngandroid.R;
import ng.prk.prkngandroid.io.ApiClient;
import ng.prk.prkngandroid.io.PrkngService;
import ng.prk.prkngandroid.model.GeoJSONFeatureProperties;
import ng.prk.prkngandroid.model.LinesGeoJSON;
import ng.prk.prkngandroid.model.LinesGeoJSONFeature;
import ng.prk.prkngandroid.model.LoginObject;
import ng.prk.prkngandroid.model.PointsGeoJSON;
import ng.prk.prkngandroid.model.PointsGeoJSONFeature;

public class MainMapFragment extends Fragment implements
        MapView.OnMapChangedListener, MapView.OnMapClickListener {
    private final static String TAG = "MainMapFragment";
    private final static double RADIUS_FIX = 1.4d;

    private CircleProgressBar mProgressBar;
    private MapView mapView;
    private String mApiKey;
    private int mLineColor;
    private Sprite mMarkerIcon;
    private LatLng mCenterLatLng;
    private boolean mIgnoreMinDistance;
    private int mLastRadius;
    private double mLastZoomLevel;
    private UpdateSpotsTasks mTask;

    public static MainMapFragment newInstance() {
        return new MainMapFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View view = inflater.inflate(R.layout.fragment_map, container, false);

        mTask = new UpdateSpotsTasks();
        mLineColor = ContextCompat.getColor(getContext(), R.color.mapLine);

        mProgressBar = (CircleProgressBar) view.findViewById(R.id.progressBar);
        final FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PackageManager.PERMISSION_GRANTED !=
                        ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) ||
                        PackageManager.PERMISSION_GRANTED !=
                                ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    requestPermissionIfNeeded();
                } else {
                    moveToMyLocation(true);
                }
            }
        });

        createMapIfNecessary(view, savedInstanceState);
        mMarkerIcon = new SpriteFactory(mapView).fromResource(R.drawable.ic_maps_my_location);

        return view;
    }

    @Override
    public void onStart() {
        Log.v(TAG, "onStart");

        super.onStart();
        mapView.onStart();

        if (PackageManager.PERMISSION_GRANTED ==
                ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            mapView.setMyLocationEnabled(true);
        }
    }

    @Override
    public void onResume() {
        Log.v(TAG, "onResume");

        super.onResume();
        mapView.onResume();

        if (mapView.getMyLocation() != null) {
            moveToMyLocation(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private void createMapIfNecessary(View view, Bundle savedInstanceState) {
        if (mapView == null) {
            mapView = (MapView) view.findViewById(R.id.mapview);

            mapView.setCenterCoordinate(new LatLng(45.501689, -73.567256));
            mapView.setZoomLevel(Const.UiConfig.DEFAULT_ZOOM);
            mapView.onCreate(savedInstanceState);
            mapView.addOnMapChangedListener(this);
            mapView.setOnMapClickListener(this);

            mLastZoomLevel = mapView.getZoomLevel();
            mCenterLatLng = mapView.getCenterCoordinate();
        }
    }

    @Override
    public void onMapChanged(int change) {

        switch (change) {
            case MapView.REGION_DID_CHANGE:
//                mCenterLatLng = mapView.getCenterCoordinate();
                updateMapData(mCenterLatLng, mapView.getZoomLevel());
            case MapView.DID_FINISH_LOADING_MAP:
            case MapView.REGION_DID_CHANGE_ANIMATED:
                if (mCenterLatLng.distanceTo(mapView.getCenterCoordinate()) >= Const.UiConfig.MIN_UPDATE_DISTACE
                        || mIgnoreMinDistance) {
                    mIgnoreMinDistance = false;
                    mCenterLatLng = mapView.getCenterCoordinate();
                    updateMapData(mCenterLatLng, mapView.getZoomLevel());
                }
                break;
            case MapView.REGION_WILL_CHANGE:
            case MapView.REGION_WILL_CHANGE_ANIMATED:
            case MapView.REGION_IS_CHANGING:
            case MapView.WILL_START_LOADING_MAP:
            case MapView.WILL_START_RENDERING_MAP:
            case MapView.WILL_START_RENDERING_FRAME:
//                Log.v(TAG, "onMapChanged @ " + change);
                break;
            case MapView.DID_FINISH_RENDERING_FRAME:
            case MapView.DID_FINISH_RENDERING_FRAME_FULLY_RENDERED:
                break;
            case MapView.DID_FINISH_RENDERING_MAP:
            case MapView.DID_FINISH_RENDERING_MAP_FULLY_RENDERED:
                Log.d(TAG, "onMapChanged @ " + change);
                break;
            case MapView.DID_FAIL_LOADING_MAP:
                Log.e(TAG, "onMapChanged failed");
                break;
        }
    }

    public void requestPermissionIfNeeded() {
        Log.v(TAG, "requestPermissionIfNeeded");

        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example if the user has previously denied the permission.
            Snackbar.make(mapView, R.string.location_permission_needed, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    requestPermission();
                                }
                            })
                    .show();
        } else {
            requestPermission();
        }
    }

    public void requestPermission() {
        Log.v(TAG, "requestPermission");

        // Permission has not been granted yet. Request it directly.
        ActivityCompat.requestPermissions(getActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                Const.RequestCodes.PERMISSION_ACCESS_LOCATION);
    }

    private void updateMapData(LatLng latLng, double zoom) {
        Log.v(TAG, "updateMapData @ " + zoom);

        if (Double.compare(Const.UiConfig.MIN_ZOOM, zoom) <= 0) {
            if (mTask.getStatus() == AsyncTask.Status.RUNNING) {
                Log.e(TAG, "skipped");
                mTask.cancel(false);
//                return;
            }
            computeScreenRadius(mapView.getZoomLevel());

            mTask = new UpdateSpotsTasks();
            mTask.execute(latLng);
        } else {
            mIgnoreMinDistance = true;
            Snackbar.make(mapView, R.string.map_zoom_needed, Snackbar.LENGTH_LONG)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mapView.setZoomLevel(Const.UiConfig.MIN_ZOOM, true);
                        }
                    }).show();
        }
    }

    private void moveToMyLocation(boolean animated) {
        if (!mapView.isMyLocationEnabled()) {
            mapView.setMyLocationEnabled(true);
        }
        final Location myLocation = mapView.getMyLocation();
        if (myLocation != null) {
            mapView.setCenterCoordinate(new LatLngZoom(
                    myLocation.getLatitude(),
                    myLocation.getLongitude(),
                    Math.max(Const.UiConfig.MY_LOCATION_ZOOM, mapView.getZoomLevel())
            ), animated);
        }
    }

    private void computeScreenRadius(double zoom) {
        Log.v(TAG, "computeScreenRadius "
                + String.format("zoom = %s", zoom));

        if (Double.compare(mLastZoomLevel, zoom) != 0 || mLastRadius == 0) {
            mLastRadius = (int) Math.ceil(mCenterLatLng.distanceTo(mapView.fromScreenLocation(new PointF(0, 0))) * RADIUS_FIX);
            mLastZoomLevel = zoom;
            Log.v(TAG, "mLastRadius 1 = " + mLastRadius);
        } else {
            Log.e(TAG, "skipped. mLastZoomLevel = " + mLastZoomLevel);
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        Log.v(TAG, "onMapClick "
                + String.format("latLng = %s", latLng));

    }

    private static class SpotAnnotation {
        List<PolylineOptions> polylines;
        List<MarkerOptions> markers;

        public SpotAnnotation(List<PolylineOptions> polylines, List<MarkerOptions> markers) {
            this.polylines = polylines;
            this.markers = markers;
        }

        public List<PolylineOptions> getPolylines() {
            return polylines;
        }

        public List<MarkerOptions> getMarkers() {
            return markers;
        }
    }

    private class UpdateSpotsTasks extends AsyncTask<LatLng, Void, SpotAnnotation> {

        private long startTime;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            try {
                mProgressBar.setVisibility(View.VISIBLE);
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected SpotAnnotation doInBackground(LatLng... params) {
            Log.v(TAG, "doInBackground");
            startTime = System.currentTimeMillis();
            final LatLng centerLatLng = params[0];

            final PrkngService service = ApiClient.getServiceLog();
            final List<PolylineOptions> polylines = new ArrayList<>();
            final List<MarkerOptions> markers = new ArrayList<>();

            try {
                if (mApiKey == null || mApiKey.isEmpty()) {
                    LoginObject loginObject = ApiClient
                            .loginEmail(
                                    service,
                                    "mudar@prk.ng",
                                    "mudar123");
                    Log.v(TAG, "name = " + loginObject.getName() + " email = " + loginObject.getEmail());
                    Log.v(TAG, "mApiKey = " + loginObject.getApikey());
                    mApiKey = loginObject.getApikey();
                }

                if (mApiKey != null && centerLatLng != null) {
                    Log.v(TAG, "mLastRadius 2 = " + mLastRadius);

                    final LinesGeoJSON spots = ApiClient.getParkingSpots(service,
                            mApiKey,
                            centerLatLng.getLatitude(),
                            centerLatLng.getLongitude(),
                            mLastRadius,
                            Const.ApiValues.DEFAULT_DURATION
                    );

                    final List<LinesGeoJSONFeature> spotsFeatures = spots.getFeatures();
                    for (LinesGeoJSONFeature feature : spotsFeatures) {
                        List<List<Double>> coords = feature.getGeometry().getCoordinates();
                        LatLng[] pointsArray = new LatLng[coords.size()];
                        int i = 0;
                        for (List<Double> latLng : coords) {
                            pointsArray[i++] = new LatLng(new LatLng(latLng.get(1), latLng.get(0)));
                        }
                        polylines.add(
                                new PolylineOptions()
                                        .add(pointsArray)
                                        .color(mLineColor)
                                        .width(2));

                        final GeoJSONFeatureProperties properties = feature.getProperties();
                        List<LatLng> buttons = properties.getButtonLocations();
                        for (LatLng button : buttons) {
                            String snippet = "ID: " + feature.getId();
                            if (properties.getRestrictType() != null) {
                                snippet += Const.LINE_SEPARATOR + "Restriction: " + properties.getRestrictType();
                            }
                            markers.add(
                                    new MarkerOptions()
                                            .position(button)
                                            .title(properties.getWayName())
                                            .snippet(snippet)
                            );
                            new MarkerOptions()
                                    .position(new LatLng(41.885, -87.679))
                                    .icon(mMarkerIcon);
                        }

                    }
                    Log.v(TAG, "DDD");
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            return new SpotAnnotation(polylines, markers);
        }

        @Override
        protected void onPostExecute(SpotAnnotation spots) {
            Log.v(TAG, "onPostExecute");
            if (isCancelled() || mapView == null) {
                return;
            }
            try {
                if (spots != null) {
                    mProgressBar.setVisibility(View.GONE);

                    Log.v(TAG, "removeAllAnnotations");
                    mapView.removeAllAnnotations();

                    Log.v(TAG, "addPolylines");
                    mapView.addPolylines(spots.getPolylines());
                    Log.v(TAG, "addMarkers");
                    mapView.addMarkers(spots.getMarkers());

//                    drawRadius();
                    Log.v(TAG, "Sync duration: " + (System.currentTimeMillis() - startTime) + " ms");
                } else {
                    Log.v(TAG, "spots not found");
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        private void drawRadius() {
            final LatLng radiusLatLng = mapView.fromScreenLocation(new PointF(0, 0));
            final MarkerOptions marker = new MarkerOptions();
            marker.position(radiusLatLng);
            mapView.addMarker(marker);

            mapView.addPolyline(new PolylineOptions()
                    .add(new LatLng[]{mapView.getCenterCoordinate(), radiusLatLng})
                    .color(Color.GREEN)
                    .width(5));
        }
    }

    private class UpdateLotsTasks extends AsyncTask {

        private PointsGeoJSON lots;

        @Override
        protected Object doInBackground(Object[] params) {
            Log.v(TAG, "doInBackground");

            final PrkngService service = ApiClient.getService();

            try {
                LoginObject loginObject = ApiClient
                        .loginEmail(
                                service,
                                "mudar@prk.ng",
                                "mudar123");
                if (loginObject != null) {
                    Log.v(TAG, "name = " + loginObject.getName() + " email = " + loginObject.getEmail());
                    Log.v(TAG, "mApiKey = " + loginObject.getApikey());

                    lots = ApiClient.getParkingLots(service,
                            loginObject.getApikey(),
                            45.501689d,
                            -73.567256d);
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Object o) {
            if (lots != null) {
                final List<PointsGeoJSONFeature> lotsFeatures = lots.getFeatures();
                for (PointsGeoJSONFeature feature : lotsFeatures) {
                    List<Double> latLng = feature.getGeometry().getCoordinates();

                    Log.v(TAG, "Point: " + latLng.toString());
                    final MarkerOptions marker = new MarkerOptions();
                    marker.position(new LatLng(latLng.get(1), latLng.get(0)));
                    mapView.addMarker(marker);

                }
            } else {
                Log.v(TAG, "lots not found");
            }
        }
    }

}
