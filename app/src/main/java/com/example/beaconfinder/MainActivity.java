package com.example.beaconfinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.MonitorNotifier;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Does beacon detection and displays a PUT button that sends an HTTP PUT request to a server upon pressing,
 * containing detected beacon details, current lat/lng, and timestamp
 */
public class MainActivity extends AppCompatActivity implements MonitorNotifier, RangeNotifier {
    /**
     * UI elements
      */
    Button putBtn;
    TextView beaconTV;
    TextView serverTV;
    TextView locationTV;

    /**
     * Android location manager. Unused in favor of Fused Location Provider Client
      */
    LocationManager locationManager;
    /**
     * A stored location
      */
    Location location;
    /**
     * Google's FusedLocationProviderClient. Used to get location
     * May need to specify it to use high accuracy provider
     */
    FusedLocationProviderClient fusedLocationClient;
    /**
     * AltBeacon's beacon manager
     */
    BeaconManager beaconManager;
    /**
     * True if inside a region
     */
    boolean insideRegion = false;
    /**
     * Desired region. Detect beacons if matching characteristics of this region
     */
    Region region;
    /**
     * List of beacons
     */
    ArrayList<Beacon> beaconList;


    /**
     * Coarse location permission code. arbitrary number. used to distinguish between permission codes
     */
    int ACCESS_COARSE_LOCATION_PERMISSION_CODE = 1337;
    /**
     * Fine location permission code. arbitrary number. used to distinguish between permission codes
     */
    int ACCESS_FINE_LOCATION_PERMISSION_CODE = 1338;
    /**
     * Preferred location update interval
     */
    int INTERVAL = 60000; // 1 minute
    /**
     * Fastest location update interval the app can handle
     */
    int FASTEST_INTERVAL = 5000; // 5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupBeaconManagement();

        // Check and request permissions
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, ACCESS_COARSE_LOCATION_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_FINE_LOCATION_PERMISSION_CODE);

        // A Location Manager, of a sorts
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set parameters and callback of location request
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationCallback mLocationCallback = new LocationCallback() {

            // Callback for getting locationResult
            // On valid location, makes PUT request to server
            // We are now entering callback hell
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        String locationDisplay = location.getLatitude() + ", " + location.getLongitude();
                        locationTV.setText(locationDisplay);
                        try  {makePUTRequest(location);}
                        catch (Exception e) {
                            Log.e("PUT", e.toString());
                        }
                    }
                }
            }
        };

        beaconTV = findViewById(R.id.beaconTV);
        serverTV = findViewById(R.id.serverTV);
        locationTV = findViewById(R.id.locationTV);
        putBtn = findViewById(R.id.putBtn);

        // PUT Button's onClickListener triggers the location request, which triggers hitting the server when it returns
        putBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                    fusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);
                }
                catch (SecurityException e) {
                    System.out.println("security exception");
                }
            } // onClick
        }); // setOnClickListener
    } // onCreate

    @Override
    public void didEnterRegion(Region region) {
        Log.d("Region callback", "did enter region");
        insideRegion = true;

       // clear list of seen beacons
        beaconList.clear();

        // start ranging
        beaconManager.startRangingBeacons(region);
    }

    private void sendNotification() {
        Toast.makeText(MainActivity.this, "Region entered", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void didExitRegion(Region region) {
        // clear list
        beaconList.clear();
        // stop ranging
        beaconManager.stopRangingBeacons(region);
    }

    @Override
    public void didDetermineStateForRegion(int state, Region region) {

    }

    private void setupBeaconManagement() {
        // Uncomment this to run simulated beacons
        BeaconManager.setBeaconSimulator(new TimedBeaconSimulator());
        ((TimedBeaconSimulator) BeaconManager.getBeaconSimulator()).createTimedSimulatedBeacons();

        beaconList = new ArrayList<>();

        // set up beaconManager
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().
                setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));

        // D'arcy's UUID
        region = new Region("backgroundRegion",
                Identifier.parse("C6C4C829-4FD9-4762-837C-DA24C665015A"), null, null);


        // sets verbose logging on
//        BeaconManager.setDebug(true);

        beaconManager.addMonitorNotifier(this);
        for (Region region: beaconManager.getMonitoredRegions()) {
            beaconManager.stopMonitoring(region);
        }

        beaconManager.startMonitoring(region);
        beaconManager.addRangeNotifier(this);



    }

    /**
     * Make HTTP PUT Request to server
     * @param location
     */
    void makePUTRequest(Location location) throws AuthFailureError {
        RequestQueue queue;
        String url;
        StringRequest stringRequest;

        queue = Volley.newRequestQueue(MainActivity.this);

        // 10.0.2.2 is the alias for localhost of the actual device (not the emulator)
        // TODO: connect to the non-aliased url
        url = "http://10.0.2.2";

        // TODO: fix this loop so it does send all beacons
        for (Beacon b: beaconList) {
            stringRequest = new StringRequest(Request.Method.PUT, url,
                    (response) -> {
                        System.out.println(response);
                        serverTV.setText(response.toString());
                    },
                    (error) -> {
                        System.out.println(error);
                        serverTV.setText(error.toString());
                    }
            ) {
                @Override
                public Map<String, String> getHeaders()
                {
                    Map<String, String> headers = new HashMap<String, String>();
                    headers.put("Content-Type", "application/json");
                    //or try with this:
                    //headers.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
                    // or content-type text
                    return headers;
                }

                @Override
                protected Map<String, String> getParams() {
                    Date currentTime = Calendar.getInstance().getTime();

                    Map<String, String> params = new HashMap<String, String>();
                    // resulting format is: key=someKey&val=someVal&
                    params.put("key", b.getId2().toString() + "-" + b.getId3().toString());
                    params.put("val", String.valueOf(location.getLatitude()) + ", " + String.valueOf(location.getLongitude()) + ", " + currentTime.toString());
                    return params;
                }
            };
            queue.add(stringRequest);
        }

        // TODO: remove debug variables
//        byte[] bodyBytes = stringRequest.getBody();
//        String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8); // for UTF-8 encoding
//        Map<String, String> headers = stringRequest.getHeaders();
//        String headersStr = headers.toString();



        // display toast
        Toast.makeText(MainActivity.this, "Request sent", Toast.LENGTH_SHORT).show();
    }

    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }
        else {
            Toast.makeText(MainActivity.this, "Permission already granted", Toast.LENGTH_SHORT).show();
        }
    }

    /** This function is called when the user accepts or decline the permission.
     *  Request Code is used to check which permission called this function.
     * This request code is provided when the user is prompt for permission.
    */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode,
                permissions,
                grantResults);

        if (requestCode == ACCESS_COARSE_LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Coarse Location Permission Granted", Toast.LENGTH_SHORT) .show();
            }
            else {
                Toast.makeText(MainActivity.this, "Coarse Location Permission Denied", Toast.LENGTH_SHORT) .show();
            }
        }
        else if (requestCode == ACCESS_FINE_LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Fine Location Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Fine Location Permission Denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        for (Beacon b : beacons) {
            if ( !(beaconList.contains(b)) ) {
                beaconList.add(b);
            }
        }
        for (Beacon b : beaconList)
            Log.d("BeaconList", b.getId1() + " " + b.getId2() + " " + b.getId3());
    }
}
