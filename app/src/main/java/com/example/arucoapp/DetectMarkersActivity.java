package com.example.arucoapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
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

                Core.rotate(rgb, rgb, Core.ROTATE_90_CLOCKWISE);

                gray = inputFrame.gray();
                ids = new MatOfInt();
                corners.clear();

                Aruco.detectMarkers(gray, dictionary, corners, ids, parameters);

                if (ids.total() > 0) {
                    Aruco.drawDetectedMarkers(rgb, corners, ids);

                    int id = ids.toArray()[0];
                    Log.d("Marker ID", String.valueOf(id));
                }

                return rgb;
            }
        });

        if(OpenCVLoader.initDebug()){
            cameraBridgeViewBase.enableView();
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }
}