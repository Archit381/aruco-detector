package com.example.arucoapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.Manifest;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


public class MainActivity extends AppCompatActivity {

    Button detectBtn;
    Button caliberateBtn;

    Button inferenceBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getPermission();

        detectBtn=findViewById(R.id.detectBtn);
        caliberateBtn=findViewById(R.id.caliberateBtn);
        inferenceBtn=findViewById(R.id.inferenceBtn);

        SharedPreferences sharedPreferences = getSharedPreferences("calibration_data", MODE_PRIVATE);
        boolean res=isCalibrationDataAvailable(sharedPreferences);

        Log.d("Result",res+"");

        detectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(res){
                    Intent intent=new Intent(MainActivity.this, DetectMarkersActivity.class);
                    startActivity(intent);
                }
                else{
                    Toast.makeText(MainActivity.this, "Complete Camera Caliberation First", Toast.LENGTH_SHORT).show();
                }

            }
        });

        caliberateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                    Intent intent=new Intent(MainActivity.this, CaptureImageActivity.class);
                    startActivity(intent);

            }
        });

        inferenceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(res){
                    Intent intent=new Intent(MainActivity.this, InferenceActivity.class);
                    startActivity(intent);
                }
                else{
                    Toast.makeText(MainActivity.this, "Complete Camera Caliberation First", Toast.LENGTH_SHORT).show();
                }
            }
        });


    }

    public static boolean isCalibrationDataAvailable(SharedPreferences sharedPreferences) {
        String cameraMatrixStr = sharedPreferences.getString("camera_matrix", "");
        String distCoeffsStr = sharedPreferences.getString("dist_coeffs", "");
        return !cameraMatrixStr.isEmpty() && !distCoeffsStr.isEmpty();
    }

    void getPermission(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.CAMERA},101);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(grantResults.length>0 && grantResults[0]!=PackageManager.PERMISSION_GRANTED){
            getPermission();
        }

    }
}