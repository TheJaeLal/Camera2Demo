package lal.jay.camera2demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity {



    public void onCapture(View view) {

    }

    private TextureView textureView;
    private TextureView.SurfaceTextureListener textureViewListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            //Surface Texture is Available
            //width x height -> 1080 x 1860 for Redmi Note 4

            //Setup Camera now since the SurfaceTexture is available
            setupCamera(width, height);
            //Toast.makeText(getApplicationContext(),"SurfaceCameraId = "+cameraId,Toast.LENGTH_SHORT).show();
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    //To hold reference to the Camera Device
    private CameraDevice cameraDevice;

    //Listener to the Camera Device

    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
//            Toast.makeText(getApplicationContext(),"Connected to Camera!",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            cameraDevice = null;
        }
    };

    private String cameraId;
    private Size previewSize;
    private ImageReader imageReader;

    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Do something...
            Log.d("onImageAvailable","Image is available!!!");

            //Image frame..

            Image image =  reader.acquireNextImage();
            if(image == null)
                return;

            //Handle YUV Images....

            //Send them to the server...

//            int width  = image.getWidth();
//            int height = image.getHeight();
//
//            final Image.Plane[] planes = image.getPlanes();
//
//            final ByteBuffer buffer = planes[0].getBuffer();
//            int pixelStride = planes[0].getPixelStride();
//            int rowStride = planes[0].getRowStride();
//
//            int rowPadding = rowStride - pixelStride * width;
//
//            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
//
//            bitmap.copyPixelsFromBuffer(buffer);
//            bitmap = Bitmap.createBitmap(bitmap,0,0,width,height);
//
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss:SSS");
//            Date resultdate = new Date(System.currentTimeMillis());
//            String mFileName = sdf.format(resultdate);
//            File mFile = new File(getActivity().getExternalFilesDir(null), "pic "+mFileName+" preview.jpg");
//            Log.i("Saved file", ""+mFile.toString());
//

            if (image != null)
                image.close();
        }
    };

    private void setupCamera(int width, int height) {
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            for (String camId : camManager.getCameraIdList()) {
                CameraCharacteristics camChars = camManager.getCameraCharacteristics(camId);

                if (camChars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {

                    //Adjust Sensor to device orientation
                    int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                    int totalRotation = sensorToDeviceRotation(camChars, deviceOrientation);

                    boolean swapRotation = totalRotation == 90 || totalRotation == 270;

                    int rotatedWidth = width;
                    int rotatedHeight = height;

                    if (swapRotation) {
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    StreamConfigurationMap map = camChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    //For extracting images/frames..
                    Size[] imgSizes = map.getOutputSizes(ImageReader.class);
                    Size imgSize = chooseOptimalSize(imgSizes,rotatedWidth,rotatedHeight);

                    //Create an ImageReader instance with the specified size and Image format..
                    imageReader = ImageReader.newInstance(imgSize.getWidth(),imgSize.getHeight(), ImageFormat.YUV_420_888,1);

                    //Setup the imageAvailableListener..
                    imageReader.setOnImageAvailableListener(imageAvailableListener,bgThreadHandler);

                    //preview Size is 1440 x 1080 for Redmi Note 4
                    previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);

                    //Setup the global cameraId to this camId
                    cameraId = camId;

                    return;
                }

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    // We need to take the camera Loading and stuff off the UI thread
    // So we create a background thread and a handler for it..

    private HandlerThread bgThread;
    private Handler bgThreadHandler;


    private void startBgThread() {
        bgThread = new HandlerThread("BackgroundThread");
        bgThread.start();

        bgThreadHandler = new Handler(bgThread.getLooper());
    }

    private void stopBgThread() {
        bgThread.quitSafely();

        try {
            bgThread.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        bgThread = null;
        bgThreadHandler = null;
    }


    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        //convert deviceOrientation to degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        //Fixed a bug here, previously returning only deviceOrientation...
        return (sensorOrientation + deviceOrientation + 360) % 360;

    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {

            return Long.signum((long) lhs.getWidth() * lhs.getHeight() /
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width && option.getWidth() >= width && option.getHeight() >= height)
                bigEnough.add(option);

        }

        if (bigEnough.size() > 0)
            return Collections.min(bigEnough, new CompareSizeByArea());
        else
            return choices[0];
    }

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(getApplicationContext(),"Why you reject the request man?\n App needs Camera Access",Toast.LENGTH_SHORT).show();
        }
    }

    private void connectCamera() {
        CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            //For Android Marshmallow Onwards..
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                //If permission has been granted, Open Camera...
                if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                    camManager.openCamera(cameraId, cameraStateCallback, bgThreadHandler);

                //Permission not granted...
                else{

                    if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                        Toast.makeText(this,"App requires Camera Permission!",Toast.LENGTH_SHORT).show();
                    }
                    requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
                }
            }
            else{
                camManager.openCamera(cameraId, cameraStateCallback, bgThreadHandler);
            }


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CaptureRequest.Builder captureRequestBuilder;

    private void startPreview()
    {
        //Get the surface Texture
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();

        //Set its buffer size based on width and height...
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(),previewSize.getHeight());

        //Create a new Surface from the SurfaceTexture
        Surface previewSurface = new Surface(surfaceTexture);

        //Create a list of outputSurfaces (where the Image goes...)

        List<Surface> outputSurfaces = new ArrayList<Surface>(0);

        //The Camera (In App) Preview is one of the Output Surfaces
        outputSurfaces.add(previewSurface);

        //The frame captured is to be processed so imageReader's surface object..
        outputSurfaces.add(imageReader.getSurface());

        try {

            //Builds the Capture Request
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            //For reading and processing each frame ..
//Not Required..
//            captureRequestBuilder.addTarget(imageReader.getSurface());

            //Sets the target for the captured Output, should be an instance of Surface..
            captureRequestBuilder.addTarget(previewSurface);

            //Start the Capture Session
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                    try {
                        //Once the Session is setup/configured set a repeating Capture request, and pass in the
                        //RequestBuilds, these repeating requests are handled by background Thread Handler...
                        //CaptureCallback allows you to process dataFrames after capture!
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, bgThreadHandler);
                    }catch (CameraAccessException e) {
                        e.printStackTrace();
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getApplicationContext(),"Unable to setup Camera Preview",Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Bind textureView variable with layout textureView
        textureView = (TextureView)findViewById(R.id.textureView);

    }

    @Override
    protected void onResume() {
        super.onResume();

        startBgThread();

        //if textureView is not available set a listener that tells us when it's available
        if(!textureView.isAvailable())
            textureView.setSurfaceTextureListener(textureViewListener);

        //If TextureView is Available
        else{
            setupCamera(textureView.getWidth(), textureView.getHeight());
//            Toast.makeText(getApplicationContext(), "CameraId = " + cameraId, Toast.LENGTH_SHORT).show();
            connectCamera();
        }
    }

    @Override
    protected void onPause() {
        //Free up resources when the app is paused..
        closeCamera();

        stopBgThread();

        super.onPause();
    }

    //To close the Camera and free up resources
    private void closeCamera() {
        if(cameraDevice!=null)
        {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        //Get the decorView
        View decorView = getWindow().getDecorView();

        //If the app is brought into focus..
        if(hasFocus)
        {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }

    }



}
