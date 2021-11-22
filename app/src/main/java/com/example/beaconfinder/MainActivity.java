package com.example.beaconfinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity {
    Button putBtn;
    TextView serverTV;
    TextView locationTV;
    LocationManager locationManager;
    Location location;
    FusedLocationProviderClient fusedLocationClient;

    int ACCESS_COARSE_LOCATION_PERMISSION_CODE = 1337;
    int ACCESS_FINE_LOCATION_PERMISSION_CODE = 1338;
    int INTERVAL = 60000; // 1 minute
    int FASTEST_INTERVAL = 5000; // 5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check and request permissions
        checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, ACCESS_COARSE_LOCATION_PERMISSION_CODE);
        checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_FINE_LOCATION_PERMISSION_CODE);

        // A Location Manager, of a sorts
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Set parameters and callback of request
        LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationCallback mLocationCallback = new LocationCallback() {

            // Callback for getting locationResult
            // On valid location, makes PUT request to server
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        String locationDisplay = location.getLatitude() + ", " + location.getLongitude();
                        locationTV.setText(locationDisplay);
                        makePUTRequest(location);
                    }
                }
            }
        };

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

    /**
     * Make HTTP PUT Request to server
     * @param location
     */
    void makePUTRequest(Location location) {
        RequestQueue queue;
        String url;
        StringRequest stringRequest;

        queue = Volley.newRequestQueue(MainActivity.this);

        // 10.0.2.2 is the alias for localhost of the actual device (not the emulator)
        // TODO: connect to the non-aliased url
        url = "http://10.0.2.2";

        // TODO: format this request as a valid PUT with location
        stringRequest = new StringRequest(Request.Method.GET, url,
                (response) -> {
                    System.out.println(response);
                    serverTV.setText(response.toString());
                },
                (error) -> {
                    System.out.println(error);
                    serverTV.setText(error.toString());
                }
        );
        queue.add(stringRequest);

        //display toast
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

    // This function is called when the user accepts or decline the permission.
    // Request Code is used to check which permission called this function.
    // This request code is provided when the user is prompt for permission.
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
}
