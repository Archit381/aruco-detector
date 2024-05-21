package com.example.arucoapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public class DetectMarkersActivity extends CameraActivity {

    CameraBridgeViewBase cameraBridgeViewBase;
    private Mat rgb;
    private Mat gray;

    private MatOfInt ids;
    private List<Mat> corners;
    private Dictionary dictionary;
    private DetectorParameters parameters;

    private Mat cameraMatrix;
    private Mat distCoeffs;
    private Mat rvecs;
    private Mat tvecs;

    public static final float SIZE = 0.04f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_markers);

        cameraBridgeViewBase=findViewById(R.id.cameraView);

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                rgb = new Mat();
                corners = new LinkedList<>();
                parameters = DetectorParameters.create();
                dictionary= Aruco.getPredefinedDictionary(Aruco.DICT_4X4_50);
            }
            @Override
            public void onCameraViewStopped() {

            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

                Imgproc.cvtColor(inputFrame.rgba(), rgb, Imgproc.COLOR_RGBA2RGB);

                gray = inputFrame.gray();

                Core.rotate(gray, gray, Core.ROTATE_90_CLOCKWISE);
                Core.rotate(rgb,rgb, Core.ROTATE_90_CLOCKWISE);

                ids = new MatOfInt();
                corners.clear();

                Aruco.detectMarkers(gray, dictionary, corners, ids, parameters);

                if(corners.size()>0){
                    Aruco.drawDetectedMarkers(rgb, corners, ids);

                    rvecs = new Mat();
                    tvecs = new Mat();

                    Aruco.estimatePoseSingleMarkers(corners, SIZE,cameraMatrix,distCoeffs,rvecs,tvecs);

                    for(int i=0;i<ids.toArray().length;i++){

                        Aruco.drawAxis(rgb, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), SIZE/2.0f);

                    }


                }

//                if (ids.total() > 0) {
//                    Aruco.drawDetectedMarkers(rgb, corners, ids);
//
//                    int id = ids.toArray()[0];
//                    Log.d("Marker ID", String.valueOf(id));
//
//
//                }

                return rgb;

            }
        });

        if(OpenCVLoader.initDebug()){
            cameraBridgeViewBase.enableView();
        }

        loadCalibrationData();

        Log.i("RESULT 1", "Camera Matrix: \n" + cameraMatrix.dump());
        Log.i("RESULT 2", "Distortion Coefficients: \n" + distCoeffs.dump());
    }

    private void loadCalibrationData() {
        SharedPreferences sharedPreferences = getSharedPreferences("calibration_data", MODE_PRIVATE);
        String cameraMatrixStr = sharedPreferences.getString("camera_matrix", "");
        String distCoeffsStr = sharedPreferences.getString("dist_coeffs", "");

        cameraMatrix = stringToMat(cameraMatrixStr);
        distCoeffs = stringToMat(distCoeffsStr);

        if (cameraMatrix.empty() || distCoeffs.empty()) {
            Log.e("AnotherActivity", "Failed to load calibration data.");
            Toast.makeText(this, "Failed to load calibration data.", Toast.LENGTH_SHORT).show();
        } else {
            Log.i("AnotherActivity", "Loaded camera matrix: \n" + cameraMatrix.dump());
            Log.i("AnotherActivity", "Loaded distortion coefficients: \n" + distCoeffs.dump());
        }
    }

    private Mat stringToMat(String matStr) {
        String[] elements = matStr.split(",");
        int rows = Integer.parseInt(elements[0]);
        int cols = Integer.parseInt(elements[1]);
        int type = Integer.parseInt(elements[2]);
        Mat mat = new Mat(rows, cols, type);
        int idx = 3;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                mat.put(i, j, Double.parseDouble(elements[idx++]));
            }
        }
        return mat;
    }


    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }
}