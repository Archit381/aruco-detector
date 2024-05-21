package com.example.arucoapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point3;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CaptureImageActivity extends CameraActivity {

    CameraBridgeViewBase cameraBridgeViewBase;
    private Mat rgb;
    private Mat gray;

    Button captureBtn;
    Button caliberateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_image);

        cameraBridgeViewBase=findViewById(R.id.cameraView);
        captureBtn=findViewById(R.id.captureBtn);
        caliberateBtn=findViewById(R.id.caliberateBtn);

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {

            }
            @Override
            public void onCameraViewStopped() {

            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

//                Imgproc.cvtColor(inputFrame.rgba(), rgb, Imgproc.COLOR_RGBA2RGB);

                rgb=inputFrame.rgba();
                gray=inputFrame.gray();
                Core.rotate(rgb,rgb, Core.ROTATE_90_CLOCKWISE);
                Core.rotate(gray,gray, Core.ROTATE_90_CLOCKWISE);

                return rgb;

            }
        });

        if(OpenCVLoader.initDebug()){
            cameraBridgeViewBase.enableView();
        }


        File calibrationFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "calibration");


//        if (!calibrationFolder.exists()) {
//            calibrationFolder.mkdirs();
//        }else{
//            File[] files = calibrationFolder.listFiles();
//            if (files != null) {
//                for (File file : files) {
//                    file.delete();
//                }
//            }
//        }

        captureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File[] files = calibrationFolder.listFiles();
                // If there are less than 10 images, save the current image
                if (files != null && files.length < 10) {
                    int imageCount = files.length + 1;
                    String filename = "image_" + imageCount + ".jpg";
                    File file = new File(calibrationFolder, filename);

                    Imgcodecs.imwrite(file.getAbsolutePath(), gray);  // changed rgb to gray

                    Toast.makeText(CaptureImageActivity.this, "Image saved to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                } else {

                    Toast.makeText(CaptureImageActivity.this, "You have captured 10 images", Toast.LENGTH_SHORT).show();

                    cameraBridgeViewBase.disableView();
                }
            }
        });

        caliberateBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                File[] files = calibrationFolder.listFiles();

//                if (files == null || files.length < 10) {
//                    Toast.makeText(CaptureImageActivity.this, "You have not captured 10 images", Toast.LENGTH_SHORT).show();
//                } else {
//                    startCaliberation();
//                }
                startCaliberation();
            }
        });

    }

    public void startCaliberation(){

        int chessboardRows=7;
        int chessboardCols=7;

        Size chessboardSize=new Size(chessboardCols,chessboardRows);

        List<Point3> objPointsList = new ArrayList<>();
        for (int i = 0; i < chessboardRows; i++) {
            for (int j = 0; j < chessboardCols; j++) {
                objPointsList.add(new Point3(j, i, 0));
            }
        }



        MatOfPoint3f objPoints = new MatOfPoint3f();
        objPoints.fromList(objPointsList);

        List<Mat> objectPoints = new ArrayList<>();
        List<Mat> imagePoints = new ArrayList<>();

        // Assume images are stored in a List<Mat> called images
        List<Mat> images = loadCalibrationImages();

        if (images.isEmpty()) {
            Log.e("CaptureImageActivity", "No images loaded for calibration.");
            Toast.makeText(this, "No images loaded for calibration.", Toast.LENGTH_SHORT).show();
            return;
        }



        for (Mat image : images) {
            Mat gray = image;
//            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);

            MatOfPoint2f imageCorners = new MatOfPoint2f();
            boolean found = Calib3d.findChessboardCorners(gray, chessboardSize, imageCorners);

            if (found) {
                objectPoints.add(objPoints);
                imagePoints.add(imageCorners);
            }

            if (objectPoints.isEmpty()) {
                Log.e("CaptureImageActivity", "Not enough valid object points for calibration.");
                Toast.makeText(this, "Not enough valid object points for calibration.", Toast.LENGTH_SHORT).show();
                return;
            } else if (imagePoints.isEmpty()) {
                Log.e("CaptureImageActivity", "Not enough valid imagePoints for calibration.");
                Toast.makeText(this, "Not enough valid imagePoints for calibration.", Toast.LENGTH_SHORT).show();
                return;
            }


            Mat cameraMatrix = Mat.eye(3, 3, CvType.CV_64F);
            Mat distCoeffs = Mat.zeros(5, 1, CvType.CV_64F);
            List<Mat> rvecs = new ArrayList<>();
            List<Mat> tvecs = new ArrayList<>();

            Calib3d.calibrateCamera(objectPoints, imagePoints, chessboardSize, cameraMatrix, distCoeffs, rvecs, tvecs);

            Log.i("RESULT 1", "Camera Matrix: \n" + cameraMatrix.dump());
            Log.i("RESULT 2", "Distortion Coefficients: \n" + distCoeffs.dump());

            saveCalibrationData(cameraMatrix, distCoeffs);

        }

    }

    public void saveCalibrationData(Mat cameraMatrix, Mat distCoeffs){
        SharedPreferences sharedPreferences = getSharedPreferences("calibration_data", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("camera_matrix", matToString(cameraMatrix));
        editor.putString("dist_coeffs", matToString(distCoeffs));
        editor.apply();
    }

    private String matToString(Mat mat) {
        int rows = mat.rows();
        int cols = mat.cols();
        int type = mat.type();
        StringBuilder sb = new StringBuilder();
        sb.append(rows).append(",").append(cols).append(",").append(type).append(",");
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                sb.append(mat.get(i, j)[0]).append(",");
            }
        }
        return sb.toString();
    }

    public List<Mat> loadCalibrationImages() {
        List<Mat> images = new ArrayList<>();
        File calibrationFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "calibration");

        if (calibrationFolder.exists()) {
            File[] files = calibrationFolder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".jpg")) {
                        Mat image = Imgcodecs.imread(file.getAbsolutePath(), Imgcodecs.IMREAD_GRAYSCALE); // Load as grayscale
                        if (!image.empty()) {
                            images.add(image);
                            Log.d("CaptureImageActivity", "Loaded image: " + file.getAbsolutePath());
                        } else {
                            Log.e("CaptureImageActivity", "Failed to load image: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        } else {
            Log.e("CaptureImageActivity", "Calibration folder does not exist.");
        }

        return images;
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }
}