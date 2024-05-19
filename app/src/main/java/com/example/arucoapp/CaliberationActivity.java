package com.example.arucoapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import org.opencv.android.CameraActivity;

public class CaliberationActivity extends CameraActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_caliberation);



    }
}