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
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnTokenCanceledListener;
import com.google.android.gms.tasks.Task;

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
        CancellationToken cancellationToken = new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return false;
            }

            @NonNull
            @Override
            public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
                return null;
            }
        };
        fusedLocationClient.getCurrentLocation(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY, cancellationToken);

        beaconTV = findViewById(R.id.beaconTV);
        serverTV = findViewById(R.id.serverTV);
        locationTV = findViewById(R.id.locationTV);
        putBtn = findViewById(R.id.putBtn);

        // PUT Button's onClickListener triggers the location request, which triggers hitting the server when it returns
        putBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                try {
                        Task<Location> taskLoc =  fusedLocationClient.getLastLocation();
                        taskLoc.addOnSuccessListener(new OnSuccessListener<Location>() {

                            @Override
                            public void onSuccess(Location location) {
                                if (location == null) {
                                    return;
                                }
                                else {
                                    String locationDisplay = location.getLatitude() + ", " + location.getLongitude();
                                    locationTV.setText(locationDisplay);
                                    try  {
                                        sendRequestPerBeacon(location);
                                    }
                                    catch (Exception e) {
                                        Log.e("PUT", e.toString());
                                    }
                                }
                            }

                        });

                }
                catch (SecurityException e) {
                    System.out.println("security exception");
                }
            } // onClick
        }); // setOnClickListener
    } // onCreate

    private void sendRequestPerBeacon(Location location) {
        for (Beacon beacon : beaconList) {
            try {
                makePUTRequest(location, beacon);
            } catch (Exception e) {
                Log.e("PUT", e.toString());
            }
        }
    }

    @Override
    public void didEnterRegion(Region region) {
        Log.d("Region callback", "did enter region");
        insideRegion = true;

       // clear list of seen beacons
        beaconList.clear();

        // start ranging
        beaconManager.startRangingBeacons(region);
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

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        beaconList = (ArrayList<Beacon>) beacons;
        beaconTV.setText("");
        for (Beacon b : beaconList) {
            beaconTV.append(b.getId2().toString() + "-" + b.getId3().toString() + "\n");
            Log.d("BeaconList", b.getId1() + " " + b.getId2() + " " + b.getId3());
        }
    }

    /**
     * Make HTTP PUT Request to server
     * @param location
     */
    void makePUTRequest(Location location, Beacon beacon) throws AuthFailureError {
        RequestQueue queue;
        String url;
        StringRequest stringRequest;

        queue = Volley.newRequestQueue(MainActivity.this);

        // 10.0.2.2 is the alias for localhost of the actual device (not the emulator)
        // TODO: add actual server when running
        url = "10.0.2.2";

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
                params.put("key", beacon.getId2().toString() + "-" + beacon.getId3().toString());
                params.put("val", String.valueOf(location.getLatitude()) + ", " + String.valueOf(location.getLongitude()) + ", " + currentTime.toString());
                return params;
            }
        };

        queue.add(stringRequest);

        showToast("Request sent");
    }

    // Function to check and request permission.
    public void checkPermission(String permission, int requestCode)
    {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {

            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
        }
        else {
            showToast("Permission already granted");
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
                showToast("Coarse Location Permission Granted");
            }
            else {
                showToast("Coarse Location Permission Denied");
            }
        }
        else if (requestCode == ACCESS_FINE_LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Fine Location Permission Granted");
            } else {
                showToast("Fine Location Permission Denied");
            }
        }
    }

    public void showToast(final String toast) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, toast, Toast.LENGTH_SHORT).show());
    }

}
