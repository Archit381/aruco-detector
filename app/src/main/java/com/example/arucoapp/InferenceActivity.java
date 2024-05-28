package com.example.arucoapp;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.arucoapp.ml.RookDeeplabModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

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

    int imageSize=512;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inference);

        image=findViewById(R.id.imageView);
        selectBtn=findViewById(R.id.selectImageBtn);
        inferenceBtn=findViewById(R.id.inferenceBtn);

        imageProcessor= new ImageProcessor.Builder().add(new ResizeOp(512,512,ResizeOp.ResizeMethod.BILINEAR)).build();

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
                try {
                    RookDeeplabModel model = RookDeeplabModel.newInstance(getApplicationContext());

                    // Creates inputs for reference and allocating size for our bytebuffer
                    TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 512, 512, 3}, DataType.FLOAT32);
                    ByteBuffer byteBuffer=ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3 );
                    byteBuffer.order(ByteOrder.nativeOrder());

                    // filling the bytebuffer with the pixel values

                    int [] intValues=new int[imageSize*imageSize];
                    resized_image.getPixels(intValues,0,resized_image.getWidth(),0,0,resized_image.getWidth(),resized_image.getWidth());

                    int pixel = 0;
                    for(int i = 0; i < imageSize; i++){
                        for(int j = 0; j < imageSize; j++){
                            int val = intValues[pixel++]; // RGB
                            byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                            byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                            byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                        }
                    }

                    inputFeature0.loadBuffer(byteBuffer);

                    // Runs model inference and gets result.
                    RookDeeplabModel.Outputs outputs = model.process(inputFeature0);
                    TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


                    Toast.makeText(InferenceActivity.this, "Output Generated", Toast.LENGTH_SHORT).show();

                    float[] outputArray=outputFeature0.getFloatArray();
                    Log.d("Output", Arrays.toString(outputArray));

                    int [] outputShape=outputFeature0.getShape();
                    Log.d("Output Shape", Arrays.toString(outputShape));

                    int maskWidth = outputShape[1];
                    int maskHeight = outputShape[2];
                    int[][] mask = new int[maskWidth][maskHeight];
                    for (int i = 0; i < maskWidth; i++) {
                        for (int j = 0; j < maskHeight; j++) {
                            mask[i][j] = (outputArray[i * maskHeight + j] > 0.5) ? 1 : 0; // Apply threshold
                        }
                    }

                    // Resize mask to original image size
                    Bitmap maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888);
                    for (int i = 0; i < maskWidth; i++) {
                        for (int j = 0; j < maskHeight; j++) {
                            maskBitmap.setPixel(j, i, mask[i][j] == 1 ? Color.GREEN : Color.TRANSPARENT);
                        }
                    }
                    Bitmap resizedMask = Bitmap.createScaledBitmap(maskBitmap, input_image.getWidth(), input_image.getHeight(), false);
                    Bitmap outputBitmap = overlayMask(input_image, resizedMask);

                    image.setImageBitmap(outputBitmap);

                    // Releases model resources if no longer used.
                    model.close();

                } catch (IOException e) {
                    // TODO Handle the exception
                }
            }
        });
    }

    private Bitmap overlayMask(Bitmap original, Bitmap mask) {
        Bitmap result = Bitmap.createBitmap(original.getWidth(), original.getHeight(), original.getConfig());
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        canvas.drawBitmap(original, 0, 0, null);
        canvas.drawBitmap(mask, 0, 0, paint);
        return result;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK && data != null){
            image_uri = data.getData();
//            image.setImageURI(image_uri);

            input_image=uriToBitmap(image_uri);

            image.setImageBitmap(input_image);


            resized_image=Bitmap.createScaledBitmap(input_image,imageSize,imageSize,false);

//            int dimension_1=Math.min(input_image.getWidth(), input_image.getHeight());
//            int dimension_2=Math.min(resized_image.getWidth(), resized_image.getHeight());
//
//            Log.d("Original Dimensions", String.valueOf(dimension_1));
//            Log.d("Resized Dimensions", String.valueOf(dimension_2));

        }
    }

    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  null;
    }
}