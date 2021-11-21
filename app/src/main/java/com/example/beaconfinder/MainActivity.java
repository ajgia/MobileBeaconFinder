package com.example.beaconfinder;

import androidx.appcompat.app.AppCompatActivity;

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

public class MainActivity extends AppCompatActivity {
    Button putBtn;
    TextView tv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv = findViewById(R.id.tv);
        putBtn = findViewById(R.id.putBtn);

        putBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // make http PUT request to server
                RequestQueue queue;
                String url;
                StringRequest stringRequest;

                queue = Volley.newRequestQueue(MainActivity.this);
                url = "http://10.0.2.2";
                stringRequest = new StringRequest(Request.Method.GET, url,
                    (response) -> {
                        System.out.println(response);
                        tv.setText(response.toString());
                    },
                    (error) -> {
                        System.out.println(error);
                        tv.setText(error.toString());
                    }
                );
                queue.add(stringRequest);


                //display toast
                Toast.makeText(MainActivity.this, "Request sent", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
