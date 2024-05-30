package com.example.arucoapp;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.example.arucoapp.ml.PickerDeeplabModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class InferenceActivity extends AppCompatActivity {

    ImageView image;
    Button selectBtn;
    Button inferenceBtn;
    Uri image_uri;

    ImageProcessor imageProcessor;
    private static final int RESULT_LOAD_IMAGE = 123;
    public static final int IMAGE_CAPTURE_CODE = 654;

    Bitmap input_image;
    Bitmap resized_image;

    int imageSize = 512;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inference);

        image = findViewById(R.id.imageView);
        selectBtn = findViewById(R.id.selectImageBtn);
        inferenceBtn = findViewById(R.id.inferenceBtn);

        imageProcessor = new ImageProcessor.Builder().add(new ResizeOp(512, 512, ResizeOp.ResizeMethod.BILINEAR)).build();

        selectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");

                Uri downloadsUri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, downloadsUri);

                startActivityForResult(intent, RESULT_LOAD_IMAGE);
            }
        });

        inferenceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new InferenceTask().execute();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null) {
            image_uri = data.getData();

            input_image = uriToBitmap(image_uri);

            image.setImageBitmap(input_image);

            resized_image = Bitmap.createScaledBitmap(input_image, imageSize, imageSize, false);
        }
    }

    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor = getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private int[][] convertOutputToSegmentationMask(float[] outputArray, int width, int height) {
        int[][] segmentationMask = new int[height][width];
        int pixel = 0;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                segmentationMask[i][j] = (outputArray[pixel++] > 0.5) ? 1 : 0;
            }
        }
        return segmentationMask;
    }

    private Bitmap overlaySegmentationMask(Bitmap originalImage, int[][] segmentationMask) {
        Bitmap result = originalImage.copy(originalImage.getConfig(), true);
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(50);  // Set transparency level

        for (int i = 0; i < segmentationMask.length; i++) {
            for (int j = 0; j < segmentationMask[i].length; j++) {
                if (segmentationMask[i][j] == 1) {
                    canvas.drawRect(j, i, j + 1, i + 1, paint);
                }
            }
        }
        return result;
    }

    private class InferenceTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                PickerDeeplabModel model = PickerDeeplabModel.newInstance(getApplicationContext());

                TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 512, 512, 3}, DataType.FLOAT32);
                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
                byteBuffer.order(ByteOrder.nativeOrder());

                int[] intValues = new int[imageSize * imageSize];
                resized_image.getPixels(intValues, 0, resized_image.getWidth(), 0, 0, resized_image.getWidth(), resized_image.getWidth());

                int pixel = 0;
                for (int i = 0; i < imageSize; i++) {
                    for (int j = 0; j < imageSize; j++) {
                        int val = intValues[pixel++];
                        byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                        byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                        byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                    }
                }

                inputFeature0.loadBuffer(byteBuffer);

                PickerDeeplabModel.Outputs outputs = model.process(inputFeature0);
                TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

                float[] outputArray = outputFeature0.getFloatArray();

                // Convert the output array to a segmentation mask
                int[][] segmentationMask = convertOutputToSegmentationMask(outputArray, imageSize, imageSize);

                // Overlay the segmentation mask on the original image
                Bitmap overlayedImage = overlaySegmentationMask(input_image, segmentationMask);

                model.close();

                return overlayedImage;

            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                image.setImageBitmap(result);
                Toast.makeText(InferenceActivity.this, "Output Generated", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(InferenceActivity.this, "Failed to generate output", Toast.LENGTH_SHORT).show();
            }
        }
    }
}