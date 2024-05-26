package com.example.arucoapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.example.arucoapp.ml.RookDeeplabModel;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.DetectorParameters;
import org.opencv.aruco.Dictionary;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
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

    Button inferenceBtn;

    public static final float SIZE = 0.004f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_markers);

        cameraBridgeViewBase=findViewById(R.id.cameraView);
        inferenceBtn=findViewById(R.id.inferenceBtn);

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
                        Mat rvec = rvecs.row(i);
                        Mat tvec = tvecs.row(i);

                        Aruco.drawAxis(rgb, cameraMatrix, distCoeffs, rvecs.row(i), tvecs.row(i), SIZE/0.01f);

                        calculateAngle(rvec);


                    }
                }

                return rgb;

            }
        });

        if(OpenCVLoader.initDebug()){
            cameraBridgeViewBase.enableView();
        }

        loadCalibrationData();

        inferenceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                cameraBridgeViewBase.disableView();

//                makeInference(rgb);
                  saveImage(rgb);


            }
        });
    }



    public void makeInference(Mat rgbFrame){
        try {
            RookDeeplabModel model = RookDeeplabModel.newInstance(this);

            Mat resizedRgb=new Mat();

            ImageProcessor imageProcessor=new ImageProcessor.Builder().add(new ResizeOp(512,512, ResizeOp.ResizeMethod.BILINEAR)).build();


            Imgproc.resize(rgbFrame, resizedRgb, new Size(512,512));

            resizedRgb.convertTo(resizedRgb, CvType.CV_32F, 1.0 / 255.0);

            ByteBuffer byteBuffer = convertMatToByteBuffer(resizedRgb);

            // Creates inputs for reference.

            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 512, 512, 3}, DataType.FLOAT32);
            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            RookDeeplabModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            float[] outputArray = outputFeature0.getFloatArray();

            Log.d("Model Output", "Output Array: " + Arrays.toString(outputArray));

            if (outputArray.length >= 4) {
                int x = (int) (outputArray[0] * rgbFrame.width());
                int y = (int) (outputArray[1] * rgbFrame.height());
                int width = (int) (outputArray[2] * rgbFrame.width());
                int height = (int) (outputArray[3] * rgbFrame.height());

                Log.d("BoundingBox", "x: " + x + ", y: " + y + ", width: " + width + ", height: " + height);

                Imgproc.rectangle(rgbFrame, new Point(x, y), new Point(x + width, y + height), new Scalar(0, 255, 0), 2);
                saveImage(rgbFrame);
            }

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }
    }

    private ByteBuffer convertMatToByteBuffer(Mat mat) {
        // Create a ByteBuffer with the appropriate size and order
        int size = (int) (mat.total() * mat.channels());
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size * 4);  // 4 bytes per float
        byteBuffer.order(ByteOrder.nativeOrder());
        // Copy the mat data to the ByteBuffer
        for (int i = 0; i < mat.rows(); i++) {
            for (int j = 0; j < mat.cols(); j++) {
                for (int k = 0; k < mat.channels(); k++) {
                    byteBuffer.putFloat((float) mat.get(i, j)[k]);
                }
            }
        }

        return byteBuffer;
    }


    private void saveImage(Mat rgbFrame) {
        File outputFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "output");

        // Create the directory if it does not exist
        if (!outputFolder.exists()) {
            boolean mkdirsSuccess = outputFolder.mkdirs();
            Log.d("saveImage", "Directory created: " + mkdirsSuccess);
        }

        // Get the number of images in the directory
        File[] files = outputFolder.listFiles();
        int imageCount = (files == null) ? 0 : files.length;

        // Create the file name
        String filename = "output_" + (imageCount + 1) + ".jpg";
        File file = new File(outputFolder, filename);

        // Save the image
        boolean success = Imgcodecs.imwrite(file.getAbsolutePath(), rgbFrame);
        if (success) {
            Log.d("saveImage", "Image saved successfully to " + file.getAbsolutePath());
            Toast.makeText(DetectMarkersActivity.this, "Output Generated to " + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        } else {
            Log.e("saveImage", "Failed to save image to " + file.getAbsolutePath());
            Toast.makeText(DetectMarkersActivity.this, "Failed to save image.", Toast.LENGTH_SHORT).show();
        }
    }

    public void calculateAngle(Mat rvec) {
        Mat rotationMatrix = new Mat();
        Calib3d.Rodrigues(rvec, rotationMatrix);

        double[] angles = rotationMatrixToEulerAngles(rotationMatrix);

        double roll = Math.toDegrees(angles[0]);
        double pitch = Math.toDegrees(angles[1]);
        double yaw = Math.toDegrees(angles[2]);

        Log.d("Angles", "Roll: " + roll + ", Pitch: " + pitch + ", Yaw: " + yaw);
    }

    public double[] rotationMatrixToEulerAngles(Mat R) {
        double[] euler = new double[3];

        double sy = Math.sqrt(R.get(0,0)[0] * R.get(0,0)[0] + R.get(1,0)[0] * R.get(1,0)[0]);

        boolean singular = sy < 1e-6;

        if (!singular) {
            euler[0] = Math.atan2(R.get(2,1)[0], R.get(2,2)[0]);
            euler[1] = Math.atan2(-R.get(2,0)[0], sy);
            euler[2] = Math.atan2(R.get(1,0)[0], R.get(0,0)[0]);
        } else {
            euler[0] = Math.atan2(-R.get(1,2)[0], R.get(1,1)[0]);
            euler[1] = Math.atan2(-R.get(2,0)[0], sy);
            euler[2] = 0;
        }

        return euler;
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